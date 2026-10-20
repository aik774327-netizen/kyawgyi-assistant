package com.example.voice.hardware

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import android.util.Log

/**
 * Clean System Action Manager mapping Gemini & Local Function Calls directly to:
 * 1. CameraManager (Flashlight toggle)
 * 2. AudioManager (Volume, Mute, Ringer modes)
 * 3. AccessibilityService / System Navigation Actions (Global Home, Back, Recent Apps, Lock Screen)
 * 4. System Settings (Wi-Fi, Bluetooth, Screen Brightness)
 */
class SystemActionManager(private val context: Context) {

  companion object {
    private const val TAG = "SystemActionManager"

    // Reference to optional KyawGyiAccessibilityService when user enables it in Android Accessibility
    @Volatile
    var activeAccessibilityService: AccessibilityService? = null
  }

  // =========================================================================
  // 1. CAMERA / FLASHLIGHT CONTROL (CameraManager)
  // =========================================================================

  fun setFlashlight(enable: Boolean): Pair<Boolean, String> = toggleFlashlight(context, enable)

  fun toggleFlashlight(context: Context, enable: Boolean): Pair<Boolean, String> {
    val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
      ?: return false to "CameraManager စနစ် ရှာမတွေ့ပါ"

    return try {
      val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
        try {
          val chars = cameraManager.getCameraCharacteristics(id)
          val hasFlash = chars.get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false
          val lensFacing = chars.get(android.hardware.camera2.CameraCharacteristics.LENS_FACING)
          hasFlash && (lensFacing == android.hardware.camera2.CameraCharacteristics.LENS_FACING_BACK)
        } catch (_: Exception) {
          false
        }
      } ?: cameraManager.cameraIdList.firstOrNull()

      if (cameraId != null) {
        cameraManager.setTorchMode(cameraId, enable)
        val msg = if (enable) "လက်နှိပ်ဓာတ်မီး (Flashlight) ဖွင့်ပေးလိုက်ပါပြီ" else "လက်နှိပ်ဓာတ်မီး (Flashlight) ပိတ်ပေးလိုက်ပါပြီ"
        true to msg
      } else {
        false to "ဤဖုန်းတွင် Flashlight မပါရှိပါ"
      }
    } catch (e: CameraAccessException) {
      Log.e(TAG, "Failed to toggle flashlight", e)
      false to "ဓာတ်မီး ထိန်းချုပ်၍ မရပါ (ကင်မရာ အလုပ်ရှုပ်နေသည် သို့မဟုတ် ခွင့်ပြုချက် လိုအပ်ပါသည်)"
    } catch (e: Exception) {
      Log.e(TAG, "Unexpected error toggling flashlight", e)
      false to "Flashlight အမှား: ${e.localizedMessage ?: "Unknown"}"
    }
  }

  // =========================================================================
  // 2. AUDIO & VOLUME CONTROL (AudioManager)
  // =========================================================================

  fun adjustVolume(increase: Boolean): Pair<Boolean, String> =
    controlAudio(context, if (increase) "UP" else "DOWN")

  fun muteVolume(): Pair<Boolean, String> =
    controlAudio(context, "MUTE")

  fun controlAudio(
    context: Context,
    action: String,
    streamType: Int = AudioManager.STREAM_MUSIC,
    targetPercent: Int? = null
  ): Pair<Boolean, String> {
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
      ?: return false to "AudioManager စနစ် မတွေ့ရှိပါ"

    return try {
      val maxVol = audioManager.getStreamMaxVolume(streamType)
      val currentVol = audioManager.getStreamVolume(streamType)

      when (action.uppercase()) {
        "UP", "RAISE" -> {
          audioManager.adjustStreamVolume(streamType, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
          val newVol = audioManager.getStreamVolume(streamType)
          true to "အသံ အရှိန်မြှင့်ပေးလိုက်ပါပြီ (လက်ရှိ: $newVol/$maxVol)"
        }
        "DOWN", "LOWER" -> {
          audioManager.adjustStreamVolume(streamType, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
          val newVol = audioManager.getStreamVolume(streamType)
          true to "အသံ လျှော့ချပေးလိုက်ပါပြီ (လက်ရှိ: $newVol/$maxVol)"
        }
        "MUTE" -> {
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            audioManager.adjustStreamVolume(streamType, AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI)
          } else {
            @Suppress("DEPRECATION")
            audioManager.setStreamMute(streamType, true)
          }
          true to "အသံ ပိတ် (Mute) ပေးလိုက်ပါပြီ"
        }
        "UNMUTE" -> {
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            audioManager.adjustStreamVolume(streamType, AudioManager.ADJUST_UNMUTE, AudioManager.FLAG_SHOW_UI)
          } else {
            @Suppress("DEPRECATION")
            audioManager.setStreamMute(streamType, false)
          }
          true to "အသံ ပြန်ဖွင့်ပေးလိုက်ပါပြီ"
        }
        "MAX" -> {
          audioManager.setStreamVolume(streamType, maxVol, AudioManager.FLAG_SHOW_UI)
          true to "အသံ အမြင့်ဆုံး ($maxVol) သို့ တင်ပေးလိုက်ပါပြီ"
        }
        "SET_PERCENT" -> {
          if (targetPercent == null || targetPercent !in 0..100) {
            return false to "အသံပမာဏ (Volume) သည် ၀ မှ ၁၀၀ ကြားသာ ဖြစ်ရပါမည် (လက်ရှိပေးပို့ချက်: $targetPercent%)။ သတ်မှတ်ဘောင် ကျော်လွန်သဖြင့် ငြင်းပယ်ပါသည်"
          }
          val pct = targetPercent
          val targetVal = (maxVol * (pct / 100f)).toInt().coerceIn(0, maxVol)
          audioManager.setStreamVolume(streamType, targetVal, AudioManager.FLAG_SHOW_UI)
          true to "အသံကို $pct% ($targetVal/$maxVol) သို့ သတ်မှတ်ပေးလိုက်ပါပြီ"
        }
        else -> false to "မသိသော Audio အမိန့်: $action"
      }
    } catch (e: Exception) {
      Log.e(TAG, "Audio control failure", e)
      false to "အသံချိန်ညှိမှု မအောင်မြင်ပါ: ${e.localizedMessage ?: "Unknown"}"
    }
  }

  // =========================================================================
  // SCREEN BRIGHTNESS CONTROL (0-100 Argument Validation)
  // =========================================================================

  fun setScreenBrightness(brightnessPercent: Int): Pair<Boolean, String> {
    if (brightnessPercent !in 0..100) {
      return false to "မျက်နှာပြင် အလင်းရောင် (Brightness) သည် ၀ မှ ၁၀၀ ကြားသာ ဖြစ်ရပါမည် (လက်ရှိပေးပို့ချက်: $brightnessPercent%)။ သတ်မှတ်ဘောင် ကျော်လွန်သဖြင့် ငြင်းပယ်ပါသည်"
    }
    // Audit A5: previously this ignored the putInt result and the catch block
    // returned true with a success-flavored message even when the write threw
    // (e.g. WRITE_SETTINGS not granted) — success was reported with no effect.
    // Also, on auto-brightness devices the value is inert until the mode is
    // switched to manual, which the old code never did.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.System.canWrite(context)) {
      return false to "မျက်နှာပြင် အလင်းရောင် ချိန်ညှိခွင့် (WRITE_SETTINGS) မပေးရသေးပါ — စနစ် Settings တွင် 'စနစ်ဆက်တင်များ ပြင်ဆင်ခွင့်' ကို ခွင့်ပြုပြီးမှ ထပ်ကြိုးစားပါ"
    }
    return try {
      val resolver = context.contentResolver
      Settings.System.putInt(
        resolver,
        Settings.System.SCREEN_BRIGHTNESS_MODE,
        Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
      )
      val targetVal = (brightnessPercent * 255 / 100).coerceIn(0, 255)
      val written = Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, targetVal)
      if (written) {
        true to "မျက်နှာပြင် အလင်းရောင်ကို $brightnessPercent% သို့ ချိန်ညှိပြီးပါပြီ"
      } else {
        false to "မျက်နှာပြင် အလင်းရောင် ချိန်ညှိ၍ မရပါ (စနစ်က ရေးသားမှုကို ငြင်းပယ်လိုက်ပါသည်)"
      }
    } catch (e: Exception) {
      Log.w(TAG, "Cannot set system brightness", e)
      false to "မျက်နှာပြင် အလင်းရောင် ချိန်ညှိ၍ မရပါ: ${e.localizedMessage ?: "အမည်မသိ အမှား"}"
    }
  }

  // =========================================================================
  // 3. ACCESSIBILITY & GLOBAL SYSTEM NAVIGATION
  // =========================================================================

  fun performGlobalHome(): Pair<Boolean, String> = performGlobalNavigation(context, "HOME")

  fun performGlobalBack(): Pair<Boolean, String> = performGlobalNavigation(context, "BACK")

  fun performGlobalNavigation(context: Context, action: String): Pair<Boolean, String> {
    val service = activeAccessibilityService
    if (service != null) {
      val globalAction = when (action.uppercase()) {
        "HOME" -> AccessibilityService.GLOBAL_ACTION_HOME
        "BACK" -> AccessibilityService.GLOBAL_ACTION_BACK
        "RECENTS", "RECENT_APPS" -> AccessibilityService.GLOBAL_ACTION_RECENTS
        "NOTIFICATIONS" -> AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS
        "QUICK_SETTINGS" -> AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS
        "LOCK_SCREEN" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN else -1
        else -> -1
      }

      if (globalAction != -1) {
        val success = service.performGlobalAction(globalAction)
        return if (success) {
          true to "စနစ်လုပ်ဆောင်ချက် ($action) ကို Accessibility ဖြင့် ဆောင်ရွက်ပြီးပါပြီ"
        } else {
          false to "Accessibility လုပ်ဆောင်ချက် မအောင်မြင်ပါ"
        }
      }
    }

    // Fallback using Standard Android System Intents
    return when (action.uppercase()) {
      "HOME" -> {
        try {
          val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
          }
          context.startActivity(homeIntent)
          true to "ပင်မစာမျက်နှာ (Home) သို့ ပြန်ရောက်ပါပြီ"
        } catch (e: Exception) {
          false to "Home သို့ ပြန်မရပါ: ${e.localizedMessage}"
        }
      }
      "SETTINGS" -> {
        try {
          val intent = Intent(Settings.ACTION_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
          }
          context.startActivity(intent)
          true to "Android ဖုန်း ဆက်တင်ကို ဖွင့်ပေးလိုက်ပါပြီ"
        } catch (e: Exception) {
          false to "ဆက်တင်ဖွင့်မရပါ: ${e.localizedMessage}"
        }
      }
      else -> {
        false to "ဤလုပ်ဆောင်ချက် ($action) အတွက် ကျော်ကြီး Accessibility Service ခွင့်ပြုချက် လိုအပ်ပါသည်"
      }
    }
  }
}
