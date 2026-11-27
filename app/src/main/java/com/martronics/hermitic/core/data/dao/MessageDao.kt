package com.martronics.hermitic.core.data.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.martronics.hermitic.core.data.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {

    @Query("SELECT * FROM messages WHERE sessionId = :sessionId ORDER BY sequenceIndex ASC")
    fun observeForSession(sessionId: String): Flow<List<MessageEntity>>

    @Upsert
    suspend fun upsert(message: MessageEntity)

    @Upsert
    suspend fun upsertAll(messages: List<MessageEntity>)

    @Query("DELETE FROM messages WHERE sessionId = :sessionId")
    suspend fun deleteForSession(sessionId: String)

    @Transaction
    suspend fun replaceForSession(sessionId: String, messages: List<MessageEntity>) {
        deleteForSession(sessionId)
        if (messages.isNotEmpty()) {
            upsertAll(messages)
        }
    }

    @Query("DELETE FROM messages")
    suspend fun deleteAll()
}
