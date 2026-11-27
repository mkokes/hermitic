package com.martronics.hermitic.feature.sessions

import com.martronics.hermitic.core.data.SessionRepository
import com.martronics.hermitic.core.data.dao.MessageDao
import com.martronics.hermitic.core.data.dao.SessionDao
import com.martronics.hermitic.core.data.entity.MessageEntity
import com.martronics.hermitic.core.data.entity.SessionEntity
import com.martronics.hermitic.core.network.HermesApiClient
import com.martronics.hermitic.core.network.HermesHeadersInterceptor
import com.martronics.hermitic.core.security.CredentialVault
import com.martronics.hermitic.core.security.SecretStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

private class SessionsTestSecretStore : SecretStore {
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

private class InMemorySessionDao : SessionDao {
    private val rows = MutableStateFlow<List<SessionEntity>>(emptyList())
    override fun observeAll(): Flow<List<SessionEntity>> = rows
    override fun observeByTitle(query: String): Flow<List<SessionEntity>> =
        rows.map { list -> list.filter { it.title.contains(query, ignoreCase = true) } }
    override suspend fun getById(sessionId: String): SessionEntity? = rows.value.firstOrNull { it.id == sessionId }
    override suspend fun upsert(session: SessionEntity) { upsertAll(listOf(session)) }
    override suspend fun upsertAll(sessions: List<SessionEntity>) {
        val merged = rows.value.associateBy { it.id }.toMutableMap()
        sessions.forEach { merged[it.id] = it }
        rows.value = merged.values.toList()
    }
    override suspend fun deleteById(sessionId: String) { rows.value = rows.value.filterNot { it.id == sessionId } }
    override suspend fun deleteMissing(keepIds: List<String>) {
        val keep = keepIds.toSet()
        rows.value = rows.value.filter { it.id in keep }
    }
    override suspend fun deleteAll() { rows.value = emptyList() }
}

private class InMemoryMessageDao : MessageDao {
    private val rows = MutableStateFlow<List<MessageEntity>>(emptyList())
    override fun observeForSession(sessionId: String): Flow<List<MessageEntity>> =
        rows.map { list -> list.filter { it.sessionId == sessionId } }
    override suspend fun upsert(message: MessageEntity) {}
    override suspend fun upsertAll(messages: List<MessageEntity>) {}
    override suspend fun deleteForSession(sessionId: String) {}
    override suspend fun deleteAll() { rows.value = emptyList() }
}

@OptIn(ExperimentalCoroutinesApi::class)
class SessionListViewModelTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: SessionRepository
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        server = MockWebServer()
        server.start()
        val store = SessionsTestSecretStore().apply {
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
        repository = SessionRepository(apiClient, InMemorySessionDao(), InMemoryMessageDao())
    }

    @After
    fun tearDown() {
        server.shutdown()
        Dispatchers.resetMain()
    }

    @Test
    fun loadsCreatesRenamesAndDeletesSessions() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"sessions":[{"id":"s1","title":"Alpha"}],"has_more":false}""",
            ),
        )
        server.enqueue(
            MockResponse().setResponseCode(200).setBody("""{"id":"s2","title":"Beta"}"""),
        )
        server.enqueue(
            MockResponse().setResponseCode(200).setBody("""{"id":"s2","title":"Beta Renamed"}"""),
        )
        server.enqueue(MockResponse().setResponseCode(204))

        val vm = SessionListViewModel(repository)
        val collector = launch(testDispatcher) { vm.uiState.collect() }

        assertEquals(1, vm.uiState.value.sessions.size)

        var createdId: String? = null
        vm.createSession("Beta") { createdId = it }
        assertEquals("s2", createdId)
        assertEquals(2, vm.uiState.value.sessions.size)

        vm.renameSession("s2", "Beta Renamed")
        assertEquals("Beta Renamed", vm.uiState.value.sessions.first { it.id == "s2" }.title)

        vm.deleteSession("s1")
        assertEquals(1, vm.uiState.value.sessions.size)
        collector.cancel()
    }
}
