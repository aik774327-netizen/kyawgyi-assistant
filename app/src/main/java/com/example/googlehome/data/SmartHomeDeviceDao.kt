package com.example.googlehome.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for Google Home Smart Devices
 */
@Dao
interface SmartHomeDeviceDao {

    @Query("SELECT * FROM smart_home_devices ORDER BY room ASC, name ASC")
    fun getAllDevicesFlow(): Flow<List<SmartHomeDeviceEntity>>

    @Query("SELECT * FROM smart_home_devices ORDER BY room ASC, name ASC")
    suspend fun getAllDevices(): List<SmartHomeDeviceEntity>

    @Query("SELECT * FROM smart_home_devices WHERE deviceId = :deviceId LIMIT 1")
    suspend fun getDeviceById(deviceId: String): SmartHomeDeviceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDevice(device: SmartHomeDeviceEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(devices: List<SmartHomeDeviceEntity>)

    @Update
    suspend fun updateDevice(device: SmartHomeDeviceEntity)

    @Delete
    suspend fun deleteDevice(device: SmartHomeDeviceEntity)

    @Query("UPDATE smart_home_devices SET isOn = :isOn, lastUpdated = :timestamp WHERE deviceId = :deviceId")
    suspend fun updatePowerState(deviceId: String, isOn: Boolean, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE smart_home_devices SET brightness = :brightness, isOn = 1, lastUpdated = :timestamp WHERE deviceId = :deviceId")
    suspend fun updateBrightness(deviceId: String, brightness: Int, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE smart_home_devices SET temperature = :temperature, isOn = 1, lastUpdated = :timestamp WHERE deviceId = :deviceId")
    suspend fun updateTemperature(deviceId: String, temperature: Int, timestamp: Long = System.currentTimeMillis())

    @Query("SELECT COUNT(*) FROM smart_home_devices")
    suspend fun getDeviceCount(): Int
}
