package com.martronics.hermitic.core.network

import com.martronics.hermitic.core.network.dto.RunStreamEvent
import com.martronics.hermitic.core.security.CredentialVault
import com.martronics.hermitic.core.security.SecretStore
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private class SseSecretStore : SecretStore {
    private val map = mutableMapOf<String, Any>()
    override fun getString(key: String): String? = map[key] as? String
    override fun putString(key: String, value: String?) {
        if (value.isNullOrBlank()) map.remove(key) else map[key] = value
    }
    override fun getBoolean(key: String, defaultValue: Boolean): Boolean = (map[key] as? Boolean) ?: defaultValue
    override fun putBoolean(key: String, value: Boolean) { map[key] = value }
    override fun getInt(key: String, defaultValue: Int): Int = (map[key] as? Int) ?: defaultValue
    override fun putInt(key: String, value: Int) { map[key] = value }
    override fun clear() { map.clear() }
}

class HermesSseClientTest {

    private lateinit var server: MockWebServer
    private lateinit var sseClient: HermesSseClient

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()
        val store = SseSecretStore().apply {
            putString("base_url", server.url("/").toString().trimEnd('/'))
            putString("api_server_key", "key-sse")
        }
        val vault = CredentialVault(store)
        val okHttp = OkHttpClient.Builder()
            .addInterceptor(HermesHeadersInterceptor { vault.currentConfig() })
            .build()
        val apiClient = HermesApiClient(
            baseClient = okHttp,
            credentialVault = vault,
            mtlsSocketFactory = null,
            json = Json { ignoreUnknownKeys = true },
        ).apply { enforceHttps = false }

        sseClient = HermesSseClient(apiClient, Json { ignoreUnknownKeys = true })
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun parsesDeltasToolsApprovalAndKeepaliveComments() = runBlocking {
        val sseBody = """
            : keepalive

            event: assistant.delta
            data: {"delta":"Checking "}

            event: tool.started
            data: {"tool":"terminal","preview":"ls -la"}

            event: tool.completed
            data: {"tool":"terminal","duration":0.4,"error":false,"preview":"README.md"}

            event: approval.request
            data: {"request_id":"req-1","execution_generation":2,"tool":"terminal","command":"rm -rf build"}

            event: run.completed
            data: {"output":"Checking done."}

        """.trimIndent() + "\n"

        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "text/event-stream")
                .setBody(sseBody),
        )

        val events = sseClient.streamRunEvents("run-1").toList()
        assertEquals(5, events.size)
        assertTrue(events[0] is RunStreamEvent.AssistantDelta)
        assertEquals("Checking ", (events[0] as RunStreamEvent.AssistantDelta).text)
        assertTrue(events[1] is RunStreamEvent.ToolStarted)
        assertTrue(events[2] is RunStreamEvent.ToolCompleted)
        val approval = events[3] as RunStreamEvent.ApprovalRequested
        assertEquals("req-1", approval.requestId)
        assertEquals(2, approval.executionGeneration)
        assertTrue(events[4] is RunStreamEvent.RunCompleted)
    }
}
