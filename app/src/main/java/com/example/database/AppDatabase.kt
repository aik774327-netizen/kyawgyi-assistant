package com.example.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.aiangel.assistant.data.dao.IrCommandDao
import com.aiangel.assistant.data.entity.IrCommandEntity

@Database(
  entities = [
    ErrorLogEntity::class,
    MemoryEntity::class,
    ShortcutEntity::class,
    EcoFlowConfigEntity::class,
    FileAssetEntity::class,
    MusicAssetEntity::class,
    AssetAliasEntity::class,
    IngestionEventEntity::class,
    ChatMessageEntity::class,
    IrCommandEntity::class,
    FanAutomationConfigEntity::class
  ],
  version = 6,
  exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
  abstract fun errorLogDao(): ErrorLogDao
  abstract fun memoryDao(): MemoryDao
  abstract fun shortcutDao(): ShortcutDao
  abstract fun ecoFlowConfigDao(): EcoFlowConfigDao
  abstract fun fileAssetDao(): FileAssetDao
  abstract fun musicAssetDao(): MusicAssetDao
  abstract fun assetAliasDao(): AssetAliasDao
  abstract fun ingestionEventDao(): IngestionEventDao
  abstract fun chatMessageDao(): ChatMessageDao
  abstract fun irCommandDao(): IrCommandDao
  abstract fun fanAutomationConfigDao(): FanAutomationConfigDao

  companion object {
    @Volatile
    private var INSTANCE: AppDatabase? = null

    private val MIGRATION_3_4 = object : Migration(3, 4) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
          "CREATE TABLE IF NOT EXISTS `ir_commands` (" +
            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`deviceName` TEXT NOT NULL, " +
            "`commandLabel` TEXT NOT NULL, " +
            "`frequencyHz` INTEGER NOT NULL, " +
            "`pattern` TEXT NOT NULL, " +
            "`learnedAt` INTEGER NOT NULL, " +
            "`brand` TEXT, " +
            "`isVerified` INTEGER NOT NULL)"
        )
      }
    }

    private val MIGRATION_2_3 = object : Migration(2, 3) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
          "CREATE TABLE IF NOT EXISTS `chat_messages` (" +
            "`id` TEXT NOT NULL, " +
            "`sender` TEXT NOT NULL, " +
            "`messageText` TEXT NOT NULL, " +
            "`attachedFileAssetId` TEXT, " +
            "`attachmentName` TEXT, " +
            "`attachmentMimeType` TEXT, " +
            "`attachmentCategory` TEXT, " +
            "`attachmentUri` TEXT, " +
            "`attachmentSize` INTEGER NOT NULL, " +
            "`ingestionStatus` TEXT, " +
            "`destinationPath` TEXT, " +
            "`metadataJson` TEXT, " +
            "`timestamp` INTEGER NOT NULL, " +
            "PRIMARY KEY(`id`))"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_chat_messages_timestamp` ON `chat_messages` (`timestamp`)")
      }
    }

    private val MIGRATION_1_2 = object : Migration(1, 2) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `ecoflow_config` (`id` INTEGER NOT NULL, `accessKey` TEXT NOT NULL, `secretKey` TEXT NOT NULL, `deviceSn` TEXT NOT NULL, `hostUrl` TEXT NOT NULL, `useHardwareBle` INTEGER NOT NULL, PRIMARY KEY(`id`))")
      }
    }

    private val MIGRATION_1_4 = object : Migration(1, 4) {
      override fun migrate(db: SupportSQLiteDatabase) {
        MIGRATION_1_2.migrate(db)
        MIGRATION_2_3.migrate(db)
        MIGRATION_3_4.migrate(db)
      }
    }

    private val MIGRATION_2_4 = object : Migration(2, 4) {
      override fun migrate(db: SupportSQLiteDatabase) {
        MIGRATION_2_3.migrate(db)
        MIGRATION_3_4.migrate(db)
      }
    }

    private val MIGRATION_4_5 = object : Migration(4, 5) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
          "CREATE TABLE IF NOT EXISTS `fan_automation_config` (" +
            "`id` INTEGER PRIMARY KEY NOT NULL, " +
            "`ecoFlowPowerLinkEnabled` INTEGER NOT NULL, " +
            "`lowBatteryThreshold` INTEGER NOT NULL, " +
            "`autoTurnOffLightOnLowBattery` INTEGER NOT NULL, " +
            "`autoReduceSpeedOnLowBattery` INTEGER NOT NULL, " +
            "`quietNightModeActive` INTEGER NOT NULL, " +
            "`defaultSleepTimerMinutes` INTEGER NOT NULL, " +
            "`updatedAt` INTEGER NOT NULL)"
        )
      }
    }

    private val MIGRATION_5_6 = object : Migration(5, 6) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `ir_commands` ADD COLUMN `evidenceState` TEXT NOT NULL DEFAULT 'NOT_TESTED'")
        db.execSQL("ALTER TABLE `ir_commands` ADD COLUMN `hexCode` TEXT")
        db.execSQL("ALTER TABLE `ir_commands` ADD COLUMN `protocol` TEXT DEFAULT 'NEC'")
        db.execSQL("ALTER TABLE `ir_commands` ADD COLUMN `lastTransmittedAt` INTEGER")
        db.execSQL("ALTER TABLE `ir_commands` ADD COLUMN `acknowledgedAt` INTEGER")
        db.execSQL("ALTER TABLE `ir_commands` ADD COLUMN `opticalVerifiedAt` INTEGER")
        db.execSQL("ALTER TABLE `ir_commands` ADD COLUMN `applianceVerifiedAt` INTEGER")
        db.execSQL("ALTER TABLE `ir_commands` ADD COLUMN `evidenceDetails` TEXT")
        db.execSQL("ALTER TABLE `ir_commands` ADD COLUMN `lastError` TEXT")
        db.execSQL("ALTER TABLE `ir_commands` ADD COLUMN `updatedAt` INTEGER NOT NULL DEFAULT 0")
      }
    }

    fun getDatabase(context: Context): AppDatabase {
      return INSTANCE ?: synchronized(this) {
        val instance = Room.databaseBuilder(
          context.applicationContext,
          AppDatabase::class.java,
          "kyaw_gyi_assistant.db"
        )
          .addMigrations(
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
            MIGRATION_1_4,
            MIGRATION_2_4,
            MIGRATION_4_5,
            MIGRATION_5_6
          )
          .fallbackToDestructiveMigration(true)
          .fallbackToDestructiveMigrationOnDowngrade(true)
          .build()
        INSTANCE = instance
        instance
      }
    }
  }
}
