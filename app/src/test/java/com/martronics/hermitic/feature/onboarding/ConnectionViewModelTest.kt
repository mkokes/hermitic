package com.martronics.hermitic.feature.onboarding

import com.martronics.hermitic.core.network.HermesApiClient
import com.martronics.hermitic.core.network.HermesHeadersInterceptor
import com.martronics.hermitic.core.security.CredentialVault
import com.martronics.hermitic.core.security.SecretStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private class OnboardingSecretStore : SecretStore {
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

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectionViewModelTest {

    private lateinit var server: MockWebServer
    private lateinit var vault: CredentialVault
    private lateinit var apiClient: HermesApiClient
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        server = MockWebServer()
        server.start()
        vault = CredentialVault(OnboardingSecretStore())
        val okHttp = OkHttpClient.Builder()
            .addInterceptor(HermesHeadersInterceptor { vault.currentConfig() })
            .build()
        apiClient = HermesApiClient(
            baseClient = okHttp,
            credentialVault = vault,
            mtlsSocketFactory = null,
            json = Json { ignoreUnknownKeys = true },
        ).apply { enforceHttps = false }
    }

    @After
    fun tearDown() {
        server.shutdown()
        Dispatchers.resetMain()
    }

    @Test
    fun rejectsCleartextUrlDuringTestConnection() {
        val vm = ConnectionViewModel(vault, apiClient)
        vm.onBaseUrlChanged("http://127.0.0.1:8642")
        vm.onApiServerKeyChanged("secret")
        vm.testConnection()

        assertNotNull(vm.uiState.value.urlError)
        assertFalse(vm.uiState.value.isTesting)
    }

    @Test
    fun saveConnectionPersistsValidatedConfig() {
        val vm = ConnectionViewModel(vault, apiClient)
        vm.onBaseUrlChanged("https://hermes.martronics.dev/")
        vm.onApiServerKeyChanged("bearer-123")
        vm.onCfAccessClientIdChanged("cf-id")
        vm.onCfAccessClientSecretChanged("cf-sec")

        var saved = false
        vm.saveConnection { saved = true }

        assertTrue(saved)
        assertTrue(vault.currentConfig().isConfigured)
        assertEquals("https://hermes.martronics.dev", vault.currentConfig().baseUrl)
    }

    @Test
    fun surfacesCloudflareHtmlErrorClearly() {
        server.enqueue(
            MockResponse()
                .setResponseCode(403)
                .addHeader("Content-Type", "text/html")
                .setBody("<html><title>Cloudflare Access</title></html>"),
        )
        // Verify direct probe mapper catches HTML login page if endpoint is HTTPS
        val err = com.martronics.hermitic.core.network.NetworkErrorMapper.mapHttpFailure(
            statusCode = 403,
            contentType = "text/html",
            locationHeader = null,
            body = "<html><title>Cloudflare Access</title></html>",
        )
        assertTrue(err.message.contains("Cloudflare Access"))
    }
}
