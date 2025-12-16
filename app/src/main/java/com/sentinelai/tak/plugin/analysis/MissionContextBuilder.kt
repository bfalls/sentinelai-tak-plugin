package com.sentinelai.tak.plugin.analysis

import com.sentinelai.tak.plugin.context.MapViewContext
import com.sentinelai.tak.plugin.context.MarkerContext
import com.sentinelai.tak.plugin.context.TakContextProvider
import com.sentinelai.tak.plugin.location.CivTakLocation
import com.sentinelai.tak.plugin.location.CivTakLocationProvider
import com.sentinelai.tak.plugin.location.OwnshipLocationProvider
import com.sentinelai.tak.plugin.network.dto.JsonMap
import com.sentinelai.tak.plugin.network.dto.LocationDto
import com.sentinelai.tak.plugin.network.dto.MissionAnalysisRequestDto
import com.sentinelai.tak.plugin.network.dto.SignalDto
import com.sentinelai.tak.plugin.network.dto.TimeWindowDto
import java.time.Clock
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/**
 * Builds a mission analysis payload using current CivTAK/ATAK context as the
 * source of truth. The host platform should supply a [TakContextProvider]
 * backed by the real TAK APIs.
 */
class MissionContextBuilder(
    private val takContextProvider: TakContextProvider,
    private val locationProvider: OwnshipLocationProvider = CivTakLocationProvider(),
    private val formatter: DateTimeFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME,
    private val clock: Clock = Clock.systemUTC(),
) {

    fun buildMissionAnalysisRequest(
        question: String,
        includeSelectedMarkers: Boolean,
        includeMapExtent: Boolean,
        includeMissionNotes: Boolean,
        timeWindow: MissionTimeWindow,
        selectedMarkers: List<MarkerContext> = emptyList(),
        source: String = "mission_analysis_panel",
    ): MissionAnalysisRequestDto {
        val missionId = takContextProvider.getMissionId()
        val missionMetadata = buildMissionMetadata(
            question = question,
            includeSelectedMarkers = includeSelectedMarkers,
            includeMapExtent = includeMapExtent,
            includeMissionNotes = includeMissionNotes,
            mapViewContext = takContextProvider.getMapView(),
        )

        val requestSignal = SignalDto(
            type = "REQUEST_INFO",
            description = question,
            timestamp = formatter.format(OffsetDateTime.now(clock)),
            metadata = mapOf("source" to source),
        )

        val markers = if (includeSelectedMarkers) {
            (selectedMarkers + takContextProvider.getSelectedMarkers()).distinctBy { markerSignature(it) }
        } else {
            emptyList()
        }

        val markerSignals = markers.map { marker ->
            SignalDto(
                type = "MARKER_CONTEXT",
                description = marker.description ?: marker.title,
                timestamp = marker.observedAt?.let { formatter.format(it) },
                metadata = marker.metadata + mapOf(
                    "marker_id" to marker.id,
                    "marker_title" to marker.title,
                    "marker_description" to marker.description,
                    "latitude" to marker.latitude,
                    "longitude" to marker.longitude,
                ),
            )
        }

        val notes = if (includeMissionNotes) takContextProvider.getMissionNotes() else null

        val location = locationProvider.getCurrentLocation()?.let { ownshipLocation(it) }
            ?: when {
                markers.isNotEmpty() -> markerLocation(markers.first())
                includeMapExtent -> takContextProvider.getMapView()?.let { mapViewLocation(it) }
                else -> null
            }

        val timeWindowDto = TimeWindowDto(
            start = formatter.format(timeWindow.start),
            end = formatter.format(timeWindow.end),
        )

        return MissionAnalysisRequestDto(
            missionId = missionId,
            missionMetadata = missionMetadata,
            signals = listOf(requestSignal) + markerSignals,
            notes = notes,
            location = location,
            timeWindow = timeWindowDto,
            intent = null,
        )
    }

    private fun buildMissionMetadata(
        question: String,
        includeSelectedMarkers: Boolean,
        includeMapExtent: Boolean,
        includeMissionNotes: Boolean,
        mapViewContext: MapViewContext?,
    ): JsonMap {
        val metadata = takContextProvider.getMissionMetadata().toMutableMap()
        metadata["question"] = question
        metadata["include_selected_markers"] = includeSelectedMarkers
        metadata["include_map_extent"] = includeMapExtent
        metadata["include_mission_notes"] = includeMissionNotes

        if (includeMapExtent) {
            mapViewContext?.let { context ->
                metadata["map_extent"] = mapOf(
                    "center_latitude" to context.centerLatitude,
                    "center_longitude" to context.centerLongitude,
                    "north_east_latitude" to context.northEastLatitude,
                    "north_east_longitude" to context.northEastLongitude,
                    "south_west_latitude" to context.southWestLatitude,
                    "south_west_longitude" to context.southWestLongitude,
                    "description" to context.description,
                )
            }
        }

        return metadata
    }

    private fun markerSignature(marker: MarkerContext): String =
        listOf(marker.id, marker.latitude, marker.longitude).joinToString(":")

    private fun ownshipLocation(location: CivTakLocation): LocationDto =
        LocationDto(
            latitude = location.latitude,
            longitude = location.longitude,
            altitudeMeters = location.altitudeMeters,
            description = "Ownship",
            horizontalSource = location.horizontalSource,
            verticalSource = location.verticalSource,
        )

    private fun markerLocation(marker: MarkerContext): LocationDto =
        LocationDto(
            latitude = marker.latitude,
            longitude = marker.longitude,
            description = marker.title ?: marker.description,
        )

    private fun mapViewLocation(mapViewContext: MapViewContext): LocationDto =
        LocationDto(
            latitude = mapViewContext.centerLatitude,
            longitude = mapViewContext.centerLongitude,
            description = mapViewContext.description,
        )
}
