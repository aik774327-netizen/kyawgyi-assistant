package com.kyawgyi.ir.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/**
 * EventLog entity to store transmission and learning event history.
 */
@Entity(tableName = "event_logs")
data class EventLog(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val timestamp: Long = System.currentTimeMillis(),
  val eventType: String, // e.g. "TRANSMIT_SUCCESS", "TRANSMIT_FAILED", "LEARN_STARTED", "LEARN_SUCCESS", "LEARN_FAILED", "VOICE_COMMAND"
  val buttonKey: String? = null,
  val message: String,
  val details: String? = null,
  val isSuccess: Boolean = true
)

@Dao
interface EventLogDao {
  @Query("SELECT * FROM event_logs ORDER BY timestamp DESC")
  fun getAllLogs(): Flow<List<EventLog>>

  @Query("SELECT * FROM event_logs WHERE eventType = :type ORDER BY timestamp DESC")
  fun getLogsByType(type: String): Flow<List<EventLog>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertLog(log: EventLog)

  @Query("DELETE FROM event_logs")
  suspend fun clearAllLogs()

  @Query("DELETE FROM event_logs WHERE id NOT IN (SELECT id FROM event_logs ORDER BY timestamp DESC LIMIT 200)")
  suspend fun pruneOldLogs()
}

@Database(entities = [EventLog::class], version = 1, exportSchema = false)
abstract class IrDatabase : RoomDatabase() {
  abstract fun eventLogDao(): EventLogDao

  companion object {
    @Volatile
    private var INSTANCE: IrDatabase? = null

    fun getInstance(context: Context): IrDatabase {
      return INSTANCE ?: synchronized(this) {
        val instance = Room.databaseBuilder(
          context.applicationContext,
          IrDatabase::class.java,
          "kennede_ir_event_logs.db"
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
