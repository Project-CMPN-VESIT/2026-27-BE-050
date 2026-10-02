package com.example.capstone.data.repository

import com.example.capstone.BuildConfig
import com.example.capstone.data.disaster.AlertLevel
import com.example.capstone.data.disaster.DisasterEvidence
import com.example.capstone.data.disaster.DisasterEvent
import com.example.capstone.data.disaster.DisasterSourceStatus
import com.example.capstone.data.disaster.DisasterType
import com.example.capstone.data.disaster.EventStatus
import com.example.capstone.data.disaster.SourceType
import okhttp3.OkHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import java.util.concurrent.TimeUnit

class LiveDisasterRepository {
    private val backendApi by lazy {
        val baseUrl = BuildConfig.BACKEND_BASE_URL.ifBlank { "http://10.0.2.2:3000/" }
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(
                OkHttpClient.Builder()
                    .connectTimeout(12, TimeUnit.SECONDS)
                    .readTimeout(12, TimeUnit.SECONDS)
                    .writeTimeout(12, TimeUnit.SECONDS)
                    .build()
            )
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(BackendApi::class.java)
    }

    suspend fun fetchGlobalLiveEvents(): List<DisasterEvent> = withContext(Dispatchers.IO) {
        backendApi.getLiveEvents()
            .map { it.toDomain() }
            .sortedByDescending { it.lastUpdated }
            .filter { it.alertLevel != AlertLevel.NO_ACTIVE_EVENT }
    }

    suspend fun fetchLiveEvent(eventId: String): DisasterEvent? = withContext(Dispatchers.IO) {
        backendApi.getLiveEvent(eventId).toDomain()
    }

    suspend fun fetchEvidence(eventId: String): List<DisasterEvidence> = withContext(Dispatchers.IO) {
        backendApi.getEvidence(eventId)
            .map { it.toDomain(eventId) }
    }

    suspend fun fetchNearbyEvents(latitude: Double, longitude: Double, radiusKm: Double = 250.0): List<DisasterEvent> =
        withContext(Dispatchers.IO) {
            backendApi.getNearbyEvents(latitude, longitude, radiusKm)
                .map { it.toDomain() }
                .filter { event ->
                    val lat = event.latitude ?: return@filter false
                    val lon = event.longitude ?: return@filter false
                    haversineKm(latitude, longitude, lat, lon) <= radiusKm
                }
        }

    suspend fun fetchNearbyEvidence(latitude: Double, longitude: Double, radiusKm: Double = 250.0): List<DisasterEvidence> =
        withContext(Dispatchers.IO) {
            backendApi.getNearbyEvidence(latitude, longitude, radiusKm)
                .map { it.toDomain(it.eventId ?: "nearby-${latitude}-${longitude}") }
        }

    suspend fun fetchNearbyAlertSummary(latitude: Double, longitude: Double, radiusKm: Double = 250.0): NearbyAlertSummary =
        withContext(Dispatchers.IO) {
            val response = backendApi.getUserLocationAlerts(latitude, longitude, radiusKm)
            val events = response.nearbyEvents.orEmpty().map { it.toDomain() }
            NearbyAlertSummary(
                latitude = response.location?.latitude ?: latitude,
                longitude = response.location?.longitude ?: longitude,
                status = response.summary?.status ?: "NO_ACTIVE_EVENT",
                title = response.summary?.title ?: "No active disaster events detected",
                locationLabel = response.summary?.location ?: response.location?.label,
                detail = response.summary?.detail ?: "No active disaster events detected nearby",
                evidenceConfidence = response.summary?.evidenceConfidence,
                nearbyEvents = events,
            )
        }

        suspend fun getSourceStatus(): List<DisasterSourceStatus> = withContext(Dispatchers.IO) {
            backendApi.getSourceStatus()
                .map { it.toDomain() }
        }

        suspend fun fetchGlobalDisasters(): List<DisasterEvent> = withContext(Dispatchers.IO) {
            backendApi.getGlobalDisasters()
                .map { it.toDomain() }
        }

        suspend fun fetchIndiaDisasters(): List<DisasterEvent> = withContext(Dispatchers.IO) {
            backendApi.getIndiaDisasters()
                .map { it.toDomain() }
        }

    suspend fun summarizeAlerts(locationLabel: String? = null): String = withContext(Dispatchers.IO) {
        val events = fetchGlobalLiveEvents()
        if (events.isEmpty()) {
            return@withContext "No live disaster events detected${if (!locationLabel.isNullOrBlank()) " near $locationLabel" else ""}."
        }
        val top = events.first()
        val prefix = when (top.alertLevel) {
            AlertLevel.AFFECTED -> "AFFECTED"
            AlertLevel.AT_RISK -> "AT RISK"
            AlertLevel.WARNING_MONITORING -> "WARNING/MONITORING"
            AlertLevel.UNVERIFIED -> "UNVERIFIED"
            AlertLevel.NO_ACTIVE_EVENT -> "NO ACTIVE EVENT"
        }
        "$prefix • ${top.displayTitle()} • ${top.location}"
    }

    private interface BackendApi {
        @GET("live-events")
        suspend fun getLiveEvents(): List<BackendEventResponse>

        @GET("live-events/{event_id}")
        suspend fun getLiveEvent(@Path("event_id") eventId: String): BackendEventResponse

        @GET("live-events/nearby")
        suspend fun getNearbyEvents(
            @Query("lat") latitude: Double,
            @Query("lng") longitude: Double,
            @Query("radiusKm") radiusKm: Double
        ): List<BackendEventResponse>

        @GET("evidence/{event_id}")
        suspend fun getEvidence(@Path("event_id") eventId: String): List<BackendEvidenceResponse>

        @GET("evidence/nearby")
        suspend fun getNearbyEvidence(
            @Query("lat") latitude: Double,
            @Query("lng") longitude: Double,
            @Query("radiusKm") radiusKm: Double
        ): List<BackendEvidenceResponse>

        @GET("alerts/user-location")
        suspend fun getUserLocationAlerts(
            @Query("lat") latitude: Double,
            @Query("lng") longitude: Double,
            @Query("radiusKm") radiusKm: Double
        ): BackendNearbyAlertResponse

        @GET("disasters/global")
        suspend fun getGlobalDisasters(): List<BackendEventResponse>

        @GET("disasters/india")
        suspend fun getIndiaDisasters(): List<BackendEventResponse>

        @GET("sources/status")
        suspend fun getSourceStatus(): List<BackendSourceStatusResponse>
    }

    private data class BackendEventResponse(
        val eventId: String? = null,
        val disasterType: String? = null,
        val title: String? = null,
        val alertLevel: String? = null,
        val eventStatus: String? = null,
        val severity: String? = null,
        val location: String? = null,
        val country: String? = null,
        val state: String? = null,
        val district: String? = null,
        val city: String? = null,
        val latitude: Double? = null,
        val longitude: Double? = null,
        val startTime: Long? = null,
        val lastUpdated: Long? = null,
        val sourceSummary: String? = null,
        val evidenceConfidence: Double? = null,
        val evidence: List<BackendEvidenceResponse>? = emptyList()
    ) {
        fun toDomain(): DisasterEvent {
            val eventTitle = title ?: "Live disaster event"
            val disaster = DisasterType.normalize(disasterType ?: eventTitle)
            val evidenceList = evidence.orEmpty().map { it.toDomain(eventId ?: eventTitle.hashCode().toString()) }
            return DisasterEvent(
                eventId = eventId ?: "event-${eventTitle.hashCode()}",
                disasterType = disaster,
                title = eventTitle,
                alertLevel = LiveDisasterRepository.toAlertLevel(alertLevel),
                eventStatus = LiveDisasterRepository.toEventStatus(eventStatus),
                severity = severity ?: "monitoring",
                location = location ?: "Global",
                country = country,
                state = state,
                district = district,
                city = city,
                latitude = latitude,
                longitude = longitude,
                startTime = startTime ?: lastUpdated ?: System.currentTimeMillis(),
                lastUpdated = lastUpdated ?: startTime ?: System.currentTimeMillis(),
                sourceSummary = sourceSummary ?: "Backend live source",
                evidenceConfidence = evidenceConfidence ?: 0.0,
                evidence = evidenceList
            )
        }
    }

    private data class BackendEvidenceResponse(
        val source: String? = null,
        val sourceType: String? = null,
        val title: String? = null,
        val text: String? = null,
        val originalUrl: String? = null,
        val timestamp: Long? = null,
        val location: String? = null,
        val country: String? = null,
        val state: String? = null,
        val district: String? = null,
        val city: String? = null,
        val latitude: Double? = null,
        val longitude: Double? = null,
        val disasterType: String? = null,
        val context: String? = null,
        val semanticConfidence: Double? = null,
        val sourceTrust: Double? = null,
        val relevance: Double? = null,
        val usableAsEvidence: Boolean? = null,
        val duplicateGroup: String? = null,
        val eventId: String? = null
    ) {
        fun toDomain(defaultEventId: String): DisasterEvidence {
            val evidenceTitle = title ?: "Disaster evidence"
            val evidenceLocation = location ?: "Global"
            return DisasterEvidence(
                source = source ?: "Live source",
                sourceType = LiveDisasterRepository.toSourceType(sourceType),
                title = evidenceTitle,
                text = text ?: evidenceTitle,
                originalUrl = originalUrl ?: "",
                timestamp = timestamp ?: System.currentTimeMillis(),
                location = evidenceLocation,
                country = country,
                state = state,
                district = district,
                city = city,
                latitude = latitude,
                longitude = longitude,
                disasterType = DisasterType.normalize(disasterType ?: evidenceTitle),
                context = LiveDisasterRepository.toEventStatus(context),
                semanticConfidence = semanticConfidence ?: 0.0,
                sourceTrust = sourceTrust ?: 0.0,
                relevance = relevance ?: 0.0,
                usableAsEvidence = usableAsEvidence ?: false,
                duplicateGroup = duplicateGroup,
                eventId = eventId ?: defaultEventId
            )
        }
    }

    private data class BackendSourceStatusResponse(
        val source: String? = null,
        val configured: Boolean? = null,
        val status: String? = null,
        val reason: String? = null,
        val key: String? = null
    ) {
        fun toDomain(): DisasterSourceStatus {
            return DisasterSourceStatus(
                source = source ?: key ?: "Unknown",
                configured = configured ?: false,
                status = status ?: "unknown",
                reason = reason ?: ""
            )
        }
    }

    private data class BackendNearbyAlertResponse(
        val location: BackendLocationResponse? = null,
        val nearbyEvents: List<BackendEventResponse>? = emptyList(),
        val summary: BackendAlertSummaryResponse? = null
    )

    private data class BackendLocationResponse(
        val latitude: Double? = null,
        val longitude: Double? = null,
        val label: String? = null
    )

    private data class BackendAlertSummaryResponse(
        val status: String? = null,
        val title: String? = null,
        val location: String? = null,
        val disasterType: String? = null,
        val evidenceConfidence: Double? = null,
        val detail: String? = null
    )

    private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1.0 - a))
        return earthRadius * c
    }

    companion object {
        fun toAlertLevel(value: String?): AlertLevel = when (value?.uppercase()) {
            "AFFECTED" -> AlertLevel.AFFECTED
            "AT_RISK" -> AlertLevel.AT_RISK
            "WARNING_MONITORING" -> AlertLevel.WARNING_MONITORING
            "UNVERIFIED" -> AlertLevel.UNVERIFIED
            else -> AlertLevel.NO_ACTIVE_EVENT
        }

        fun toEventStatus(value: String?): EventStatus = when (value?.uppercase()) {
            "ACTIVE_EVENT" -> EventStatus.ACTIVE_EVENT
            "AT_RISK" -> EventStatus.AT_RISK
            "FORECAST_WARNING" -> EventStatus.FORECAST_WARNING
            "PREPAREDNESS" -> EventStatus.PREPAREDNESS
            "RECOVERY" -> EventStatus.RECOVERY
            "HISTORICAL" -> EventStatus.HISTORICAL
            "GENERAL_INFORMATION" -> EventStatus.GENERAL_INFORMATION
            "POLICY" -> EventStatus.POLICY
            "UNVERIFIED_REPORT" -> EventStatus.UNVERIFIED_REPORT
            "IRRELEVANT" -> EventStatus.IRRELEVANT
            else -> EventStatus.GENERAL_INFORMATION
        }

        fun toSourceType(value: String?): SourceType = when (value?.uppercase()) {
            "OFFICIAL" -> SourceType.OFFICIAL
            "SOCIAL" -> SourceType.SOCIAL
            "VIDEO" -> SourceType.VIDEO
            "RELIEF_WEB" -> SourceType.RELIEF_WEB
            "AI_ANALYSIS" -> SourceType.AI_ANALYSIS
            "HISTORICAL_RISK" -> SourceType.HISTORICAL_RISK
            else -> SourceType.NEWS
        }
    }
}

data class NearbyAlertSummary(
    val latitude: Double,
    val longitude: Double,
    val status: String,
    val title: String,
    val locationLabel: String?,
    val detail: String,
    val evidenceConfidence: Double?,
    val nearbyEvents: List<DisasterEvent>
)
