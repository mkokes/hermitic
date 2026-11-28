package com.martronics.hermitic.feature.chat

import androidx.lifecycle.SavedStateHandle
import com.martronics.hermitic.core.data.ChatRunRepository
import com.martronics.hermitic.core.data.RunPhase
import com.martronics.hermitic.core.data.SessionRepository
import com.martronics.hermitic.core.data.dao.MessageDao
import com.martronics.hermitic.core.data.dao.SessionDao
import com.martronics.hermitic.core.data.entity.MessageEntity
import com.martronics.hermitic.core.data.entity.SessionEntity
import com.martronics.hermitic.core.network.HermesApiClient
import com.martronics.hermitic.core.network.HermesHeadersInterceptor
import com.martronics.hermitic.core.network.HermesSseClient
import com.martronics.hermitic.core.security.CredentialVault
import com.martronics.hermitic.core.security.SecretStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private class ChatVmSecretStore : SecretStore {
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

private class VmFakeSessionDao : SessionDao {
    private val rows = MutableStateFlow<List<SessionEntity>>(emptyList())
    override fun observeAll(): Flow<List<SessionEntity>> = rows
    override fun observeByTitle(query: String): Flow<List<SessionEntity>> = rows
    override suspend fun getById(sessionId: String): SessionEntity? = rows.value.firstOrNull { it.id == sessionId }
    override suspend fun upsert(session: SessionEntity) { upsertAll(listOf(session)) }
    override suspend fun upsertAll(sessions: List<SessionEntity>) {
        val merged = rows.value.associateBy { it.id }.toMutableMap()
        sessions.forEach { merged[it.id] = it }
        rows.value = merged.values.toList()
    }
    override suspend fun deleteById(sessionId: String) {}
    override suspend fun deleteMissing(keepIds: List<String>) {}
    override suspend fun deleteAll() { rows.value = emptyList() }
}

private class VmFakeMessageDao : MessageDao {
    private val rows = MutableStateFlow<List<MessageEntity>>(emptyList())
    override fun observeForSession(sessionId: String): Flow<List<MessageEntity>> =
        rows.map { list -> list.filter { it.sessionId == sessionId }.sortedBy { it.sequenceIndex } }
    override suspend fun upsert(message: MessageEntity) { upsertAll(listOf(message)) }
    override suspend fun upsertAll(messages: List<MessageEntity>) {
        val merged = rows.value.associateBy { it.id }.toMutableMap()
        messages.forEach { merged[it.id] = it }
        rows.value = merged.values.toList()
    }
    override suspend fun deleteForSession(sessionId: String) {
        rows.value = rows.value.filterNot { it.sessionId == sessionId }
    }
    override suspend fun deleteAll() { rows.value = emptyList() }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

    private lateinit var server: MockWebServer
    private lateinit var sessionRepository: SessionRepository
    private lateinit var chatRunRepository: ChatRunRepository
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        server = MockWebServer()
        server.start()
        val store = ChatVmSecretStore().apply {
            putString("base_url", server.url("/").toString().trimEnd('/'))
            putString("api_server_key", "key-vm")
        }
        val vault = CredentialVault(store)
        val okHttp = OkHttpClient.Builder()
            .addInterceptor(HermesHeadersInterceptor { vault.currentConfig() })
            .build()
        val json = Json { ignoreUnknownKeys = true }
        val apiClient = HermesApiClient(
            baseClient = okHttp,
            credentialVault = vault,
            mtlsSocketFactory = null,
            json = json,
        ).apply { enforceHttps = false }
        val sseClient = HermesSseClient(apiClient, json)
        sessionRepository = SessionRepository(apiClient, VmFakeSessionDao(), VmFakeMessageDao())
        chatRunRepository = ChatRunRepository(apiClient, sseClient, sessionRepository)
    }

    @After
    fun tearDown() {
        server.shutdown()
        Dispatchers.resetMain()
    }

    @Test
    fun streamsClarifyAndCompletesWithTranscript() = runBlocking {
        // Initial transcript load
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"messages":[]}"""))
        // createRun
        server.enqueue(MockResponse().setResponseCode(202).setBody("""{"run_id":"run-1","status":"started"}"""))
        // SSE stream with clarify.request
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "text/event-stream")
                .setBody(
                    """
                    event: tool.started
                    data: {"tool":"clarify","preview":"Asking user"}

                    event: clarify.request
                    data: {"request_id":"cl-1","execution_generation":2,"question":"Which env?","choices":["Staging","Prod"]}

                    """.trimIndent(),
                )
                .setSocketPolicy(SocketPolicy.KEEP_OPEN),
        )
        // submitClarify response
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"run_id":"run-1","status":"running"}"""))

        val vm = ChatViewModel(
            savedStateHandle = SavedStateHandle(mapOf("sessionId" to "s1")),
            sessionRepository = sessionRepository,
            chatRunRepository = chatRunRepository,
        )
        val collector = launch(testDispatcher) { vm.uiState.collect() }

        vm.onDraftChanged("Deploy release")
        vm.sendMessage()

        val waiting = withTimeout(5_000) {
            vm.uiState.first { it.activeRun.phase == RunPhase.WAITING_CLARIFY }
        }
        assertNotNull(waiting.activeRun.pendingClarify)
        assertEquals(listOf("Staging", "Prod"), waiting.activeRun.pendingClarify?.choices)

        vm.submitClarify("Staging")
        val resumed = withTimeout(5_000) {
            vm.uiState.first { it.activeRun.pendingClarify == null && it.activeRun.phase == RunPhase.STREAMING }
        }
        assertNull(resumed.activeRun.pendingClarify)
        assertTrue(resumed.activeRun.tools.isNotEmpty())
        collector.cancel()
    }
}
