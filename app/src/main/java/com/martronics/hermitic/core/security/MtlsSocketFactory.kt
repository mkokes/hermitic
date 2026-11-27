package com.martronics.hermitic.core.security

import android.content.Context
import android.security.KeyChain
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.Socket
import java.security.KeyStore
import java.security.Principal
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.KeyManager
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509ExtendedKeyManager
import javax.net.ssl.X509TrustManager

data class TlsSocketConfig(
    val sslSocketFactory: SSLSocketFactory,
    val trustManager: X509TrustManager,
)

@Singleton
class MtlsSocketFactory @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun build(mtlsAlias: String): TlsSocketConfig {
        val trustManager = systemTrustManager()
        val keyManagers: Array<KeyManager>? = mtlsAlias
            .trim()
            .takeIf { it.isNotEmpty() }
            ?.let { alias ->
                val chain = KeyChain.getCertificateChain(context, alias)
                val privateKey = KeyChain.getPrivateKey(context, alias)
                if (chain != null && chain.isNotEmpty() && privateKey != null) {
                    arrayOf(KeyChainAliasKeyManager(alias, chain, privateKey))
                } else {
                    null
                }
            }

        val sslContext = SSLContext.getInstance("TLS").apply {
            init(keyManagers, arrayOf(trustManager), SecureRandom())
        }

        return TlsSocketConfig(
            sslSocketFactory = sslContext.socketFactory,
            trustManager = trustManager,
        )
    }

    private fun systemTrustManager(): X509TrustManager {
        val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply {
            init(null as KeyStore?)
        }
        return tmf.trustManagers
            .filterIsInstance<X509TrustManager>()
            .firstOrNull()
            ?: error("No system X509TrustManager found")
    }

    private class KeyChainAliasKeyManager(
        private val alias: String,
        private val chain: Array<X509Certificate>,
        private val privateKey: PrivateKey,
    ) : X509ExtendedKeyManager() {
        override fun getClientAliases(keyType: String?, issuers: Array<out Principal>?): Array<String> =
            arrayOf(alias)

        override fun chooseClientAlias(
            keyTypes: Array<out String>?,
            issuers: Array<out Principal>?,
            socket: Socket?,
        ): String = alias

        override fun getServerAliases(keyType: String?, issuers: Array<out Principal>?): Array<String>? =
            null

        override fun chooseServerAlias(
            keyType: String?,
            issuers: Array<out Principal>?,
            socket: Socket?,
        ): String? = null

        override fun getCertificateChain(alias: String?): Array<X509Certificate>? =
            if (alias == this.alias) chain else null

        override fun getPrivateKey(alias: String?): PrivateKey? =
            if (alias == this.alias) privateKey else null
    }
}
