package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ChatMessageEntity::class,
        MemoryEntity::class,
        DailyTaskEntity::class,
        Task::class,
        HabitEntity::class,
        FocusSessionEntity::class,
        CommandLogEntity::class,
        CustomShortcutEntity::class,
        DailyPlannerItemEntity::class,
        com.example.ingestion.IngestedAssetEntity::class,
        com.example.data.local.entity.ConversationEntity::class,
        com.example.data.local.entity.ShortcutEntity::class,
        com.example.data.local.entity.UrlControlEntity::class,
        com.example.googlehome.data.SmartHomeDeviceEntity::class,
        com.example.data.local.entity.LearnedAliasEntity::class
    ],
    version = 13,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun memoryDao(): MemoryDao
    abstract fun dailyTaskDao(): DailyTaskDao
    abstract fun taskDao(): TaskDao
    abstract fun habitDao(): HabitDao
    abstract fun focusSessionDao(): FocusSessionDao
    abstract fun commandLogDao(): CommandLogDao
    abstract fun customShortcutDao(): CustomShortcutDao
    abstract fun dailyPlannerDao(): DailyPlannerDao
    abstract fun ingestedAssetDao(): com.example.ingestion.IngestedAssetDao

    // Unified KyawGyi DAOs
    abstract fun conversationDao(): com.example.data.local.dao.ConversationDao
    abstract fun shortcutDao(): com.example.data.local.dao.ShortcutDao
    abstract fun urlControlDao(): com.example.data.local.dao.UrlControlDao
    abstract fun smartHomeDeviceDao(): com.example.googlehome.data.SmartHomeDeviceDao
    abstract fun learnedAliasDao(): com.example.data.local.dao.LearnedAliasDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "ai_angel_v25_db"
                )
                .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                .addMigrations(*AppDatabaseMigrations.ALL_MIGRATIONS)
                .addCallback(AppDatabaseMigrations.DatabaseCallback)
                .fallbackToDestructiveMigrationOnDowngrade(false)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
