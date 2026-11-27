package com.martronics.hermitic.core.di

import com.martronics.hermitic.core.security.EncryptedPrefsSecretStore
import com.martronics.hermitic.core.security.SecretStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SecurityModule {

    @Binds
    @Singleton
    abstract fun bindSecretStore(impl: EncryptedPrefsSecretStore): SecretStore
}
