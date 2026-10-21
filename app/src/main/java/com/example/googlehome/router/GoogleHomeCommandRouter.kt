package com.example.googlehome.router

import android.content.Context
import android.util.Log
import com.example.engine.BurmesePhoneticNormalizer
import com.example.googlehome.service.GoogleHomeDeviceService
import com.example.googlehome.service.SmartHomeExecutionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Smart Home Voice Command Router
 * Parses natural Burmese spoken voice inputs, extracts device target & intent parameters
 * (turn on/off, dimming, temperature change), and routes them to Google Home API endpoints.
 */
class GoogleHomeCommandRouter(private val context: Context) {

    private val TAG = "GOOGLE_HOME_ROUTER"
    private val deviceService = GoogleHomeDeviceService(context)

    /**
     * Parse & execute smart home intent from spoken input
     * Returns execution result if matched, or null if unrelated.
     */
    suspend fun routeVoiceCommand(spokenText: String): SmartHomeExecutionResult? = withContext(Dispatchers.IO) {
        val clean = spokenText.trim().lowercase()
        val normalized = BurmesePhoneticNormalizer.normalizeBurmeseText(clean)

        // No seeding: commands run only against devices genuinely stored in
        // the local Room DB (no discovery source exists - see FAKE-08).

        // 1. Check if user is asking for Google Home app directly
        if (clean.contains("google home") || clean.contains("ဂူဂဲလ်ဟုမ်း") || clean.contains("ဂူဂယ်ဟုမ်း")) {
            return@withContext SmartHomeExecutionResult(
                isSuccess = true,
                deviceId = "app_google_home",
                deviceName = "Google Home App",
                actionType = "OPEN_APP",
                targetState = "LAUNCHED",
                speechFeedback = "ဟုတ်ကဲ့၊ Google Home အပလီကေးရှင်းကို ဖွင့်ပေးလိုက်ပါပြီခင်ဗျာ။",
                executionRoute = "APP_LAUNCHER"
            )
        }

        // 2. Fetch all persistent devices
        val devices = com.example.data.local.KyawGyiDatabase.getDatabase(context).smartHomeDeviceDao().getAllDevices()
        if (devices.isEmpty()) return@withContext null

        // 3. Find matching device by aliases or Burmese name
        val matchedDevice = devices.firstOrNull { dev ->
            val phrases = dev.burmeseTriggerPhrases.split(",").map { it.trim().lowercase() }
            phrases.any { phrase ->
                clean.contains(phrase) || normalized.contains(BurmesePhoneticNormalizer.normalizeBurmeseText(phrase))
            } || clean.contains(dev.name.lowercase())
        } ?: return@withContext null

        Log.d(TAG, "Matched Smart Home Device: ${matchedDevice.name} (${matchedDevice.deviceId}) for query '$spokenText'")

        // 4. Extract intent type (Power On/Off, Brightness/Dimming, Temperature)
        val isTurnOn = clean.contains("ဖွင့်") || clean.contains("on") || clean.contains("တင်")
        val isTurnOff = clean.contains("ပိတ်") || clean.contains("off") || clean.contains("ချ")
        val isDimming = clean.contains("မှိန်") || clean.contains("dim") || clean.contains("လင်း") || clean.contains("အလင်း")
        val isTemperature = clean.contains("ဒီဂရီ") || clean.contains("degree") || clean.contains("အေး") || clean.contains("ပူ") || clean.contains("အပူချိန်")

        // Parse numeric values (e.g. 24 ဒီဂရီ, 50%)
        val numberRegex = "(\\d+)".toRegex()
        val numericMatch = numberRegex.find(clean)?.value?.toIntOrNull()

        return@withContext when {
            // Temperature Set for AC
            (matchedDevice.deviceType == "AC" || isTemperature) && numericMatch != null -> {
                deviceService.setTemperature(matchedDevice.deviceId, numericMatch)
            }

            // Dimming / Brightness adjustment for Lights
            isDimming && numericMatch != null -> {
                deviceService.setBrightness(matchedDevice.deviceId, numericMatch)
            }

            // Dimming relative command ("မီးမှိန်ပေး")
            isDimming && (clean.contains("မှိန်") || clean.contains("လျှော့")) -> {
                deviceService.setBrightness(matchedDevice.deviceId, 30)
            }

            // Bright relative command ("မီးလင်းပေး")
            isDimming && (clean.contains("လင်း") || clean.contains("တင်")) -> {
                deviceService.setBrightness(matchedDevice.deviceId, 100)
            }

            // Turn On
            isTurnOn -> {
                deviceService.togglePower(matchedDevice.deviceId, true)
            }

            // Turn Off
            isTurnOff -> {
                deviceService.togglePower(matchedDevice.deviceId, false)
            }

            else -> {
                // Default toggle
                deviceService.togglePower(matchedDevice.deviceId, !matchedDevice.isOn)
            }
        }
    }
}
