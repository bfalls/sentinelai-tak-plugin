package com.sentinelai.tak.plugin.network.dto

import com.squareup.moshi.Json

typealias JsonMap = Map<String, Any?>

/**
 * Data transfer objects aligned with the SentinelAI backend contract.
 */
data class SignalDto(
    @Json(name = "type") val type: String,
    @Json(name = "description") val description: String? = null,
    @Json(name = "timestamp") val timestamp: String? = null,
    @Json(name = "metadata") val metadata: JsonMap = emptyMap(),
)

data class LocationDto(
    @Json(name = "latitude") val latitude: Double,
    @Json(name = "longitude") val longitude: Double,
    @Json(name = "altitude_meters") val altitudeMeters: Double? = null,
    @Json(name = "description") val description: String? = null,
    @Json(name = "horizontal_source") val horizontalSource: String? = null,
    @Json(name = "vertical_source") val verticalSource: String? = null,
)

data class TimeWindowDto(
    @Json(name = "start") val start: String,
    @Json(name = "end") val end: String,
)

data class MissionAnalysisRequestDto(
    @Json(name = "mission_id") val missionId: String? = null,
    @Json(name = "mission_metadata") val missionMetadata: JsonMap = emptyMap(),
    @Json(name = "signals") val signals: List<SignalDto> = emptyList(),
    @Json(name = "notes") val notes: String? = null,
    @Json(name = "location") val location: LocationDto? = null,
    @Json(name = "time_window") val timeWindow: TimeWindowDto,
    @Json(name = "intent") val intent: String? = null,
)

data class MissionAnalysisResponseDto(
    @Json(name = "intent") val intent: String,
    @Json(name = "summary") val summary: String,
    @Json(name = "risks") val risks: List<String>,
    @Json(name = "recommendations") val recommendations: List<String>,
)
