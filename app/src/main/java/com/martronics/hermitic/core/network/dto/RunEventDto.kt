package com.martronics.hermitic.core.network.dto

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

sealed interface RunStreamEvent {
    data class AssistantDelta(val text: String) : RunStreamEvent
    data class AssistantCommentary(val text: String, val alreadyStreamed: Boolean) : RunStreamEvent
    data class ToolStarted(val tool: String, val preview: String) : RunStreamEvent
    data class ToolCompleted(
        val tool: String,
        val durationSeconds: Double?,
        val error: Boolean,
        val preview: String,
    ) : RunStreamEvent

    data class SubagentStarted(val delegationId: String?, val goal: String?) : RunStreamEvent
    data class SubagentCompleted(
        val delegationId: String?,
        val status: String?,
        val summary: String?,
    ) : RunStreamEvent

    data class ApprovalRequested(
        val requestId: String,
        val executionGeneration: Int,
        val tool: String?,
        val command: String?,
        val summary: String?,
    ) : RunStreamEvent

    data class ClarifyRequested(
        val requestId: String,
        val executionGeneration: Int,
        val question: String,
        val choices: List<String>,
    ) : RunStreamEvent

    data class RunCompleted(val output: String?, val errorCode: String?) : RunStreamEvent
    data class RunIncomplete(val output: String?, val errorCode: String?) : RunStreamEvent
    data class RunFailed(val error: String) : RunStreamEvent
    data object RunCancelled : RunStreamEvent
    data class RunInterrupted(val error: String?) : RunStreamEvent
    data class StreamDisconnected(val cause: Throwable?) : RunStreamEvent
}

object RunEventParser {

    fun parse(eventType: String?, rawData: String, json: Json): RunStreamEvent? {
        val trimmed = rawData.trim()
        if (trimmed.isEmpty() || trimmed == "[DONE]") return null

        val obj = runCatching { json.parseToJsonElement(trimmed) as? JsonObject }.getOrNull()
        val resolvedType = eventType?.takeIf { it.isNotBlank() }
            ?: obj?.str("type")
            ?: obj?.str("event")
            ?: return null

        return when (resolvedType) {
            "assistant.delta", "message.delta", "response.output_text.delta" -> {
                val text = obj?.str("delta") ?: obj?.str("text") ?: ""
                if (text.isEmpty()) null else RunStreamEvent.AssistantDelta(text)
            }

            "assistant.commentary", "message.interim" -> {
                val text = obj?.str("text") ?: ""
                val already = obj?.bool("already_streamed") ?: false
                if (text.isEmpty()) null else RunStreamEvent.AssistantCommentary(text, already)
            }

            "tool.started", "hermes.tool.progress" -> {
                val tool = obj?.str("tool") ?: obj?.str("name") ?: "tool"
                val preview = obj?.str("preview") ?: obj?.str("arguments") ?: ""
                RunStreamEvent.ToolStarted(tool = tool, preview = preview)
            }

            "tool.completed", "tool.failed" -> {
                val tool = obj?.str("tool") ?: obj?.str("name") ?: "tool"
                val isFailed = resolvedType == "tool.failed" || (obj?.bool("error") == true)
                val duration = obj?.double("duration")
                val preview = obj?.str("preview") ?: obj?.str("output") ?: obj?.str("error_message") ?: ""
                RunStreamEvent.ToolCompleted(
                    tool = tool,
                    durationSeconds = duration,
                    error = isFailed,
                    preview = preview,
                )
            }

            "subagent.start" -> RunStreamEvent.SubagentStarted(
                delegationId = obj?.str("delegation_id"),
                goal = obj?.str("goal") ?: obj?.str("summary"),
            )

            "subagent.complete" -> RunStreamEvent.SubagentCompleted(
                delegationId = obj?.str("delegation_id"),
                status = obj?.str("status"),
                summary = obj?.str("summary"),
            )

            "approval.request" -> {
                val reqId = obj?.str("request_id") ?: obj?.str("prompt_id") ?: return null
                val gen = obj.int("execution_generation") ?: 0
                RunStreamEvent.ApprovalRequested(
                    requestId = reqId,
                    executionGeneration = gen,
                    tool = obj.str("tool"),
                    command = obj.str("command"),
                    summary = obj.str("summary") ?: obj.str("description"),
                )
            }

            "clarify.request" -> {
                val reqId = obj?.str("request_id") ?: obj?.str("prompt_id") ?: return null
                val gen = obj.int("execution_generation") ?: 0
                val choices = (obj["choices"] as? JsonArray)
                    ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                    .orEmpty()
                RunStreamEvent.ClarifyRequested(
                    requestId = reqId,
                    executionGeneration = gen,
                    question = obj.str("question") ?: obj.str("prompt") ?: "Clarification needed",
                    choices = choices,
                )
            }

            "run.completed", "response.completed" -> RunStreamEvent.RunCompleted(
                output = obj?.str("output"),
                errorCode = obj?.str("error_code"),
            )

            "run.incomplete", "response.incomplete" -> RunStreamEvent.RunIncomplete(
                output = obj?.str("output"),
                errorCode = obj?.str("error_code") ?: "iteration_budget_exhausted",
            )

            "run.failed", "response.failed" -> RunStreamEvent.RunFailed(
                error = obj?.str("error") ?: obj?.str("message") ?: "Run failed",
            )

            "run.cancelled", "response.cancelled" -> RunStreamEvent.RunCancelled

            "run.interrupted" -> RunStreamEvent.RunInterrupted(
                error = obj?.str("error"),
            )

            else -> null
        }
    }

    private fun JsonObject.str(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.bool(key: String): Boolean? =
        (this[key] as? JsonPrimitive)?.booleanOrNull

    private fun JsonObject.int(key: String): Int? =
        (this[key] as? JsonPrimitive)?.intOrNull

    private fun JsonObject.double(key: String): Double? =
        (this[key] as? JsonPrimitive)?.doubleOrNull
}
