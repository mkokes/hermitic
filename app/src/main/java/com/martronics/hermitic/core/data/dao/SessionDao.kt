package com.martronics.hermitic.core.data.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.martronics.hermitic.core.data.entity.SessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {

    @Query(
        """
        SELECT * FROM sessions
        ORDER BY COALESCE(lastActiveAt, updatedAt, createdAt, '') DESC, syncedAtMs DESC
        """,
    )
    fun observeAll(): Flow<List<SessionEntity>>

    @Query(
        """
        SELECT * FROM sessions
        WHERE title LIKE '%' || :query || '%'
        ORDER BY COALESCE(lastActiveAt, updatedAt, createdAt, '') DESC, syncedAtMs DESC
        """,
    )
    fun observeByTitle(query: String): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE id = :sessionId LIMIT 1")
    suspend fun getById(sessionId: String): SessionEntity?

    @Upsert
    suspend fun upsert(session: SessionEntity)

    @Upsert
    suspend fun upsertAll(sessions: List<SessionEntity>)

    @Query("DELETE FROM sessions WHERE id = :sessionId")
    suspend fun deleteById(sessionId: String)

    @Query("DELETE FROM sessions WHERE id NOT IN (:keepIds)")
    suspend fun deleteMissing(keepIds: List<String>)

    @Query("DELETE FROM sessions")
    suspend fun deleteAll()
}
