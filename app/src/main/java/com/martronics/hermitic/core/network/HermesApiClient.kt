package com.martronics.hermitic.core.network

import com.martronics.hermitic.core.network.dto.CapabilitiesResponseDto
import com.martronics.hermitic.core.network.dto.CreateRunRequestDto
import com.martronics.hermitic.core.network.dto.CreateRunResponseDto
import com.martronics.hermitic.core.network.dto.CreateSessionRequestDto
import com.martronics.hermitic.core.network.dto.HealthResponseDto
import com.martronics.hermitic.core.network.dto.MessageDto
import com.martronics.hermitic.core.network.dto.RunApprovalRequestDto
import com.martronics.hermitic.core.network.dto.RunClarifyRequestDto
import com.martronics.hermitic.core.network.dto.RunStatusResponseDto
import com.martronics.hermitic.core.network.dto.RunStopResponseDto
import com.martronics.hermitic.core.network.dto.SessionDto
import com.martronics.hermitic.core.network.dto.SessionListResponseDto
import com.martronics.hermitic.core.network.dto.SessionMessagesResponseDto
import com.martronics.hermitic.core.network.dto.UpdateSessionRequestDto
import com.martronics.hermitic.core.security.ConnectionConfig
import com.martronics.hermitic.core.security.CredentialVault
import com.martronics.hermitic.core.security.MtlsSocketFactory
import com.martronics.hermitic.core.security.UrlValidationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.decodeFromJsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HermesApiClient @Inject constructor(
    private val baseClient: OkHttpClient,
    private val credentialVault: CredentialVault,
    private val mtlsSocketFactory: MtlsSocketFactory?,
    private val json: Json,
) {
    internal var enforceHttps: Boolean = true

    internal fun buildClient(config: ConnectionConfig): OkHttpClient {
        val builder = baseClient.newBuilder()
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)

        if (config.mtlsAlias.isNotBlank() && mtlsSocketFactory != null) {
            val tls = mtlsSocketFactory.build(config.mtlsAlias)
            builder.sslSocketFactory(tls.sslSocketFactory, tls.trustManager)
        }
        return builder.build()
    }

    internal fun currentConfigOrThrow(overrideConfig: ConnectionConfig? = null): ConnectionConfig {
        val config = overrideConfig ?: credentialVault.currentConfig()
        if (!config.isConfigured) {
            throw HermesApiError.NotConfigured()
        }
        if (enforceHttps) {
            val check = CredentialVault.validateHttpsUrl(config.baseUrl)
            if (check is UrlValidationResult.Invalid) {
                throw HermesApiError.CleartextBlocked(config.baseUrl)
            }
        }
        return config
    }

    suspend fun checkHealth(overrideConfig: ConnectionConfig? = null): HealthResponseDto =
        executeJson(
            pathSegments = listOf("health"),
            overrideConfig = overrideConfig,
        )

    suspend fun getCapabilities(overrideConfig: ConnectionConfig? = null): CapabilitiesResponseDto =
        executeJson(
            pathSegments = listOf("v1", "capabilities"),
            overrideConfig = overrideConfig,
        )

    suspend fun listSessions(
        limit: Int = 30,
        offset: Int = 0,
        includeChildren: Boolean = false,
    ): SessionListResponseDto {
        val rawText = executeRaw(
            pathSegments = listOf("api", "sessions"),
            queryParams = mapOf(
                "limit" to limit.toString(),
                "offset" to offset.toString(),
                "include_children" to includeChildren.toString(),
            ),
        )
        val element = json.parseToJsonElement(rawText)
        return if (element is JsonArray) {
            val sessions = json.decodeFromJsonElement<List<SessionDto>>(element)
            SessionListResponseDto(sessions = sessions, total = sessions.size, limit = limit, offset = offset)
        } else {
            json.decodeFromJsonElement(element)
        }
    }

    suspend fun createSession(title: String? = null): SessionDto =
        executeJson(
            pathSegments = listOf("api", "sessions"),
            method = "POST",
            bodyJson = json.encodeToString(CreateSessionRequestDto(title = title?.takeIf { it.isNotBlank() })),
        )

    suspend fun updateSession(sessionId: String, title: String? = null, endReason: String? = null): SessionDto =
        executeJson(
            pathSegments = listOf("api", "sessions", sessionId),
            method = "PATCH",
            bodyJson = json.encodeToString(UpdateSessionRequestDto(title = title, endReason = endReason)),
        )

    suspend fun deleteSession(sessionId: String) {
        executeRaw(
            pathSegments = listOf("api", "sessions", sessionId),
            method = "DELETE",
        )
    }

    suspend fun getSessionMessages(
        sessionId: String,
        includeCompacted: Boolean = false,
    ): List<MessageDto> {
        val rawText = executeRaw(
            pathSegments = listOf("api", "sessions", sessionId, "messages"),
            queryParams = mapOf(
                "inline_images" to "false",
                "include_compacted" to includeCompacted.toString(),
            ),
        )
        val element = json.parseToJsonElement(rawText)
        return if (element is JsonArray) {
            json.decodeFromJsonElement(element)
        } else {
            json.decodeFromJsonElement<SessionMessagesResponseDto>(element).messages
        }
    }

    suspend fun createRun(
        input: String,
        sessionId: String?,
        idempotencyKey: String,
    ): CreateRunResponseDto {
        val headers = buildMap {
            put(HermesHeadersInterceptor.HEADER_IDEMPOTENCY_KEY, idempotencyKey)
            if (!sessionId.isNullOrBlank()) {
                put(HermesHeadersInterceptor.HEADER_HERMES_SESSION_ID, sessionId)
            }
        }
        return executeJson(
            pathSegments = listOf("v1", "runs"),
            method = "POST",
            bodyJson = json.encodeToString(CreateRunRequestDto(input = input, sessionId = sessionId)),
            extraHeaders = headers,
        )
    }

    suspend fun getRun(runId: String): RunStatusResponseDto =
        executeJson(pathSegments = listOf("v1", "runs", runId))

    suspend fun stopRun(runId: String): RunStopResponseDto =
        executeJson(
            pathSegments = listOf("v1", "runs", runId, "stop"),
            method = "POST",
            bodyJson = "{}",
        )

    suspend fun submitApproval(
        runId: String,
        requestId: String,
        executionGeneration: Int,
        choice: String,
    ): RunStatusResponseDto =
        executeJson(
            pathSegments = listOf("v1", "runs", runId, "approval"),
            method = "POST",
            bodyJson = json.encodeToString(
                RunApprovalRequestDto(
                    requestId = requestId,
                    executionGeneration = executionGeneration,
                    choice = choice,
                ),
            ),
        )

    suspend fun submitClarify(
        runId: String,
        requestId: String,
        executionGeneration: Int,
        answer: String,
    ): RunStatusResponseDto =
        executeJson(
            pathSegments = listOf("v1", "runs", runId, "clarify"),
            method = "POST",
            bodyJson = json.encodeToString(
                RunClarifyRequestDto(
                    requestId = requestId,
                    executionGeneration = executionGeneration,
                    answer = answer,
                ),
            ),
        )

    private suspend inline fun <reified T> executeJson(
        pathSegments: List<String>,
        method: String = "GET",
        queryParams: Map<String, String> = emptyMap(),
        bodyJson: String? = null,
        extraHeaders: Map<String, String> = emptyMap(),
        overrideConfig: ConnectionConfig? = null,
    ): T {
        val raw = executeRaw(
            pathSegments = pathSegments,
            method = method,
            queryParams = queryParams,
            bodyJson = bodyJson,
            extraHeaders = extraHeaders,
            overrideConfig = overrideConfig,
        )
        return json.decodeFromString(raw)
    }

    private suspend fun executeRaw(
        pathSegments: List<String>,
        method: String = "GET",
        queryParams: Map<String, String> = emptyMap(),
        bodyJson: String? = null,
        extraHeaders: Map<String, String> = emptyMap(),
        overrideConfig: ConnectionConfig? = null,
    ): String = withContext(Dispatchers.IO) {
        val config = currentConfigOrThrow(overrideConfig)
        val urlBuilder = config.baseUrl.trimEnd('/').toHttpUrl().newBuilder()
        pathSegments.forEach { urlBuilder.addPathSegment(it) }
        queryParams.forEach { (k, v) -> urlBuilder.addQueryParameter(k, v) }

        val reqBuilder = Request.Builder().url(urlBuilder.build())
        if (overrideConfig != null) {
            reqBuilder.tag(ConnectionConfig::class.java, overrideConfig)
            if (overrideConfig.apiServerKey.isNotBlank()) {
                reqBuilder.header(
                    HermesHeadersInterceptor.HEADER_AUTHORIZATION,
                    "Bearer ${overrideConfig.apiServerKey}",
                )
            }
            if (overrideConfig.cfAccessClientId.isNotBlank()) {
                reqBuilder.header(
                    HermesHeadersInterceptor.HEADER_CF_CLIENT_ID,
                    overrideConfig.cfAccessClientId,
                )
            }
            if (overrideConfig.cfAccessClientSecret.isNotBlank()) {
                reqBuilder.header(
                    HermesHeadersInterceptor.HEADER_CF_CLIENT_SECRET,
                    overrideConfig.cfAccessClientSecret,
                )
            }
        }
        extraHeaders.forEach { (k, v) -> reqBuilder.header(k, v) }

        val body = bodyJson?.toRequestBody(JSON_MEDIA_TYPE)
        when (method) {
            "GET" -> reqBuilder.get()
            "POST" -> reqBuilder.post(body ?: "{}".toRequestBody(JSON_MEDIA_TYPE))
            "PATCH" -> reqBuilder.patch(body ?: "{}".toRequestBody(JSON_MEDIA_TYPE))
            "DELETE" -> reqBuilder.delete(body)
            else -> reqBuilder.method(method, body)
        }

        val client = buildClient(config)
        try {
            client.newCall(reqBuilder.build()).execute().use { response ->
                val responseBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw NetworkErrorMapper.mapHttpFailure(
                        statusCode = response.code,
                        contentType = response.header("Content-Type"),
                        locationHeader = response.header("Location"),
                        body = responseBody,
                        retryAfterHeader = response.header("Retry-After"),
                        json = json,
                    )
                }
                val contentType = response.header("Content-Type").orEmpty()
                if (contentType.contains("text/html", ignoreCase = true) ||
                    responseBody.trimStart().startsWith("<!DOCTYPE", ignoreCase = true) ||
                    responseBody.trimStart().startsWith("<html", ignoreCase = true)
                ) {
                    throw HermesApiError.CloudflareAccessRedirect(response.code, response.request.url.toString())
                }
                responseBody
            }
        } catch (t: Throwable) {
            throw NetworkErrorMapper.mapException(t)
        }
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
