package com.martronics.hermitic.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.martronics.hermitic.core.data.SessionRepository
import com.martronics.hermitic.core.security.AppLockManager
import com.martronics.hermitic.core.security.ConnectionConfig
import com.martronics.hermitic.core.security.CredentialVault
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val credentialVault: CredentialVault,
    private val appLockManager: AppLockManager,
    private val sessionRepository: SessionRepository,
) : ViewModel() {

    val config: StateFlow<ConnectionConfig> = credentialVault.config

    fun setBiometricLockEnabled(enabled: Boolean) {
        credentialVault.setBiometricLockEnabled(enabled)
        appLockManager.syncWithPreference()
    }

    fun setFlagSecureEnabled(enabled: Boolean) {
        credentialVault.setFlagSecureEnabled(enabled)
    }

    fun wipeCredentialsAndCache(onWiped: () -> Unit) {
        viewModelScope.launch {
            credentialVault.clearAll()
            appLockManager.syncWithPreference()
            sessionRepository.wipeCache()
            onWiped()
        }
    }
}
