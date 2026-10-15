package com.example.permissions

import android.content.Context
import android.os.Build
import android.os.PowerManager

/**
 * Dedicated Battery Optimization Exemption Manager.
 * Checks Doze mode exemptions for sustained background audio sensing and BLE reconnects.
 */
class BatteryOptimizationManager(private val context: Context) {

  companion object {
    const val MYANMAR_MESSAGE = "နောက်ခံတွင် အသံနှင့် Bluetooth လုပ်ဆောင်မှုများကို တည်ငြိမ်စေရန် Battery Optimization setting ကို စစ်ဆေးနိုင်ပါသည်။"
    const val ENGLISH_MESSAGE = "Battery optimization may affect background voice and Bluetooth reliability."
    const val MYANMAR_RATIONALE = "ဖုန်းမျက်နှာပြင် ပိတ်ထားချိန် (Screen Off) တွင်လည်း ကျော်ကြီး အသံလက်ခံစနစ်နှင့် Bluetooth ချိတ်ဆက်မှု မပြတ်တောက်စေရန် ဘက်ထရီချွေတာမှုမှ ကင်းလွတ်ခွင့် ပေးရန် လိုအပ်ပါသည်။"
    const val ENGLISH_RATIONALE = "Battery optimization exemption is required to prevent Android Doze from suspending background voice listening and BLE connectivity when the screen is off."
    const val MYANMAR_DENIED_FALLBACK = "ဘက်ထရီကင်းလွတ်ခွင့် မပေးထားပါက စခရင်ပိတ်ထားချိန်တွင် အသံဖမ်းယူမှု ယာယီရပ်တန့်နိုင်ပါသည်။ သို့သော် အက်ပ်ဖွင့်ထားချိန်တွင် လုပ်ဆောင်ချက်အားလုံး ပုံမှန်အတိုင်း ၁၀၀% အပြည့်အဝ ဆက်လက်အလုပ်လုပ်ပါမည်။"
    const val ENGLISH_DENIED_FALLBACK = "Without battery exemption, background listening may sleep during Doze. All features remain 100% functional while the app is in the foreground."
  }

  fun checkReadiness(): BatteryReadiness {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
      if (pm?.isIgnoringBatteryOptimizations(context.packageName) == true) {
        BatteryReadiness.EXEMPTED
      } else {
        BatteryReadiness.OPTIMIZED
      }
    } else {
      BatteryReadiness.EXEMPTED
    }
  }

  fun isIgnoringBatteryOptimizations(): Boolean {
    return checkReadiness() == BatteryReadiness.EXEMPTED
  }
}
