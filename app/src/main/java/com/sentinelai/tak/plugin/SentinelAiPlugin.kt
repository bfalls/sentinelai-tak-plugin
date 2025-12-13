package com.sentinelai.tak.plugin

import android.content.Context
import android.util.Log
import com.sentinelai.tak.plugin.context.MarkerContextMenuHost
import com.sentinelai.tak.plugin.context.MarkerContextMenuRegistrar

/**
 * Minimal placeholder for the SentinelAI TAK plugin entry point.
 *
 * In future phases this class will integrate with the CivTAK/ATAK plugin SDK
 * lifecycle to register menu items, tools, and background services.
 */
class SentinelAiPlugin(
    private val markerMenuHost: MarkerContextMenuHost? = null,
) {

    fun initialize(context: Context) {
        Log.i(TAG, "SentinelAI plugin initialized with context: ${'$'}{context.packageName}")

        markerMenuHost?.let {
            MarkerContextMenuRegistrar(context, it).register()
        } ?: Log.w(TAG, "Marker context menu host not provided; skipping menu registration")
    }

    companion object {
        private const val TAG = "SentinelAiPlugin"
    }
}
