package com.martronics.hermitic.feature.sessions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.martronics.hermitic.core.data.SessionRepository
import com.martronics.hermitic.core.data.entity.SessionEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SessionListUiState(
    val sessions: List<SessionEntity> = emptyList(),
    val searchQuery: String = "",
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val errorMessage: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SessionListViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
) : ViewModel() {

    private val searchQuery = MutableStateFlow("")
    private val metaState = MutableStateFlow(MetaState())
    private var loadedCount = 0

    private data class MetaState(
        val isRefreshing: Boolean = false,
        val isLoadingMore: Boolean = false,
        val hasMore: Boolean = false,
        val errorMessage: String? = null,
    )

    private val filteredSessions = searchQuery
        .flatMapLatest { query -> sessionRepository.observeSessions(query) }

    val uiState: StateFlow<SessionListUiState> = combine(
        filteredSessions,
        searchQuery,
        metaState,
    ) { sessions, query, meta ->
        SessionListUiState(
            sessions = sessions,
            searchQuery = query,
            isRefreshing = meta.isRefreshing,
            isLoadingMore = meta.isLoadingMore,
            hasMore = meta.hasMore,
            errorMessage = meta.errorMessage,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SessionListUiState(),
    )

    init {
        refresh()
    }

    fun onSearchQueryChanged(query: String) {
        searchQuery.value = query
    }

    fun dismissError() {
        metaState.update { it.copy(errorMessage = null) }
    }

    fun refresh() {
        viewModelScope.launch {
            metaState.update { it.copy(isRefreshing = true, errorMessage = null) }
            runCatching {
                sessionRepository.refreshSessions(limit = PAGE_SIZE, offset = 0)
            }.onSuccess { page ->
                loadedCount = page.fetchedCount
                metaState.update {
                    it.copy(
                        isRefreshing = false,
                        hasMore = page.hasMore,
                    )
                }
            }.onFailure { err ->
                metaState.update {
                    it.copy(
                        isRefreshing = false,
                        errorMessage = err.message ?: "Failed to refresh sessions.",
                    )
                }
            }
        }
    }

    fun loadMore() {
        if (metaState.value.isLoadingMore || !metaState.value.hasMore) return
        viewModelScope.launch {
            metaState.update { it.copy(isLoadingMore = true, errorMessage = null) }
            runCatching {
                sessionRepository.refreshSessions(limit = PAGE_SIZE, offset = loadedCount)
            }.onSuccess { page ->
                loadedCount += page.fetchedCount
                metaState.update {
                    it.copy(
                        isLoadingMore = false,
                        hasMore = page.hasMore,
                    )
                }
            }.onFailure { err ->
                metaState.update {
                    it.copy(
                        isLoadingMore = false,
                        errorMessage = err.message ?: "Failed to load more sessions.",
                    )
                }
            }
        }
    }

    fun createSession(title: String?, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            metaState.update { it.copy(errorMessage = null) }
            runCatching {
                sessionRepository.createSession(title = title?.trim()?.takeIf { it.isNotEmpty() })
            }.onSuccess { created ->
                onCreated(created.id)
            }.onFailure { err ->
                metaState.update {
                    it.copy(errorMessage = err.message ?: "Failed to create session.")
                }
            }
        }
    }

    fun renameSession(sessionId: String, newTitle: String) {
        val trimmed = newTitle.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            metaState.update { it.copy(errorMessage = null) }
            runCatching {
                sessionRepository.renameSession(sessionId = sessionId, title = trimmed)
            }.onFailure { err ->
                metaState.update {
                    it.copy(errorMessage = err.message ?: "Failed to rename session.")
                }
            }
        }
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            metaState.update { it.copy(errorMessage = null) }
            runCatching {
                sessionRepository.deleteSession(sessionId)
            }.onFailure { err ->
                metaState.update {
                    it.copy(errorMessage = err.message ?: "Failed to delete session.")
                }
            }
        }
    }

    companion object {
        const val PAGE_SIZE = 30
    }
}
