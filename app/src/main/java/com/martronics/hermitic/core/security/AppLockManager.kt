package com.martronics.hermitic.core.security

import android.content.Context
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppLockManager @Inject constructor(
    private val vault: CredentialVault,
) {
    private val _isLocked = MutableStateFlow(vault.currentConfig().biometricLockEnabled)
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    private var lastBackgroundedAtMs: Long? = null

    fun onBackground(nowMs: Long = System.currentTimeMillis()) {
        lastBackgroundedAtMs = nowMs
    }

    fun onForeground(nowMs: Long = System.currentTimeMillis()) {
        val config = vault.currentConfig()
        if (!config.biometricLockEnabled) {
            _isLocked.value = false
            return
        }
        val bgAt = lastBackgroundedAtMs ?: return
        val elapsedMs = (nowMs - bgAt).coerceAtLeast(0L)
        if (elapsedMs >= config.biometricGraceSeconds * 1000L) {
            _isLocked.value = true
        }
    }

    fun lockNow() {
        if (vault.currentConfig().biometricLockEnabled) {
            _isLocked.value = true
        }
    }

    fun unlock() {
        _isLocked.value = false
        lastBackgroundedAtMs = null
    }

    fun syncWithPreference() {
        if (!vault.currentConfig().biometricLockEnabled) {
            _isLocked.value = false
            lastBackgroundedAtMs = null
        }
    }

    fun canAuthenticate(context: Context): Boolean {
        val manager = BiometricManager.from(context)
        val authenticators = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        } else {
            BiometricManager.Authenticators.BIOMETRIC_WEAK or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        }
        return manager.canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS
    }

    @Suppress("DEPRECATION")
    fun promptUnlock(
        activity: FragmentActivity,
        onError: (String) -> Unit = {},
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    unlock()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onError(errString.toString())
                }
            },
        )

        val builder = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Hermitic")
            .setSubtitle("Verify your identity to access Hermes sessions and credentials")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL,
            )
        } else {
            builder.setDeviceCredentialAllowed(true)
        }

        prompt.authenticate(builder.build())
    }
}
