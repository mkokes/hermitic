package com.martronics.hermitic.feature.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.martronics.hermitic.core.data.ActiveRunState
import com.martronics.hermitic.core.data.ChatRunRepository
import com.martronics.hermitic.core.data.SessionRepository
import com.martronics.hermitic.core.data.entity.MessageEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import javax.inject.Inject

data class ChatUiState(
    val sessionId: String = "",
    val messages: List<MessageEntity> = emptyList(),
    val activeRun: ActiveRunState = ActiveRunState(),
    val draftText: String = "",
    val isRefreshingTranscript: Boolean = false,
    val transcriptError: String? = null,
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sessionRepository: SessionRepository,
    private val chatRunRepository: ChatRunRepository,
) : ViewModel() {

    private val sessionId: String = savedStateHandle.get<String>("sessionId").orEmpty().let { raw ->
        runCatching { URLDecoder.decode(raw, StandardCharsets.UTF_8.name()) }.getOrDefault(raw)
    }
    private val draftText = MutableStateFlow("")
    private val transcriptMeta = MutableStateFlow(TranscriptMeta())

    private data class TranscriptMeta(
        val isRefreshing: Boolean = false,
        val error: String? = null,
    )

    val uiState: StateFlow<ChatUiState> = combine(
        sessionRepository.observeMessages(sessionId),
        chatRunRepository.activeRun,
        draftText,
        transcriptMeta,
    ) { messages, activeRun, draft, meta ->
        val scopedRun = if (activeRun.sessionId == sessionId) {
            activeRun
        } else {
            ActiveRunState(sessionId = sessionId)
        }
        ChatUiState(
            sessionId = sessionId,
            messages = messages,
            activeRun = scopedRun,
            draftText = draft,
            isRefreshingTranscript = meta.isRefreshing,
            transcriptError = meta.error,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ChatUiState(sessionId = sessionId),
    )

    init {
        chatRunRepository.resetForSession(sessionId)
        refreshTranscript()
    }

    fun onDraftChanged(text: String) {
        draftText.value = text
    }

    fun refreshTranscript() {
        if (sessionId.isBlank()) return
        viewModelScope.launch {
            transcriptMeta.update { it.copy(isRefreshing = true, error = null) }
            runCatching {
                sessionRepository.refreshMessages(sessionId)
            }.onSuccess {
                transcriptMeta.update { it.copy(isRefreshing = false) }
            }.onFailure { err ->
                transcriptMeta.update {
                    it.copy(
                        isRefreshing = false,
                        error = err.message ?: "Failed to sync transcript.",
                    )
                }
            }
        }
    }

    fun sendMessage() {
        val text = draftText.value.trim()
        if (text.isEmpty() || uiState.value.activeRun.isActive) return
        draftText.value = ""
        chatRunRepository.sendPrompt(sessionId = sessionId, prompt = text)
    }

    fun stopRun() {
        chatRunRepository.stopActiveRun()
    }

    fun approveOnce() {
        chatRunRepository.submitApproval("once")
    }

    fun allowSession() {
        chatRunRepository.submitApproval("session")
    }

    fun denyApproval() {
        chatRunRepository.submitApproval("deny")
    }

    fun submitClarify(answer: String) {
        chatRunRepository.submitClarify(answer)
    }

    fun dismissErrors() {
        transcriptMeta.update { it.copy(error = null) }
        chatRunRepository.dismissError()
    }
}
