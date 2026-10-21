package com.example.fallback

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.data.FailureCategory

/**
 * အမှားအမျိုးအစားအလိုက် အစားထိုးကုစားမှု ပုံစံ (Remediation Strategy Type)
 */
enum class RemediationType(val code: String, val labelMm: String) {
    SETTINGS_PERMISSION("SETTINGS_PERMISSION", "ခွင့်ပြုချက် ဆက်တင် ဖွင့်လှစ်ပေးခြင်း"),
    DIRECT_INTENT("DIRECT_INTENT", "တိုက်ရိုက်စနစ် Intent ဖြင့် အစားထိုး ဆောင်ရွက်ခြင်း"),
    WEB_FALLBACK("WEB_FALLBACK", "ဝဘ်ဆိုက် (Web Browser) မှတစ်ဆင့် အစားထိုး ဖွင့်လှစ်ခြင်း"),
    PLAY_STORE_INSTALL("PLAY_STORE_INSTALL", "Google Play Store တွင် အက်ပ်ဒေါင်းလုဒ် ရယူရန် လမ်းညွှန်ခြင်း"),
    OFFLINE_FASTPATH("OFFLINE_FASTPATH", "အော့ဖ်လိုင်း စက်တွင်း အသံအမိန့် ဖြင့် အစားထိုးဆောင်ရွက်ခြင်း"),
    GOOGLE_ASSISTANT_RELAY("GOOGLE_ASSISTANT_RELAY", "Google Assistant / Home သို့ လွှဲပြောင်း ဆောင်ရွက်ခြင်း"),
    MANUAL_GUIDANCE("MANUAL_GUIDANCE", "အသုံးပြုသူ ကိုယ်တိုင် ပြုလုပ်နိုင်သော အဆင့်ဆင့် လမ်းညွှန်ချက်")
}

/**
 * အစားထိုးလမ်းကြောင်းနှင့် လမ်းညွှန်ချက် အချက်အလက် (Alternative Flow Definition)
 *
 * အခြေခံစည်းမျဉ်းများ:
 * ၁။ မအောင်မြင်ခဲ့သော နည်းလမ်းကို ထပ်မံ အကြံမပေးရ (Avoided Method Tracking)
 * ၂။ အမှားအမျိုးအစားအလိုက် အစားထိုးနိုင်မည့် အမှန်တကယ် လက်တွေ့ကျသော လမ်းကြောင်း သတ်မှတ်ပေးခြင်း
 * ၃။ တိုက်ရိုက် ခေါ်ယူနိုင်သော Fallback Intent ပံ့ပိုးပေးခြင်း
 */
data class AlternativeFlow(
    val category: FailureCategory,
    val remediationType: RemediationType,
    val titleMm: String,
    val explanationMm: String,
    val actionGuidanceMm: String,
    val avoidedMethodMm: String, // မအောင်မြင်ခဲ့သဖြင့် ထပ်မံ မသုံးတော့သော နည်းလမ်း
    val fallbackIntentAction: String? = null,
    val fallbackIntentUri: String? = null,
    val canAutoExecute: Boolean = false
) {
    /**
     * အစားထိုး Intent ကို စနစ်ထဲတွင် ဖန်တီးပေးခြင်း
     */
    fun createIntent(): Intent? {
        if (fallbackIntentAction.isNullOrBlank()) return null
        return try {
            val intent = if (!fallbackIntentUri.isNullOrBlank()) {
                Intent(fallbackIntentAction, Uri.parse(fallbackIntentUri))
            } else {
                Intent(fallbackIntentAction)
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent
        } catch (e: Exception) {
            null
        }
    }
}
