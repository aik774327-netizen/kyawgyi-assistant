package com.example.actions

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.example.utils.KyawGyiPermissionManager
import java.util.Locale

enum class SystemSettingTarget {
    VOLUME,
    BRIGHTNESS,
    UNKNOWN
}

enum class AdjustmentType {
    SET_ABSOLUTE_PERCENT,  // e.g. 50%
    INCREASE_DELTA,        // e.g. +20% or volume up
    DECREASE_DELTA,        // e.g. -20% or volume down
    MUTE,                  // 0%
    MAX                    // 100%
}

data class ParsedSystemAction(
    val target: SystemSettingTarget,
    val adjustmentType: AdjustmentType,
    val valuePercent: Int? = null,
    val rawMatchedPhrase: String
)

data class SystemActionResult(
    val isSuccess: Boolean,
    val messageMM: String,
    val currentLevelPercent: Int,
    val target: SystemSettingTarget,
    val requiresPermission: Boolean = false
)

/**
 * Generic System Action Handler for KYAW GYI AI
 * Parses and executes parameterized NUMBER + UNIT + VERB system commands
 * (Volume, Screen Brightness, Mute, Max) dynamically from Myanmar voice queries.
 */
class GenericSystemActionHandler(private val context: Context) {

    private val TAG = "SystemActionHandler"
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    companion object {
        private val MYANMAR_DIGIT_MAP = mapOf(
            '၀' to '0', '၁' to '1', '၂' to '2', '၃' to '3', '၄' to '4',
            '၅' to '5', '၆' to '6', '၇' to '7', '၈' to '8', '၉' to '9'
        )

        private val MYANMAR_WORD_NUMBER_MAP = listOf(
            "တစ်ရာ" to 100, "ကိုးဆယ်" to 90, "ရှစ်ဆယ်" to 80, "ခုနစ်ဆယ်" to 70,
            "ခြောက်ဆယ်" to 60, "ငါးဆယ်" to 50, "လေးဆယ်" to 40, "သုံးဆယ်" to 30,
            "နှစ်ဆယ်" to 20, "ဆယ်" to 10, "ကိုး" to 9, "ရှစ်" to 8,
            "ခုနစ်" to 7, "ခုန်နစ်" to 7, "ခြောက်" to 6, "ငါး" to 5,
            "လေး" to 4, "သုံး" to 3, "နှစ်" to 2, "တစ်" to 1, "သုည" to 0
        )
    }

    /**
     * Converts mixed Myanmar/English numbers and phrases into an integer percentage (0-100).
     */
    fun extractNumericValue(text: String): Int? {
        val converted = text.map { MYANMAR_DIGIT_MAP[it] ?: it }.joinToString("")
        
        // Match standard digits 0-100
        val digitMatch = "\\b(\\d{1,3})\\b".toRegex().find(converted)
        if (digitMatch != null) {
            val num = digitMatch.groupValues[1].toIntOrNull()
            if (num != null) return num.coerceIn(0, 100)
        }

        // Special word concepts
        if (text.contains("တစ်ဝက်") || text.contains("တဝက်") || text.contains("half")) return 50
        if (text.contains("အပြည့်") || text.contains("အကုန်") || text.contains("max") || text.contains("full")) return 100
        if (text.contains("သုည") || text.contains("zero")) return 0

        // Match word numbers (sorted by length descending to prevent sub-string prefix collisions)
        for ((word, value) in MYANMAR_WORD_NUMBER_MAP) {
            if (text.contains(word)) {
                return value.coerceIn(0, 100)
            }
        }

        return null
    }

    /**
     * Parse parameterized voice command into a structured system action.
     */
    fun parseCommand(rawText: String): ParsedSystemAction? {
        val lower = rawText.lowercase(Locale.ROOT)

        val isVolume = lower.contains("အသံ") || lower.contains("volume") || lower.contains("sound")
        val isBrightness = lower.contains("အလင်း") || lower.contains("brightness") || lower.contains("screen") || lower.contains("မျက်နှာပြင်")

        if (!isVolume && !isBrightness) return null

        val target = if (isVolume) SystemSettingTarget.VOLUME else SystemSettingTarget.BRIGHTNESS
        val extractedNumber = extractNumericValue(lower)

        // 1. MUTE / ZERO
        if (lower.contains("ပိတ်") || lower.contains("တိတ်") || lower.contains("mute") || lower.contains("သုည") || extractedNumber == 0) {
            return ParsedSystemAction(target, AdjustmentType.MUTE, 0, rawText)
        }

        // 2. MAX / FULL
        if (lower.contains("အပြည့်") || lower.contains("max") || lower.contains("အကုန်တင်") || extractedNumber == 100) {
            return ParsedSystemAction(target, AdjustmentType.MAX, 100, rawText)
        }

        // 3. EXPLICIT VALUE SETTING (e.g., 50%, 70%, ၃၀ ရာခိုင်နှုန်း)
        if (extractedNumber != null) {
            return ParsedSystemAction(target, AdjustmentType.SET_ABSOLUTE_PERCENT, extractedNumber, rawText)
        }

        // 4. RELATIVE INCREASE
        if (lower.contains("မြှင့်") || lower.contains("ကျယ်") || lower.contains("တင်") || lower.contains("တိုး") || lower.contains("up") || lower.contains("increase") || lower.contains("louder")) {
            return ParsedSystemAction(target, AdjustmentType.INCREASE_DELTA, valuePercent = 15, rawMatchedPhrase = rawText)
        }

        // 5. RELATIVE DECREASE
        if (lower.contains("လျှော့") || lower.contains("ချ") || lower.contains("down") || lower.contains("decrease") || lower.contains("lower") || lower.contains("quiet")) {
            return ParsedSystemAction(target, AdjustmentType.DECREASE_DELTA, valuePercent = 15, rawMatchedPhrase = rawText)
        }

        return null
    }

    /**
     * Execute parameterized system action safely.
     */
    fun executeAction(action: ParsedSystemAction): SystemActionResult {
        return when (action.target) {
            SystemSettingTarget.VOLUME -> executeVolumeAction(action)
            SystemSettingTarget.BRIGHTNESS -> executeBrightnessAction(action)
            SystemSettingTarget.UNKNOWN -> SystemActionResult(false, "စနစ် လုပ်ဆောင်ချက်ကို မသိရှိပါ", 0, SystemSettingTarget.UNKNOWN)
        }
    }

    private fun executeVolumeAction(action: ParsedSystemAction): SystemActionResult {
        return try {
            val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            val currentPct = if (maxVol > 0) ((currentVol.toFloat() / maxVol) * 100).toInt() else 0

            val targetVol = when (action.adjustmentType) {
                AdjustmentType.SET_ABSOLUTE_PERCENT -> {
                    val pct = action.valuePercent ?: 50
                    ((pct.toFloat() / 100.0f) * maxVol).toInt().coerceIn(0, maxVol)
                }
                AdjustmentType.INCREASE_DELTA -> {
                    val delta = action.valuePercent ?: 15
                    val newPct = (currentPct + delta).coerceIn(0, 100)
                    ((newPct.toFloat() / 100.0f) * maxVol).toInt().coerceIn(0, maxVol)
                }
                AdjustmentType.DECREASE_DELTA -> {
                    val delta = action.valuePercent ?: 15
                    val newPct = (currentPct - delta).coerceIn(0, 100)
                    ((newPct.toFloat() / 100.0f) * maxVol).toInt().coerceIn(0, maxVol)
                }
                AdjustmentType.MUTE -> 0
                AdjustmentType.MAX -> maxVol
            }

            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, AudioManager.FLAG_SHOW_UI)
            val finalPct = if (maxVol > 0) ((targetVol.toFloat() / maxVol) * 100).toInt() else 0
            
            val msg = when (action.adjustmentType) {
                AdjustmentType.MUTE -> "အသံ ပိတ်ထားလိုက်ပါပြီခင်ဗျာ။"
                AdjustmentType.MAX -> "အသံကို အမြင့်ဆုံး (၁၀၀%) သို့ တင်ပေးလိုက်ပါပြီ။"
                else -> "အသံ အတိုးအကျယ်ကို $finalPct% သို့ ချိန်ညှိပေးလိုက်ပါပြီ။"
            }

            SystemActionResult(true, msg, finalPct, SystemSettingTarget.VOLUME)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to adjust volume", e)
            SystemActionResult(false, "အသံ ချိန်ညှိ၍ မရပါခင်ဗျာ: ${e.localizedMessage}", 0, SystemSettingTarget.VOLUME)
        }
    }

    private fun executeBrightnessAction(action: ParsedSystemAction): SystemActionResult {
        return try {
            val canWrite = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Settings.System.canWrite(context)
            } else true

            if (!canWrite) {
                KyawGyiPermissionManager.openWriteSystemSettings(context)
                return SystemActionResult(
                    isSuccess = false,
                    messageMM = "မျက်နှာပြင် အလင်းအမှောင် ချိန်ညှိရန် စနစ် ဆက်တင် ပြင်ဆင်ခွင့် (WRITE_SETTINGS) ဖွင့်ပေးရန် လိုအပ်ပါသည်။ ဆက်တင်ကို ဖွင့်ပေးထားပါသည်ခင်ဗျာ။",
                    currentLevelPercent = 0,
                    target = SystemSettingTarget.BRIGHTNESS,
                    requiresPermission = true
                )
            }

            val currentRaw = Settings.System.getInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                128
            )
            val currentPct = ((currentRaw.toFloat() / 255.0f) * 100).toInt()

            val targetPct = when (action.adjustmentType) {
                AdjustmentType.SET_ABSOLUTE_PERCENT -> (action.valuePercent ?: 50).coerceIn(5, 100)
                AdjustmentType.INCREASE_DELTA -> (currentPct + (action.valuePercent ?: 20)).coerceIn(5, 100)
                AdjustmentType.DECREASE_DELTA -> (currentPct - (action.valuePercent ?: 20)).coerceIn(5, 100)
                AdjustmentType.MUTE -> 5
                AdjustmentType.MAX -> 100
            }

            val targetRaw = ((targetPct.toFloat() / 100.0f) * 255).toInt().coerceIn(5, 255)
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                targetRaw
            )

            SystemActionResult(
                isSuccess = true,
                messageMM = "မျက်နှာပြင် အလင်းအမှောင်ကို $targetPct% သို့ ချိန်ညှိပေးလိုက်ပါပြီ။",
                currentLevelPercent = targetPct,
                target = SystemSettingTarget.BRIGHTNESS
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to adjust brightness", e)
            SystemActionResult(false, "မျက်နှာပြင် အလင်းအမှောင် ချိန်ညှိ၍ မရပါခင်ဗျာ: ${e.localizedMessage}", 0, SystemSettingTarget.BRIGHTNESS)
        }
    }
}
