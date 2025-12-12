package com.sentinelai.tak.plugin

import android.content.Context
import android.util.Log

/**
 * Minimal placeholder for the SentinelAI TAK plugin entry point.
 *
 * In future phases this class will integrate with the CivTAK/ATAK plugin SDK
 * lifecycle to register menu items, tools, and background services.
 */
class SentinelAiPlugin {

    fun initialize(context: Context) {
        // TODO: Wire into CivTAK/ATAK plugin SDK lifecycle hooks
        Log.i(TAG, "SentinelAI plugin initialized with context: ${'$'}{context.packageName}")
    }

    companion object {
        private const val TAG = "SentinelAiPlugin"
    }
}
