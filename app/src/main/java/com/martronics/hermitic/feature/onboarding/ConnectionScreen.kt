package com.martronics.hermitic.feature.onboarding

import android.app.Activity
import android.security.KeyChain
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionScreen(
    canNavigateBack: Boolean,
    onBack: () -> Unit,
    onConnected: () -> Unit,
    viewModel: ConnectionViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showApiKey by remember { mutableStateOf(false) }
    var showCfSecret by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Hermes Connection") },
                navigationIcon = {
                    if (canNavigateBack) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "HTTPS-only direct tunnel to your Hermes API Server",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }

            OutlinedTextField(
                value = state.baseUrl,
                onValueChange = viewModel::onBaseUrlChanged,
                label = { Text("Hermes Endpoint URL (https://)") },
                placeholder = { Text("https://hermes.yourdomain.com") },
                singleLine = true,
                isError = state.urlError != null,
                supportingText = state.urlError?.let { msg -> { Text(msg) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = state.apiServerKey,
                onValueChange = viewModel::onApiServerKeyChanged,
                label = { Text("Hermes API_SERVER_KEY") },
                singleLine = true,
                visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showApiKey = !showApiKey }) {
                        Icon(
                            imageVector = if (showApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = "Toggle key visibility",
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Edge Access (Optional — Cloudflare Access)",
                style = MaterialTheme.typography.titleMedium,
            )

            OutlinedTextField(
                value = state.cfAccessClientId,
                onValueChange = viewModel::onCfAccessClientIdChanged,
                label = { Text("CF-Access-Client-Id") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = state.cfAccessClientSecret,
                onValueChange = viewModel::onCfAccessClientSecretChanged,
                label = { Text("CF-Access-Client-Secret") },
                singleLine = true,
                visualTransformation = if (showCfSecret) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showCfSecret = !showCfSecret }) {
                        Icon(
                            imageVector = if (showCfSecret) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = "Toggle secret visibility",
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = {
                        val activity = context as? Activity ?: return@OutlinedButton
                        KeyChain.choosePrivateKeyAlias(
                            activity,
                            { alias ->
                                activity.runOnUiThread {
                                    viewModel.onMtlsAliasSelected(alias.orEmpty())
                                }
                            },
                            null,
                            null,
                            null,
                            -1,
                            state.mtlsAlias.takeIf { it.isNotBlank() },
                        )
                    },
                ) {
                    Text(
                        if (state.mtlsAlias.isBlank()) "Select mTLS Certificate" else "mTLS: ${state.mtlsAlias}",
                    )
                }
                if (state.mtlsAlias.isNotBlank()) {
                    OutlinedButton(onClick = { viewModel.onMtlsAliasSelected("") }) {
                        Text("Clear")
                    }
                }
            }

            state.errorMessage?.let { error ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }

            state.diagnostic?.let { diag ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = "Connected (${diag.healthStatus})",
                                style = MaterialTheme.typography.titleSmall,
                            )
                        }
                        Text(
                            text = "Platform: ${diag.capabilities.platform.ifBlank { "hermes-agent" }} · Model: ${diag.capabilities.model.ifBlank { "default" }}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            text = "Runs API: ${diag.supportsRuns} · Inline Approval: ${diag.supportsRunApproval}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = viewModel::testConnection,
                    enabled = !state.isTesting,
                    modifier = Modifier.weight(1f),
                ) {
                    if (state.isTesting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text("Test Connection")
                    }
                }

                Button(
                    onClick = { viewModel.saveConnection(onConnected) },
                    enabled = !state.isTesting,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Save & Connect")
                }
            }
        }
    }
}
