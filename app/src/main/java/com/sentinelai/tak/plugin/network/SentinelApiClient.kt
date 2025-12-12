package com.sentinelai.tak.plugin.network

import com.sentinelai.tak.plugin.config.SentinelConfig
import com.sentinelai.tak.plugin.config.SentinelConfigRepository
import com.sentinelai.tak.plugin.network.dto.MissionAnalysisRequestDto
import com.sentinelai.tak.plugin.network.dto.MissionAnalysisResponseDto
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class SentinelApiException(message: String, cause: Throwable? = null) : Exception(message, cause)

class SentinelApiClient(
    private val configRepository: SentinelConfigRepository,
    private val moshi: Moshi = defaultMoshi(),
    private val baseClient: OkHttpClient = OkHttpClient.Builder().build(),
) {

    private val requestAdapter: JsonAdapter<MissionAnalysisRequestDto> =
        moshi.adapter(MissionAnalysisRequestDto::class.java)
    private val responseAdapter: JsonAdapter<MissionAnalysisResponseDto> =
        moshi.adapter(MissionAnalysisResponseDto::class.java)
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun healthCheck(): Boolean = withContext(Dispatchers.IO) {
        val config = configRepository.load()
        val client = clientForConfig(config)
        val url = buildUrl(config.backendUrl, "/healthz")
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw SentinelApiException("Health check failed with HTTP ${'$'}{response.code}")
            }
            true
        }
    }

    suspend fun analyzeMission(request: MissionAnalysisRequestDto): MissionAnalysisResponseDto =
        withContext(Dispatchers.IO) {
            val config = configRepository.load()
            val client = clientForConfig(config)
            val url = buildUrl(config.backendUrl, "/api/v1/analysis/mission")

            val serializedRequest = requestAdapter.toJson(request)
            val body = serializedRequest.toRequestBody(jsonMediaType)

            val requestBuilder = Request.Builder()
                .url(url)
                .post(body)
                .header("Content-Type", jsonMediaType.toString())

            if (config.apiKey.isNotBlank()) {
                requestBuilder.header(API_KEY_HEADER, config.apiKey)
            }

            val httpRequest = requestBuilder.build()
            client.newCall(httpRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string().orEmpty()
                    throw SentinelApiException(
                        "Mission analysis failed with HTTP ${'$'}{response.code}: ${'$'}{errorBody.take(200)}",
                    )
                }

                val responseBody = response.body?.string()
                    ?: throw SentinelApiException("Mission analysis response was empty")

                try {
                    responseAdapter.fromJson(responseBody)
                        ?: throw SentinelApiException("Unable to parse mission analysis response")
                } catch (ex: JsonDataException) {
                    throw SentinelApiException("Malformed mission analysis response", ex)
                } catch (ex: IOException) {
                    throw SentinelApiException("Failed reading mission analysis response", ex)
                }
            }
        }

    private fun clientForConfig(config: SentinelConfig): OkHttpClient {
        if (config.backendUrl.isBlank()) {
            throw SentinelApiException("Backend URL is not configured")
        }

        return baseClient.newBuilder()
            .callTimeout(config.requestTimeoutSeconds.toLong(), TimeUnit.SECONDS)
            .build()
    }

    private fun buildUrl(baseUrl: String, path: String): HttpUrl {
        val url = baseUrl.toHttpUrlOrNull()
            ?: throw SentinelApiException("Invalid backend URL: ${'$'}baseUrl")

        return url.newBuilder()
            .addPathSegments(path.trimStart('/'))
            .build()
    }

    companion object {
        private const val API_KEY_HEADER = "X-Sentinel-API-Key"

        fun defaultMoshi(): Moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
    }
}
