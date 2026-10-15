package com.example.realworld

import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.BatteryManager
import android.provider.Settings
import com.example.database.ErrorCategory
import com.example.service.MyanmarTtsEngine

sealed class ActionResult {
  data class Success(val message: String) : ActionResult()
  data class Failure(
    val category: ErrorCategory,
    val reasonDetail: String,
    val alternativeAdvice: String,
    val failedMethod: String = ""
  ) : ActionResult()
}

class RealDeviceActionExecutor(private val context: Context) {

  private val myanmarTts = MyanmarTtsEngine(context)
  private val appLifecycleManager = AppLifecycleManager(context)

  fun speakText(text: String) {
    myanmarTts.speak(text)
  }

  fun stopSpeech() {
    myanmarTts.stop()
  }

  /**
   * Opens the system dialer with the target phone number using Intent.ACTION_DIAL.
   * Note: Uses ACTION_DIAL (safer — opens phone dialer with number prefilled for explicit
   * user review and confirmation), NOT ACTION_CALL (which would place calls directly).
   */
  fun dialPhone(targetName: String, rawPhoneNumber: String?): ActionResult {
    val cleanNumber = rawPhoneNumber?.trim()?.filter { it.isDigit() || it == '+' }
    if (cleanNumber.isNullOrEmpty()) {
      return ActionResult.Failure(
        category = ErrorCategory.REAL_WORLD_MISMATCH,
        reasonDetail = "\"$targetName\" အတွက် မှန်ကန်သော ဖုန်းနံပါတ် သတ်မှတ်ထားခြင်း မရှိပါ (ဖုန်းစာအုပ်တွင် မတွေ့ပါ)",
        alternativeAdvice = "ဆက်တင် (Settings) > ဖုန်းစာရင်း တွင် နံပါတ် အမှန်ကို ထည့်သွင်းပေးပါ"
      )
    }

    return try {
      val intent = Intent(Intent.ACTION_DIAL).apply {
        data = Uri.parse("tel:$cleanNumber")
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
      }
      context.startActivity(intent)
      ActionResult.Success("$targetName ($cleanNumber) ဆီသို့ ဖုန်းခေါ်ဆိုရန် ဖုန်းခေါ်စနစ် ဖွင့်လှစ်ပြီးပါပြီ")
    } catch (e: ActivityNotFoundException) {
      ActionResult.Failure(
        category = ErrorCategory.REAL_WORLD_MISMATCH,
        reasonDetail = "ဖုန်းခေါ်ဆိုနိုင်သော Dial App စနစ် စက်ထဲတွင် မတွေ့ရှိပါ",
        alternativeAdvice = "ဖုန်းခေါ်စနစ် အစား SMS သို့မဟုတ် Telegram ဖြင့် ဆက်သွယ်ပါ"
      )
    } catch (e: Exception) {
      ActionResult.Failure(
        category = ErrorCategory.TECHNICAL,
        reasonDetail = "ဖုန်းခေါ်ဆိုမှု စနစ် အမှားဖြစ်ပွားသည်: ${e.localizedMessage ?: "Unknown"}",
        alternativeAdvice = "စက်၏ ခွင့်ပြုချက် (Permissions) များကို စစ်ဆေးပါ"
      )
    }
  }

  fun openApp(appName: String, fallbackUrl: String? = null): ActionResult {
    return appLifecycleManager.openApp(appName)
  }

  fun closeApp(appName: String): ActionResult {
    return appLifecycleManager.closeApp(appName)
  }

  fun returnToHome(customMessage: String? = null): ActionResult {
    return appLifecycleManager.returnToHomeScreen(customMessage)
  }

  fun openSystemSetting(settingName: String): ActionResult {
    return try {
      val intent = when (settingName.lowercase()) {
        "wifi" -> Intent(Settings.ACTION_WIFI_SETTINGS)
        "bluetooth" -> Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
        else -> Intent(Settings.ACTION_SETTINGS)
      }.apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
      }
      context.startActivity(intent)
      ActionResult.Success("$settingName ဆက်တင်ကို ဖွင့်ပေးလိုက်ပါပြီ ခင်ဗျာ။")
    } catch (e: Exception) {
      ActionResult.Failure(
        category = ErrorCategory.TECHNICAL,
        reasonDetail = "Android System Settings ဖွင့်မရပါ: ${e.localizedMessage ?: "Unknown"}",
        alternativeAdvice = "ဖုန်း၏ Notification Bar ပေါ်ရှိ Quick Settings ကို အသုံးပြုပါ"
      )
    }
  }

  fun searchWeb(query: String): ActionResult {
    return try {
      val intent = Intent(Intent.ACTION_WEB_SEARCH).apply {
        putExtra(SearchManager.QUERY, query)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
      }
      context.startActivity(intent)
      ActionResult.Success("\"$query\" ကို Google တွင် ရှာဖွေပေးနေပါသည် ခင်ဗျာ။")
    } catch (e: Exception) {
      // fallback to browser url
      try {
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=${Uri.encode(query)}")).apply {
          flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(browserIntent)
        ActionResult.Success("\"$query\" ကို Browser တွင် ရှာဖွေပေးနေပါသည် ခင်ဗျာ။")
      } catch (e2: Exception) {
        ActionResult.Failure(
          category = ErrorCategory.TECHNICAL,
          reasonDetail = "ရှာဖွေမှုစနစ် ဖွင့်မရပါ: ${e2.localizedMessage ?: "Unknown"}",
          alternativeAdvice = "အင်တာနက် ချိတ်ဆက်မှု ရှိမရှိ စစ်ဆေးပါ"
        )
      }
    }
  }

  fun getRealDeviceBatteryPercentage(): Int {
    return try {
      val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
      bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
    } catch (_: Exception) {
      -1
    }
  }
}
