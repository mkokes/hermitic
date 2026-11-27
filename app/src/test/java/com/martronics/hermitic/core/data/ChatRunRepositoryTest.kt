package com.martronics.hermitic.core.data

import com.martronics.hermitic.core.data.dao.MessageDao
import com.martronics.hermitic.core.data.dao.SessionDao
import com.martronics.hermitic.core.data.entity.MessageEntity
import com.martronics.hermitic.core.data.entity.SessionEntity
import com.martronics.hermitic.core.network.HermesApiClient
import com.martronics.hermitic.core.network.HermesHeadersInterceptor
import com.martronics.hermitic.core.network.HermesSseClient
import com.martronics.hermitic.core.security.CredentialVault
import com.martronics.hermitic.core.security.SecretStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private class ChatRepoSecretStore : SecretStore {
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

private class ChatFakeSessionDao : SessionDao {
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

private class ChatFakeMessageDao : MessageDao {
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

class ChatRunRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: ChatRunRepository

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()
        val store = ChatRepoSecretStore().apply {
            putString("base_url", server.url("/").toString().trimEnd('/'))
            putString("api_server_key", "key-chat")
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
        val sessionRepo = SessionRepository(apiClient, ChatFakeSessionDao(), ChatFakeMessageDao())
        repository = ChatRunRepository(apiClient, sseClient, sessionRepo)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun recoversFromSseDropViaRunPollAndRefreshesTranscript() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(202).setBody("""{"run_id":"run-99","status":"started"}"""))
        // Dropped SSE connection before terminal event
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "text/event-stream")
                .setBody("event: assistant.delta\ndata: {\"delta\":\"Partial\"}\n\n")
                .setSocketPolicy(SocketPolicy.DISCONNECT_AT_END),
        )
        // Reconcile poll returns completed
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"run_id":"run-99","status":"completed","output":"Partial complete"}"""),
        )
        // Transcript refresh
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"messages":[{"id":"m1","role":"user","content":"hi"},{"id":"m2","role":"assistant","content":"Partial complete"}]}"""),
        )

        repository.sendPrompt("s1", "hi", idempotencyKey = "idem-123")

        val completed = withTimeout(5_000) {
            repository.activeRun.first { it.phase == RunPhase.COMPLETED }
        }
        assertEquals("run-99", completed.runId)
        assertTrue(completed.streamedText.contains("Partial"))

        val createReq = server.takeRequest()
        assertEquals("idem-123", createReq.getHeader("Idempotency-Key"))
    }

    @Test
    fun handles409ConflictOnStaleApprovalCleanly() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(202).setBody("""{"run_id":"run-50","status":"started"}"""))
        // Keep SSE waiting on approval without closing until test finishes
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "text/event-stream")
                .setBody("event: approval.request\ndata: {\"request_id\":\"p1\",\"execution_generation\":4,\"tool\":\"terminal\"}\n\n")
                .setSocketPolicy(SocketPolicy.KEEP_OPEN),
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(409)
                .setBody("""{"code":"stale_generation","detail":"Already resolved"}"""),
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"run_id":"run-50","status":"completed","output":"Resolved elsewhere"}"""),
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"messages":[]}"""),
        )

        repository.sendPrompt("s1", "run command", idempotencyKey = "idem-50")
        withTimeout(5_000) {
            repository.activeRun.first { it.phase == RunPhase.WAITING_APPROVAL }
        }

        repository.submitApproval("once")
        val settled = withTimeout(5_000) {
            repository.activeRun.first { it.phase == RunPhase.COMPLETED }
        }
        assertNull(settled.pendingApproval)
        assertTrue(settled.errorMessage?.contains("stale_generation") == true)
    }
}
