package com.sentinelai.tak.plugin.context

import com.sentinelai.tak.plugin.network.dto.JsonMap

/**
 * Simplified map view details that can be sourced from CivTAK/ATAK APIs. The
 * extent fields are optional so hosts can fill in as much fidelity as they
 * support.
 */
data class MapViewContext(
    val centerLatitude: Double,
    val centerLongitude: Double,
    val northEastLatitude: Double? = null,
    val northEastLongitude: Double? = null,
    val southWestLatitude: Double? = null,
    val southWestLongitude: Double? = null,
    val description: String? = null,
)

interface MapContextProvider {
    fun getMapView(): MapViewContext?
    fun getSelectedMarkers(): List<MarkerContext>
}

interface MissionNotesProvider {
    fun getMissionNotes(): String?
}

interface MissionMetadataProvider {
    fun getMissionId(): String?
    fun getMissionMetadata(): JsonMap
}

interface TakContextProvider : MapContextProvider, MissionNotesProvider, MissionMetadataProvider

/**
 * Default provider that reads from [MissionContextStore]. A CivTAK/ATAK host
 * should supply a platform-backed implementation that fetches live mission
 * information and map state.
 */
class DefaultTakContextProvider : TakContextProvider {
    override fun getMapView(): MapViewContext? = MissionContextStore.getMapViewContext()

    override fun getSelectedMarkers(): List<MarkerContext> = MissionContextStore.getPreloadedMarkers()

    override fun getMissionNotes(): String? = MissionContextStore.getMissionNotes()

    override fun getMissionId(): String? = MissionContextStore.getMissionId()

    override fun getMissionMetadata(): JsonMap = MissionContextStore.getMissionMetadata()
}
