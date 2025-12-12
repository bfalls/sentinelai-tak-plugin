package com.sentinelai.tak.plugin.config

import android.content.Context
import android.content.SharedPreferences

/**
 * Simple SharedPreferences-backed repository for SentinelAI configuration.
 */
data class SentinelConfig(
    val backendUrl: String = "",
    val apiKey: String = "",
    val requestTimeoutSeconds: Int = SentinelConfigRepository.DEFAULT_TIMEOUT_SECONDS,
    val debugLoggingEnabled: Boolean = false,
)

class SentinelConfigRepository(context: Context) {

    private val preferences: SharedPreferences =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun load(): SentinelConfig {
        val backendUrl = preferences.getString(KEY_BACKEND_URL, "") ?: ""
        val apiKey = preferences.getString(KEY_API_KEY, "") ?: ""
        val timeout = preferences.getInt(KEY_TIMEOUT_SECONDS, DEFAULT_TIMEOUT_SECONDS)
        val debug = preferences.getBoolean(KEY_DEBUG_ENABLED, false)

        return SentinelConfig(
            backendUrl = backendUrl,
            apiKey = apiKey,
            requestTimeoutSeconds = timeout,
            debugLoggingEnabled = debug,
        )
    }

    fun save(config: SentinelConfig) {
        preferences.edit()
            .putString(KEY_BACKEND_URL, config.backendUrl)
            .putString(KEY_API_KEY, config.apiKey)
            .putInt(KEY_TIMEOUT_SECONDS, config.requestTimeoutSeconds)
            .putBoolean(KEY_DEBUG_ENABLED, config.debugLoggingEnabled)
            .apply()
    }

    companion object {
        private const val PREFERENCES_NAME = "sentinel_ai_config"
        private const val KEY_BACKEND_URL = "backend_url"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_TIMEOUT_SECONDS = "timeout_seconds"
        private const val KEY_DEBUG_ENABLED = "debug_enabled"
        const val DEFAULT_TIMEOUT_SECONDS = 45
    }
}
