package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Root-Cause Classification for Failures (အချက် ၂)
 * နည်းပညာပိုင်း (Technical)
 * အရင်းအမြစ် (Resource)
 * လက်တွေ့မကိုက်ညီမှု (Pragmatic / Contextual)
 */
enum class FailureCategory(val code: String, val burmeseLabel: String, val descriptionMm: String) {
    NONE("NONE", "အောင်မြင်သည်", "လုပ်ဆောင်ချက် အောင်မြင်ပါသည်"),
    TECHNICAL("TECHNICAL", "နည်းပညာပိုင်းဆိုင်ရာ ချို့ယွင်းချက်", "Android API၊ Background Service သို့မဟုတ် Code ပိုင်းဆိုင်ရာ ချို့ယွင်းမှုကြောင့် ဖြစ်ပါသည်"),
    RESOURCE("RESOURCE", "အရင်းအမြစ်/စက်ပိုင်းဆိုင်ရာ မလုံလောက်မှု (အရင်းအမြစ်ဆိုင်ရာ ကန့်သတ်ချက်)", "အင်တာနက်လိုင်း၊ RAM/Storage သို့မဟုတ် အော့ဖ်လိုင်း အသံဖိုင်ဒေတာ မလုံလောက်မှုကြောင့် ဖြစ်ပါသည်"),
    PRAGMATIC_CONTEXTUAL("PRAGMATIC_CONTEXTUAL", "လက်တွေ့မကိုက်ညီမှု/အခြေအနေအရ ချို့ယွင်းချက်", "ခွင့်ပြုချက် မရှိခြင်း (Permission Denied)၊ Screen ပိတ်ထားခြင်း သို့မဟုတ် အက်ပ် မရှိခြင်းကြောင့် ဖြစ်ပါသည်");

    companion object {
        fun fromCode(code: String): FailureCategory {
            return entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: NONE
        }
    }
}

@Entity(tableName = "command_logs")
data class CommandLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val commandText: String,
    val commandType: String, // HOME, BACK, RECENTS, SCROLL, OPEN_APP, WAKE_WORD, CUSTOM, UNKNOWN, READ_SCREEN
    val status: String,      // SUCCESS, FAILED, PARSED
    val actionDetail: String,
    val timestamp: Long = System.currentTimeMillis(),

    // Structured Root-Cause Classification (အချက် ၂)
    val failureCategory: String = "NONE",       // TECHNICAL, RESOURCE, PRAGMATIC_CONTEXTUAL, NONE
    val failureReason: String = "",             // အမှားဖြစ်ရသော အကြောင်းရင်း (မြန်မာဘာသာ ရှင်းလင်းချက်)
    val suggestedAlternative: String = "",     // မအောင်မြင်ခဲ့သော နည်းလမ်းအစား အကြံပြုချက်
    val isRepeatedFailure: Boolean = false,     // ယခင်က မအောင်မြင်ခဲ့သော လုပ်ဆောင်ချက် ဟုတ်/မဟုတ်
    val remediationAction: String = "",         // Intent Action (အချက် ၃ Fallback & Alternative Flow)
    val remediationUri: String = ""             // Intent Uri (အချက် ၃ Fallback & Alternative Flow)
)

@Entity(tableName = "custom_shortcuts")
data class CustomShortcutEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val phrase: String,
    val targetPackageOrAction: String,
    val description: String,
    val isEnabled: Boolean = true
)
