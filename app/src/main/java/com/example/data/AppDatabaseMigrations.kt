package com.example.data

import android.util.Log
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * KYAW GYI AI — Robust Database Schema Migrations & Data Preservation System (အချက် ၁)
 *
 * Ensures that user tasks, conversations, memory store, custom shortcuts, focus logs,
 * and learned aliases are NEVER lost during app upgrades.
 *
 * Replaces destructive wipe (fallbackToDestructiveMigration) with explicit, non-destructive
 * SQLite table creation, column expansion, and performance indexing.
 */
object AppDatabaseMigrations {

    private const val TAG = "AppDatabaseMigrations"

    /**
     * Migration 1 -> 2: Add daily_tasks and habits tables
     */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            Log.i(TAG, "Migrating database from version 1 to 2...")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS daily_tasks (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    title TEXT NOT NULL,
                    category TEXT NOT NULL DEFAULT 'General',
                    isCompleted INTEGER NOT NULL DEFAULT 0,
                    dueDate TEXT NOT NULL DEFAULT 'Today',
                    timestamp INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS habits (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    title TEXT NOT NULL,
                    streakCount INTEGER NOT NULL DEFAULT 0,
                    frequency TEXT NOT NULL DEFAULT 'Daily',
                    isCompletedToday INTEGER NOT NULL DEFAULT 0,
                    lastCompletedTimestamp INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent()
            )
        }
    }

    /**
     * Migration 2 -> 3: Add focus_sessions table
     */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            Log.i(TAG, "Migrating database from version 2 to 3...")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS focus_sessions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    sessionName TEXT NOT NULL,
                    durationMinutes INTEGER NOT NULL,
                    category TEXT NOT NULL DEFAULT 'Work',
                    colorHex TEXT NOT NULL DEFAULT '#3B82F6',
                    timestamp INTEGER NOT NULL,
                    notes TEXT NOT NULL DEFAULT ''
                )
                """.trimIndent()
            )
        }
    }

    /**
     * Migration 3 -> 4: Add command_logs and custom_shortcuts tables
     */
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            Log.i(TAG, "Migrating database from version 3 to 4...")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS command_logs (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    commandText TEXT NOT NULL,
                    commandType TEXT NOT NULL,
                    status TEXT NOT NULL,
                    actionDetail TEXT NOT NULL,
                    timestamp INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS custom_shortcuts (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    phrase TEXT NOT NULL,
                    targetPackageOrAction TEXT NOT NULL,
                    description TEXT NOT NULL,
                    isEnabled INTEGER NOT NULL DEFAULT 1
                )
                """.trimIndent()
            )
        }
    }

    /**
     * Migration 4 -> 5: Add daily_planner_items table
     */
    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            Log.i(TAG, "Migrating database from version 4 to 5...")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS daily_planner_items (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    title TEXT NOT NULL,
                    description TEXT NOT NULL DEFAULT '',
                    category TEXT NOT NULL DEFAULT 'General',
                    isHabit INTEGER NOT NULL DEFAULT 0,
                    isCompleted INTEGER NOT NULL DEFAULT 0,
                    streakCount INTEGER NOT NULL DEFAULT 0,
                    targetDaysPerWeek INTEGER NOT NULL DEFAULT 7,
                    priority TEXT NOT NULL DEFAULT 'Medium',
                    scheduledDate TEXT NOT NULL DEFAULT 'Today',
                    dayOfWeek INTEGER NOT NULL DEFAULT 1,
                    weeklyCompletionHistory TEXT NOT NULL DEFAULT '0,0,0,0,0,0,0',
                    lastCompletedTimestamp INTEGER NOT NULL DEFAULT 0,
                    reminderTime TEXT NOT NULL DEFAULT '',
                    createdAt INTEGER NOT NULL
                )
                """.trimIndent()
            )
        }
    }

    /**
     * Migration 5 -> 6: Add ingested_assets table
     */
    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            Log.i(TAG, "Migrating database from version 5 to 6...")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS ingested_assets (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    fileUri TEXT NOT NULL,
                    fileName TEXT NOT NULL,
                    mimeType TEXT NOT NULL,
                    category TEXT NOT NULL,
                    sizeBytes INTEGER NOT NULL,
                    sha256Hash TEXT NOT NULL,
                    storedRelativePath TEXT NOT NULL,
                    originalSource TEXT NOT NULL DEFAULT 'USER_UPLOAD',
                    aliasName TEXT,
                    metadataJson TEXT NOT NULL DEFAULT '{}',
                    extractedSummary TEXT,
                    indexedKeywords TEXT NOT NULL DEFAULT '',
                    timestamp INTEGER NOT NULL
                )
                """.trimIndent()
            )
        }
    }

    /**
     * Migration 6 -> 7: Add conversation_history and kyawgyi_shortcuts tables
     */
    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            Log.i(TAG, "Migrating database from version 6 to 7...")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS conversation_history (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    userQuery TEXT NOT NULL,
                    assistantResponse TEXT NOT NULL,
                    executionMode TEXT NOT NULL,
                    timestamp INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS kyawgyi_shortcuts (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    triggerPhrase TEXT NOT NULL,
                    actionType TEXT NOT NULL,
                    payload TEXT NOT NULL,
                    isEnabled INTEGER NOT NULL DEFAULT 1
                )
                """.trimIndent()
            )
        }
    }

    /**
     * Migration 7 -> 8: Add url_controls table
     */
    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            Log.i(TAG, "Migrating database from version 7 to 8...")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS url_controls (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    name TEXT NOT NULL,
                    triggerPhrase TEXT NOT NULL,
                    url TEXT NOT NULL,
                    actionType TEXT NOT NULL DEFAULT 'OPEN_URL',
                    requestBody TEXT NOT NULL DEFAULT '',
                    headers TEXT NOT NULL DEFAULT '',
                    successSpeech TEXT NOT NULL DEFAULT 'လင့်ခ်သို့ ချိတ်ဆက်ဆောင်ရွက်ပြီးပါပြီ',
                    failureSpeech TEXT NOT NULL DEFAULT 'လင့်ခ်သို့ ချိတ်ဆက်၍ မရပါခင်ဗျာ',
                    isEnabled INTEGER NOT NULL DEFAULT 1,
                    lastExecutedAt INTEGER NOT NULL DEFAULT 0,
                    lastStatus TEXT NOT NULL DEFAULT 'IDLE'
                )
                """.trimIndent()
            )
        }
    }

    /**
     * Migration 8 -> 9: Add smart_home_devices table
     */
    val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            Log.i(TAG, "Migrating database from version 8 to 9...")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS smart_home_devices (
                    deviceId TEXT PRIMARY KEY NOT NULL,
                    name TEXT NOT NULL,
                    room TEXT NOT NULL DEFAULT 'ဧည့်ခန်း (Living Room)',
                    deviceType TEXT NOT NULL DEFAULT 'LIGHT',
                    isOn INTEGER NOT NULL DEFAULT 0,
                    brightness INTEGER NOT NULL DEFAULT 100,
                    temperature INTEGER NOT NULL DEFAULT 24,
                    burmeseTriggerPhrases TEXT NOT NULL DEFAULT '',
                    traitType TEXT NOT NULL DEFAULT 'action.devices.traits.OnOff',
                    apiEndpoint TEXT NOT NULL DEFAULT '',
                    isOnline INTEGER NOT NULL DEFAULT 1,
                    lastUpdated INTEGER NOT NULL
                )
                """.trimIndent()
            )
        }
    }

    /**
     * Migration 9 -> 10: Add learned_aliases table
     */
    val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            Log.i(TAG, "Migrating database from version 9 to 10...")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS learned_aliases (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    normalizedPhrase TEXT NOT NULL,
                    rawPhrase TEXT NOT NULL,
                    actionType TEXT NOT NULL,
                    targetPayload TEXT NOT NULL,
                    displayLabel TEXT NOT NULL,
                    confidence REAL NOT NULL DEFAULT 1.0,
                    learnedAt INTEGER NOT NULL,
                    usageCount INTEGER NOT NULL DEFAULT 1
                )
                """.trimIndent()
            )
        }
    }

    /**
     * Universal Resilient Migration from any Legacy Version (1..9) directly to Version 10
     * Ensures all 15 tables and their columns exist safely without any data destruction.
     */
    val MIGRATION_1_10 = object : Migration(1, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            Log.i(TAG, "Executing Universal Resilient Migration 1 -> 10...")
            createAllTablesIfNotExist(db)
        }
    }

    /**
     * Migration 10 -> 11: Add failure analysis and learning fields to command_logs (အချက် ၂)
     */
    val MIGRATION_10_11 = object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            Log.i(TAG, "Migrating database from version 10 to 11 (Adding Root-Cause Classification columns)...")
            try {
                db.execSQL("ALTER TABLE command_logs ADD COLUMN failureCategory TEXT NOT NULL DEFAULT 'NONE';")
            } catch (e: Exception) { Log.w(TAG, "failureCategory column may already exist", e) }
            try {
                db.execSQL("ALTER TABLE command_logs ADD COLUMN failureReason TEXT NOT NULL DEFAULT '';")
            } catch (e: Exception) { Log.w(TAG, "failureReason column may already exist", e) }
            try {
                db.execSQL("ALTER TABLE command_logs ADD COLUMN suggestedAlternative TEXT NOT NULL DEFAULT '';")
            } catch (e: Exception) { Log.w(TAG, "suggestedAlternative column may already exist", e) }
            try {
                db.execSQL("ALTER TABLE command_logs ADD COLUMN isRepeatedFailure INTEGER NOT NULL DEFAULT 0;")
            } catch (e: Exception) { Log.w(TAG, "isRepeatedFailure column may already exist", e) }
        }
    }

    val MIGRATION_1_11 = object : Migration(1, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            Log.i(TAG, "Executing Universal Resilient Migration 1 -> 11...")
            createAllTablesIfNotExist(db)
        }
    }

    fun dropAllUnmanagedIndices(db: SupportSQLiteDatabase) {
        val unmanagedIndices = listOf(
            "index_chat_messages_timestamp",
            "index_tasks_isCompleted",
            "index_habits_isCompletedToday",
            "index_daily_planner_scheduledDate",
            "index_conversation_history_timestamp",
            "index_learned_aliases_phrase",
            "index_url_controls_triggerPhrase",
            "index_kyawgyi_shortcuts_trigger",
            "index_command_logs_commandText",
            "index_command_logs_status"
        )
        for (indexName in unmanagedIndices) {
            try {
                db.execSQL("DROP INDEX IF EXISTS $indexName;")
            } catch (e: Exception) {
                Log.w(TAG, "Could not drop index $indexName: ${e.message}")
            }
        }
    }

    /**
     * Migration 11 -> 12: Add remediationAction and remediationUri columns to command_logs (အချက် ၃ Fallback & Alternative Flow)
     */
    val MIGRATION_11_12 = object : Migration(11, 12) {
        override fun migrate(db: SupportSQLiteDatabase) {
            Log.i(TAG, "Migrating database from version 11 to 12 (Adding Remediation Action & URI columns)...")
            try {
                db.execSQL("ALTER TABLE command_logs ADD COLUMN remediationAction TEXT NOT NULL DEFAULT '';")
            } catch (e: Exception) { Log.w(TAG, "remediationAction column may already exist", e) }
            try {
                db.execSQL("ALTER TABLE command_logs ADD COLUMN remediationUri TEXT NOT NULL DEFAULT '';")
            } catch (e: Exception) { Log.w(TAG, "remediationUri column may already exist", e) }
            dropAllUnmanagedIndices(db)
        }
    }

    val MIGRATION_1_12 = object : Migration(1, 12) {
        override fun migrate(db: SupportSQLiteDatabase) {
            Log.i(TAG, "Executing Universal Resilient Migration 1 -> 12...")
            createAllTablesIfNotExist(db)
            dropAllUnmanagedIndices(db)
        }
    }

    /**
     * Migration 12 -> 13: Schema cleanup and dropping conflicting manual indexes
     */
    val MIGRATION_12_13 = object : Migration(12, 13) {
        override fun migrate(db: SupportSQLiteDatabase) {
            Log.i(TAG, "Migrating database from version 12 to 13 (Dropping conflicting unmanaged indexes)...")
            try {
                db.execSQL("ALTER TABLE command_logs ADD COLUMN remediationAction TEXT NOT NULL DEFAULT '';")
            } catch (e: Exception) { Log.w(TAG, "remediationAction column may already exist", e) }
            try {
                db.execSQL("ALTER TABLE command_logs ADD COLUMN remediationUri TEXT NOT NULL DEFAULT '';")
            } catch (e: Exception) { Log.w(TAG, "remediationUri column may already exist", e) }
            dropAllUnmanagedIndices(db)
        }
    }

    val MIGRATION_11_13 = object : Migration(11, 13) {
        override fun migrate(db: SupportSQLiteDatabase) {
            Log.i(TAG, "Migrating database from version 11 to 13 (Remediation columns + drop conflicting indexes)...")
            try {
                db.execSQL("ALTER TABLE command_logs ADD COLUMN remediationAction TEXT NOT NULL DEFAULT '';")
            } catch (e: Exception) { Log.w(TAG, "remediationAction column may already exist", e) }
            try {
                db.execSQL("ALTER TABLE command_logs ADD COLUMN remediationUri TEXT NOT NULL DEFAULT '';")
            } catch (e: Exception) { Log.w(TAG, "remediationUri column may already exist", e) }
            dropAllUnmanagedIndices(db)
        }
    }

    val MIGRATION_1_13 = object : Migration(1, 13) {
        override fun migrate(db: SupportSQLiteDatabase) {
            Log.i(TAG, "Executing Universal Resilient Migration 1 -> 13...")
            createAllTablesIfNotExist(db)
            dropAllUnmanagedIndices(db)
        }
    }

    /**
     * Safe creation of all 15 Room tables with exact matching schemas
     */
    fun createAllTablesIfNotExist(db: SupportSQLiteDatabase) {
        // 1. chat_messages
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS chat_messages (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                role TEXT NOT NULL,
                content TEXT NOT NULL,
                responseMode TEXT NOT NULL DEFAULT 'QUICK',
                engineType TEXT NOT NULL DEFAULT 'Response Engine',
                thoughtChain TEXT,
                imageUri TEXT,
                timestamp INTEGER NOT NULL
            )
            """.trimIndent()
        )

        // 2. memory_store
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS memory_store (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                key TEXT NOT NULL,
                value TEXT NOT NULL,
                category TEXT NOT NULL DEFAULT 'Memory Store',
                isPinned INTEGER NOT NULL DEFAULT 0,
                updatedAt INTEGER NOT NULL
            )
            """.trimIndent()
        )

        // 3. daily_tasks
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS daily_tasks (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                title TEXT NOT NULL,
                category TEXT NOT NULL DEFAULT 'General',
                isCompleted INTEGER NOT NULL DEFAULT 0,
                dueDate TEXT NOT NULL DEFAULT 'Today',
                timestamp INTEGER NOT NULL
            )
            """.trimIndent()
        )

        // 4. tasks
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS tasks (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                title TEXT NOT NULL,
                isCompleted INTEGER NOT NULL DEFAULT 0,
                category TEXT NOT NULL DEFAULT 'General',
                priority TEXT NOT NULL DEFAULT 'MEDIUM',
                dueDateMillis INTEGER,
                tags TEXT NOT NULL DEFAULT '',
                createdAt INTEGER NOT NULL
            )
            """.trimIndent()
        )

        // 5. habits
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS habits (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                title TEXT NOT NULL,
                streakCount INTEGER NOT NULL DEFAULT 0,
                frequency TEXT NOT NULL DEFAULT 'Daily',
                isCompletedToday INTEGER NOT NULL DEFAULT 0,
                lastCompletedTimestamp INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )

        // 6. focus_sessions
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS focus_sessions (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                sessionName TEXT NOT NULL,
                durationMinutes INTEGER NOT NULL,
                category TEXT NOT NULL DEFAULT 'Work',
                colorHex TEXT NOT NULL DEFAULT '#3B82F6',
                timestamp INTEGER NOT NULL,
                notes TEXT NOT NULL DEFAULT ''
            )
            """.trimIndent()
        )

        // 7. command_logs
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS command_logs (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                commandText TEXT NOT NULL,
                commandType TEXT NOT NULL,
                status TEXT NOT NULL,
                actionDetail TEXT NOT NULL,
                timestamp INTEGER NOT NULL,
                failureCategory TEXT NOT NULL DEFAULT 'NONE',
                failureReason TEXT NOT NULL DEFAULT '',
                suggestedAlternative TEXT NOT NULL DEFAULT '',
                isRepeatedFailure INTEGER NOT NULL DEFAULT 0,
                remediationAction TEXT NOT NULL DEFAULT '',
                remediationUri TEXT NOT NULL DEFAULT ''
            )
            """.trimIndent()
        )

        // 8. custom_shortcuts
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS custom_shortcuts (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                phrase TEXT NOT NULL,
                targetPackageOrAction TEXT NOT NULL,
                description TEXT NOT NULL,
                isEnabled INTEGER NOT NULL DEFAULT 1
            )
            """.trimIndent()
        )

        // 9. daily_planner_items
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS daily_planner_items (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                title TEXT NOT NULL,
                description TEXT NOT NULL DEFAULT '',
                category TEXT NOT NULL DEFAULT 'General',
                isHabit INTEGER NOT NULL DEFAULT 0,
                isCompleted INTEGER NOT NULL DEFAULT 0,
                streakCount INTEGER NOT NULL DEFAULT 0,
                targetDaysPerWeek INTEGER NOT NULL DEFAULT 7,
                priority TEXT NOT NULL DEFAULT 'Medium',
                scheduledDate TEXT NOT NULL DEFAULT 'Today',
                dayOfWeek INTEGER NOT NULL DEFAULT 1,
                weeklyCompletionHistory TEXT NOT NULL DEFAULT '0,0,0,0,0,0,0',
                lastCompletedTimestamp INTEGER NOT NULL DEFAULT 0,
                reminderTime TEXT NOT NULL DEFAULT '',
                createdAt INTEGER NOT NULL
            )
            """.trimIndent()
        )

        // 10. ingested_assets
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS ingested_assets (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                fileUri TEXT NOT NULL,
                fileName TEXT NOT NULL,
                mimeType TEXT NOT NULL,
                category TEXT NOT NULL,
                sizeBytes INTEGER NOT NULL,
                sha256Hash TEXT NOT NULL,
                storedRelativePath TEXT NOT NULL,
                originalSource TEXT NOT NULL DEFAULT 'USER_UPLOAD',
                aliasName TEXT,
                metadataJson TEXT NOT NULL DEFAULT '{}',
                extractedSummary TEXT,
                indexedKeywords TEXT NOT NULL DEFAULT '',
                timestamp INTEGER NOT NULL
            )
            """.trimIndent()
        )

        // 11. conversation_history
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS conversation_history (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                userQuery TEXT NOT NULL,
                assistantResponse TEXT NOT NULL,
                executionMode TEXT NOT NULL,
                timestamp INTEGER NOT NULL
            )
            """.trimIndent()
        )

        // 12. kyawgyi_shortcuts
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS kyawgyi_shortcuts (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                triggerPhrase TEXT NOT NULL,
                actionType TEXT NOT NULL,
                payload TEXT NOT NULL,
                isEnabled INTEGER NOT NULL DEFAULT 1
            )
            """.trimIndent()
        )

        // 13. url_controls
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS url_controls (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL,
                triggerPhrase TEXT NOT NULL,
                url TEXT NOT NULL,
                actionType TEXT NOT NULL DEFAULT 'OPEN_URL',
                requestBody TEXT NOT NULL DEFAULT '',
                headers TEXT NOT NULL DEFAULT '',
                successSpeech TEXT NOT NULL DEFAULT 'လင့်ခ်သို့ ချိတ်ဆက်ဆောင်ရွက်ပြီးပါပြီ',
                failureSpeech TEXT NOT NULL DEFAULT 'လင့်ခ်သို့ ချိတ်ဆက်၍ မရပါခင်ဗျာ',
                isEnabled INTEGER NOT NULL DEFAULT 1,
                lastExecutedAt INTEGER NOT NULL DEFAULT 0,
                lastStatus TEXT NOT NULL DEFAULT 'IDLE'
            )
            """.trimIndent()
        )

        // 14. smart_home_devices
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS smart_home_devices (
                deviceId TEXT PRIMARY KEY NOT NULL,
                name TEXT NOT NULL,
                room TEXT NOT NULL DEFAULT 'ဧည့်ခန်း (Living Room)',
                deviceType TEXT NOT NULL DEFAULT 'LIGHT',
                isOn INTEGER NOT NULL DEFAULT 0,
                brightness INTEGER NOT NULL DEFAULT 100,
                temperature INTEGER NOT NULL DEFAULT 24,
                burmeseTriggerPhrases TEXT NOT NULL DEFAULT '',
                traitType TEXT NOT NULL DEFAULT 'action.devices.traits.OnOff',
                apiEndpoint TEXT NOT NULL DEFAULT '',
                isOnline INTEGER NOT NULL DEFAULT 1,
                lastUpdated INTEGER NOT NULL
            )
            """.trimIndent()
        )

        // 15. learned_aliases
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS learned_aliases (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                normalizedPhrase TEXT NOT NULL,
                rawPhrase TEXT NOT NULL,
                actionType TEXT NOT NULL,
                targetPayload TEXT NOT NULL,
                displayLabel TEXT NOT NULL,
                confidence REAL NOT NULL DEFAULT 1.0,
                learnedAt INTEGER NOT NULL,
                usageCount INTEGER NOT NULL DEFAULT 1
            )
            """.trimIndent()
        )
    }

    /**
     * Runs SQLite PRAGMA quick_check to verify non-destructive migration integrity on boot.
     */
    fun runPragmaQuickCheck(db: SupportSQLiteDatabase): Boolean {
        return try {
            db.query("PRAGMA quick_check;").use { cursor ->
                if (cursor.moveToFirst()) {
                    val result = cursor.getString(0) ?: ""
                    val isOk = result.equals("ok", ignoreCase = true)
                    if (!isOk) {
                        Log.e(TAG, "SQLite PRAGMA quick_check reported issue: $result")
                    }
                    isOk
                } else {
                    false
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "PRAGMA quick_check execution warning: ${e.message}")
            false
        }
    }

    /**
     * Database Callback ensuring WAL mode, PRAGMA quick_check, foreign keys, and query performance indexes
     */
    val DatabaseCallback = object : RoomDatabase.Callback() {
        override fun onOpen(db: SupportSQLiteDatabase) {
            super.onOpen(db)
            try {
                // Ensure write-ahead logging (WAL) for smooth concurrent UI and background service queries
                try {
                    db.query("PRAGMA journal_mode=WAL;").close()
                } catch (ignored: Exception) {
                    // Journal mode is natively controlled by Room's JournalMode.WRITE_AHEAD_LOGGING
                }

                // Verify SQLite page & index integrity via PRAGMA quick_check
                val quickCheckPassed = runPragmaQuickCheck(db)

                try {
                    db.setForeignKeyConstraintsEnabled(true)
                } catch (ignored: Exception) {
                    db.execSQL("PRAGMA foreign_keys=ON;")
                }

                // Self-healing check: guarantee all 15 tables exist
                createAllTablesIfNotExist(db)

                // Ensure unmanaged indices that conflict with Room TableInfo validation are removed
                dropAllUnmanagedIndices(db)

                Log.i(TAG, "AppDatabase successfully verified and optimized with WAL mode (quick_check=$quickCheckPassed).")
            } catch (e: Exception) {
                Log.e(TAG, "Error executing database onOpen optimizations: ${e.message}", e)
            }
        }
    }

    /**
     * Master array of all registered migrations
     */
    val ALL_MIGRATIONS = arrayOf(
        MIGRATION_1_2,
        MIGRATION_2_3,
        MIGRATION_3_4,
        MIGRATION_4_5,
        MIGRATION_5_6,
        MIGRATION_6_7,
        MIGRATION_7_8,
        MIGRATION_8_9,
        MIGRATION_9_10,
        MIGRATION_1_10,
        MIGRATION_10_11,
        MIGRATION_1_11,
        MIGRATION_11_12,
        MIGRATION_1_12,
        MIGRATION_12_13,
        MIGRATION_11_13,
        MIGRATION_1_13
    )
}
