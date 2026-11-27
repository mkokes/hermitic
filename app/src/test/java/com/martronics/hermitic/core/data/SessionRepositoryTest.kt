package com.martronics.hermitic.core.data

import com.martronics.hermitic.core.data.dao.MessageDao
import com.martronics.hermitic.core.data.dao.SessionDao
import com.martronics.hermitic.core.data.entity.MessageEntity
import com.martronics.hermitic.core.data.entity.SessionEntity
import com.martronics.hermitic.core.network.HermesApiClient
import com.martronics.hermitic.core.network.HermesHeadersInterceptor
import com.martronics.hermitic.core.security.CredentialVault
import com.martronics.hermitic.core.security.SecretStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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

private class RepoSecretStore : SecretStore {
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

private class FakeSessionDao : SessionDao {
    private val rows = MutableStateFlow<List<SessionEntity>>(emptyList())

    override fun observeAll(): Flow<List<SessionEntity>> = rows

    override fun observeByTitle(query: String): Flow<List<SessionEntity>> =
        rows.map { list -> list.filter { it.title.contains(query, ignoreCase = true) } }

    override suspend fun getById(sessionId: String): SessionEntity? =
        rows.value.firstOrNull { it.id == sessionId }

    override suspend fun upsert(session: SessionEntity) {
        upsertAll(listOf(session))
    }

    override suspend fun upsertAll(sessions: List<SessionEntity>) {
        val merged = rows.value.associateBy { it.id }.toMutableMap()
        sessions.forEach { merged[it.id] = it }
        rows.value = merged.values.toList()
    }

    override suspend fun deleteById(sessionId: String) {
        rows.value = rows.value.filterNot { it.id == sessionId }
    }

    override suspend fun deleteMissing(keepIds: List<String>) {
        val keep = keepIds.toSet()
        rows.value = rows.value.filter { it.id in keep }
    }

    override suspend fun deleteAll() {
        rows.value = emptyList()
    }
}

private class FakeMessageDao : MessageDao {
    private val rows = MutableStateFlow<List<MessageEntity>>(emptyList())

    override fun observeForSession(sessionId: String): Flow<List<MessageEntity>> =
        rows.map { list -> list.filter { it.sessionId == sessionId }.sortedBy { it.sequenceIndex } }

    override suspend fun upsert(message: MessageEntity) {
        upsertAll(listOf(message))
    }

    override suspend fun upsertAll(messages: List<MessageEntity>) {
        val merged = rows.value.associateBy { it.id }.toMutableMap()
        messages.forEach { merged[it.id] = it }
        rows.value = merged.values.toList()
    }

    override suspend fun deleteForSession(sessionId: String) {
        rows.value = rows.value.filterNot { it.sessionId == sessionId }
    }

    override suspend fun deleteAll() {
        rows.value = emptyList()
    }
}

class SessionRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: SessionRepository

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()
        val store = RepoSecretStore().apply {
            putString("base_url", server.url("/").toString().trimEnd('/'))
            putString("api_server_key", "key-1")
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

        repository = SessionRepository(apiClient, FakeSessionDao(), FakeMessageDao())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun refreshesCachesAndFiltersSessions() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "sessions": [
                    {"id": "s1", "title": "Android Build", "message_count": 4},
                    {"id": "s2", "title": "Cloudflare Tunnel", "message_count": 2}
                  ],
                  "has_more": false
                }
                """.trimIndent(),
            ),
        )

        val result = repository.refreshSessions()
        assertEquals(2, result.fetchedCount)
        assertEquals(false, result.hasMore)

        val all = repository.observeSessions().first()
        assertEquals(2, all.size)

        val filtered = repository.observeSessions("Cloudflare").first()
        assertEquals(1, filtered.size)
        assertEquals("s2", filtered.first().id)
    }

    @Test
    fun refreshMessagesUsesInlineImagesFalseAndParsesPlaceholders() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "session_id": "s1",
                  "messages": [
                    {
                      "id": "m1",
                      "role": "user",
                      "content": [
                        {"type": "text", "text": "Inspect this screenshot"},
                        {"type": "image_url", "image_url": {"url": "[image]"}}
                      ]
                    }
                  ]
                }
                """.trimIndent(),
            ),
        )

        val messages = repository.refreshMessages("s1")
        assertEquals(1, messages.size)
        assertEquals("Inspect this screenshot\n[image]", messages.first().content)
        val req = server.takeRequest()
        assertTrue(req.path!!.contains("inline_images=false"))
    }
}
