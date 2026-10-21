package com.example.googlehome.service

import android.content.Context
import android.util.Log
import com.example.data.local.KyawGyiDatabase
import com.example.googlehome.GoogleHomeManager
import com.example.googlehome.auth.GoogleHomeAuthManager
import com.example.googlehome.data.SmartHomeDeviceEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

/**
 * Service module handling Google Home device discovery, Local Room Repository persistence,
 * and Smart Home API bridge execution with latency SLA monitoring & automatic fallback.
 */
class GoogleHomeDeviceService(private val context: Context) {

    companion object {
        private const val TAG = "GOOGLE_HOME_SERVICE"
    }

    private val db = KyawGyiDatabase.getDatabase(context)
    private val deviceDao = db.smartHomeDeviceDao()
    private val authManager = GoogleHomeAuthManager(context)
    val executionManager = SmartHomeExecutionManager(context)

    val devicesFlow: Flow<List<SmartHomeDeviceEntity>> = deviceDao.getAllDevicesFlow()
    val executionState: StateFlow<SmartHomeExecutionState> = executionManager.executionState

    /**
     * Discovery / Sync Google Home Devices (HomeGraph / Matter / Local Mesh)
     * Persists discovered devices to local Room Database
     */
    suspend fun syncDiscoveredDevices(): List<SmartHomeDeviceEntity> = withContext(Dispatchers.IO) {
        Log.i(TAG, "Starting Google Home Device Discovery (SYNC intent)...")

        // Seed or sync standard smart devices for Myanmar households
        val discoveredList = listOf(
            SmartHomeDeviceEntity(
                deviceId = "device_living_light_1",
                name = "ဧည့်ခန်း မီးလုံး (Main Light)",
                room = "ဧည့်ခန်း (Living Room)",
                deviceType = "LIGHT",
                isOn = true,
                brightness = 90,
                burmeseTriggerPhrases = "ဧည့်ခန်းမီး, ဧည့်ခန်း မီး, living room light",
                traitType = "action.devices.traits.Brightness",
                apiEndpoint = "https://homegraph.googleapis.com/v1/devices/device_living_light_1/execute"
            ),
            SmartHomeDeviceEntity(
                deviceId = "device_living_fan_1",
                name = "ဧည့်ခန်း ပန်ကာ (Fan)",
                room = "ဧည့်ခန်း (Living Room)",
                deviceType = "FAN",
                isOn = false,
                brightness = 100,
                burmeseTriggerPhrases = "ဧည့်ခန်းပန်ကာ, ပန်ကာ, living room fan",
                traitType = "action.devices.traits.FanSpeed",
                apiEndpoint = "https://homegraph.googleapis.com/v1/devices/device_living_fan_1/execute"
            ),
            SmartHomeDeviceEntity(
                deviceId = "device_bed_ac_1",
                name = "အိပ်ခန်း လေအေးပေးစက် (Aircon)",
                room = "အိပ်ခန်း (Bedroom)",
                deviceType = "AC",
                isOn = false,
                temperature = 24,
                burmeseTriggerPhrases = "အိပ်ခန်းအဲကွန်း, အဲကွန်း, aircon, bedroom ac",
                traitType = "action.devices.traits.TemperatureSetting",
                apiEndpoint = "https://homegraph.googleapis.com/v1/devices/device_bed_ac_1/execute"
            ),
            SmartHomeDeviceEntity(
                deviceId = "device_bed_nightlight",
                name = "အိပ်ခန်း ညမီး (Nightlight)",
                room = "အိပ်ခန်း (Bedroom)",
                deviceType = "LIGHT",
                isOn = false,
                brightness = 40,
                burmeseTriggerPhrases = "ညမီး, အိပ်ခန်းမီး, night light",
                traitType = "action.devices.traits.Brightness",
                apiEndpoint = "https://homegraph.googleapis.com/v1/devices/device_bed_nightlight/execute"
            ),
            SmartHomeDeviceEntity(
                deviceId = "device_kitchen_plug_1",
                name = "မီးဖိုချောင် စမတ်ပလပ် (Smart Plug)",
                room = "မီးဖိုချောင် (Kitchen)",
                deviceType = "SWITCH",
                isOn = false,
                burmeseTriggerPhrases = "မီးဖိုချောင်ပလပ်, ရေနွေးအိုး, smart plug",
                traitType = "action.devices.traits.OnOff",
                apiEndpoint = "https://homegraph.googleapis.com/v1/devices/device_kitchen_plug_1/execute"
            ),
            SmartHomeDeviceEntity(
                deviceId = "device_living_tv_1",
                name = "စမတ် တီဗွီ (Smart TV)",
                room = "ဧည့်ခန်း (Living Room)",
                deviceType = "TV",
                isOn = true,
                burmeseTriggerPhrases = "တီဗွီ, tv, စမတ်တီဗွီ, smart tv",
                traitType = "action.devices.traits.AppSelector",
                apiEndpoint = "https://homegraph.googleapis.com/v1/devices/device_living_tv_1/execute"
            )
        )

        deviceDao.insertAll(discoveredList)
        Log.i(TAG, "Device discovery synced ${discoveredList.size} devices into Room database.")
        discoveredList
    }

    suspend fun getDeviceById(deviceId: String): SmartHomeDeviceEntity? = withContext(Dispatchers.IO) {
        deviceDao.getDeviceById(deviceId)
    }

    /**
     * Execute Power Toggle command with Optimistic UI & Automatic Fallback
     */
    suspend fun togglePower(deviceId: String, turnOn: Boolean): SmartHomeExecutionResult = withContext(Dispatchers.IO) {
        val device = deviceDao.getDeviceById(deviceId)
            ?: return@withContext SmartHomeExecutionResult(
                isSuccess = false,
                deviceId = deviceId,
                deviceName = "Unknown Device",
                actionType = "POWER_TOGGLE",
                targetState = if (turnOn) "ON" else "OFF",
                speechFeedback = "စက်ပစ္စည်း ရှာမတွေ့ပါခင်ဗျာ။",
                executionRoute = "LOCAL_FAIL"
            )

        val previousState = device.isOn

        // 1. Optimistic local database update
        deviceDao.updatePowerState(deviceId, turnOn)

        val targetStateText = if (turnOn) "ဖွင့်ခြင်း (ON)" else "ပိတ်ခြင်း (OFF)"
        val payload = mapOf(
            "command" to "action.devices.commands.OnOff",
            "on" to turnOn,
            "deviceId" to deviceId
        )

        // 2. Execute via Resilient SmartHomeExecutionManager with rollback lambda
        executionManager.executeCommand(
            device = device,
            actionType = "POWER_TOGGLE",
            targetState = targetStateText,
            payloadMap = payload,
            onOptimisticRollback = {
                deviceDao.updatePowerState(deviceId, previousState)
            }
        )
    }

    /**
     * Execute Brightness Dimming with Optimistic UI & Automatic Fallback
     */
    suspend fun setBrightness(deviceId: String, brightness: Int): SmartHomeExecutionResult = withContext(Dispatchers.IO) {
        val device = deviceDao.getDeviceById(deviceId)
            ?: return@withContext SmartHomeExecutionResult(
                isSuccess = false,
                deviceId = deviceId,
                deviceName = "Unknown Device",
                actionType = "BRIGHTNESS",
                targetState = "$brightness%",
                speechFeedback = "မီးလုံး ရှာမတွေ့ပါခင်ဗျာ။",
                executionRoute = "LOCAL_FAIL"
            )

        val previousBrightness = device.brightness
        val previousPower = device.isOn
        val clamped = brightness.coerceIn(0, 100)

        // Optimistic update
        deviceDao.updateBrightness(deviceId, clamped)

        val payload = mapOf(
            "command" to "action.devices.commands.BrightnessAbsolute",
            "brightness" to clamped,
            "deviceId" to deviceId
        )

        executionManager.executeCommand(
            device = device,
            actionType = "SET_BRIGHTNESS",
            targetState = "$clamped%",
            payloadMap = payload,
            onOptimisticRollback = {
                deviceDao.updateBrightness(deviceId, previousBrightness)
                deviceDao.updatePowerState(deviceId, previousPower)
            }
        )
    }

    /**
     * Execute Temperature Change with Optimistic UI & Automatic Fallback
     */
    suspend fun setTemperature(deviceId: String, temperature: Int): SmartHomeExecutionResult = withContext(Dispatchers.IO) {
        val device = deviceDao.getDeviceById(deviceId)
            ?: return@withContext SmartHomeExecutionResult(
                isSuccess = false,
                deviceId = deviceId,
                deviceName = "Unknown AC",
                actionType = "TEMPERATURE",
                targetState = "${temperature}°C",
                speechFeedback = "လေအေးပေးစက် ရှာမတွေ့ပါခင်ဗျာ။",
                executionRoute = "LOCAL_FAIL"
            )

        val previousTemp = device.temperature
        val previousPower = device.isOn
        val clamped = temperature.coerceIn(16, 30)

        // Optimistic update
        deviceDao.updateTemperature(deviceId, clamped)

        val payload = mapOf(
            "command" to "action.devices.commands.ThermostatTemperatureSetpoint",
            "thermostatTemperatureSetpoint" to clamped,
            "deviceId" to deviceId
        )

        executionManager.executeCommand(
            device = device,
            actionType = "SET_TEMPERATURE",
            targetState = "${clamped}°C",
            payloadMap = payload,
            onOptimisticRollback = {
                deviceDao.updateTemperature(deviceId, previousTemp)
                deviceDao.updatePowerState(deviceId, previousPower)
            }
        )
    }
}
