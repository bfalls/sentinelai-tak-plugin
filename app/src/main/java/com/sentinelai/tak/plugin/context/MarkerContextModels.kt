package com.sentinelai.tak.plugin.context

import com.sentinelai.tak.plugin.network.dto.JsonMap
import java.time.OffsetDateTime

/**
 * Simple representation of a TAK map marker that can be shared across the
 * SentinelAI plugin without pulling in SDK-only types. The host platform should
 * map its native marker object into this structure before handing it to the
 * plugin.
 */
data class MarkerContext(
    val id: String?,
    val title: String?,
    val description: String?,
    val latitude: Double,
    val longitude: Double,
    val metadata: Map<String, Any?> = emptyMap(),
    val observedAt: OffsetDateTime? = null,
)

/**
 * Store to hold the latest marker the user requested analysis for so the
 * mission analysis panel can pre-populate its fields when opened. It also
 * serves as a lightweight cache for other TAK-context values until the
 * platform-specific providers can be wired in.
 */
object MissionContextStore {
    @Volatile
    private var preloadedMarkers: List<MarkerContext> = emptyList()

    @Volatile
    private var mapViewContext: MapViewContext? = null

    @Volatile
    private var missionNotes: String? = null

    @Volatile
    private var missionId: String? = null

    @Volatile
    private var missionMetadata: JsonMap = emptyMap()

    fun preloadMarker(markerContext: MarkerContext) {
        preloadedMarkers = listOf(markerContext)
    }

    fun preloadMarkers(markers: List<MarkerContext>) {
        preloadedMarkers = markers
    }

    fun getPreloadedMarkers(): List<MarkerContext> = preloadedMarkers

    fun clearPreloadedMarkers() {
        preloadedMarkers = emptyList()
    }

    fun consumePreloadedMarker(): MarkerContext? {
        val marker = preloadedMarkers.firstOrNull()
        clearPreloadedMarkers()
        return marker
    }

    fun updateMapViewContext(viewContext: MapViewContext?) {
        mapViewContext = viewContext
    }

    fun getMapViewContext(): MapViewContext? = mapViewContext

    fun updateMissionNotes(notes: String?) {
        missionNotes = notes
    }

    fun getMissionNotes(): String? = missionNotes

    fun updateMissionId(id: String?) {
        missionId = id
    }

    fun getMissionId(): String? = missionId

    fun updateMissionMetadata(metadata: JsonMap) {
        missionMetadata = metadata
    }

    fun getMissionMetadata(): JsonMap = missionMetadata
}
