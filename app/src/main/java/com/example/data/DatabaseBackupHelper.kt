package com.example.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * KYAW GYI AI — Database Backup, Restore & Integrity Safety Helper (အချက် ၁)
 *
 * Provides safe local backup of the SQLite database (ai_angel_v25_db, -wal, -shm)
 * to prevent data loss across device reboots or app upgrades.
 */
object DatabaseBackupHelper {

    private const val TAG = "DatabaseBackupHelper"
    private const val DB_NAME = "ai_angel_v25_db"
    private const val BACKUP_DIR_NAME = "database_backups"

    data class DatabaseStats(
        val chatMessagesCount: Long,
        val tasksCount: Long,
        val dailyPlannerItemsCount: Long,
        val memoryStoreCount: Long,
        val customShortcutsCount: Long,
        val learnedAliasesCount: Long,
        val conversationHistoryCount: Long,
        val databaseSizeBytes: Long
    )

    /**
     * Creates an atomic internal snapshot backup of the current database file.
     */
    suspend fun createBackup(context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            val dbFile = context.getDatabasePath(DB_NAME)
            if (!dbFile.exists()) {
                Log.w(TAG, "Source database file does not exist yet: ${dbFile.absolutePath}")
                return@withContext false
            }

            val backupDir = File(context.filesDir, BACKUP_DIR_NAME)
            if (!backupDir.exists()) {
                backupDir.mkdirs()
            }

            val targetBackupFile = File(backupDir, "${DB_NAME}_backup.sqlite")

            // Copy primary DB file
            copyFile(dbFile, targetBackupFile)

            // Copy WAL and SHM files if present
            val walFile = File(dbFile.parentFile, "$DB_NAME-wal")
            if (walFile.exists()) {
                copyFile(walFile, File(backupDir, "${DB_NAME}_backup.sqlite-wal"))
            }

            val shmFile = File(dbFile.parentFile, "$DB_NAME-shm")
            if (shmFile.exists()) {
                copyFile(shmFile, File(backupDir, "${DB_NAME}_backup.sqlite-shm"))
            }

            Log.i(TAG, "Database successfully backed up to ${targetBackupFile.absolutePath} (${targetBackupFile.length()} bytes)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create database backup: ${e.message}", e)
            false
        }
    }

    /**
     * Checks database size and records counts across core tables
     */
    suspend fun inspectDatabaseStats(context: Context): DatabaseStats = withContext(Dispatchers.IO) {
        val db = AppDatabase.getDatabase(context)
        val dbFile = context.getDatabasePath(DB_NAME)
        val size = if (dbFile.exists()) dbFile.length() else 0L

        var chatCount = 0L
        var tasksCount = 0L
        var plannerCount = 0L
        var memoryCount = 0L
        var shortcutCount = 0L
        var aliasesCount = 0L
        var convCount = 0L

        try {
            val sqliteDb = db.openHelper.readableDatabase
            chatCount = queryCount(sqliteDb, "chat_messages")
            tasksCount = queryCount(sqliteDb, "tasks")
            plannerCount = queryCount(sqliteDb, "daily_planner_items")
            memoryCount = queryCount(sqliteDb, "memory_store")
            shortcutCount = queryCount(sqliteDb, "custom_shortcuts")
            aliasesCount = queryCount(sqliteDb, "learned_aliases")
            convCount = queryCount(sqliteDb, "conversation_history")
        } catch (e: Exception) {
            Log.w(TAG, "Could not query stats directly from SQLite: ${e.message}")
        }

        DatabaseStats(
            chatMessagesCount = chatCount,
            tasksCount = tasksCount,
            dailyPlannerItemsCount = plannerCount,
            memoryStoreCount = memoryCount,
            customShortcutsCount = shortcutCount,
            learnedAliasesCount = aliasesCount,
            conversationHistoryCount = convCount,
            databaseSizeBytes = size
        )
    }

    private fun queryCount(db: androidx.sqlite.db.SupportSQLiteDatabase, tableName: String): Long {
        return try {
            val cursor = db.query("SELECT COUNT(*) FROM $tableName")
            if (cursor.moveToFirst()) {
                val count = cursor.getLong(0)
                cursor.close()
                count
            } else {
                cursor.close()
                0L
            }
        } catch (e: Exception) {
            0L
        }
    }

    private fun copyFile(src: File, dst: File) {
        FileInputStream(src).use { inStream ->
            FileOutputStream(dst).use { outStream ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (inStream.read(buffer).also { bytesRead = it } > 0) {
                    outStream.write(buffer, 0, bytesRead)
                }
            }
        }
    }
}
