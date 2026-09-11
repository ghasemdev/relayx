package com.parsomash.relayx.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [OutboxMessageEntity::class],
    version = 1,
    exportSchema = false
)
abstract class RelayDatabase : RoomDatabase() {

    abstract fun outboxMessageDao(): OutboxMessageDao

    companion object {
        private const val DATABASE_NAME = "relayx_gateway.db"

        @Volatile
        private var INSTANCE: RelayDatabase? = null

        fun getInstance(context: Context): RelayDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    RelayDatabase::class.java,
                    DATABASE_NAME
                )
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
