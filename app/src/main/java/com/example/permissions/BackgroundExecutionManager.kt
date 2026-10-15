package com.example.permissions

import android.content.Context
import android.os.Build
import android.os.PowerManager

/**
 * Dedicated Background Execution & OEM Reliability Manager.
 * Detects device manufacturer, provides OEM-specific auto-start protection,
 * Doze mode battery optimization exemptions, and tracks background service health.
 */
class BackgroundExecutionManager(private val context: Context) {

  val manufacturer: String
    get() = Build.MANUFACTURER.lowercase()

  fun getOemName(): String {
    val m = manufacturer
    return when {
      m.contains("xiaomi") || m.contains("redmi") || m.contains("poco") -> "Xiaomi (MIUI / HyperOS)"
      m.contains("samsung") -> "Samsung (One UI)"
      m.contains("oppo") || m.contains("realme") || m.contains("oneplus") -> "OPPO / Realme (ColorOS)"
      m.contains("vivo") || m.contains("iqoo") -> "Vivo (FuntouchOS / OriginOS)"
      m.contains("huawei") || m.contains("honor") -> "Huawei / Honor (EMUI / MagicOS)"
      m.contains("transsion") || m.contains("tecno") || m.contains("infinix") || m.contains("itel") -> "Tecno / Infinix / itel (HiOS / XOS)"
      else -> Build.MANUFACTURER.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }
  }

  fun hasOemSpecificAutoStart(): Boolean {
    val m = manufacturer
    return m.contains("xiaomi") || m.contains("redmi") || m.contains("poco") ||
      m.contains("huawei") || m.contains("honor") ||
      m.contains("samsung") ||
      m.contains("oppo") || m.contains("realme") || m.contains("oneplus") ||
      m.contains("vivo") || m.contains("iqoo") ||
      m.contains("transsion") || m.contains("tecno") || m.contains("infinix") || m.contains("itel")
  }

  fun isBatteryOptimizationExempted(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
      pm?.isIgnoringBatteryOptimizations(context.packageName) == true
    } else {
      true
    }
  }

  fun openBatteryOptimizationSettings(): Boolean {
    return AndroidSettingsManager.openBatteryOptimizationSettings(context)
  }

  fun openOemAutoStartSettings(): Boolean {
    return AndroidSettingsManager.openOemBackgroundSettings(context)
  }

  /**
   * Tailored step-by-step Burmese instructions for keeping "Hey Kyaw Gyi" active
   * when screen is turned off or app is closed.
   */
  fun getOemGuidanceBurmese(): List<String> {
    val m = manufacturer
    return when {
      m.contains("xiaomi") || m.contains("redmi") || m.contains("poco") -> listOf(
        "၁။ 'Autostart (အလိုအလျောက် စတင်ဖွင့်လှစ်ခွင့်)' ကို ဖွင့်ပေးပါ။",
        "၂။ Battery Saver တွင် 'No restrictions (ကန့်သတ်ချက်မရှိ)' ကို ရွေးချယ်ပေးပါ။",
        "၃။ Recent Apps စခရင်တွင် အက်ပ်ကို အောက်သို့ဆွဲ၍ Lock (သော့ခတ်) ထားပါ။"
      )
      m.contains("samsung") -> listOf(
        "၁။ Device Care > Battery > Background usage limits သို့ သွားပါ။",
        "၂။ 'Never sleeping apps (ဘယ်သောအခါမှ မအိပ်သော အက်ပ်များ)' တွင် Kyaw Gyi ကို ထည့်သွင်းပါ။",
        "၃။ Optimize battery usage တွင် 'All apps' ရွေးပြီး Kyaw Gyi ကို ပိတ်ပေးပါ။"
      )
      m.contains("oppo") || m.contains("realme") || m.contains("oneplus") -> listOf(
        "၁။ App Info > Battery usage တွင် 'Allow background activity' နှင့် 'Allow auto-launch' ကို ဖွင့်ပါ။",
        "၂။ Battery > More settings > Optimize battery use တွင် 'Don't optimize' ကို ရွေးပါ။",
        "၃။ Recent tasks တွင် အက်ပ်ကို Lock ပြုလုပ်ထားပါ။"
      )
      m.contains("vivo") || m.contains("iqoo") -> listOf(
        "၁။ iManager > App manager > Autostart တွင် Kyaw Gyi ကို ဖွင့်ပေးပါ။",
        "၂။ Battery > High background power consumption တွင် Kyaw Gyi ကို Allow ပြုလုပ်ပေးပါ။",
        "၃။ Background task list တွင် Lock ထားပေးပါ။"
      )
      m.contains("huawei") || m.contains("honor") -> listOf(
        "၁။ Settings > Apps > App launch တွင် Kyaw Gyi ကို 'Manage manually' သို့ ပြောင်းပါ။",
        "၂။ Auto-launch, Secondary launch နှင့် Run in background (၃) ခုလုံးကို ဖွင့်ပေးပါ။"
      )
      m.contains("transsion") || m.contains("tecno") || m.contains("infinix") || m.contains("itel") -> listOf(
        "၁။ Phone Master > Auto-start management တွင် Kyaw Gyi ကို ဖွင့်ပေးပါ။",
        "၂။ Power Marathon / Battery Saver တွင် Kyaw Gyi ကို 'Protect from cleanup' သို့မဟုတ် 'No restriction' ထားပါ။",
        "၃။ Recent Apps စခရင်တွင် အက်ပ်ကို Lock (သော့ခတ်) ထားပေးပါ။"
      )
      else -> listOf(
        "၁။ App Info > Battery တွင် 'Unrestricted (ကန့်သတ်ချက်မရှိ)' ကို ရွေးချယ်ပေးပါ။",
        "၂။ Battery Optimization Exemption ကို Allow (ကင်းလွတ်ခွင့်) ပေးပါ။",
        "၃။ အက်ပ်အား နောက်ခံမှ ရပ်တန့်မသွားစေရန် စနစ်ဆက်တင်တွင် ခွင့်ပြုထားပါ။"
      )
    }
  }
}
