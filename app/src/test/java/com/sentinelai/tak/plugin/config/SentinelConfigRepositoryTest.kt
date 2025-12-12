package com.sentinelai.tak.plugin.config

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class SentinelConfigRepositoryTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("sentinel_ai_config", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun `returns defaults when preferences are empty`() {
        val repository = SentinelConfigRepository(context)

        val config = repository.load()

        assertEquals("", config.backendUrl)
        assertEquals("", config.apiKey)
        assertEquals(SentinelConfigRepository.DEFAULT_TIMEOUT_SECONDS, config.requestTimeoutSeconds)
        assertEquals(false, config.debugLoggingEnabled)
    }

    @Test
    fun `persists and reloads configuration`() {
        val repository = SentinelConfigRepository(context)
        val original = SentinelConfig(
            backendUrl = "https://example.test",
            apiKey = "secret",
            requestTimeoutSeconds = 60,
            debugLoggingEnabled = true,
        )

        repository.save(original)
        val reloaded = repository.load()

        assertEquals(original.backendUrl, reloaded.backendUrl)
        assertEquals(original.apiKey, reloaded.apiKey)
        assertEquals(original.requestTimeoutSeconds, reloaded.requestTimeoutSeconds)
        assertEquals(original.debugLoggingEnabled, reloaded.debugLoggingEnabled)
    }
}
