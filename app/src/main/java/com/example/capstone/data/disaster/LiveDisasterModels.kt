package com.example.capstone.data.disaster

import java.util.Locale

enum class DisasterType {
    FLOOD,
    FLASH_FLOOD,
    CYCLONE,
    HURRICANE,
    TROPICAL_STORM,
    EARTHQUAKE,
    TSUNAMI,
    LANDSLIDE,
    MUDSLIDE,
    AVALANCHE,
    WILDFIRE,
    VOLCANIC_ERUPTION,
    TORNADO,
    SEVERE_STORM,
    LIGHTNING,
    EXTREME_RAINFALL,
    DROUGHT,
    HEATWAVE,
    EXTREME_HEAT,
    COLD_WAVE,
    STORM_SURGE,
    COASTAL_FLOODING,
    DAM_RESERVOIR_FLOODING,
    OTHER;

    companion object {
        fun normalize(raw: String?): DisasterType {
            val normalized = raw?.trim()?.lowercase(Locale.US) ?: ""
            return when {
                normalized.contains("flash flood") || normalized.contains("flashflood") -> FLASH_FLOOD
                normalized.contains("flood") -> FLOOD
                normalized.contains("cyclone") || normalized.contains("storm surge") -> CYCLONE
                normalized.contains("hurricane") -> HURRICANE
                normalized.contains("tropical") -> TROPICAL_STORM
                normalized.contains("earthquake") || normalized.contains("quake") -> EARTHQUAKE
                normalized.contains("tsunami") -> TSUNAMI
                normalized.contains("landslide") -> LANDSLIDE
                normalized.contains("mudslide") -> MUDSLIDE
                normalized.contains("avalanche") -> AVALANCHE
                normalized.contains("wildfire") || normalized.contains("forest fire") || normalized.contains("brush fire") -> WILDFIRE
                normalized.contains("volcan") -> VOLCANIC_ERUPTION
                normalized.contains("tornado") -> TORNADO
                normalized.contains("lightning") -> LIGHTNING
                normalized.contains("severe storm") || normalized.contains("storm") -> SEVERE_STORM
                normalized.contains("heatwave") || normalized.contains("extreme heat") || normalized.contains("heat wave") -> HEATWAVE
                normalized.contains("drought") -> DROUGHT
                normalized.contains("cold wave") || normalized.contains("coldwave") -> COLD_WAVE
                normalized.contains("storm surge") -> STORM_SURGE
                normalized.contains("coastal") && normalized.contains("flood") -> COASTAL_FLOODING
                normalized.contains("dam") || normalized.contains("reservoir") -> DAM_RESERVOIR_FLOODING
                else -> OTHER
            }
        }
    }
}

enum class EventStatus {
    ACTIVE_EVENT,
    AT_RISK,
    FORECAST_WARNING,
    PREPAREDNESS,
    RECOVERY,
    HISTORICAL,
    GENERAL_INFORMATION,
    POLICY,
    UNVERIFIED_REPORT,
    IRRELEVANT
}

enum class SourceType {
    OFFICIAL,
    NEWS,
    SOCIAL,
    VIDEO,
    RELIEF_WEB,
    AI_ANALYSIS,
    HISTORICAL_RISK
}

enum class AlertLevel {
    AFFECTED,
    AT_RISK,
    WARNING_MONITORING,
    UNVERIFIED,
    NO_ACTIVE_EVENT
}

data class DisasterEvidence(
    val source: String,
    val sourceType: SourceType,
    val title: String,
    val text: String,
    val originalUrl: String,
    val timestamp: Long,
    val location: String,
    val country: String? = null,
    val state: String? = null,
    val district: String? = null,
    val city: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val disasterType: DisasterType = DisasterType.OTHER,
    val context: EventStatus = EventStatus.ACTIVE_EVENT,
    val semanticConfidence: Double = 0.0,
    val sourceTrust: Double = 0.0,
    val relevance: Double = 0.0,
    val usableAsEvidence: Boolean = false,
    val duplicateGroup: String? = null,
    val eventId: String? = null
)

data class DisasterEvent(
    val eventId: String,
    val disasterType: DisasterType,
    val title: String,
    val alertLevel: AlertLevel,
    val eventStatus: EventStatus,
    val severity: String,
    val location: String,
    val country: String? = null,
    val state: String? = null,
    val district: String? = null,
    val city: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val startTime: Long,
    val lastUpdated: Long,
    val sourceSummary: String,
    val evidenceConfidence: Double,
    val evidence: List<DisasterEvidence>
) {
    fun displayTitle(): String = title.ifBlank { disasterType.name.replace('_', ' ').lowercase(Locale.US).replaceFirstChar { it.titlecase(Locale.US) } }
}

data class DisasterSourceStatus(
    val source: String,
    val configured: Boolean,
    val status: String,
    val reason: String = ""
)
