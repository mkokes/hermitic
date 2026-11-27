package com.martronics.hermitic.core.security

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

data class ConnectionConfig(
    val baseUrl: String = "",
    val apiServerKey: String = "",
    val cfAccessClientId: String = "",
    val cfAccessClientSecret: String = "",
    val mtlsAlias: String = "",
    val biometricLockEnabled: Boolean = false,
    val flagSecureEnabled: Boolean = true,
    val biometricGraceSeconds: Int = DEFAULT_GRACE_SECONDS,
) {
    val isConfigured: Boolean
        get() = baseUrl.isNotBlank() && apiServerKey.isNotBlank()

    override fun toString(): String = buildString {
        append("ConnectionConfig(")
        append("baseUrl='").append(baseUrl).append("', ")
        append("apiServerKey=").append(if (apiServerKey.isBlank()) "<empty>" else "***").append(", ")
        append("cfAccessClientId=").append(if (cfAccessClientId.isBlank()) "<empty>" else "***").append(", ")
        append("cfAccessClientSecret=").append(if (cfAccessClientSecret.isBlank()) "<empty>" else "***").append(", ")
        append("mtlsAlias='").append(mtlsAlias).append("', ")
        append("biometricLockEnabled=").append(biometricLockEnabled).append(", ")
        append("flagSecureEnabled=").append(flagSecureEnabled).append(", ")
        append("biometricGraceSeconds=").append(biometricGraceSeconds)
        append(")")
    }

    companion object {
        const val DEFAULT_GRACE_SECONDS = 30
    }
}

sealed interface UrlValidationResult {
    data class Valid(val normalizedUrl: String) : UrlValidationResult
    data class Invalid(val reason: String) : UrlValidationResult
}

@Singleton
class CredentialVault @Inject constructor(
    private val store: SecretStore,
) {
    private val _config = MutableStateFlow(readConfig())
    val config: StateFlow<ConnectionConfig> = _config.asStateFlow()

    fun currentConfig(): ConnectionConfig = _config.value

    fun saveConnection(
        rawBaseUrl: String,
        apiServerKey: String,
        cfAccessClientId: String = "",
        cfAccessClientSecret: String = "",
        mtlsAlias: String = "",
    ): UrlValidationResult {
        val validation = validateHttpsUrl(rawBaseUrl)
        if (validation is UrlValidationResult.Invalid) {
            return validation
        }
        val normalizedUrl = (validation as UrlValidationResult.Valid).normalizedUrl
        val trimmedKey = apiServerKey.trim()
        require(trimmedKey.isNotEmpty()) { "API Server Key must not be blank" }

        store.putString(KEY_BASE_URL, normalizedUrl)
        store.putString(KEY_API_SERVER_KEY, trimmedKey)
        store.putString(KEY_CF_CLIENT_ID, cfAccessClientId.trim())
        store.putString(KEY_CF_CLIENT_SECRET, cfAccessClientSecret.trim())
        store.putString(KEY_MTLS_ALIAS, mtlsAlias.trim())

        _config.value = readConfig()
        return validation
    }

    fun setBiometricLockEnabled(enabled: Boolean) {
        store.putBoolean(KEY_BIOMETRIC_ENABLED, enabled)
        _config.value = readConfig()
    }

    fun setFlagSecureEnabled(enabled: Boolean) {
        store.putBoolean(KEY_FLAG_SECURE_ENABLED, enabled)
        _config.value = readConfig()
    }

    fun setBiometricGraceSeconds(seconds: Int) {
        store.putInt(KEY_BIOMETRIC_GRACE_SECONDS, seconds.coerceAtLeast(0))
        _config.value = readConfig()
    }

    fun clearAll() {
        store.clear()
        _config.value = readConfig()
    }

    private fun readConfig(): ConnectionConfig = ConnectionConfig(
        baseUrl = store.getString(KEY_BASE_URL).orEmpty(),
        apiServerKey = store.getString(KEY_API_SERVER_KEY).orEmpty(),
        cfAccessClientId = store.getString(KEY_CF_CLIENT_ID).orEmpty(),
        cfAccessClientSecret = store.getString(KEY_CF_CLIENT_SECRET).orEmpty(),
        mtlsAlias = store.getString(KEY_MTLS_ALIAS).orEmpty(),
        biometricLockEnabled = store.getBoolean(KEY_BIOMETRIC_ENABLED, false),
        flagSecureEnabled = store.getBoolean(KEY_FLAG_SECURE_ENABLED, true),
        biometricGraceSeconds = store.getInt(
            KEY_BIOMETRIC_GRACE_SECONDS,
            ConnectionConfig.DEFAULT_GRACE_SECONDS,
        ),
    )

    companion object {
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_API_SERVER_KEY = "api_server_key"
        private const val KEY_CF_CLIENT_ID = "cf_access_client_id"
        private const val KEY_CF_CLIENT_SECRET = "cf_access_client_secret"
        private const val KEY_MTLS_ALIAS = "mtls_alias"
        private const val KEY_BIOMETRIC_ENABLED = "biometric_lock_enabled"
        private const val KEY_FLAG_SECURE_ENABLED = "flag_secure_enabled"
        private const val KEY_BIOMETRIC_GRACE_SECONDS = "biometric_grace_seconds"

        fun validateHttpsUrl(rawUrl: String): UrlValidationResult {
            val trimmed = rawUrl.trim()
            if (trimmed.isEmpty()) {
                return UrlValidationResult.Invalid("Endpoint URL is required.")
            }

            val parsed = runCatching { URI(trimmed) }.getOrNull()
                ?: return UrlValidationResult.Invalid("Endpoint URL is not a valid URI.")

            val scheme = parsed.scheme?.lowercase()
            if (scheme != "https") {
                return UrlValidationResult.Invalid(
                    "Only https:// endpoints are allowed (cleartext HTTP is disabled).",
                )
            }

            val host = parsed.host
            if (host.isNullOrBlank()) {
                return UrlValidationResult.Invalid("Endpoint URL must include a valid hostname.")
            }

            if (!parsed.userInfo.isNullOrBlank()) {
                return UrlValidationResult.Invalid("Embedded credentials in the URL are not allowed.")
            }

            val portPart = if (parsed.port != -1) ":${parsed.port}" else ""
            val cleanPath = (parsed.rawPath ?: "").trimEnd('/')
            val normalized = "https://$host$portPart$cleanPath"
            return UrlValidationResult.Valid(normalized)
        }
    }
}
