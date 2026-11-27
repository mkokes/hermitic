package com.martronics.hermitic.core.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey val id: String,
    val title: String,
    val source: String?,
    val model: String?,
    val createdAt: String?,
    val updatedAt: String?,
    val lastActiveAt: String?,
    val messageCount: Int,
    val endReason: String?,
    val syncedAtMs: Long,
)
