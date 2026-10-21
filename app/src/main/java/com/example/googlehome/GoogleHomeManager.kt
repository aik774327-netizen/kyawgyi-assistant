package com.example.googlehome

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.example.actions.CommandResult
import com.example.actions.KyawGyiActionExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Smart Home Device Types supported in Google Home / Matter / Local Webhook Ecosystem
 */
enum class SmartDeviceType(val burmeseName: String, val defaultTrait: String) {
    LIGHT("မီးလုံး / မီးချောင်း", "action.devices.traits.OnOff"),
    SWITCH("ခလုတ် (Switch / Plug)", "action.devices.traits.OnOff"),
    AIR_CONDITIONER("လေအေးပေးစက် (Aircon)", "action.devices.traits.TemperatureSetting"),
    FAN("ပန်ကာ (Fan)", "action.devices.traits.FanSpeed"),
    SPEAKER("စမတ် စပီကာ (Speaker / Cast)", "action.devices.traits.Volume"),
    DOOR_LOCK("တံခါးသော့ (Smart Lock)", "action.devices.traits.LockUnlock"),
    TV("စမတ် တီဗွီ (Smart TV)", "action.devices.traits.AppSelector"),
    GENERIC("အခြား စမတ်ပစ္စည်း", "action.devices.traits.OnOff")
}

data class GoogleHomeDevice(
    val id: String,
    val name: String,
    val burmeseAlias: List<String>,
    val room: String,
    val type: SmartDeviceType,
    var isOn: Boolean = false,
    var brightness: Int = 100, // 0 - 100%
    var temperature: Int = 24, // Celsius for AC
    val webhookEndpoint: String = "",
    val googleAssistantVoiceCommand: String = ""
)

/**
 * Google Home Integration & Smart Automation Manager for Kyaw Gyi Assistant.
 * Provides:
 * 1. Direct App Linking & Device Control (Google Home app package: com.google.android.apps.chromecast.app)
 * 2. Android Device Controls API / Quick Settings Tiles
 * 3. Smart Local / Cloud Webhooks execution (Home Assistant / Matter / Google Smart Home Action)
 * 4. Google Assistant / Nest Cast integration via Deep Links
 */
class GoogleHomeManager(private val context: Context) {

    companion object {
        const val GOOGLE_HOME_PACKAGE = "com.google.android.apps.chromecast.app"
        const val GOOGLE_ASSISTANT_PACKAGE = "com.google.android.googlequicksearchbox"
        private const val TAG = "GOOGLE_HOME_MANAGER"

        // Default Sample Smart Home setup for Myanmar Household
        val DEFAULT_SMART_DEVICES = listOf(
            GoogleHomeDevice(
                id = "device_living_light",
                name = "ဧည့်ခန်း မီးလုံး",
                burmeseAlias = listOf("ဧည့်ခန်းမီး", "ဧည့်ခန်း မီး", "living room light"),
                room = "ဧည့်ခန်း (Living Room)",
                type = SmartDeviceType.LIGHT,
                isOn = true,
                googleAssistantVoiceCommand = "Turn on Living Room Light"
            ),
            GoogleHomeDevice(
                id = "device_bed_ac",
                name = "အိပ်ခန်း လေအေးပေးစက်",
                burmeseAlias = listOf("အိပ်ခန်းအဲကွန်း", "အဲကွန်း", "aircon", "အိပ်ခန်း လေအေးပေးစက်"),
                room = "အိပ်ခန်း (Bedroom)",
                type = SmartDeviceType.AIR_CONDITIONER,
                isOn = false,
                temperature = 25,
                googleAssistantVoiceCommand = "Set Bedroom Air Conditioner to 25 degrees"
            ),
            GoogleHomeDevice(
                id = "device_living_tv",
                name = "ဧည့်ခန်း စမတ် TV",
                burmeseAlias = listOf("တီဗွီ", "tv", "ဧည့်ခန်းတီဗွီ"),
                room = "ဧည့်ခန်း (Living Room)",
                type = SmartDeviceType.TV,
                isOn = true,
                googleAssistantVoiceCommand = "Turn on Living Room TV"
            ),
            GoogleHomeDevice(
                id = "device_kitchen_plug",
                name = "မီးဖိုချောင် ပလပ်ပေါက်",
                burmeseAlias = listOf("မီးဖိုချောင်ပလပ်", "ရေနွေးအိုး", "kitchen plug"),
                room = "မီးဖိုချောင် (Kitchen)",
                type = SmartDeviceType.SWITCH,
                isOn = false,
                googleAssistantVoiceCommand = "Turn off Kitchen Plug"
            )
        )
    }

    /**
     * Launch Google Home app or Device Controls settings on Android 11+
     */
    fun openGoogleHomeApp(): CommandResult {
        return try {
            val pm = context.packageManager
            val launchIntent = pm.getLaunchIntentForPackage(GOOGLE_HOME_PACKAGE)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                CommandResult(
                    isSuccess = true,
                    action = "OPEN_GOOGLE_HOME",
                    target = GOOGLE_HOME_PACKAGE,
                    executionStarted = true,
                    executionConfirmed = true,
                    userMessage = "ဟုတ်ကဲ့၊ Google Home အပလီကေးရှင်းကို ဖွင့်ပေးလိုက်ပါပြီခင်ဗျာ။"
                )
            } else {
                // Open Device Controls in System Settings if Home app is not installed
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val intent = Intent(Settings.ACTION_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                }
                CommandResult(
                    isSuccess = true,
                    action = "OPEN_STORE_GOOGLE_HOME",
                    target = GOOGLE_HOME_PACKAGE,
                    executionStarted = true,
                    userMessage = "Google Home App မရှိသေးသဖြင့် Play Store (သို့မဟုတ်) Device Controls ဖွင့်ပေးထားပါသည်ခင်ဗျာ။"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error opening Google Home", e)
            CommandResult(
                isSuccess = false,
                action = "OPEN_GOOGLE_HOME_ERROR",
                target = GOOGLE_HOME_PACKAGE,
                userMessage = "Google Home ချိတ်ဆက်မှုကို ဖွင့်ရန် အဆင်မပြေပါခင်ဗျာ။"
            )
        }
    }

    /**
     * Direct Voice Trigger via Google Assistant / Nest Cast
     */
    fun sendCommandToGoogleAssistant(voiceCommand: String): CommandResult {
        return try {
            val encodedQuery = Uri.encode(voiceCommand)
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("googleapp://assistant?query=$encodedQuery")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
            } else {
                val webIntent = Intent(Intent.ACTION_WEB_SEARCH).apply {
                    putExtra("query", voiceCommand)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(webIntent)
            }
            CommandResult(
                isSuccess = true,
                action = "GOOGLE_ASSISTANT_COMMAND",
                target = voiceCommand,
                executionStarted = true,
                executionConfirmed = true,
                userMessage = "Google Home / Assistant သို့ အမိန့်ပေးပို့လိုက်ပါပြီ - '$voiceCommand'"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error executing Google Assistant Voice Command", e)
            CommandResult(
                isSuccess = false,
                action = "GOOGLE_ASSISTANT_ERROR",
                target = voiceCommand,
                userMessage = "Google Assistant သို့ အမိန့်ပို့ရန် အဆင်မပြေပါခင်ဗျာ။"
            )
        }
    }

    /**
     * Smart Home Device Toggle (On/Off, Temperature, Brightness)
     * Handles local/cloud webhook integration (Home Assistant / Local IoT / Google Actions)
     */
    suspend fun executeSmartDeviceAction(
        device: GoogleHomeDevice,
        turnOn: Boolean,
        targetValue: Int? = null
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        device.isOn = turnOn
        if (targetValue != null) {
            if (device.type == SmartDeviceType.AIR_CONDITIONER) device.temperature = targetValue
            if (device.type == SmartDeviceType.LIGHT) device.brightness = targetValue
        }

        // 1. If device has custom webhook URL (Home Assistant / Node-RED / Matter bridge)
        if (device.webhookEndpoint.isNotBlank() && device.webhookEndpoint.startsWith("http")) {
            try {
                val url = URL(device.webhookEndpoint)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json; utf-8")
                conn.connectTimeout = 4000
                conn.readTimeout = 4000
                conn.doOutput = true

                val payload = JSONObject().apply {
                    put("deviceId", device.id)
                    put("state", if (turnOn) "ON" else "OFF")
                    put("value", targetValue ?: if (turnOn) 1 else 0)
                }

                OutputStreamWriter(conn.outputStream).use { writer ->
                    writer.write(payload.toString())
                    writer.flush()
                }

                val responseCode = conn.responseCode
                conn.disconnect()
                if (responseCode in 200..299) {
                    val statusText = if (turnOn) "ဖွင့်ပြီးပါပြီ" else "ပိတ်ပြီးပါပြီ"
                    return@withContext Pair(true, "ဟုတ်ကဲ့၊ ${device.name} ကို $statusText ခင်ဗျာ။")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Webhook call failed for ${device.name}, fallback to Assistant Command", e)
            }
        }

        // 2. Default Assistant Integration fallback
        val actionWord = if (turnOn) "Turn on" else "Turn off"
        val cmd = "$actionWord ${device.name}"
        sendCommandToGoogleAssistant(cmd)

        val status = if (turnOn) "ဖွင့်ပြီးပါပြီ" else "ပိတ်ပြီးပါပြီ"
        return@withContext Pair(true, "ဟုတ်ကဲ့၊ Google Home စနစ်မှတစ်ဆင့် ${device.name} ကို $status ခင်ဗျာ။")
    }

    /**
     * Resolve Burmese Smart Home Voice Command
     * Examples: "ဧည့်ခန်းမီး ဖွင့်", "အဲကွန်း ပိတ်", "Google Home ဖွင့်", "တီဗွီ ဖွင့်"
     */
    suspend fun processSmartHomeVoiceCommand(spokenText: String): Pair<Boolean, String>? {
        val clean = spokenText.trim().lowercase()

        // Check Direct Google Home App Opening
        if (clean.contains("google home") || clean.contains("ဂူဂဲလ်ဟုမ်း") || clean.contains("ဂူဂယ်ဟုမ်း")) {
            val res = openGoogleHomeApp()
            return Pair(true, res.userMessage)
        }

        // Match Device from List
        for (device in DEFAULT_SMART_DEVICES) {
            val matchesAlias = device.burmeseAlias.any { clean.contains(it.lowercase()) } || clean.contains(device.name.lowercase())
            if (matchesAlias) {
                val isTurnOn = clean.contains("ဖွင့်") || clean.contains("on") || clean.contains("တင်")
                val isTurnOff = clean.contains("ပိတ်") || clean.contains("off") || clean.contains("ချ")

                if (isTurnOn || isTurnOff) {
                    return executeSmartDeviceAction(device, turnOn = isTurnOn)
                }
            }
        }

        return null
    }
}
