package com.example.realworld

import android.annotation.SuppressLint

import android.app.ActivityManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.provider.Settings
import com.example.database.ErrorCategory

class AppLifecycleManager(private val context: Context) {

  data class AppPackageInfo(
    val displayName: String,
    val packageName: String,
    val fallbackUrl: String? = null
  )

  private val knownAppDirectory = mapOf(
    "viber" to AppPackageInfo("Viber", "com.viber.voip", "https://www.viber.com"),
    "ဗိုက်ဘာ" to AppPackageInfo("Viber", "com.viber.voip", "https://www.viber.com"),
    "ဗိုင်းဘာ" to AppPackageInfo("Viber", "com.viber.voip", "https://www.viber.com"),
    "telegram" to AppPackageInfo("Telegram", "org.telegram.messenger", "https://t.me"),
    "တယ်လီဂရမ်" to AppPackageInfo("Telegram", "org.telegram.messenger", "https://t.me"),
    "facebook" to AppPackageInfo("Facebook", "com.facebook.katana", "https://www.facebook.com"),
    "ဖေ့စ်ဘုတ်" to AppPackageInfo("Facebook", "com.facebook.katana", "https://www.facebook.com"),
    "ဖေ့ဘုတ်" to AppPackageInfo("Facebook", "com.facebook.katana", "https://www.facebook.com"),
    "youtube" to AppPackageInfo("YouTube", "com.google.android.youtube", "https://www.youtube.com"),
    "ယူကျု" to AppPackageInfo("YouTube", "com.google.android.youtube", "https://www.youtube.com"),
    "ယူကျူ" to AppPackageInfo("YouTube", "com.google.android.youtube", "https://www.youtube.com"),
    "kpay" to AppPackageInfo("KBZPay", "com.kbzbank.kpay"),
    "kbzpay" to AppPackageInfo("KBZPay", "com.kbzbank.kpay"),
    "ကေပေး" to AppPackageInfo("KBZPay", "com.kbzbank.kpay"),
    "wavepay" to AppPackageInfo("WavePay", "com.wavemoney.wavepay"),
    "ဝေ့ပေး" to AppPackageInfo("WavePay", "com.wavemoney.wavepay"),
    "chrome" to AppPackageInfo("Google Chrome", "com.android.chrome", "https://www.google.com"),
    "ခရုမ်း" to AppPackageInfo("Google Chrome", "com.android.chrome", "https://www.google.com"),
    "messenger" to AppPackageInfo("Messenger", "com.facebook.orca"),
    "မက်ဆင်ဂျာ" to AppPackageInfo("Messenger", "com.facebook.orca"),
    "camera" to AppPackageInfo("Camera", "com.android.camera"),
    "ကင်မရာ" to AppPackageInfo("Camera", "com.android.camera"),
    "settings" to AppPackageInfo("Settings", "com.android.settings"),
    "ဆက်တင်" to AppPackageInfo("Settings", "com.android.settings")
  )

  /**
   * Open an application by natural name
   */
  fun openApp(targetApp: String): ActionResult {
    val cleanKey = targetApp.lowercase().trim()
    val appInfo = knownAppDirectory[cleanKey] ?: knownAppDirectory.entries.firstOrNull {
      cleanKey.contains(it.key)
    }?.value

    if (cleanKey.contains("camera") || cleanKey.contains("ကင်မရာ")) {
      return openCamera()
    }

    if (appInfo == null) {
      // Try generic launch intent by name
      return tryLaunchBySearch(cleanKey)
    }

    val pm = context.packageManager
    val launchIntent = pm.getLaunchIntentForPackage(appInfo.packageName)

    return if (launchIntent != null) {
      launchIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
      context.startActivity(launchIntent)
      ActionResult.Success("${appInfo.displayName} အက်ပ်ကို အောင်မြင်စွာ ဖွင့်လှစ်ပေးလိုက်ပါပြီ ခင်ဗျာ။")
    } else {
      if (!appInfo.fallbackUrl.isNullOrEmpty()) {
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(appInfo.fallbackUrl)).apply {
          flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(browserIntent)
        ActionResult.Success("${appInfo.displayName} အက်ပ် စက်ထဲတွင် မရှိသေးသဖြင့် ဝက်ဘ်ဆိုဒ်ကို ဖွင့်လှစ်ပေးလိုက်ပါပြီ။")
      } else {
        ActionResult.Failure(
          category = ErrorCategory.REAL_WORLD_MISMATCH,
          reasonDetail = "${appInfo.displayName} (${appInfo.packageName}) အက်ပ်ကို ဖုန်းထဲတွင် ထည့်သွင်းထားခြင်း မရှိသေးပါ",
          alternativeAdvice = "Google Play Store မှ ${appInfo.displayName} ကို ဦးစွာ ဒေါင်းလုဒ်ရယူပါ"
        )
      }
    }
  }

  /**
   * Close an application by natural name
   * Uses multi-tier closing mechanism:
   * 1. Navigates back to Home screen to exit the target app interface
   * 2. Calls ActivityManager.killBackgroundProcesses
   * 3. For complete termination, provides direct deep link to Force-Stop
   */
  @SuppressLint("MissingPermission")
  fun closeApp(targetApp: String): ActionResult {
    val cleanKey = targetApp.lowercase().trim()

    // Special: "အက်ပ်အားလုံးပိတ်", "ပင်မမျက်နှာပြင်သို့သွားပါ", "close all"
    // Audit A6: this only navigates Home — it does not close any app, and the
    // message must not claim it did.
    if (cleanKey.contains("အားလုံး") || cleanKey.contains("all") || cleanKey.contains("home") || cleanKey.contains("ပင်မ")) {
      return returnToHomeScreen("ပင်မ Home မျက်နှာပြင်သို့ ပြန်လည်ရောက်ရှိပါပြီ ခင်ဗျာ။")
    }

    val appInfo = knownAppDirectory[cleanKey] ?: knownAppDirectory.entries.firstOrNull {
      cleanKey.contains(it.key)
    }?.value

    val targetName = appInfo?.displayName ?: targetApp
    val targetPackage = appInfo?.packageName

    try {
      if (targetPackage != null) {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        am?.killBackgroundProcesses(targetPackage)
      }

      // Return to home screen (the app view leaves the foreground)
      returnToHomeScreen()

      // Audit A6: killBackgroundProcesses cannot terminate a foreground app,
      // so the old message ("အက်ပ်ကို ပိတ်ပြီး") overstated what happened.
      // Report exactly what was done; Android gives no API to force-close
      // another app's foreground screen without root/device-owner.
      return ActionResult.Success("$targetName ၏ နောက်ကွယ်လုပ်ငန်းစဉ်များကို ရပ်တန့်ပြီး ပင်မ Home မျက်နှာပြင်သို့ ပြန်သွားပါပြီ။ (Android စနစ်အရ အခြားအက်ပ်၏ မျက်နှာပြင်ကို တိုက်ရိုက် အပြီးပိတ်ခွင့် မရှိပါ — အပြီးပိတ်လိုပါက Recent Apps မှ ပိတ်ပါ) ခင်ဗျာ။")
    } catch (e: Exception) {
      return ActionResult.Failure(
        category = ErrorCategory.TECHNICAL,
        reasonDetail = "$targetName အက်ပ်ကို ပိတ်ရာတွင် ချို့ယွင်းမှု ဖြစ်ပွားသည်: ${e.localizedMessage ?: "Unknown"}",
        alternativeAdvice = "မကြာသေးမီက ဖွင့်ထားသော အက်ပ်များ (Recent Apps Overview) မှ ပိတ်ပါ"
      )
    }
  }

  /**
   * Navigate immediately to the Android OS Home screen
   */
  fun returnToHomeScreen(customMessage: String? = null): ActionResult {
    return try {
      val homeIntent = Intent(Intent.ACTION_MAIN).apply {
        addCategory(Intent.CATEGORY_HOME)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
      }
      context.startActivity(homeIntent)
      ActionResult.Success(customMessage ?: "ပင်မ Home မျက်နှာပြင်သို့ ပြန်လည်ရောက်ရှိပါပြီ ခင်ဗျာ။")
    } catch (e: Exception) {
      ActionResult.Failure(
        category = ErrorCategory.TECHNICAL,
        reasonDetail = "ပင်မမျက်နှာပြင်သို့ သွားမရပါ: ${e.localizedMessage ?: "Unknown"}",
        alternativeAdvice = "ဖုန်း၏ Home ခလုတ်ကို နှိပ်ပါ"
      )
    }
  }

  private fun openCamera(): ActionResult {
    return try {
      val cameraIntent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
      }
      context.startActivity(cameraIntent)
      ActionResult.Success("ကင်မရာ (Camera) ကို ဖွင့်လှစ်ပေးလိုက်ပါပြီ ခင်ဗျာ။")
    } catch (_: Exception) {
      try {
        val pm = context.packageManager
        val camIntent = pm.getLaunchIntentForPackage("com.android.camera") ?: Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        camIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(camIntent)
        ActionResult.Success("ကင်မရာကို ဖွင့်ပေးလိုက်ပါပြီ ခင်ဗျာ။")
      } catch (e: Exception) {
        ActionResult.Failure(
          category = ErrorCategory.REAL_WORLD_MISMATCH,
          reasonDetail = "စက်ထဲတွင် ကင်မရာအက်ပ် ဖွင့်မရပါ",
          alternativeAdvice = "ကင်မရာခွင့်ပြုချက် (Camera Permission) ပေးထားခြင်း ရှိမရှိ စစ်ဆေးပါ"
        )
      }
    }
  }

  private fun tryLaunchBySearch(cleanName: String): ActionResult {
    val pm = context.packageManager
    val packages = pm.getInstalledApplications(0)
    val matched = packages.firstOrNull {
      it.packageName.contains(cleanName, ignoreCase = true)
    }

    if (matched != null) {
      val intent = pm.getLaunchIntentForPackage(matched.packageName)
      if (intent != null) {
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
        return ActionResult.Success("$cleanName အက်ပ်ကို ဖွင့်လှစ်ပေးလိုက်ပါပြီ ခင်ဗျာ။")
      }
    }

    return ActionResult.Failure(
      category = ErrorCategory.REAL_WORLD_MISMATCH,
      reasonDetail = "\"$cleanName\" အက်ပ်ကို ဖုန်းထဲတွင် ရှာမတွေ့ပါ",
      alternativeAdvice = "ဥပမာ- \"Viber ဖွင့်\"၊ \"Telegram ဖွင့်\"၊ \"Facebook ဖွင့်\" ဟု ပြောဆိုပါ"
    )
  }
}
