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

enum class SentinelApiErrorKind {
    CONFIG,
    NETWORK,
    BACKEND,
    TIMEOUT,
    PARSE,
    UNKNOWN,
}

class SentinelApiException(
    val kind: SentinelApiErrorKind,
    message: String,
    cause: Throwable? = null,
    val statusCode: Int? = null,
) : Exception(message, cause)

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

    suspend fun healthCheck(configOverride: SentinelConfig? = null): Boolean = withContext(Dispatchers.IO) {
        val config = configOverride ?: configRepository.load()
        val client = clientForConfig(config)
        val url = buildUrl(config.backendUrl, "/healthz")
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw SentinelApiException(
                        kind = SentinelApiErrorKind.BACKEND,
                        message = "Health check failed with HTTP ${'$'}{response.code}",
                        statusCode = response.code,
                    )
                }
                true
            }
        } catch (ex: IOException) {
            throw classifyNetworkException(ex)
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
            try {
                client.newCall(httpRequest).execute().use { response ->
                    if (!response.isSuccessful) {
                        val errorBody = response.body?.string().orEmpty()
                        throw SentinelApiException(
                            kind = SentinelApiErrorKind.BACKEND,
                            message = "Mission analysis failed with HTTP ${'$'}{response.code}",
                            statusCode = response.code,
                            cause = null,
                        ).attachBackendDetails(errorBody)
                    }

                    val responseBody = response.body?.string()
                        ?: throw SentinelApiException(
                            kind = SentinelApiErrorKind.BACKEND,
                            message = "Mission analysis response was empty",
                        )

                    val parsedResponse = try {
                        responseAdapter.fromJson(responseBody)
                            ?: throw SentinelApiException(
                                kind = SentinelApiErrorKind.PARSE,
                                message = "Unable to parse mission analysis response",
                            )
                    } catch (ex: JsonDataException) {
                        throw SentinelApiException(
                            kind = SentinelApiErrorKind.PARSE,
                            message = "Malformed mission analysis response",
                            cause = ex,
                        )
                    } catch (ex: IOException) {
                        throw SentinelApiException(
                            kind = SentinelApiErrorKind.UNKNOWN,
                            message = "Failed reading mission analysis response",
                            cause = ex,
                        )
                    }

                    parsedResponse
                }
            } catch (ex: IOException) {
                throw classifyNetworkException(ex)
            }
        }

    private fun clientForConfig(config: SentinelConfig): OkHttpClient {
        if (config.backendUrl.isBlank()) {
            throw SentinelApiException(
                kind = SentinelApiErrorKind.CONFIG,
                message = "Backend URL is not configured",
            )
        }

        return baseClient.newBuilder()
            .callTimeout(config.requestTimeoutSeconds.toLong(), TimeUnit.SECONDS)
            .connectTimeout(config.requestTimeoutSeconds.toLong(), TimeUnit.SECONDS)
            .readTimeout(config.requestTimeoutSeconds.toLong(), TimeUnit.SECONDS)
            .writeTimeout(config.requestTimeoutSeconds.toLong(), TimeUnit.SECONDS)
            .build()
    }

    private fun buildUrl(baseUrl: String, path: String): HttpUrl {
        val url = baseUrl.toHttpUrlOrNull()
            ?: throw SentinelApiException(
                kind = SentinelApiErrorKind.CONFIG,
                message = "Invalid backend URL",
            )

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

    private fun classifyNetworkException(ex: IOException): SentinelApiException {
        val isTimeout = ex.message?.contains("timeout", ignoreCase = true) == true
        val kind = if (isTimeout) SentinelApiErrorKind.TIMEOUT else SentinelApiErrorKind.NETWORK
        val message = if (isTimeout) {
            "The request timed out while contacting the SentinelAI backend"
        } else {
            "Unable to reach the SentinelAI backend"
        }
        return SentinelApiException(kind = kind, message = message, cause = ex)
    }

    private fun SentinelApiException.attachBackendDetails(errorBody: String): SentinelApiException {
        if (errorBody.isBlank()) return this
        // We avoid logging or exposing full payloads; only a short summary is attached for visibility.
        val condensedMessage = listOfNotNull(this.message, errorBody.take(200)).joinToString(separator = ": ")
        return SentinelApiException(
            kind = this.kind,
            message = condensedMessage,
            cause = this.cause,
            statusCode = this.statusCode,
        )
    }
}
