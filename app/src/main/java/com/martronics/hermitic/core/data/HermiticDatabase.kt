package com.martronics.hermitic.core.data

import androidx.room.Database
import androidx.room.RoomDatabase
import com.martronics.hermitic.core.data.dao.MessageDao
import com.martronics.hermitic.core.data.dao.SessionDao
import com.martronics.hermitic.core.data.entity.MessageEntity
import com.martronics.hermitic.core.data.entity.SessionEntity

@Database(
    entities = [
        SessionEntity::class,
        MessageEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class HermiticDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun messageDao(): MessageDao
}
