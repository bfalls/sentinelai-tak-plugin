package com.sentinelai.tak.plugin.context

import android.content.Context
import android.util.Log

/**
 * Abstraction over the host platform's marker context menu APIs. CivTAK/ATAK
 * specific implementations should register the provided menu option with the
 * host and invoke [onMarkerSelected] with a [MarkerContext] when the user taps
 * it.
 */
interface MarkerContextMenuHost {
    fun registerMarkerMenuItem(title: String, onMarkerSelected: (MarkerContext) -> Unit)
}

class MarkerContextMenuRegistrar(
    private val appContext: Context,
    private val menuHost: MarkerContextMenuHost,
) {

    fun register() {
        menuHost.registerMarkerMenuItem(MENU_TITLE) { markerContext ->
            MissionContextStore.preloadMarker(markerContext)
            Log.i(TAG, "Preloaded marker ${'$'}{markerContext.id ?: markerContext.title} for SentinelAI analysis")
            // Future: open mission analysis panel directly once available.
        }
        Log.i(TAG, "Registered SentinelAI marker context menu handler in ${'$'}{appContext.packageName}")
    }

    companion object {
        private const val TAG = "SentinelMarkerMenu"
        private const val MENU_TITLE = "Ask SentinelAI about this marker"
    }
}
