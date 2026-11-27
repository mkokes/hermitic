package com.martronics.hermitic.core.di

import android.content.Context
import androidx.room.Room
import com.martronics.hermitic.core.data.HermiticDatabase
import com.martronics.hermitic.core.data.dao.MessageDao
import com.martronics.hermitic.core.data.dao.SessionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideHermiticDatabase(@ApplicationContext context: Context): HermiticDatabase =
        Room.databaseBuilder(
            context,
            HermiticDatabase::class.java,
            "hermitic_cache.db",
        ).build()

    @Provides
    fun provideSessionDao(db: HermiticDatabase): SessionDao = db.sessionDao()

    @Provides
    fun provideMessageDao(db: HermiticDatabase): MessageDao = db.messageDao()
}
