package com.example.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ErrorLogDao {
  @Query("SELECT * FROM error_logs ORDER BY timestamp DESC")
  fun getAllLogs(): Flow<List<ErrorLogEntity>>

  @Query("SELECT * FROM error_logs WHERE (actionKey = :actionKey OR rawCommand = :rawCommand) AND isResolved = 0 ORDER BY timestamp DESC LIMIT 1")
  suspend fun findRecentFailure(actionKey: String, rawCommand: String): ErrorLogEntity?

  @Query("SELECT failedMethod FROM error_logs WHERE actionKey = :actionKey AND isResolved = 0")
  suspend fun getFailedMethodsForAction(actionKey: String): List<String>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertLog(log: ErrorLogEntity): Long

  @Query("UPDATE error_logs SET retryCount = retryCount + 1, timestamp = :newTime WHERE id = :id")
  suspend fun incrementRetry(id: Long, newTime: Long = System.currentTimeMillis())

  @Query("UPDATE error_logs SET isResolved = 1 WHERE actionKey = :actionKey")
  suspend fun markResolved(actionKey: String)

  @Query("DELETE FROM error_logs")
  suspend fun clearAll()
}

@Dao
interface MemoryDao {
  @Query("SELECT * FROM learned_memories ORDER BY createdAt DESC")
  fun getAll(): Flow<List<MemoryEntity>>

  @Query("SELECT * FROM learned_memories WHERE aliasOrKey = :key LIMIT 1")
  suspend fun findByKey(key: String): MemoryEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insert(entry: MemoryEntity)

  @Query("DELETE FROM learned_memories WHERE id = :id")
  suspend fun delete(id: String)

  @Query("DELETE FROM learned_memories")
  suspend fun clearAll()
}

@Dao
interface ShortcutDao {
  @Query("SELECT * FROM custom_shortcuts")
  fun getAll(): Flow<List<ShortcutEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insert(shortcut: ShortcutEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertAll(shortcuts: List<ShortcutEntity>)

  @Query("DELETE FROM custom_shortcuts WHERE id = :id")
  suspend fun delete(id: String)
}

@Dao
interface EcoFlowConfigDao {
  @Query("SELECT * FROM ecoflow_config WHERE id = 1 LIMIT 1")
  fun getConfig(): Flow<EcoFlowConfigEntity?>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun saveConfig(config: EcoFlowConfigEntity)
}

@Dao
interface FanAutomationConfigDao {
  @Query("SELECT * FROM fan_automation_config WHERE id = 1 LIMIT 1")
  fun getConfig(): Flow<FanAutomationConfigEntity?>

  @Query("SELECT * FROM fan_automation_config WHERE id = 1 LIMIT 1")
  suspend fun getConfigDirect(): FanAutomationConfigEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun saveConfig(config: FanAutomationConfigEntity)

  @Query("DELETE FROM fan_automation_config")
  suspend fun clearConfig()
}

