package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_messages ORDER BY id ASC")
    fun getAllMessages(): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages ORDER BY id DESC LIMIT 50")
    fun getLast50Messages(): Flow<List<ChatMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity): Long

    @Query("DELETE FROM chat_messages WHERE id = :id")
    suspend fun deleteMessageById(id: Long)

    @Query("DELETE FROM chat_messages")
    suspend fun clearHistory()

    @Query("SELECT COUNT(*) FROM chat_messages")
    suspend fun getMessageCount(): Int
}

@Dao
interface CommandLogDao {
    @Query("SELECT * FROM command_logs ORDER BY timestamp DESC")
    fun getAllCommandLogs(): Flow<List<CommandLogEntity>>

    @Query("SELECT * FROM command_logs ORDER BY timestamp DESC LIMIT 50")
    fun getLast50CommandLogs(): Flow<List<CommandLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCommandLog(log: CommandLogEntity): Long

    @Query("DELETE FROM command_logs WHERE id = :id")
    suspend fun deleteCommandLogById(id: Long)

    @Query("DELETE FROM command_logs")
    suspend fun clearCommandLogs()

    @Query("SELECT COUNT(*) FROM command_logs")
    suspend fun getCommandLogCount(): Int

    @Query("SELECT * FROM command_logs WHERE status = 'FAILED' ORDER BY timestamp DESC")
    fun getFailedCommandLogs(): Flow<List<CommandLogEntity>>

    @Query("SELECT * FROM command_logs WHERE status = 'FAILED' AND (commandText = :queryText OR actionDetail LIKE '%' || :queryText || '%') ORDER BY timestamp DESC LIMIT 1")
    suspend fun findRecentFailure(queryText: String): CommandLogEntity?

    @Query("SELECT * FROM command_logs WHERE status = 'FAILED' ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentFailuresList(limit: Int = 20): List<CommandLogEntity>

    @Query("SELECT * FROM command_logs WHERE status = 'FAILED' ORDER BY timestamp DESC LIMIT 200")
    suspend fun getAllFailuresForMemoryRestore(): List<CommandLogEntity>

    @Query("DELETE FROM command_logs WHERE status = 'FAILED'")
    suspend fun clearAllFailures()
}

@Dao
interface CustomShortcutDao {
    @Query("SELECT * FROM custom_shortcuts ORDER BY id DESC")
    fun getAllCustomShortcuts(): Flow<List<CustomShortcutEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomShortcut(shortcut: CustomShortcutEntity): Long

    @Update
    suspend fun updateCustomShortcut(shortcut: CustomShortcutEntity)

    @Delete
    suspend fun deleteCustomShortcut(shortcut: CustomShortcutEntity)

    @Query("SELECT COUNT(*) FROM custom_shortcuts")
    suspend fun getCustomShortcutCount(): Int
}

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memory_store ORDER BY isPinned DESC, id DESC")
    fun getAllMemories(): Flow<List<MemoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemory(memory: MemoryEntity): Long

    @Delete
    suspend fun deleteMemory(memory: MemoryEntity)

    @Query("SELECT COUNT(*) FROM memory_store")
    suspend fun getMemoryCount(): Int
}

@Dao
interface DailyTaskDao {
    @Query("SELECT * FROM daily_tasks ORDER BY isCompleted ASC, timestamp DESC")
    fun getAllTasks(): Flow<List<DailyTaskEntity>>

    @Query("SELECT * FROM daily_tasks WHERE category = :category ORDER BY isCompleted ASC, timestamp DESC")
    fun getTasksByCategory(category: String): Flow<List<DailyTaskEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: DailyTaskEntity): Long

    @Update
    suspend fun updateTask(task: DailyTaskEntity)

    @Delete
    suspend fun deleteTask(task: DailyTaskEntity)

    @Query("DELETE FROM daily_tasks WHERE id = :id")
    suspend fun deleteTaskById(id: Int)

    @Query("SELECT COUNT(*) FROM daily_tasks")
    suspend fun getTaskCount(): Int
}

@Dao
interface HabitDao {
    @Query("SELECT * FROM habits ORDER BY streakCount DESC, id DESC")
    fun getAllHabits(): Flow<List<HabitEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHabit(habit: HabitEntity): Long

    @Update
    suspend fun updateHabit(habit: HabitEntity)

    @Delete
    suspend fun deleteHabit(habit: HabitEntity)

    @Query("SELECT COUNT(*) FROM habits")
    suspend fun getHabitCount(): Int
}

@Dao
interface FocusSessionDao {
    @Query("SELECT * FROM focus_sessions ORDER BY timestamp DESC")
    fun getAllFocusSessions(): Flow<List<FocusSessionEntity>>

    @Query("SELECT * FROM focus_sessions WHERE category = :category ORDER BY timestamp DESC")
    fun getSessionsByCategory(category: String): Flow<List<FocusSessionEntity>>

    @Query("SELECT * FROM focus_sessions WHERE timestamp >= :sinceTimestamp ORDER BY timestamp DESC")
    fun getSessionsSince(sinceTimestamp: Long): Flow<List<FocusSessionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFocusSession(session: FocusSessionEntity): Long

    @Update
    suspend fun updateFocusSession(session: FocusSessionEntity)

    @Delete
    suspend fun deleteFocusSession(session: FocusSessionEntity)

    @Query("DELETE FROM focus_sessions WHERE id = :id")
    suspend fun deleteFocusSessionById(id: Int)

    @Query("SELECT COUNT(*) FROM focus_sessions")
    suspend fun getFocusSessionCount(): Int
}
