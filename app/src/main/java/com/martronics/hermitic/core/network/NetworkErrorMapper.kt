package com.martronics.hermitic.core.network

import com.martronics.hermitic.core.network.dto.ApiErrorDto
import kotlinx.serialization.json.Json
import java.io.IOException
import javax.net.ssl.SSLException

sealed class HermesApiError(
    override val message: String,
    val code: String? = null,
    val httpStatus: Int? = null,
) : Exception(message) {

    class NotConfigured : HermesApiError(
        message = "Hermes endpoint and API key are not configured.",
        code = "not_configured",
    )

    class CleartextBlocked(url: String) : HermesApiError(
        message = "Cleartext HTTP endpoint rejected ($url). Use https://.",
        code = "cleartext_blocked",
    )

    class CloudflareAccessRedirect(
        httpStatus: Int,
        location: String? = null,
    ) : HermesApiError(
        message = buildString {
            append("Cloudflare Access intercepted the request (HTTP $httpStatus)")
            if (!location.isNullOrBlank()) {
                append(" -> $location")
            }
            append(". Configure non-interactive Service Token headers or mTLS in Access.")
        },
        code = "cloudflare_access_redirect",
        httpStatus = httpStatus,
    )

    class Unauthorized(detail: String? = null) : HermesApiError(
        message = detail ?: "Unauthorized (HTTP 401). Verify API_SERVER_KEY.",
        code = "unauthorized",
        httpStatus = 401,
    )

    class Conflict(
        code: String,
        detail: String,
    ) : HermesApiError(
        message = detail,
        code = code,
        httpStatus = 409,
    )

    class RateLimited(retryAfterSeconds: Int? = null) : HermesApiError(
        message = if (retryAfterSeconds != null) {
            "Too many concurrent runs or rate-limited. Retry in ${retryAfterSeconds}s."
        } else {
            "Too many concurrent runs or rate-limited (HTTP 429)."
        },
        code = "rate_limited",
        httpStatus = 429,
    )

    class ServiceUnavailable(retryAfterSeconds: Int? = null, code: String? = null) : HermesApiError(
        message = if (retryAfterSeconds != null) {
            "Hermes gateway temporarily unavailable (${code ?: "503"}). Retry in ${retryAfterSeconds}s."
        } else {
            "Hermes gateway temporarily unavailable (${code ?: "503"})."
        },
        code = code ?: "service_unavailable",
        httpStatus = 503,
    )

    class TlsFailure(detail: String) : HermesApiError(
        message = "TLS / mTLS handshake failed: $detail",
        code = "tls_failure",
    )

    class NetworkFailure(detail: String) : HermesApiError(
        message = "Network error: $detail",
        code = "network_error",
    )

    class HttpFailure(
        httpStatus: Int,
        code: String?,
        detail: String,
    ) : HermesApiError(
        message = detail,
        code = code,
        httpStatus = httpStatus,
    )
}

object NetworkErrorMapper {

    fun mapHttpFailure(
        statusCode: Int,
        contentType: String?,
        locationHeader: String?,
        body: String,
        retryAfterHeader: String? = null,
        json: Json = Json { ignoreUnknownKeys = true },
    ): HermesApiError {
        val normalizedCt = contentType?.lowercase().orEmpty()
        val trimmedBody = body.trim()
        val looksLikeHtml = normalizedCt.contains("text/html") ||
            trimmedBody.startsWith("<!DOCTYPE", ignoreCase = true) ||
            trimmedBody.startsWith("<html", ignoreCase = true)

        val isCloudflareRedirect = statusCode in 300..399 ||
            (statusCode == 403 && looksLikeHtml) ||
            (looksLikeHtml && (
                trimmedBody.contains("cloudflareaccess", ignoreCase = true) ||
                    trimmedBody.contains("Cloudflare Access", ignoreCase = true) ||
                    locationHeader?.contains("cloudflareaccess", ignoreCase = true) == true
                ))

        if (isCloudflareRedirect) {
            return HermesApiError.CloudflareAccessRedirect(statusCode, locationHeader)
        }

        val parsedError = runCatching {
            if (trimmedBody.startsWith("{")) json.decodeFromString<ApiErrorDto>(trimmedBody) else null
        }.getOrNull()

        val errorCode = parsedError?.code ?: parsedError?.error
        val detail = parsedError?.detail
            ?: parsedError?.message
            ?: parsedError?.error
            ?: trimmedBody.takeIf { it.isNotBlank() && !looksLikeHtml }
            ?: "HTTP $statusCode"

        val retryAfter = retryAfterHeader?.trim()?.toIntOrNull()

        return when (statusCode) {
            401 -> HermesApiError.Unauthorized(detail)
            409 -> HermesApiError.Conflict(
                code = errorCode ?: "conflict",
                detail = detail,
            )
            429 -> HermesApiError.RateLimited(retryAfter)
            503 -> HermesApiError.ServiceUnavailable(retryAfter, errorCode)
            else -> HermesApiError.HttpFailure(statusCode, errorCode, detail)
        }
    }

    fun mapException(throwable: Throwable): HermesApiError = when (throwable) {
        is HermesApiError -> throwable
        is SSLException -> HermesApiError.TlsFailure(throwable.message ?: "SSL error")
        is IOException -> HermesApiError.NetworkFailure(throwable.message ?: "I/O error")
        else -> HermesApiError.NetworkFailure(throwable.message ?: throwable.javaClass.simpleName)
    }
}
