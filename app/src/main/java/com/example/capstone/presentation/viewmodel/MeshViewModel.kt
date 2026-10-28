package com.example.capstone.presentation.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.capstone.data.MeshMessage
import com.example.capstone.data.MeshTelemetryState
import com.example.capstone.data.SafeReadyPreferences
import com.example.capstone.data.repository.MeshRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MeshViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = MeshRepository(application.applicationContext)
    private val prefs = SafeReadyPreferences(application.applicationContext)

    private val _connectionState = MutableStateFlow(com.example.capstone.data.MeshConnectionState())
    val connectionState: StateFlow<com.example.capstone.data.MeshConnectionState> = _connectionState

    private val _messages = MutableStateFlow<List<MeshMessage>>(repo.getCachedMessages())
    val messages: StateFlow<List<MeshMessage>> = _messages

    private val _telemetry = MutableStateFlow(MeshTelemetryState())
    val telemetry: StateFlow<MeshTelemetryState> = _telemetry

    private val _nearbyDevices = MutableStateFlow<List<com.example.capstone.data.MeshDevice>>(emptyList())
    val nearbyDevices: StateFlow<List<com.example.capstone.data.MeshDevice>> = _nearbyDevices

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage

    init {
        viewModelScope.launch {
            // Observe state and messages from repo
            repo.state.collectLatest { _connectionState.value = it }
        }

        viewModelScope.launch {
            repo.messages.collectLatest { _messages.value = it }
        }

        viewModelScope.launch {
            repo.telemetry.collectLatest { _telemetry.value = it }
        }

        viewModelScope.launch {
            repo.nearbyDevices.collectLatest { _nearbyDevices.value = it }
        }
    }

    fun start(displayName: String) {
        if (!isEmergencyModeEnabled()) {
            _actionMessage.value = "Emergency Mode is required to start mesh service"
            return
        }
        repo.start(displayName)
    }

    fun startAdvertisingOnly() {
        if (!isEmergencyModeEnabled()) {
            _actionMessage.value = "Emergency Mode is required to start mesh service"
            return
        }
        val name = prefs.getUserProfile().name
        _actionMessage.value = "Starting advertise-only mode..."
        repo.startAdvertisingOnly(name)
        _actionMessage.value = "Advertise-only started"
    }

    fun startDiscoveryOnly() {
        if (!isEmergencyModeEnabled()) {
            _actionMessage.value = "Emergency Mode is required to start mesh service"
            return
        }
        val name = prefs.getUserProfile().name
        _actionMessage.value = "Starting discovery-only mode..."
        repo.startDiscoveryOnly(name)
        _actionMessage.value = "Discovery-only started"
    }

    fun stop() {
        repo.stop()
    }

    fun broadcast(message: MeshMessage) {
        if (!isEmergencyModeEnabled()) {
            _actionMessage.value = "Emergency Mode is required to send mesh messages"
            return
        }
        repo.broadcast(message)
    }

    fun acknowledge(messageId: String, deviceId: String) {
        repo.acknowledge(messageId, deviceId)
    }

    fun clearTelemetry() {
        repo.clearTelemetry()
    }

    fun resendLatestFailed() {
        viewModelScope.launch {
            if (!isEmergencyModeEnabled()) {
                _actionMessage.value = "Emergency Mode is required to retry failed mesh messages"
                return@launch
            }
            _actionMessage.value = repo.resendLatestFailed()
        }
    }

    fun restartMesh() {
        val name = prefs.getUserProfile().name
        _actionMessage.value = "Restarting mesh discovery..."
        repo.stop()
        repo.start(name)
        _actionMessage.value = "Mesh discovery restarted"
    }

    private fun isEmergencyModeEnabled(): Boolean = prefs.getEmergencyModeEnabled()

    fun clearActionMessage() {
        _actionMessage.value = null
    }

    fun getRoomMigrationPreviewCount(): Int = repo.getRoomMigrationPreviewCount()
}

