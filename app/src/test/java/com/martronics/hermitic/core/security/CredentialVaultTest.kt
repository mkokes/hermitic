package com.martronics.hermitic.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private class InMemorySecretStore : SecretStore {
    private val strings = mutableMapOf<String, String>()
    private val booleans = mutableMapOf<String, Boolean>()
    private val ints = mutableMapOf<String, Int>()

    override fun getString(key: String): String? = strings[key]

    override fun putString(key: String, value: String?) {
        if (value.isNullOrBlank()) strings.remove(key) else strings[key] = value
    }

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean =
        booleans[key] ?: defaultValue

    override fun putBoolean(key: String, value: Boolean) {
        booleans[key] = value
    }

    override fun getInt(key: String, defaultValue: Int): Int =
        ints[key] ?: defaultValue

    override fun putInt(key: String, value: Int) {
        ints[key] = value
    }

    override fun clear() {
        strings.clear()
        booleans.clear()
        ints.clear()
    }
}

class CredentialVaultTest {

    @Test
    fun rejectsCleartextHttpAndInvalidUrls() {
        val httpResult = CredentialVault.validateHttpsUrl("http://hermes.example.com")
        assertTrue(httpResult is UrlValidationResult.Invalid)

        val emptyResult = CredentialVault.validateHttpsUrl("   ")
        assertTrue(emptyResult is UrlValidationResult.Invalid)

        val userInfoResult = CredentialVault.validateHttpsUrl("https://user:pass@hermes.example.com")
        assertTrue(userInfoResult is UrlValidationResult.Invalid)
    }

    @Test
    fun normalizesValidHttpsUrl() {
        val result = CredentialVault.validateHttpsUrl("  https://hermes.martronics.dev/  ")
        assertTrue(result is UrlValidationResult.Valid)
        assertEquals("https://hermes.martronics.dev", (result as UrlValidationResult.Valid).normalizedUrl)
    }

    @Test
    fun storesSecretsSafelyAndRedactsToString() {
        val vault = CredentialVault(InMemorySecretStore())
        val result = vault.saveConnection(
            rawBaseUrl = "https://hermes.martronics.dev:8443/",
            apiServerKey = "super-secret-bearer-token",
            cfAccessClientId = "cf-id-value",
            cfAccessClientSecret = "cf-secret-value",
            mtlsAlias = "marty-client-cert",
        )
        assertTrue(result is UrlValidationResult.Valid)

        val cfg = vault.currentConfig()
        assertTrue(cfg.isConfigured)
        assertEquals("https://hermes.martronics.dev:8443", cfg.baseUrl)
        assertEquals("super-secret-bearer-token", cfg.apiServerKey)
        assertEquals("cf-id-value", cfg.cfAccessClientId)
        assertEquals("cf-secret-value", cfg.cfAccessClientSecret)
        assertEquals("marty-client-cert", cfg.mtlsAlias)

        val logSafe = cfg.toString()
        assertFalse(logSafe.contains("super-secret-bearer-token"))
        assertFalse(logSafe.contains("cf-secret-value"))
        assertFalse(logSafe.contains("cf-id-value"))
    }

    @Test
    fun clearAllRemovesStoredSecrets() {
        val vault = CredentialVault(InMemorySecretStore())
        vault.saveConnection("https://hermes.martronics.dev", "token-123")
        vault.setBiometricLockEnabled(true)
        assertTrue(vault.currentConfig().isConfigured)

        vault.clearAll()
        val cleared = vault.currentConfig()
        assertFalse(cleared.isConfigured)
        assertEquals("", cleared.baseUrl)
        assertEquals("", cleared.apiServerKey)
        assertFalse(cleared.biometricLockEnabled)
    }

    @Test
    fun appLockManagerRespectsGraceWindow() {
        val vault = CredentialVault(InMemorySecretStore())
        vault.setBiometricLockEnabled(true)
        vault.setBiometricGraceSeconds(30)

        val lockManager = AppLockManager(vault)
        assertTrue(lockManager.isLocked.value)

        lockManager.unlock()
        assertFalse(lockManager.isLocked.value)

        lockManager.onBackground(nowMs = 1_000L)
        lockManager.onForeground(nowMs = 10_000L)
        assertFalse(lockManager.isLocked.value)

        lockManager.onBackground(nowMs = 20_000L)
        lockManager.onForeground(nowMs = 55_000L)
        assertTrue(lockManager.isLocked.value)
    }
}
