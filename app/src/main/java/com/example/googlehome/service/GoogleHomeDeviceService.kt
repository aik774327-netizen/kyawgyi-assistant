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
     * Returns the locally stored smart-home devices. There is NO discovery
     * source (HomeGraph / Matter / local mesh) in this app, so nothing is
     * discovered and nothing is inserted.
     */
    suspend fun syncDiscoveredDevices(): List<SmartHomeDeviceEntity> = withContext(Dispatchers.IO) {
        // No real discovery source exists in this app: there is no HomeGraph,
        // Matter, or local-mesh client, and GoogleHomeManager only opens the
        // Google Home app / Assistant. The previous implementation fabricated
        // six household devices and persisted them to Room as if they had
        // been discovered (FAKE-08, removed 2026-10-22). Return only the
        // devices genuinely stored locally; insert nothing.
        val stored = deviceDao.getAllDevices()
        Log.i(TAG, "No Google Home discovery source configured; returning ${stored.size} locally stored device(s), inserting none.")
        stored
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
