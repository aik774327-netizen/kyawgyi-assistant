package com.example.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

/**
 * Dedicated Microphone Permission & Audio Hardware Verification Manager.
 * Checks hardware availability and runtime permission with bilingual messaging.
 */
class MicrophonePermissionManager(private val context: Context) {

  companion object {
    const val MYANMAR_MESSAGE = "ကျော်ကြီး၏ အသံအမိန့်များကို နားထောင်ရန် Microphone ခွင့်ပြုချက်လိုအပ်ပါသည်။"
    const val ENGLISH_MESSAGE = "Microphone permission is required for Kyaw Gyi voice commands."
    const val MYANMAR_RATIONALE = "ကျော်ကြီးအား အသံဖြင့် အမိန့်ပေးခိုင်းစေရန်နှင့် မြန်မာစကားသံကို အချိန်နှင့်တပြေးညီ ဖမ်းယူနားထောင်နိုင်ရန် မိုက်ခရိုဖုန်းခွင့်ပြုချက် လိုအပ်ပါသည်။"
    const val ENGLISH_RATIONALE = "Microphone permission is required for Kyaw Gyi voice commands and real-time Burmese speech listening."
    const val MYANMAR_DENIED_FALLBACK = "မိုက်ခရိုဖုန်းခွင့်ပြုချက် မရရှိပါက အသံဖြင့် အမိန့်ပေးခြင်းကို အသုံးမပြုနိုင်ပါ။ သို့သော် ခလုတ်များကို နှိပ်၍ သမားရိုးကျ ထိန်းချုပ်မှုများကို ပုံမှန်အတိုင်း ဆက်လက်အသုံးပြုနိုင်ပါသည်။"
    const val ENGLISH_DENIED_FALLBACK = "Without microphone permission, voice commands are disabled. Manual on-screen controls remain fully functional."
  }

  fun checkReadiness(isPermanentlyDenied: Boolean = false): MicrophoneReadiness {
    val pm = context.packageManager
    if (!pm.hasSystemFeature(PackageManager.FEATURE_MICROPHONE)) {
      return MicrophoneReadiness.MIC_HARDWARE_UNAVAILABLE
    }

    val isGranted = ContextCompat.checkSelfPermission(
      context,
      Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED

    return when {
      isGranted -> MicrophoneReadiness.MIC_PERMISSION_GRANTED
      isPermanentlyDenied -> MicrophoneReadiness.MIC_PERMISSION_PERMANENTLY_DENIED
      else -> MicrophoneReadiness.MIC_PERMISSION_DENIED
    }
  }

  fun isGranted(): Boolean {
    return ContextCompat.checkSelfPermission(
      context,
      Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED
  }
}
