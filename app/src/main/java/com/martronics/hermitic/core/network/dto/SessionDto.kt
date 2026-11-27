package com.martronics.hermitic.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class SessionDto(
    val id: String,
    val title: String? = null,
    val source: String? = null,
    val model: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("last_active_at") val lastActiveAt: String? = null,
    @SerialName("message_count") val messageCount: Int? = null,
    @SerialName("end_reason") val endReason: String? = null,
)

@Serializable
data class SessionListResponseDto(
    val sessions: List<SessionDto> = emptyList(),
    val total: Int? = null,
    val limit: Int? = null,
    val offset: Int? = null,
    @SerialName("has_more") val hasMore: Boolean? = null,
)

@Serializable
data class CreateSessionRequestDto(
    val title: String? = null,
)

@Serializable
data class UpdateSessionRequestDto(
    val title: String? = null,
    @SerialName("end_reason") val endReason: String? = null,
)

@Serializable
data class MessageDto(
    val id: String? = null,
    val role: String,
    val content: JsonElement? = null,
    @SerialName("created_at") val createdAt: String? = null,
    val timestamp: String? = null,
    @SerialName("tool_name") val toolName: String? = null,
    @SerialName("tool_call_id") val toolCallId: String? = null,
)

@Serializable
data class SessionMessagesResponseDto(
    @SerialName("session_id") val sessionId: String? = null,
    val messages: List<MessageDto> = emptyList(),
    val total: Int? = null,
)
