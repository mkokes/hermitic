package com.martronics.hermitic.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class CreateRunRequestDto(
    val input: String,
    @SerialName("session_id") val sessionId: String? = null,
    val instructions: String? = null,
    val model: String? = null,
    val provider: String? = null,
)

@Serializable
data class CreateRunResponseDto(
    @SerialName("run_id") val runId: String,
    val status: String,
    @SerialName("session_id") val sessionId: String? = null,
)

@Serializable
data class PendingControlDto(
    val kind: String? = null,
    val type: String? = null,
    @SerialName("prompt_id") val promptId: String? = null,
    @SerialName("request_id") val requestId: String? = null,
    @SerialName("execution_generation") val executionGeneration: Int = 0,
    val tool: String? = null,
    val command: String? = null,
    val summary: String? = null,
    val question: String? = null,
    val prompt: String? = null,
    val choices: List<String> = emptyList(),
) {
    val effectiveRequestId: String
        get() = requestId ?: promptId.orEmpty()
}

@Serializable
data class RunUsageDto(
    @SerialName("input_tokens") val inputTokens: Int = 0,
    @SerialName("output_tokens") val outputTokens: Int = 0,
    @SerialName("total_tokens") val totalTokens: Int = 0,
    @SerialName("cache_read_tokens") val cacheReadTokens: Int = 0,
    @SerialName("cache_write_tokens") val cacheWriteTokens: Int = 0,
)

@Serializable
data class RunRuntimeDto(
    val provider: String? = null,
    val model: String? = null,
    @SerialName("route_source") val routeSource: String? = null,
)

@Serializable
data class RunStatusResponseDto(
    @SerialName("object") val objectType: String? = null,
    @SerialName("run_id") val runId: String,
    val status: String,
    @SerialName("session_id") val sessionId: String? = null,
    @SerialName("admission_id") val admissionId: String? = null,
    @SerialName("execution_generation") val executionGeneration: Int? = null,
    val model: String? = null,
    val output: String? = null,
    val error: String? = null,
    @SerialName("error_code") val errorCode: String? = null,
    @SerialName("pending_controls") val pendingControls: List<PendingControlDto> = emptyList(),
    @SerialName("pending_control") val pendingControl: PendingControlDto? = null,
    val usage: RunUsageDto? = null,
    val runtime: RunRuntimeDto? = null,
    @SerialName("shutdown_requested_at") val shutdownRequestedAt: Long? = null,
    val details: JsonElement? = null,
)

@Serializable
data class RunStopResponseDto(
    val status: String = "stopping",
)

@Serializable
data class RunApprovalRequestDto(
    @SerialName("request_id") val requestId: String,
    @SerialName("execution_generation") val executionGeneration: Int,
    val choice: String,
)

@Serializable
data class RunClarifyRequestDto(
    @SerialName("request_id") val requestId: String,
    @SerialName("execution_generation") val executionGeneration: Int,
    val answer: String,
)

@Serializable
data class ApiErrorDto(
    val error: String? = null,
    val code: String? = null,
    val detail: String? = null,
    val message: String? = null,
)
