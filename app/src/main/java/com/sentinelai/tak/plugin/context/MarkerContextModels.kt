package com.sentinelai.tak.plugin.context

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
 * mission analysis panel can pre-populate its fields when opened.
 */
object MissionContextStore {
    @Volatile
    private var preloadedMarker: MarkerContext? = null

    fun preloadMarker(markerContext: MarkerContext) {
        preloadedMarker = markerContext
    }

    fun consumePreloadedMarker(): MarkerContext? {
        val marker = preloadedMarker
        preloadedMarker = null
        return marker
    }
}
