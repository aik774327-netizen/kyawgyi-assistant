package com.example.pipeline.response

import com.example.pipeline.contract.UnifiedActionPlan
import com.example.pipeline.contract.UnifiedActionResult
import com.example.semantic.model.ConfidenceLevel

/**
 * Natural Myanmar Response Generator
 * Generates natural, polite, and context-appropriate Myanmar responses,
 * avoiding robotic phrasing.
 */
class ResponseGenerator {

    /**
     * Generate response for an executed action result
     */
    fun generateSuccessResponse(actionType: String, target: String?, parameters: Map<String, Any?> = emptyMap()): String {
        return when (actionType.uppercase()) {
            "OPEN_APP" -> "${target ?: "App"} ဖွင့်ပေးလိုက်ပါတယ်။"
            "VOLUME_UP" -> "အသံ တိုးပေးလိုက်ပါပြီခင်ဗျာ။"
            "VOLUME_DOWN" -> "အသံ လျှော့ပေးလိုက်ပါပြီခင်ဗျာ။"
            "MUTE" -> "အသံ ပိတ်ထားပေးလိုက်ပါပြီခင်ဗျာ။"
            "FLASHLIGHT" -> {
                val enable = parameters["enable"] as? Boolean ?: true
                if (enable) "ဓာတ်မီး ဖွင့်ပေးလိုက်ပါပြီ။" else "ဓာတ်မီး ပိတ်ပေးလိုက်ပါပြီ။"
            }
            "CALL" -> "${target ?: "ဖုန်းနံပါတ်"} ထံသို့ ဖုန်းခေါ်ဆိုပေးလိုက်ပါပြီ။"
            "SEND_MESSAGE" -> "${target ?: "လူကြီးမင်း"} ဆီသို့ မက်ဆေ့ခ်ျ ပို့ဆောင်ပေးလိုက်ပါပြီ။"
            "BACK" -> "နောက်သို့ ပြန်သွားပေးလိုက်ပါပြီ။"
            "HOME" -> "ပင်မစာမျက်နှာသို့ သွားပေးလိုက်ပါပြီ။"
            "RECENTS" -> "ဖွင့်ထားသော App များကို ပြပေးလိုက်ပါပြီ။"
            "NOTIFICATIONS" -> "အသိပေးချက်များကို ဖွင့်ပေးလိုက်ပါပြီ။"
            "QUICK_SETTINGS" -> "ဆက်တင်များ panel ဖွင့်ပေးလိုက်ပါပြီ။"
            "TAKE_SCREENSHOT" -> "စခရင် ဓာတ်ပုံ ရိုက်ယူပေးလိုက်ပါပြီ။"
            "WIFI_TOGGLE" -> "Wi-Fi ဆက်တင်ကို ပြောင်းလဲပေးလိုက်ပါပြီ။"
            "BLUETOOTH_TOGGLE" -> "Bluetooth ဆက်တင်ကို ပြောင်းလဲပေးလိုက်ပါပြီ။"
            "SET_TIMER" -> {
                val mins = parameters["durationMinutes"] ?: "သတ်မှတ်ထားသော"
                "Timer $mins မိနစ် စတင်မှတ်သားပေးလိုက်ပါပြီ။"
            }
            "SET_ALARM" -> "နှိုးစက် သတ်မှတ်ပေးလိုက်ပါပြီ။"
            "PLAY_MUSIC" -> "${target ?: "သီချင်း"} ဖွင့်ပေးလိုက်ပါပြီ။"
            "NEXT_TRACK" -> "နောက်တစ်ပုဒ်သို့ ကျော်ပေးလိုက်ပါပြီ။"
            "PREVIOUS_TRACK" -> "အရင်တစ်ပုဒ်သို့ ပြန်ဖွင့်ပေးလိုက်ပါပြီ။"
            "PAUSE_MEDIA" -> "ခေတ္တ ရပ်တန့်ပေးထားပါတယ်။"
            "RESUME_MEDIA" -> "ဆက်လက် ဖွင့်ပြပေးနေပါတယ်။"
            "COPY" -> "စာသားများကို ကူးယူပေးလိုက်ပါပြီ။"
            "PASTE" -> "ကူးယူထားသော စာသားကို ထည့်သွင်းပေးလိုက်ပါပြီ။"
            "SEARCH" -> "\"${target ?: "ရှာဖွေချက်"}\" ကို ရှာဖွေပေးနေပါတယ်။"
            "READ_SCREEN" -> {
                val text = parameters["screenText"] as? String ?: ""
                if (text.isBlank()) {
                    "မျက်နှာပြင်ပေါ်တွင် ဖတ်ပြစရာ စာသား မတွေ့ရှိပါခင်ဗျာ"
                } else {
                    "မျက်နှာပြင်ပေါ်ရှိ စာသားများမှာ အောက်ပါအတိုင်း ဖြစ်ပါသည်ခင်ဗျာ။ $text"
                }
            }
            "INGEST_FILE" -> "${target ?: "ဖိုင်"} ကို အောင်မြင်စွာ စနစ်တကျ လက်ခံသိမ်းဆည်းပြီးပါပြီ။"
            "SEARCH_FILES" -> "${target ?: "ဖိုင်များ"} ရှာဖွေမှု ပြီးဆုံးပါပြီ။"
            "ROUTE_ASSET" -> "${target ?: "ဖိုင်"} ကို သက်ဆိုင်ရာ လုံခြုံသော နေရာသို့ စနစ်တကျ လမ်းကြောင်းခွဲခြား ပို့ဆောင်ပြီးပါပြီ။"
            else -> "${target ?: "လုပ်ဆောင်ချက်"} ကို အောင်မြင်စွာ ဆောင်ရွက်ပေးလိုက်ပါပြီ။"
        }
    }

    /**
     * Generate response when an action fails
     */
    fun generateFailureResponse(actionType: String, target: String?, reason: String?, errorCode: String?): String {
        return when (errorCode) {
            "APP_NOT_FOUND" -> "${target ?: "App"} ကို ဒီဖုန်းထဲမှာ ထည့်သွင်းမထားပါခင်ဗျာ။"
            "PERMISSION_DENIED" -> "ဤလုပ်ဆောင်ချက်အတွက် ခွင့်ပြုချက် (Permission) လိုအပ်နေပါသည်ခင်ဗျာ။"
            "CONTACT_NOT_FOUND" -> "${target ?: "အမည်"} ကို ဖုန်းစာအုပ်ထဲတွင် မတွေ့ရှိပါခင်ဗျာ။"
            "NO_FLASH_HARDWARE" -> "ဤဖုန်းတွင် ဓာတ်မီး (Camera Flash) Hardware မပါရှိပါခင်ဗျာ။"
            else -> when (actionType.uppercase()) {
                "OPEN_APP" -> "${target ?: "App"} ဖွင့်ပေးတာ မအောင်မြင်သေးပါဘူးခင်ဗျာ။ ထပ်စမ်းပေးမလား?"
                "CALL" -> "${target ?: "ဖုန်းနံပါတ်"} သို့ ဖုန်းခေါ်ဆို၍ မရသေးပါခင်ဗျာ။"
                "FLASHLIGHT" -> "ဓာတ်မီး အဖွင့်/အပိတ် ဆောင်ရွက်ရာတွင် အဆင်မပြေဖြစ်သွားပါသည်ခင်ဗျာ။"
                else -> "${target ?: "လုပ်ဆောင်ချက်"} ဆောင်ရွက်ရာတွင် အဆင်မပြေဖြစ်သွားပါသည်ခင်ဗျာ။"
            }
        }
    }

    /**
     * Generate clarification response when parameters/targets are missing or ambiguous
     */
    fun generateClarificationResponse(intent: String, missingSlot: String?, currentTarget: String?): String {
        return when (intent.uppercase()) {
            "CALL", "CALL_CONTACT" -> "ဘယ်သူ့ကို ဖုန်းဆက်ပေးရမလဲခင်ဗျာ?"
            "SEND_MESSAGE", "SEND_SMS" -> {
                if (currentTarget.isNullOrBlank()) "ဘယ်သူ့ဆီကို စာပို့ပေးရမလဲခင်ဗျာ?"
                else "ဘာပြောပြီး ပို့ပေးရမလဲခင်ဗျာ?"
            }
            "DELETE", "DELETE_ITEM" -> "ဘယ်အရာကို ဖျက်ပေးရမလဲခင်ဗျာ?"
            "OPEN_APP" -> "ဘယ် App ကို ဖွင့်ပေးရမလဲခင်ဗျာ?"
            "PLAY_MUSIC" -> "ဘယ်သီချင်း ဖွင့်ပေးရမလဲခင်ဗျာ?"
            "SET_TIMER" -> "Timer ဘယ်နှစ်မိနစ် မှတ်ပေးရမလဲခင်ဗျာ?"
            else -> "မည်သည်ကို ဆောင်ရွက်ပေးရမလဲ ရှင်းလင်းစွာ ထပ်မံပြောကြားပေးပါခင်ဗျာ။"
        }
    }

    /**
     * Generate confirmation request response for high-risk actions
     */
    fun generateConfirmationPrompt(actionType: String, target: String?, parameters: Map<String, Any?> = emptyMap()): String {
        return when (actionType.uppercase()) {
            "DELETE" -> "${target ?: "ရွေးချယ်ထားသော အရာ"} ကို အမှန်တကယ် ဖျက်ပစ်မှာ သေချာပါသလားခင်ဗျာ။"
            "SEND_MESSAGE" -> {
                val msg = parameters["message"] as? String ?: ""
                "${target ?: "လက်ခံသူ"} ဆီသို့ \"$msg\" ဟု မက်ဆေ့ခ်ျ ပို့ရန် သေချာပါသလားခင်ဗျာ။"
            }
            "CALL" -> "${target ?: "ဖုန်းနံပါတ်"} ထံသို့ တိုက်ရိုက် ဖုန်းခေါ်ဆိုမှာ သေချာပါသလားခင်ဗျာ။"
            else -> "${target ?: "ဤလုပ်ဆောင်ချက်"} ကို အတည်ပြု လုပ်ဆောင်ပေးရမလားခင်ဗျာ။"
        }
    }

    /**
     * Fallback response when speech/command confidence is low or unknown
     */
    fun generateUnknownOrRepeatResponse(confidenceLevel: ConfidenceLevel): String {
        return when (confidenceLevel) {
            ConfidenceLevel.ASK_FOR_CLARIFICATION -> "နားလည်ရ ခက်ခဲနေပါသဖြင့် ပိုမိုတိကျစွာ ထပ်ပြောပေးပါခင်ဗျာ။"
            ConfidenceLevel.ASK_USER_TO_REPEAT -> "နားမလည်လိုက်သေးပါဘူးခင်ဗျာ။ တစ်ခါထပ်ပြောပေးပါ။"
            else -> "ခိုင်းစေလိုသည်များကို ပြောပြနိုင်ပါသည်ခင်ဗျာ။"
        }
    }
}
