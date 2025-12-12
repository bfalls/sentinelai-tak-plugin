package com.sentinelai.tak.plugin.analysis

import com.sentinelai.tak.plugin.context.MapViewContext
import com.sentinelai.tak.plugin.context.MarkerContext
import com.sentinelai.tak.plugin.context.TakContextProvider
import com.sentinelai.tak.plugin.network.dto.MissionAnalysisRequestDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Clock
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class MissionContextBuilderTest {

    private val fixedClock: Clock = Clock.fixed(OffsetDateTime.parse("2024-03-01T12:00:00Z").toInstant(), ZoneOffset.UTC)
    private val formatter: DateTimeFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME

    @Test
    fun `builds request with markers map extent and mission metadata`() {
        val takProvider = FakeTakContextProvider(
            missionId = "mission-42",
            missionMetadata = mutableMapOf("priority" to "HIGH"),
            missionNotes = "Friendly convoy headed north",
            mapViewContext = MapViewContext(
                centerLatitude = 40.0,
                centerLongitude = -105.0,
                northEastLatitude = 40.5,
                northEastLongitude = -104.5,
                southWestLatitude = 39.5,
                southWestLongitude = -105.5,
                description = "Current AO",
            ),
            selectedMarkers = listOf(
                MarkerContext(
                    id = "alpha",
                    title = "Alpha",
                    description = "Observation point",
                    latitude = 40.01,
                    longitude = -105.01,
                    metadata = mapOf("type" to "OP"),
                    observedAt = OffsetDateTime.parse("2024-03-01T11:30:00Z"),
                )
            ),
        )

        val builder = MissionContextBuilder(
            takContextProvider = takProvider,
            formatter = formatter,
            clock = fixedClock,
        )

        val timeWindow = MissionTimeWindow(
            start = OffsetDateTime.parse("2024-03-01T10:00:00Z"),
            end = OffsetDateTime.parse("2024-03-01T12:00:00Z"),
        )

        val request: MissionAnalysisRequestDto = builder.buildMissionAnalysisRequest(
            question = "Summarize activity",
            includeSelectedMarkers = true,
            includeMapExtent = true,
            includeMissionNotes = true,
            timeWindow = timeWindow,
            selectedMarkers = emptyList(),
            source = "unit-test",
        )

        assertEquals("mission-42", request.missionId)
        assertEquals("Friendly convoy headed north", request.notes)
        assertEquals(2, request.signals.size) // request + one marker
        assertEquals("REQUEST_INFO", request.signals.first().type)
        assertEquals("unit-test", request.signals.first().metadata["source"])
        val markerSignal = request.signals.last()
        assertEquals("MARKER_CONTEXT", markerSignal.type)
        assertEquals("Alpha", markerSignal.metadata["marker_title"])
        assertEquals(40.01, markerSignal.metadata["latitude"])
        assertEquals(-105.01, markerSignal.metadata["longitude"])
        assertEquals("2024-03-01T11:30:00Z", markerSignal.timestamp)

        // Mission metadata is augmented with flags and map extent
        assertEquals("Summarize activity", request.missionMetadata["question"])
        assertEquals(true, request.missionMetadata["include_selected_markers"])
        val mapExtent = request.missionMetadata["map_extent"] as Map<*, *>
        assertEquals(40.0, mapExtent["center_latitude"])
        assertEquals(-105.0, mapExtent["center_longitude"])
        assertEquals("Current AO", mapExtent["description"])

        assertNotNull(request.location)
        assertEquals(40.01, request.location?.latitude)
        assertEquals(-105.01, request.location?.longitude)

        assertEquals("2024-03-01T10:00:00Z", request.timeWindow.start)
        assertEquals("2024-03-01T12:00:00Z", request.timeWindow.end)
    }

    @Test
    fun `falls back to map extent when no markers included`() {
        val takProvider = FakeTakContextProvider(
            missionId = null,
            missionMetadata = mutableMapOf("priority" to "LOW"),
            missionNotes = null,
            mapViewContext = MapViewContext(
                centerLatitude = 12.34,
                centerLongitude = 56.78,
                description = "Cursor extent",
            ),
            selectedMarkers = emptyList(),
        )

        val builder = MissionContextBuilder(
            takContextProvider = takProvider,
            formatter = formatter,
            clock = fixedClock,
        )

        val timeWindow = MissionTimeWindow(
            start = OffsetDateTime.parse("2024-03-01T10:00:00Z"),
            end = OffsetDateTime.parse("2024-03-01T12:00:00Z"),
        )

        val request = builder.buildMissionAnalysisRequest(
            question = "Map extent only",
            includeSelectedMarkers = false,
            includeMapExtent = true,
            includeMissionNotes = false,
            timeWindow = timeWindow,
            selectedMarkers = emptyList(),
            source = "unit-test",
        )

        assertNull(request.missionId)
        assertEquals(1, request.signals.size)
        assertEquals("REQUEST_INFO", request.signals.first().type)
        assertEquals("Map extent only", request.missionMetadata["question"])
        assertNotNull(request.location)
        assertEquals(12.34, request.location?.latitude)
        assertEquals(56.78, request.location?.longitude)
        assertEquals("Cursor extent", request.location?.description)
    }

    private class FakeTakContextProvider(
        private val missionId: String?,
        private val missionMetadata: MutableMap<String, Any?>,
        private val missionNotes: String?,
        private val mapViewContext: MapViewContext?,
        private val selectedMarkers: List<MarkerContext>,
    ) : TakContextProvider {
        override fun getMapView(): MapViewContext? = mapViewContext
        override fun getSelectedMarkers(): List<MarkerContext> = selectedMarkers
        override fun getMissionNotes(): String? = missionNotes
        override fun getMissionId(): String? = missionId
        override fun getMissionMetadata(): MutableMap<String, Any?> = missionMetadata
    }
}
