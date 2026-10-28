package com.example.capstone.data.repository

import android.content.Context
import com.example.capstone.data.MeshConnectionState
import com.example.capstone.data.MeshDevice
import com.example.capstone.data.MeshMessage
import com.example.capstone.data.MeshMessageType
import com.example.capstone.data.MeshSendStatus
import com.example.capstone.data.MeshTelemetryState
import com.example.capstone.data.SafeReadyPreferences
import com.example.capstone.data.local.mesh.MeshDatabase
import com.example.capstone.data.local.mesh.MeshMessageEntity
import com.example.capstone.data.local.mesh.MeshDeviceEntity
import com.example.capstone.data.local.mesh.MeshRoomMigrationPlan
import com.example.capstone.data.local.mesh.MeshMessageCache
import com.example.capstone.service.MeshService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * MeshRepository — thin adapter between the app and the transport layer.
 *
 * Responsibilities:
 * - Start/stop the transport
 * - Persist received messages to MeshMessageCache
 * - Provide reactive streams for connection state and cached messages
 */
class MeshRepository(context: Context) {
    private val cache = MeshMessageCache(context)
    private val database = MeshDatabase.getInstance(context)
    private val dao = database.meshMessageDao()
    private val deviceDao = database.meshDeviceDao()
    private val service = MeshService(context)
    private val prefs = SafeReadyPreferences(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(MeshConnectionState())
    val state = _state.asStateFlow()

    private val _messages = MutableStateFlow<List<MeshMessage>>(emptyList())
    val messages = _messages.asStateFlow()

    private val _telemetry = MutableStateFlow(MeshTelemetryState())
    val telemetry = _telemetry.asStateFlow()

    private val _nearbyDevices = MutableStateFlow<List<MeshDevice>>(emptyList())
    val nearbyDevices = _nearbyDevices.asStateFlow()

    private val localDeviceId by lazy { cache.getOrCreateDeviceId() }

    init {
        scope.launch {
            seedRoomFromLegacyCacheIfNeeded()
            dao.observeRecent(MESSAGE_LIMIT).collectLatest { entities ->
                _messages.value = entities.map { it.toDomain() }
            }
        }
        scope.launch {
            deviceDao.observeAll().collectLatest { entities ->
                _nearbyDevices.value = entities.map { MeshRoomMigrationPlan.toDomain(it) }
            }
        }
    }

    private val listener = object : MeshService.Listener {
        override fun onStateChanged(state: MeshConnectionState) {
            _state.value = state
            if (state.connectedDevices > 0) {
                scope.launch { flushPendingMessages() }
            }
        }

        override fun onEndpointDiscovered(deviceId: String, deviceName: String, signalStrength: Int?) {
            scope.launch {
                val existing = deviceDao.getById(deviceId)
                val device = MeshDeviceEntity(
                    deviceId = deviceId,
                    deviceName = deviceName,
                    userId = existing?.userId,
                    lastSeen = System.currentTimeMillis(),
                    signalStrength = signalStrength,
                    estimatedDistanceMeters = existing?.estimatedDistanceMeters,
                    isActive = true
                )
                deviceDao.upsert(device)
            }
        }

        override fun onEndpointLost(deviceId: String) {
            scope.launch {
                deviceDao.markInactive(deviceId)
            }
        }

        override fun onMessageReceived(message: MeshMessage) {
            scope.launch { 
                handleInboundMessage(message)
                if (message.type == MeshMessageType.SOS) {
                    sendAcknowledge(message)
                }
            }
        }

        override fun onConnectionError(message: String) {
            _state.value = _state.value.copy(lastError = message)
            _telemetry.value = _telemetry.value.copy(lastError = message)
        }
    }

    private suspend fun sendAcknowledge(sosMessage: MeshMessage) {
        val ack = MeshMessage(
            senderId = localDeviceId,
            senderName = prefs.getUserProfile().name,
            type = MeshMessageType.ACK,
            content = "ACK:${sosMessage.id}"
        )
        service.broadcastMessage(ack)
    }

    fun start(displayName: String) {
        if (!prefs.getEmergencyModeEnabled()) {
            _state.value = _state.value.copy(lastError = "Emergency Mode is required to start mesh service")
            return
        }
        service.start(displayName, listener)
        // Seed initial state
        _state.value = _state.value.copy(isAdvertising = service.isRunning(), isDiscovering = service.isRunning())
    }

    fun startAdvertisingOnly(displayName: String) {
        if (!prefs.getEmergencyModeEnabled()) {
            _state.value = _state.value.copy(lastError = "Emergency Mode is required to start mesh service")
            return
        }
        service.startAdvertisingOnly(displayName, listener)
        _state.value = _state.value.copy(isAdvertising = service.isRunning(), isDiscovering = false)
    }

    fun startDiscoveryOnly(displayName: String) {
        if (!prefs.getEmergencyModeEnabled()) {
            _state.value = _state.value.copy(lastError = "Emergency Mode is required to start mesh service")
            return
        }
        service.startDiscoveryOnly(displayName, listener)
        _state.value = _state.value.copy(isAdvertising = false, isDiscovering = service.isRunning())
    }

    fun stop() {
        service.stop()
        _state.value = _state.value.copy(isAdvertising = false, isDiscovering = false, connectedDevices = 0)
    }

    fun broadcast(message: MeshMessage) {
        if (!prefs.getEmergencyModeEnabled()) {
            _telemetry.value = _telemetry.value.copy(lastError = "Emergency Mode is required to send mesh messages")
            return
        }
        scope.launch {
            val normalized = normalizeOutgoing(message)
                .copy(sendStatus = MeshSendStatus.PENDING, retryCount = 0, lastAttemptAt = System.currentTimeMillis())
            dao.upsert(normalized.toEntity())
            dao.deleteExpired(System.currentTimeMillis())
            sendWithRetry(normalized, isRelay = false)
        }
    }

    fun acknowledge(messageId: String, deviceId: String) {
        scope.launch {
            val existing = dao.getById(messageId)?.toDomain() ?: return@launch
            dao.upsert(existing.copy(acknowledgedBy = existing.acknowledgedBy + deviceId).toEntity())
        }
    }

    fun getCachedMessages() = _messages.value

    fun getDeviceId(): String = cache.getOrCreateDeviceId()

    fun getDisplayName(): String = cache.getDisplayName()

    fun setDisplayName(name: String) = cache.setDisplayName(name)

    fun getRoomMigrationPreviewCount(): Int {
        return MeshRoomMigrationPlan.toEntities(cache.getMessages()).size
    }

    suspend fun resendLatestFailed(): String {
        if (!prefs.getEmergencyModeEnabled()) {
            return "Emergency Mode is required to retry failed mesh messages"
        }
        val failed = dao.getLatestFailed()?.toDomain() ?: return "No failed mesh message found"
        val retryMessage = failed.copy(
            sendStatus = MeshSendStatus.PENDING,
            retryCount = failed.retryCount + 1,
            lastAttemptAt = System.currentTimeMillis(),
        )
        dao.upsert(retryMessage.toEntity())
        sendWithRetry(retryMessage, isRelay = false)
        return if (_telemetry.value.lastError.isNullOrBlank()) {
            "Retry finished for ${failed.id}"
        } else {
            "Retry attempted for ${failed.id}: ${_telemetry.value.lastError}"
        }
    }

    fun clearTelemetry() {
        _telemetry.value = MeshTelemetryState()
    }

    private suspend fun handleInboundMessage(message: MeshMessage) {
        dao.deleteExpired(System.currentTimeMillis())

        if (message.isExpired()) {
            _telemetry.value = _telemetry.value.copy(droppedExpired = _telemetry.value.droppedExpired + 1)
            return
        }
        
        if (message.type == MeshMessageType.ACK && message.content.startsWith("ACK:")) {
            val targetId = message.content.removePrefix("ACK:")
            acknowledge(targetId, message.senderId)
            return
        }

        if (dao.hasMessage(message.id)) {
            _telemetry.value = _telemetry.value.copy(droppedDuplicate = _telemetry.value.droppedDuplicate + 1)
            return
        }
        if (message.relayedBy.contains(localDeviceId)) {
            _telemetry.value = _telemetry.value.copy(droppedDuplicate = _telemetry.value.droppedDuplicate + 1)
            return
        }

        dao.upsert(message.copy(sendStatus = MeshSendStatus.SENT).toEntity())

        if (shouldRelay(message)) {
            val relayed = message.nextRelay(localDeviceId).copy(
                sendStatus = MeshSendStatus.RELAYED,
                lastAttemptAt = System.currentTimeMillis(),
            )
            dao.upsert(relayed.toEntity())
            
            // Jitter to avoid collision
            delay((200..1000).random().toLong())
            sendWithRetry(relayed, isRelay = true)
        }
    }

    private suspend fun flushPendingMessages() {
        if (service.getConnectedCount() <= 0) return

        val queued = dao.getRecent(MESSAGE_LIMIT)
            .filter { message ->
                (message.type == MeshMessageType.SOS || message.type == MeshMessageType.ALERT) &&
                    (message.sendStatus == MeshSendStatus.PENDING || message.sendStatus == MeshSendStatus.FAILED)
            }
            .map { it.toDomain() }
            .sortedByDescending { it.timestamp }

        queued.forEach { sendWithRetry(it, isRelay = false) }
    }

    private fun shouldRelay(message: MeshMessage): Boolean {
        // Only relay if it's a high-priority type (SOS, ALERT)
        // and it hasn't exceeded TTL or reached us before
        return (message.type == MeshMessageType.SOS || message.type == MeshMessageType.ALERT) &&
            message.shouldRelay() &&
            !message.relayedBy.contains(localDeviceId)
    }

    private suspend fun sendWithRetry(message: MeshMessage, isRelay: Boolean, maxRetries: Int = 2) {
        if (service.getConnectedCount() <= 0) {
            dao.upsert(message.copy(sendStatus = MeshSendStatus.PENDING).toEntity())
            _telemetry.value = _telemetry.value.copy(lastError = "Queued for relay until a nearby device is available")
            return
        }

        var attempt = 0
        while (attempt <= maxRetries) {
            val attemptMessage = message.copy(
                retryCount = message.retryCount + attempt,
                lastAttemptAt = System.currentTimeMillis(),
                sendStatus = MeshSendStatus.PENDING,
            )
            dao.upsert(attemptMessage.toEntity())

            val success = service.broadcastMessage(message)
            if (success) {
                dao.upsert(
                    attemptMessage.copy(
                        sendStatus = if (isRelay) MeshSendStatus.RELAYED else MeshSendStatus.SENT,
                    ).toEntity()
                )
                _telemetry.value = _telemetry.value.copy(
                    sent = _telemetry.value.sent + 1,
                    relayed = _telemetry.value.relayed + if (isRelay) 1 else 0,
                )
                return
            }

            if (attempt == maxRetries) {
                dao.upsert(attemptMessage.copy(sendStatus = MeshSendStatus.FAILED).toEntity())
                _telemetry.value = _telemetry.value.copy(
                    failed = _telemetry.value.failed + 1,
                    lastError = _state.value.lastError ?: "Send failed after retries",
                )
                return
            }

            attempt += 1
            _telemetry.value = _telemetry.value.copy(retried = _telemetry.value.retried + 1)
            delay(attempt * 700L)
        }
    }

    private fun normalizeOutgoing(message: MeshMessage): MeshMessage {
        val cleaned = if (message.senderId.isBlank()) {
            message.copy(senderId = localDeviceId)
        } else message

        return if (cleaned.ttl <= 0 || cleaned.isExpired()) {
            cleaned.copy(ttl = MeshMessage.DEFAULT_TTL, expiresAt = System.currentTimeMillis() + MeshMessage.DEFAULT_TTL_MS)
        } else {
            cleaned
        }
    }

    private suspend fun seedRoomFromLegacyCacheIfNeeded() {
        if (dao.countMessages() > 0) return
        val legacyMessages = cache.getMessages()
        if (legacyMessages.isNotEmpty()) {
            dao.upsertAll(MeshRoomMigrationPlan.toEntities(legacyMessages))
        }
        val legacyDevices = cache.getDevices()
        if (legacyDevices.isNotEmpty()) {
            deviceDao.upsertAll(MeshRoomMigrationPlan.toDeviceEntities(legacyDevices))
        }
    }

    private fun MeshMessage.toEntity(): MeshMessageEntity {
        return MeshMessageEntity(
            id = id,
            senderId = senderId,
            senderName = senderName,
            timestamp = timestamp,
            type = type,
            content = content,
            location = location,
            signalStrength = signalStrength,
            ttl = ttl,
            hopCount = hopCount,
            acknowledgedBy = acknowledgedBy,
            relayedBy = relayedBy,
            expiresAt = expiresAt,
            sendStatus = sendStatus,
            retryCount = retryCount,
            lastAttemptAt = lastAttemptAt,
        )
    }

    private fun MeshMessageEntity.toDomain(): MeshMessage {
        return MeshMessage(
            id = id,
            senderId = senderId,
            senderName = senderName,
            timestamp = timestamp,
            type = type,
            content = content,
            location = location,
            signalStrength = signalStrength,
            ttl = ttl,
            hopCount = hopCount,
            acknowledgedBy = acknowledgedBy,
            relayedBy = relayedBy,
            expiresAt = expiresAt,
            sendStatus = sendStatus,
            retryCount = retryCount,
            lastAttemptAt = lastAttemptAt,
        )
    }

    companion object {
        private const val MESSAGE_LIMIT = 100
    }
}
