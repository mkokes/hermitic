package com.martronics.hermitic.core.network

import com.martronics.hermitic.core.security.ConnectionConfig
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class HermesHeadersInterceptorTest {

    private lateinit var server: MockWebServer

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun injectsBearerAndCloudflareServiceTokenHeaders() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        val client = OkHttpClient.Builder()
            .addInterceptor(
                HermesHeadersInterceptor {
                    ConnectionConfig(
                        baseUrl = server.url("/").toString(),
                        apiServerKey = "hermes-key-xyz",
                        cfAccessClientId = "cf-client-123",
                        cfAccessClientSecret = "cf-secret-456",
                    )
                },
            )
            .build()

        client.newCall(Request.Builder().url(server.url("/health")).build()).execute().close()

        val recorded = server.takeRequest()
        assertEquals("Bearer hermes-key-xyz", recorded.getHeader("Authorization"))
        assertEquals("cf-client-123", recorded.getHeader("CF-Access-Client-Id"))
        assertEquals("cf-secret-456", recorded.getHeader("CF-Access-Client-Secret"))
        assertEquals("application/json", recorded.getHeader("Accept"))
    }

    @Test
    fun redactHeaderMasksSecretsOnly() {
        assertEquals("<redacted>", HermesHeadersInterceptor.redactHeader("Authorization", "Bearer abc"))
        assertEquals("<redacted>", HermesHeadersInterceptor.redactHeader("CF-Access-Client-Secret", "sec"))
        assertEquals("cf-id", HermesHeadersInterceptor.redactHeader("CF-Access-Client-Id", "cf-id"))
    }
}
