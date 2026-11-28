package com.martronics.hermitic.feature.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.martronics.hermitic.core.data.PendingApproval
import com.martronics.hermitic.core.data.PendingClarify

@Composable
fun ApprovalDecisionCard(
    approval: PendingApproval,
    onApproveOnce: () -> Unit,
    onAllowSession: () -> Unit,
    onDeny: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.primary,
        ),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = "Approval required",
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "Approval Required${approval.tool?.let { ": $it" }.orEmpty()}",
                    style = MaterialTheme.typography.titleSmall,
                )
            }

            approval.summary?.takeIf { it.isNotBlank() }?.let { summary ->
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            approval.command?.takeIf { it.isNotBlank() }?.let { cmd ->
                Text(
                    text = cmd,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onApproveOnce,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Once")
                }
                OutlinedButton(
                    onClick = onAllowSession,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Session")
                }
                OutlinedButton(
                    onClick = onDeny,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Deny")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ClarifyDecisionCard(
    clarify: PendingClarify,
    onSubmitAnswer: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var freeText by remember(clarify.requestId, clarify.executionGeneration) { mutableStateOf("") }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.secondary,
        ),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.HelpOutline,
                    contentDescription = "Clarification requested",
                    tint = MaterialTheme.colorScheme.secondary,
                )
                Text(
                    text = "Agent Clarification",
                    style = MaterialTheme.typography.titleSmall,
                )
            }

            Text(
                text = clarify.question,
                style = MaterialTheme.typography.bodyMedium,
            )

            if (clarify.choices.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    clarify.choices.forEach { choice ->
                        SuggestionChip(
                            onClick = { onSubmitAnswer(choice) },
                            label = { Text(choice) },
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = freeText,
                    onValueChange = { freeText = it },
                    placeholder = { Text("Or type a reply...") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = {
                        val trimmed = freeText.trim()
                        if (trimmed.isNotEmpty()) {
                            onSubmitAnswer(trimmed)
                        }
                    },
                    enabled = freeText.isNotBlank(),
                ) {
                    Text("Reply")
                }
            }
        }
    }
}
