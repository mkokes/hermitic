package com.martronics.hermitic.core.network

import com.martronics.hermitic.core.security.ConnectionConfig
import okhttp3.Interceptor
import okhttp3.Response

class HermesHeadersInterceptor(
    private val configProvider: () -> ConnectionConfig,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val config = original.tag(ConnectionConfig::class.java) ?: configProvider()
        val builder = original.newBuilder()

        if (config.apiServerKey.isNotBlank() && original.header(HEADER_AUTHORIZATION) == null) {
            builder.header(HEADER_AUTHORIZATION, "Bearer ${config.apiServerKey}")
        }
        if (config.cfAccessClientId.isNotBlank()) {
            builder.header(HEADER_CF_CLIENT_ID, config.cfAccessClientId)
        }
        if (config.cfAccessClientSecret.isNotBlank()) {
            builder.header(HEADER_CF_CLIENT_SECRET, config.cfAccessClientSecret)
        }
        if (original.header(HEADER_ACCEPT) == null) {
            builder.header(HEADER_ACCEPT, "application/json")
        }

        return chain.proceed(builder.build())
    }

    companion object {
        const val HEADER_AUTHORIZATION = "Authorization"
        const val HEADER_CF_CLIENT_ID = "CF-Access-Client-Id"
        const val HEADER_CF_CLIENT_SECRET = "CF-Access-Client-Secret"
        const val HEADER_IDEMPOTENCY_KEY = "Idempotency-Key"
        const val HEADER_HERMES_SESSION_ID = "X-Hermes-Session-Id"
        const val HEADER_ACCEPT = "Accept"

        private val SENSITIVE_HEADERS = setOf(
            HEADER_AUTHORIZATION.lowercase(),
            HEADER_CF_CLIENT_SECRET.lowercase(),
            "cookie",
            "set-cookie",
        )

        fun redactHeader(name: String, value: String): String =
            if (name.lowercase() in SENSITIVE_HEADERS) "<redacted>" else value
    }
}
