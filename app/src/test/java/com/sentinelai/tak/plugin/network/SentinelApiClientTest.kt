package com.sentinelai.tak.plugin.network

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.sentinelai.tak.plugin.config.SentinelConfig
import com.sentinelai.tak.plugin.config.SentinelConfigRepository
import com.sentinelai.tak.plugin.network.dto.LocationDto
import com.sentinelai.tak.plugin.network.dto.MissionAnalysisRequestDto
import com.sentinelai.tak.plugin.network.dto.SignalDto
import com.sentinelai.tak.plugin.network.dto.TimeWindowDto
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class SentinelApiClientTest {

    private lateinit var context: Context
    private lateinit var configRepository: SentinelConfigRepository
    private lateinit var mockWebServer: MockWebServer

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        configRepository = SentinelConfigRepository(context)
        mockWebServer = MockWebServer()
        mockWebServer.start()
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
        context.getSharedPreferences("sentinel_ai_config", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun `healthCheck returns true for healthy backend`() = runBlocking {
        val baseUrl = mockWebServer.url("/").toString()
        configRepository.save(
            SentinelConfig(
                backendUrl = baseUrl,
                apiKey = "",
                requestTimeoutSeconds = 15,
                debugLoggingEnabled = false,
            )
        )
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("ok"))

        val client = SentinelApiClient(configRepository)
        val healthy = client.healthCheck()

        assertTrue(healthy)
        val recorded = mockWebServer.takeRequest()
        assertEquals("/healthz", recorded.path)
    }

    @Test
    fun `analyzeMission posts request and parses response`() = runBlocking {
        val baseUrl = mockWebServer.url("/").toString()
        configRepository.save(
            SentinelConfig(
                backendUrl = baseUrl,
                apiKey = "test-key",
                requestTimeoutSeconds = 30,
                debugLoggingEnabled = false,
            )
        )

        val responseJson = """
            {
              "intent": "SITUATIONAL_AWARENESS",
              "summary": "All clear",
              "risks": ["None"],
              "recommendations": ["Continue monitoring"]
            }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(responseJson))

        val requestDto = MissionAnalysisRequestDto(
            missionId = "123",
            missionMetadata = mapOf("question" to "Status"),
            signals = listOf(
                SignalDto(
                    type = "REQUEST_INFO",
                    description = "Status",
                    timestamp = DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(OffsetDateTime.parse("2024-03-01T12:00:00Z")),
                    metadata = mapOf("source" to "unit-test"),
                )
            ),
            notes = null,
            location = LocationDto(latitude = 10.0, longitude = 20.0, description = "AO"),
            timeWindow = TimeWindowDto(start = "2024-03-01T10:00:00Z", end = "2024-03-01T12:00:00Z"),
            intent = null,
        )

        val client = SentinelApiClient(
            configRepository = configRepository,
            moshi = SentinelApiClient.defaultMoshi(),
            baseClient = OkHttpClient.Builder().build(),
        )

        val response = client.analyzeMission(requestDto)

        assertEquals("SITUATIONAL_AWARENESS", response.intent)
        assertEquals("All clear", response.summary)
        assertEquals(listOf("None"), response.risks)
        assertEquals(listOf("Continue monitoring"), response.recommendations)

        val recorded = mockWebServer.takeRequest()
        assertEquals("/api/v1/analysis/mission", recorded.path)
        assertEquals("test-key", recorded.getHeader("X-Sentinel-API-Key"))
        val body = recorded.body.readUtf8()
        assertTrue(body.contains("\"mission_id\":\"123\""))
        assertTrue(body.contains("REQUEST_INFO"))
    }
}
