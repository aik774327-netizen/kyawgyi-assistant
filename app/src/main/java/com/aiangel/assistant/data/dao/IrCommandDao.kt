package com.aiangel.assistant.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.aiangel.assistant.data.entity.IrCommandEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface IrCommandDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(command: IrCommandEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(commands: List<IrCommandEntity>)

    @Update
    suspend fun update(command: IrCommandEntity)

    @Query("SELECT * FROM ir_commands WHERE deviceName = :deviceName ORDER BY commandLabel ASC")
    fun observeCommandsForDevice(deviceName: String): Flow<List<IrCommandEntity>>

    @Query("SELECT * FROM ir_commands WHERE deviceName = :deviceName ORDER BY commandLabel ASC")
    suspend fun getCommandsForDevice(deviceName: String): List<IrCommandEntity>

    @Query("SELECT * FROM ir_commands ORDER BY id ASC")
    fun observeAll(): Flow<List<IrCommandEntity>>

    @Query("SELECT * FROM ir_commands WHERE deviceName = :deviceName AND commandLabel = :label LIMIT 1")
    suspend fun findCommand(deviceName: String, label: String): IrCommandEntity?

    @Query("SELECT * FROM ir_commands WHERE deviceName = :deviceName AND commandLabel = :label LIMIT 1")
    fun observeCommand(deviceName: String, label: String): Flow<IrCommandEntity?>

    @Query("SELECT DISTINCT deviceName FROM ir_commands ORDER BY deviceName ASC")
    suspend fun getKnownDevices(): List<String>

    @Query("SELECT COUNT(*) FROM ir_commands WHERE deviceName = :deviceName AND commandLabel = :label")
    suspend fun commandExists(deviceName: String, label: String): Int

    @Query("UPDATE ir_commands SET evidenceState = :state, updatedAt = :updatedAt WHERE deviceName = :deviceName AND commandLabel = :label")
    suspend fun updateLifecycleState(deviceName: String, label: String, state: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE ir_commands SET evidenceState = :state, isVerified = :isVerified, applianceVerifiedAt = :verifiedAt, updatedAt = :updatedAt WHERE deviceName = :deviceName AND commandLabel = :label")
    suspend fun updateApplianceVerification(deviceName: String, label: String, state: String, isVerified: Boolean, verifiedAt: Long, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE ir_commands SET lastError = :error, updatedAt = :updatedAt WHERE deviceName = :deviceName AND commandLabel = :label")
    suspend fun recordError(deviceName: String, label: String, error: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE ir_commands SET evidenceState = :state, isVerified = :isVerified, acknowledgedAt = :ackAt, opticalVerifiedAt = :optAt, applianceVerifiedAt = :appAt, evidenceDetails = :details, lastError = :error, updatedAt = :updatedAt WHERE deviceName = :deviceName AND commandLabel = :label")
    suspend fun recordVerificationResult(
        deviceName: String,
        label: String,
        state: String,
        isVerified: Boolean,
        ackAt: Long?,
        optAt: Long?,
        appAt: Long?,
        details: String?,
        error: String?,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Delete
    suspend fun delete(command: IrCommandEntity)

    @Query("DELETE FROM ir_commands WHERE deviceName = :deviceName")
    suspend fun deleteAllForDevice(deviceName: String)
}

