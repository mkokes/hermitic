package com.martronics.hermitic.core.di

import com.martronics.hermitic.core.network.HermesHeadersInterceptor
import com.martronics.hermitic.core.security.CredentialVault
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = false
    }

    @Provides
    @Singleton
    fun provideHermesHeadersInterceptor(vault: CredentialVault): HermesHeadersInterceptor =
        HermesHeadersInterceptor { vault.currentConfig() }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        headersInterceptor: HermesHeadersInterceptor,
    ): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(headersInterceptor)
        .build()
}
