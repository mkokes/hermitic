package com.martronics.hermitic.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.martronics.hermitic.core.network.HermesApiClient
import com.martronics.hermitic.core.network.dto.CapabilitiesResponseDto
import com.martronics.hermitic.core.security.ConnectionConfig
import com.martronics.hermitic.core.security.CredentialVault
import com.martronics.hermitic.core.security.UrlValidationResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ConnectionDiagnostic(
    val healthStatus: String,
    val capabilities: CapabilitiesResponseDto,
) {
    val supportsRunApproval: Boolean
        get() = capabilities.features["run_approval"] == true
    val supportsRuns: Boolean
        get() = capabilities.features["run_submission"] == true
}

data class ConnectionUiState(
    val baseUrl: String = "",
    val apiServerKey: String = "",
    val cfAccessClientId: String = "",
    val cfAccessClientSecret: String = "",
    val mtlsAlias: String = "",
    val isTesting: Boolean = false,
    val diagnostic: ConnectionDiagnostic? = null,
    val errorMessage: String? = null,
    val urlError: String? = null,
)

@HiltViewModel
class ConnectionViewModel @Inject constructor(
    private val credentialVault: CredentialVault,
    private val apiClient: HermesApiClient,
) : ViewModel() {

    private val _uiState = MutableStateFlow(initialState())
    val uiState: StateFlow<ConnectionUiState> = _uiState.asStateFlow()

    private fun initialState(): ConnectionUiState {
        val current = credentialVault.currentConfig()
        return ConnectionUiState(
            baseUrl = current.baseUrl,
            apiServerKey = current.apiServerKey,
            cfAccessClientId = current.cfAccessClientId,
            cfAccessClientSecret = current.cfAccessClientSecret,
            mtlsAlias = current.mtlsAlias,
        )
    }

    fun onBaseUrlChanged(value: String) {
        _uiState.update { it.copy(baseUrl = value, urlError = null, errorMessage = null, diagnostic = null) }
    }

    fun onApiServerKeyChanged(value: String) {
        _uiState.update { it.copy(apiServerKey = value, errorMessage = null, diagnostic = null) }
    }

    fun onCfAccessClientIdChanged(value: String) {
        _uiState.update { it.copy(cfAccessClientId = value, errorMessage = null, diagnostic = null) }
    }

    fun onCfAccessClientSecretChanged(value: String) {
        _uiState.update { it.copy(cfAccessClientSecret = value, errorMessage = null, diagnostic = null) }
    }

    fun onMtlsAliasSelected(alias: String) {
        _uiState.update { it.copy(mtlsAlias = alias, errorMessage = null, diagnostic = null) }
    }

    fun testConnection() {
        val state = _uiState.value
        val validation = CredentialVault.validateHttpsUrl(state.baseUrl)
        if (validation is UrlValidationResult.Invalid) {
            _uiState.update { it.copy(urlError = validation.reason, diagnostic = null) }
            return
        }
        if (state.apiServerKey.trim().isEmpty()) {
            _uiState.update { it.copy(errorMessage = "API_SERVER_KEY is required.", diagnostic = null) }
            return
        }

        val normalizedUrl = (validation as UrlValidationResult.Valid).normalizedUrl
        val probeConfig = ConnectionConfig(
            baseUrl = normalizedUrl,
            apiServerKey = state.apiServerKey.trim(),
            cfAccessClientId = state.cfAccessClientId.trim(),
            cfAccessClientSecret = state.cfAccessClientSecret.trim(),
            mtlsAlias = state.mtlsAlias.trim(),
        )

        viewModelScope.launch {
            _uiState.update { it.copy(isTesting = true, errorMessage = null, urlError = null, diagnostic = null) }
            runCatching {
                val health = apiClient.checkHealth(overrideConfig = probeConfig)
                val capabilities = apiClient.getCapabilities(overrideConfig = probeConfig)
                ConnectionDiagnostic(healthStatus = health.status, capabilities = capabilities)
            }.onSuccess { diag ->
                _uiState.update { it.copy(isTesting = false, diagnostic = diag) }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isTesting = false,
                        errorMessage = err.message ?: "Connection check failed.",
                    )
                }
            }
        }
    }

    fun saveConnection(onSaved: () -> Unit) {
        val state = _uiState.value
        val validation = CredentialVault.validateHttpsUrl(state.baseUrl)
        if (validation is UrlValidationResult.Invalid) {
            _uiState.update { it.copy(urlError = validation.reason) }
            return
        }
        if (state.apiServerKey.trim().isEmpty()) {
            _uiState.update { it.copy(errorMessage = "API_SERVER_KEY is required.") }
            return
        }

        credentialVault.saveConnection(
            rawBaseUrl = state.baseUrl,
            apiServerKey = state.apiServerKey,
            cfAccessClientId = state.cfAccessClientId,
            cfAccessClientSecret = state.cfAccessClientSecret,
            mtlsAlias = state.mtlsAlias,
        )
        onSaved()
    }
}
