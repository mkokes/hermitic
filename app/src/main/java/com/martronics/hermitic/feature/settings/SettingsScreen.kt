package com.martronics.hermitic.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onEditConnection: () -> Unit,
    onWiped: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val config by viewModel.config.collectAsStateWithLifecycle()
    var showWipeConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Security & Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "Configured Endpoint",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = config.baseUrl.ifBlank { "Not configured" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = buildString {
                            append("CF Access: ")
                            append(if (config.cfAccessClientId.isNotBlank()) "Service Token" else "Off")
                            append(" · mTLS: ")
                            append(config.mtlsAlias.ifBlank { "Off" })
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(
                        onClick = onEditConnection,
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        Text("Edit Connection")
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text("Biometric App Lock", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Require fingerprint/device unlock on launch and after ${config.biometricGraceSeconds}s in background",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = config.biometricLockEnabled,
                    onCheckedChange = viewModel::setBiometricLockEnabled,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text("Mask Screen in Recents (FLAG_SECURE)", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Block screenshots and hide chat previews in the app switcher",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = config.flagSecureEnabled,
                    onCheckedChange = viewModel::setFlagSecureEnabled,
                )
            }

            Button(
                onClick = { showWipeConfirm = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Wipe Credentials & Cache")
            }
        }
    }

    if (showWipeConfirm) {
        AlertDialog(
            onDismissRequest = { showWipeConfirm = false },
            title = { Text("Wipe Credentials & Cache?") },
            text = {
                Text("This permanently deletes stored API keys, Cloudflare tokens, and cached session transcripts from this device.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showWipeConfirm = false
                        viewModel.wipeCredentialsAndCache(onWiped)
                    },
                ) {
                    Text("Wipe", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showWipeConfirm = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}
