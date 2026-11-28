package com.martronics.hermitic.feature.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.martronics.hermitic.core.data.ActiveRunState
import com.martronics.hermitic.core.data.RunPhase
import com.martronics.hermitic.core.data.entity.MessageEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onBack: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    val showLiveTurn = state.activeRun.isActive ||
        state.activeRun.tools.isNotEmpty() ||
        state.activeRun.pendingApproval != null ||
        state.activeRun.pendingClarify != null ||
        state.activeRun.phase == RunPhase.INCOMPLETE ||
        state.activeRun.phase == RunPhase.CANCELLED

    LaunchedEffect(state.messages.size, state.activeRun.streamedText.length, state.activeRun.tools.size) {
        val totalItems = state.messages.size + if (showLiveTurn) 1 else 0
        if (totalItems > 0) {
            listState.animateScrollToItem(totalItems - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Session ${state.sessionId.take(12)}") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::refreshTranscript) {
                        Icon(Icons.Default.Refresh, contentDescription = "Sync transcript")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding(),
        ) {
            if (state.isRefreshingTranscript || state.activeRun.phase == RunPhase.RECONNECTING) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            val combinedError = state.activeRun.errorMessage ?: state.transcriptError
            combinedError?.let { error ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = viewModel::dismissErrors) {
                            Text("Dismiss")
                        }
                    }
                }
            }

            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                items(
                    items = state.messages,
                    key = { it.id },
                ) { msg ->
                    MessageBubble(message = msg)
                }

                if (showLiveTurn) {
                    item(key = "live-active-run") {
                        LiveRunCard(
                            run = state.activeRun,
                            onApproveOnce = viewModel::approveOnce,
                            onAllowSession = viewModel::allowSession,
                            onDeny = viewModel::denyApproval,
                            onClarify = viewModel::submitClarify,
                        )
                    }
                }
            }

            Surface(
                tonalElevation = 3.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    OutlinedTextField(
                        value = state.draftText,
                        onValueChange = viewModel::onDraftChanged,
                        placeholder = { Text("Message Hermes...") },
                        maxLines = 5,
                        enabled = !state.activeRun.isActive,
                        modifier = Modifier.weight(1f),
                    )

                    if (state.activeRun.isActive) {
                        FilledIconButton(
                            onClick = viewModel::stopRun,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError,
                            ),
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = "Stop run")
                        }
                    } else {
                        FilledIconButton(
                            onClick = viewModel::sendMessage,
                            enabled = state.draftText.isNotBlank(),
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: MessageEntity) {
    val isUser = message.role == "user"
    val isTool = message.role == "tool"

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = when {
                isUser -> MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                isTool -> MaterialTheme.colorScheme.surfaceVariant
                else -> MaterialTheme.colorScheme.surface
            },
            tonalElevation = if (isUser) 0.dp else 1.dp,
            modifier = Modifier.widthIn(max = 340.dp),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (isTool && !message.toolName.isNullOrBlank()) {
                    Text(
                        text = "tool: ${message.toolName}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    text = message.content.ifBlank { "[empty]" },
                    style = if (isTool) {
                        MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                    } else {
                        MaterialTheme.typography.bodyLarge
                    },
                )
            }
        }
    }
}

@Composable
private fun LiveRunCard(
    run: ActiveRunState,
    onApproveOnce: () -> Unit,
    onAllowSession: () -> Unit,
    onDeny: () -> Unit,
    onClarify: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        run.tools.forEach { tool ->
            ToolTimelineCard(item = tool)
        }

        run.pendingApproval?.let { approval ->
            ApprovalDecisionCard(
                approval = approval,
                onApproveOnce = onApproveOnce,
                onAllowSession = onAllowSession,
                onDeny = onDeny,
            )
        }

        run.pendingClarify?.let { clarify ->
            ClarifyDecisionCard(
                clarify = clarify,
                onSubmitAnswer = onClarify,
            )
        }

        if (run.interimText.isNotBlank() && run.streamedText.isBlank()) {
            Text(
                text = run.interimText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (run.streamedText.isNotBlank() || run.phase == RunPhase.SUBMITTING || run.phase == RunPhase.STOPPING) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    val statusTag = when {
                        run.phase == RunPhase.SUBMITTING -> "Starting run..."
                        run.phase == RunPhase.STOPPING -> "Stopping..."
                        run.phase == RunPhase.RECONNECTING -> "Reconnecting stream..."
                        run.phase == RunPhase.CANCELLED -> "Cancelled"
                        run.isIncompleteBudgetExhausted -> "Partial answer (iteration budget exhausted)"
                        else -> null
                    }
                    statusTag?.let { tag ->
                        Text(
                            text = tag,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    if (run.streamedText.isNotBlank()) {
                        Text(
                            text = run.streamedText,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        }
    }
}
