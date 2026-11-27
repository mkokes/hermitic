package com.martronics.hermitic.core.network

import com.martronics.hermitic.core.security.ConnectionConfig
import com.martronics.hermitic.core.security.CredentialVault
import com.martronics.hermitic.core.security.SecretStore
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

private class FakeStore : SecretStore {
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

class HermesApiClientTest {

    private lateinit var server: MockWebServer
    private lateinit var vault: CredentialVault
    private lateinit var client: HermesApiClient

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()
        val store = FakeStore().apply {
            putString("base_url", server.url("/").toString().trimEnd('/'))
            putString("api_server_key", "test-key")
        }
        vault = CredentialVault(store)
        val okHttp = OkHttpClient.Builder()
            .addInterceptor(HermesHeadersInterceptor { vault.currentConfig() })
            .build()
        client = HermesApiClient(
            baseClient = okHttp,
            credentialVault = vault,
            mtlsSocketFactory = null,
            json = Json { ignoreUnknownKeys = true },
        ).apply {
            enforceHttps = false
        }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun checkHealthAndCapabilitiesSuccess() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"status":"ok"}"""))
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "object": "hermes.api_server.capabilities",
                  "platform": "hermes-agent",
                  "model": "hermes-agent",
                  "features": {"run_submission": true, "run_approval": true}
                }
                """.trimIndent(),
            ),
        )

        val health = client.checkHealth()
        val caps = client.getCapabilities()
        assertEquals("ok", health.status)
        assertEquals(true, caps.features["run_approval"])
    }

    @Test
    fun getSessionMessagesPassesInlineImagesFalse() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"session_id":"s1","messages":[{"role":"user","content":"hi"}]}""",
            ),
        )

        val msgs = client.getSessionMessages("s1")
        assertEquals(1, msgs.size)
        val req = server.takeRequest()
        assertTrue(req.path!!.contains("inline_images=false"))
    }

    @Test
    fun enforcesHttpsWhenEnabled() = runBlocking {
        client.enforceHttps = true
        val httpConfig = ConnectionConfig(
            baseUrl = "http://127.0.0.1:8642",
            apiServerKey = "k",
        )
        var thrown: Throwable? = null
        try {
            client.checkHealth(overrideConfig = httpConfig)
        } catch (t: Throwable) {
            thrown = t
        }
        assertTrue(thrown is HermesApiError.CleartextBlocked)
    }
}
