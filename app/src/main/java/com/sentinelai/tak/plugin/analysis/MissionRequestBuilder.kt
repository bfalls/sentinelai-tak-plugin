package com.sentinelai.tak.plugin.analysis

import com.sentinelai.tak.plugin.context.MarkerContext
import com.sentinelai.tak.plugin.network.dto.JsonMap
import com.sentinelai.tak.plugin.network.dto.LocationDto
import com.sentinelai.tak.plugin.network.dto.MissionAnalysisRequestDto
import com.sentinelai.tak.plugin.network.dto.SignalDto
import com.sentinelai.tak.plugin.network.dto.TimeWindowDto
import java.time.format.DateTimeFormatter

/**
 * Builds a mission analysis payload for marker-driven queries while keeping the
 * JSON contract aligned with the SentinelAI backend.
 */
class MissionRequestBuilder(private val formatter: DateTimeFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME) {

    fun buildForMarker(
        markerContext: MarkerContext,
        timeWindow: MissionTimeWindow,
        notes: String? = null,
        missionId: String? = null,
        missionMetadata: JsonMap = emptyMap(),
        intent: String? = null,
        prompt: String? = null,
    ): MissionAnalysisRequestDto {
        val markerMetadata = markerContext.metadata + mapOf(
            "marker_id" to markerContext.id,
            "marker_title" to markerContext.title,
            "marker_description" to markerContext.description,
        )

        val signal = SignalDto(
            type = "MARKER_CONTEXT",
            description = prompt ?: markerContext.description,
            timestamp = markerContext.observedAt?.let { formatter.format(it) },
            metadata = markerMetadata,
        )

        val location = LocationDto(
            latitude = markerContext.latitude,
            longitude = markerContext.longitude,
            description = markerContext.title,
        )

        val timeWindowDto = TimeWindowDto(
            start = formatter.format(timeWindow.start),
            end = formatter.format(timeWindow.end),
        )

        return MissionAnalysisRequestDto(
            missionId = missionId,
            missionMetadata = missionMetadata,
            signals = listOf(signal),
            notes = notes,
            location = location,
            timeWindow = timeWindowDto,
            intent = intent,
        )
    }
}
