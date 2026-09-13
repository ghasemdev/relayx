package com.parsomash.relayx.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [OutboxMessageEntity::class, RuleEntity::class],
    version = 2,
    exportSchema = false
)
abstract class RelayDatabase : RoomDatabase() {

    abstract fun outboxMessageDao(): OutboxMessageDao
    abstract fun ruleDao(): RuleDao

    companion object {
        private const val DATABASE_NAME = "relayx_gateway.db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `filter_rules` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `sender_pattern` TEXT NOT NULL,
                        `sender_match_type` TEXT NOT NULL,
                        `content_pattern` TEXT,
                        `action` TEXT NOT NULL,
                        `transform_pattern` TEXT,
                        `priority` INTEGER NOT NULL,
                        `enabled` INTEGER NOT NULL,
                        `created_at` INTEGER NOT NULL,
                        `updated_at` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `idx_rules_priority_enabled` ON `filter_rules` (`priority`, `enabled`)"
                )
            }
        }

        @Volatile
        private var INSTANCE: RelayDatabase? = null

        fun getInstance(context: Context): RelayDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    RelayDatabase::class.java,
                    DATABASE_NAME
                )
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
