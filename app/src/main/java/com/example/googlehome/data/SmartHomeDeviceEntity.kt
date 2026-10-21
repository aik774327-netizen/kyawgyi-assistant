package com.example.googlehome.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room Database Entity for persistent Google Home Smart Devices
 */
@Entity(tableName = "smart_home_devices")
data class SmartHomeDeviceEntity(
    @PrimaryKey
    val deviceId: String,
    val name: String,
    val room: String = "ဧည့်ခန်း (Living Room)",
    val deviceType: String = "LIGHT", // LIGHT, SWITCH, AC, TV, FAN, SPEAKER
    val isOn: Boolean = false,
    val brightness: Int = 100, // 0 - 100%
    val temperature: Int = 24, // 16 - 30 C
    val burmeseTriggerPhrases: String = "", // Comma-separated burmese aliases
    val traitType: String = "action.devices.traits.OnOff",
    val apiEndpoint: String = "",
    val isOnline: Boolean = true,
    val lastUpdated: Long = System.currentTimeMillis()
)
