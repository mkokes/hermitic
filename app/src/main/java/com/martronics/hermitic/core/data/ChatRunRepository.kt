package com.martronics.hermitic.core.data

import com.martronics.hermitic.core.data.entity.MessageEntity
import com.martronics.hermitic.core.network.HermesApiClient
import com.martronics.hermitic.core.network.HermesApiError
import com.martronics.hermitic.core.network.HermesSseClient
import com.martronics.hermitic.core.network.dto.PendingControlDto
import com.martronics.hermitic.core.network.dto.RunStatusResponseDto
import com.martronics.hermitic.core.network.dto.RunStreamEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

enum class ToolStatus {
    RUNNING,
    COMPLETED,
    FAILED,
}

data class ToolTimelineItem(
    val id: String,
    val tool: String,
    val status: ToolStatus,
    val preview: String,
    val durationSeconds: Double? = null,
)

data class PendingApproval(
    val requestId: String,
    val executionGeneration: Int,
    val tool: String?,
    val command: String?,
    val summary: String?,
)

data class PendingClarify(
    val requestId: String,
    val executionGeneration: Int,
    val question: String,
    val choices: List<String>,
)

enum class RunPhase {
    IDLE,
    SUBMITTING,
    STREAMING,
    WAITING_APPROVAL,
    WAITING_CLARIFY,
    STOPPING,
    RECONNECTING,
    COMPLETED,
    INCOMPLETE,
    CANCELLED,
    FAILED,
}

data class ActiveRunState(
    val sessionId: String = "",
    val runId: String? = null,
    val phase: RunPhase = RunPhase.IDLE,
    val streamedText: String = "",
    val interimText: String = "",
    val tools: List<ToolTimelineItem> = emptyList(),
    val pendingApproval: PendingApproval? = null,
    val pendingClarify: PendingClarify? = null,
    val errorMessage: String? = null,
    val isIncompleteBudgetExhausted: Boolean = false,
) {
    val isActive: Boolean
        get() = phase == RunPhase.SUBMITTING ||
            phase == RunPhase.STREAMING ||
            phase == RunPhase.WAITING_APPROVAL ||
            phase == RunPhase.WAITING_CLARIFY ||
            phase == RunPhase.STOPPING ||
            phase == RunPhase.RECONNECTING
}

@Singleton
class ChatRunRepository @Inject constructor(
    private val apiClient: HermesApiClient,
    private val sseClient: HermesSseClient,
    private val sessionRepository: SessionRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _activeRun = MutableStateFlow(ActiveRunState())
    val activeRun: StateFlow<ActiveRunState> = _activeRun.asStateFlow()

    private var streamJob: Job? = null

    fun dismissError() {
        _activeRun.update { it.copy(errorMessage = null) }
    }

    fun resetForSession(sessionId: String) {
        if (_activeRun.value.sessionId != sessionId && !_activeRun.value.isActive) {
            _activeRun.value = ActiveRunState(sessionId = sessionId)
        }
    }

    fun sendPrompt(
        sessionId: String,
        prompt: String,
        idempotencyKey: String = UUID.randomUUID().toString(),
    ) {
        val trimmed = prompt.trim()
        if (trimmed.isEmpty() || _activeRun.value.isActive) return

        streamJob?.cancel()
        streamJob = scope.launch {
            val optimisticId = "local-user-$idempotencyKey"
            sessionRepository.appendOrUpdateLocalMessage(
                MessageEntity(
                    id = optimisticId,
                    sessionId = sessionId,
                    sequenceIndex = Int.MAX_VALUE / 2,
                    role = "user",
                    content = trimmed,
                    createdAt = null,
                    toolName = null,
                    toolCallId = null,
                ),
            )

            _activeRun.value = ActiveRunState(
                sessionId = sessionId,
                phase = RunPhase.SUBMITTING,
            )

            val created = submitWithIdempotencyRetry(trimmed, sessionId, idempotencyKey)
            created.onSuccess { response ->
                _activeRun.update {
                    it.copy(
                        runId = response.runId,
                        phase = RunPhase.STREAMING,
                    )
                }
                consumeWithReconciliation(runId = response.runId, sessionId = sessionId)
            }.onFailure { err ->
                _activeRun.update {
                    it.copy(
                        phase = RunPhase.FAILED,
                        errorMessage = err.message ?: "Failed to start run.",
                    )
                }
            }
        }
    }

    private suspend fun submitWithIdempotencyRetry(
        prompt: String,
        sessionId: String,
        idempotencyKey: String,
    ): Result<com.martronics.hermitic.core.network.dto.CreateRunResponseDto> {
        var attempt = 0
        var lastError: Throwable? = null
        while (attempt < 2) {
            try {
                return Result.success(
                    apiClient.createRun(
                        input = prompt,
                        sessionId = sessionId,
                        idempotencyKey = idempotencyKey,
                    ),
                )
            } catch (e: HermesApiError.NetworkFailure) {
                lastError = e
                attempt++
                if (attempt < 2) delay(600L)
            } catch (t: Throwable) {
                return Result.failure(t)
            }
        }
        return Result.failure(lastError ?: HermesApiError.NetworkFailure("Run admission failed"))
    }

    private suspend fun consumeWithReconciliation(runId: String, sessionId: String) {
        var reconnectAttempt = 0
        while (true) {
            var disconnected = false
            try {
                sseClient.streamRunEvents(runId).collect { event ->
                    when (event) {
                        is RunStreamEvent.StreamDisconnected -> disconnected = true
                        else -> applyEvent(sessionId, event)
                    }
                }
            } catch (t: Throwable) {
                _activeRun.update {
                    it.copy(
                        phase = RunPhase.FAILED,
                        errorMessage = t.message ?: "Stream error",
                    )
                }
                return
            }

            if (!disconnected || _activeRun.value.phase in setOf(
                    RunPhase.COMPLETED,
                    RunPhase.INCOMPLETE,
                    RunPhase.CANCELLED,
                    RunPhase.FAILED,
                )
            ) {
                return
            }

            if (reconnectAttempt >= MAX_RECONNECT_ATTEMPTS) {
                _activeRun.update {
                    it.copy(
                        phase = RunPhase.FAILED,
                        errorMessage = "Lost connection to stream after $MAX_RECONNECT_ATTEMPTS retries.",
                    )
                }
                return
            }

            _activeRun.update { it.copy(phase = RunPhase.RECONNECTING) }
            delay(500L * (1L shl reconnectAttempt))
            reconnectAttempt++

            val polled = runCatching { apiClient.getRun(runId) }.getOrNull() ?: continue
            if (reconcileFromPoll(sessionId, polled)) {
                return
            }
        }
    }

    private suspend fun applyEvent(sessionId: String, event: RunStreamEvent) {
        when (event) {
            is RunStreamEvent.AssistantDelta -> {
                _activeRun.update {
                    it.copy(
                        phase = if (it.phase == RunPhase.STOPPING) RunPhase.STOPPING else RunPhase.STREAMING,
                        streamedText = it.streamedText + event.text,
                    )
                }
            }

            is RunStreamEvent.AssistantCommentary -> {
                if (!event.alreadyStreamed) {
                    _activeRun.update {
                        it.copy(interimText = event.text)
                    }
                }
            }

            is RunStreamEvent.ToolStarted -> {
                val item = ToolTimelineItem(
                    id = "${event.tool}-${_activeRun.value.tools.size}",
                    tool = event.tool,
                    status = ToolStatus.RUNNING,
                    preview = event.preview,
                )
                _activeRun.update { it.copy(tools = it.tools + item) }
            }

            is RunStreamEvent.ToolCompleted -> {
                _activeRun.update { state ->
                    val updated = state.tools.toMutableList()
                    val idx = updated.indexOfLast { it.tool == event.tool && it.status == ToolStatus.RUNNING }
                    val newItem = ToolTimelineItem(
                        id = if (idx >= 0) updated[idx].id else "${event.tool}-${updated.size}",
                        tool = event.tool,
                        status = if (event.error) ToolStatus.FAILED else ToolStatus.COMPLETED,
                        preview = event.preview,
                        durationSeconds = event.durationSeconds,
                    )
                    if (idx >= 0) updated[idx] = newItem else updated.add(newItem)
                    state.copy(tools = updated)
                }
            }

            is RunStreamEvent.SubagentStarted -> {
                val item = ToolTimelineItem(
                    id = "subagent-${event.delegationId ?: _activeRun.value.tools.size}",
                    tool = "delegate_task",
                    status = ToolStatus.RUNNING,
                    preview = event.goal ?: "Running delegated subagent",
                )
                _activeRun.update { it.copy(tools = it.tools + item) }
            }

            is RunStreamEvent.SubagentCompleted -> {
                _activeRun.update { state ->
                    val item = ToolTimelineItem(
                        id = "subagent-${event.delegationId ?: state.tools.size}-done",
                        tool = "delegate_task",
                        status = if (event.status == "failed") ToolStatus.FAILED else ToolStatus.COMPLETED,
                        preview = event.summary ?: event.status.orEmpty(),
                    )
                    state.copy(tools = state.tools + item)
                }
            }

            is RunStreamEvent.ApprovalRequested -> {
                _activeRun.update {
                    it.copy(
                        phase = RunPhase.WAITING_APPROVAL,
                        pendingApproval = PendingApproval(
                            requestId = event.requestId,
                            executionGeneration = event.executionGeneration,
                            tool = event.tool,
                            command = event.command,
                            summary = event.summary,
                        ),
                        pendingClarify = null,
                    )
                }
            }

            is RunStreamEvent.ClarifyRequested -> {
                _activeRun.update {
                    it.copy(
                        phase = RunPhase.WAITING_CLARIFY,
                        pendingClarify = PendingClarify(
                            requestId = event.requestId,
                            executionGeneration = event.executionGeneration,
                            question = event.question,
                            choices = event.choices,
                        ),
                        pendingApproval = null,
                    )
                }
            }

            is RunStreamEvent.RunCompleted -> {
                val isBudgetExhausted = event.errorCode == "iteration_budget_exhausted"
                _activeRun.update {
                    it.copy(
                        phase = if (isBudgetExhausted) RunPhase.INCOMPLETE else RunPhase.COMPLETED,
                        streamedText = it.streamedText.ifBlank { event.output.orEmpty() },
                        pendingApproval = null,
                        pendingClarify = null,
                        isIncompleteBudgetExhausted = isBudgetExhausted,
                    )
                }
                runCatching { sessionRepository.refreshMessages(sessionId) }
            }

            is RunStreamEvent.RunIncomplete -> {
                _activeRun.update {
                    it.copy(
                        phase = RunPhase.INCOMPLETE,
                        streamedText = it.streamedText.ifBlank { event.output.orEmpty() },
                        pendingApproval = null,
                        pendingClarify = null,
                        isIncompleteBudgetExhausted = true,
                    )
                }
                runCatching { sessionRepository.refreshMessages(sessionId) }
            }

            is RunStreamEvent.RunCancelled -> {
                _activeRun.update {
                    it.copy(
                        phase = RunPhase.CANCELLED,
                        pendingApproval = null,
                        pendingClarify = null,
                    )
                }
                runCatching { sessionRepository.refreshMessages(sessionId) }
            }

            is RunStreamEvent.RunFailed -> {
                _activeRun.update {
                    it.copy(
                        phase = RunPhase.FAILED,
                        errorMessage = event.error,
                        pendingApproval = null,
                        pendingClarify = null,
                    )
                }
                runCatching { sessionRepository.refreshMessages(sessionId) }
            }

            is RunStreamEvent.RunInterrupted -> {
                _activeRun.update {
                    it.copy(
                        phase = RunPhase.FAILED,
                        errorMessage = event.error ?: "Gateway interrupted the run.",
                        pendingApproval = null,
                        pendingClarify = null,
                    )
                }
                runCatching { sessionRepository.refreshMessages(sessionId) }
            }

            is RunStreamEvent.StreamDisconnected -> Unit
        }
    }

    private suspend fun reconcileFromPoll(sessionId: String, status: RunStatusResponseDto): Boolean {
        val controls = buildList {
            addAll(status.pendingControls)
            status.pendingControl?.let { add(it) }
        }
        if (controls.isNotEmpty()) {
            applyPendingControl(controls.first())
            return false
        }

        return when (status.status) {
            "completed" -> {
                val isBudgetExhausted = status.errorCode == "iteration_budget_exhausted"
                _activeRun.update {
                    it.copy(
                        phase = if (isBudgetExhausted) RunPhase.INCOMPLETE else RunPhase.COMPLETED,
                        streamedText = it.streamedText.ifBlank { status.output.orEmpty() },
                        pendingApproval = null,
                        pendingClarify = null,
                        isIncompleteBudgetExhausted = isBudgetExhausted,
                    )
                }
                runCatching { sessionRepository.refreshMessages(sessionId) }
                true
            }

            "incomplete" -> {
                _activeRun.update {
                    it.copy(
                        phase = RunPhase.INCOMPLETE,
                        streamedText = it.streamedText.ifBlank { status.output.orEmpty() },
                        pendingApproval = null,
                        pendingClarify = null,
                        isIncompleteBudgetExhausted = true,
                    )
                }
                runCatching { sessionRepository.refreshMessages(sessionId) }
                true
            }

            "cancelled" -> {
                _activeRun.update {
                    it.copy(
                        phase = RunPhase.CANCELLED,
                        pendingApproval = null,
                        pendingClarify = null,
                    )
                }
                runCatching { sessionRepository.refreshMessages(sessionId) }
                true
            }

            "failed", "interrupted" -> {
                _activeRun.update {
                    it.copy(
                        phase = RunPhase.FAILED,
                        errorMessage = status.error ?: "Run ${status.status}",
                        pendingApproval = null,
                        pendingClarify = null,
                    )
                }
                runCatching { sessionRepository.refreshMessages(sessionId) }
                true
            }

            else -> {
                _activeRun.update { it.copy(phase = RunPhase.STREAMING) }
                false
            }
        }
    }

    private fun applyPendingControl(control: PendingControlDto) {
        val kind = (control.kind ?: control.type).orEmpty().lowercase()
        if (kind.contains("clarify")) {
            _activeRun.update {
                it.copy(
                    phase = RunPhase.WAITING_CLARIFY,
                    pendingClarify = PendingClarify(
                        requestId = control.effectiveRequestId,
                        executionGeneration = control.executionGeneration,
                        question = control.question ?: control.prompt ?: "Clarification needed",
                        choices = control.choices,
                    ),
                    pendingApproval = null,
                )
            }
        } else {
            _activeRun.update {
                it.copy(
                    phase = RunPhase.WAITING_APPROVAL,
                    pendingApproval = PendingApproval(
                        requestId = control.effectiveRequestId,
                        executionGeneration = control.executionGeneration,
                        tool = control.tool,
                        command = control.command,
                        summary = control.summary,
                    ),
                    pendingClarify = null,
                )
            }
        }
    }

    fun stopActiveRun() {
        val runId = _activeRun.value.runId ?: return
        scope.launch {
            _activeRun.update { it.copy(phase = RunPhase.STOPPING) }
            runCatching { apiClient.stopRun(runId) }
                .onFailure { err ->
                    _activeRun.update {
                        it.copy(errorMessage = err.message ?: "Failed to stop run")
                    }
                }
        }
    }

    fun submitApproval(choice: String) {
        val state = _activeRun.value
        val runId = state.runId ?: return
        val pending = state.pendingApproval ?: return
        scope.launch {
            runCatching {
                apiClient.submitApproval(
                    runId = runId,
                    requestId = pending.requestId,
                    executionGeneration = pending.executionGeneration,
                    choice = choice,
                )
            }.onSuccess {
                _activeRun.update {
                    it.copy(
                        phase = RunPhase.STREAMING,
                        pendingApproval = null,
                    )
                }
            }.onFailure { err ->
                if (err is HermesApiError.Conflict) {
                    _activeRun.update {
                        it.copy(
                            pendingApproval = null,
                            errorMessage = "Approval already resolved or expired (${err.code}).",
                        )
                    }
                    runCatching { apiClient.getRun(runId) }.onSuccess { polled ->
                        if (reconcileFromPoll(state.sessionId, polled)) {
                            streamJob?.cancel()
                        }
                    }
                } else {
                    _activeRun.update {
                        it.copy(errorMessage = err.message ?: "Approval submission failed.")
                    }
                }
            }
        }
    }

    fun submitClarify(answer: String) {
        val state = _activeRun.value
        val runId = state.runId ?: return
        val pending = state.pendingClarify ?: return
        val trimmed = answer.trim()
        if (trimmed.isEmpty()) return

        scope.launch {
            runCatching {
                apiClient.submitClarify(
                    runId = runId,
                    requestId = pending.requestId,
                    executionGeneration = pending.executionGeneration,
                    answer = trimmed,
                )
            }.onSuccess {
                _activeRun.update {
                    it.copy(
                        phase = RunPhase.STREAMING,
                        pendingClarify = null,
                    )
                }
            }.onFailure { err ->
                if (err is HermesApiError.Conflict) {
                    _activeRun.update {
                        it.copy(
                            pendingClarify = null,
                            errorMessage = "Clarification already resolved or expired (${err.code}).",
                        )
                    }
                    runCatching { apiClient.getRun(runId) }.onSuccess { polled ->
                        if (reconcileFromPoll(state.sessionId, polled)) {
                            streamJob?.cancel()
                        }
                    }
                } else {
                    _activeRun.update {
                        it.copy(errorMessage = err.message ?: "Clarification submission failed.")
                    }
                }
            }
        }
    }

    companion object {
        private const val MAX_RECONNECT_ATTEMPTS = 3
    }
}
