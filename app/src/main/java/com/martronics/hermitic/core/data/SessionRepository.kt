package com.martronics.hermitic.core.data

import com.martronics.hermitic.core.data.dao.MessageDao
import com.martronics.hermitic.core.data.dao.SessionDao
import com.martronics.hermitic.core.data.entity.MessageEntity
import com.martronics.hermitic.core.data.entity.SessionEntity
import com.martronics.hermitic.core.network.HermesApiClient
import com.martronics.hermitic.core.network.dto.SessionDto
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

data class SessionPageResult(
    val fetchedCount: Int,
    val hasMore: Boolean,
)

@Singleton
class SessionRepository @Inject constructor(
    private val apiClient: HermesApiClient,
    private val sessionDao: SessionDao,
    private val messageDao: MessageDao,
) {
    fun observeSessions(query: String = ""): Flow<List<SessionEntity>> {
        val trimmed = query.trim()
        return if (trimmed.isEmpty()) {
            sessionDao.observeAll()
        } else {
            sessionDao.observeByTitle(trimmed)
        }
    }

    fun observeMessages(sessionId: String): Flow<List<MessageEntity>> =
        messageDao.observeForSession(sessionId)

    suspend fun refreshSessions(limit: Int = 30, offset: Int = 0): SessionPageResult {
        val page = apiClient.listSessions(limit = limit, offset = offset)
        val now = System.currentTimeMillis()
        val entities = page.sessions.map { it.toEntity(now) }
        if (entities.isNotEmpty()) {
            sessionDao.upsertAll(entities)
        }
        val hasMore = page.hasMore ?: (page.sessions.size >= limit)
        if (offset == 0 && !hasMore) {
            if (entities.isEmpty()) {
                sessionDao.deleteAll()
            } else {
                sessionDao.deleteMissing(entities.map { it.id })
            }
        }
        return SessionPageResult(
            fetchedCount = entities.size,
            hasMore = hasMore,
        )
    }

    suspend fun createSession(title: String? = null): SessionEntity {
        val created = apiClient.createSession(title)
        val entity = created.toEntity(System.currentTimeMillis())
        sessionDao.upsert(entity)
        return entity
    }

    suspend fun renameSession(sessionId: String, title: String): SessionEntity {
        val updated = apiClient.updateSession(sessionId = sessionId, title = title.trim())
        val entity = updated.toEntity(System.currentTimeMillis())
        sessionDao.upsert(entity)
        return entity
    }

    suspend fun deleteSession(sessionId: String) {
        apiClient.deleteSession(sessionId)
        sessionDao.deleteById(sessionId)
    }

    suspend fun refreshMessages(sessionId: String): List<MessageEntity> {
        ensureSessionRow(sessionId)
        val remote = apiClient.getSessionMessages(sessionId = sessionId)
        val entities = remote.mapIndexed { idx, dto ->
            val stableId = dto.id?.takeIf { it.isNotBlank() } ?: "$sessionId:$idx:${dto.role}"
            MessageEntity(
                id = stableId,
                sessionId = sessionId,
                sequenceIndex = idx,
                role = dto.role,
                content = MessageContentParser.toDisplayText(dto.content),
                createdAt = dto.createdAt ?: dto.timestamp,
                toolName = dto.toolName,
                toolCallId = dto.toolCallId,
            )
        }
        messageDao.replaceForSession(sessionId, entities)
        return entities
    }

    suspend fun ensureSessionRow(sessionId: String) {
        if (sessionDao.getById(sessionId) == null) {
            sessionDao.upsert(
                SessionEntity(
                    id = sessionId,
                    title = "Session $sessionId",
                    source = "api_server",
                    model = null,
                    createdAt = null,
                    updatedAt = null,
                    lastActiveAt = null,
                    messageCount = 0,
                    endReason = null,
                    syncedAtMs = System.currentTimeMillis(),
                ),
            )
        }
    }

    suspend fun appendOrUpdateLocalMessage(message: MessageEntity) {
        ensureSessionRow(message.sessionId)
        messageDao.upsert(message)
    }

    suspend fun wipeCache() {
        messageDao.deleteAll()
        sessionDao.deleteAll()
    }

    private fun SessionDto.toEntity(syncedAtMs: Long): SessionEntity = SessionEntity(
        id = id,
        title = title?.takeIf { it.isNotBlank() } ?: "Untitled session",
        source = source,
        model = model,
        createdAt = createdAt,
        updatedAt = updatedAt,
        lastActiveAt = lastActiveAt,
        messageCount = messageCount ?: 0,
        endReason = endReason,
        syncedAtMs = syncedAtMs,
    )
}
