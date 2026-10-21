package com.example.semantic

import com.example.semantic.model.ActionPlan
import com.example.semantic.model.ConfidenceLevel
import com.example.semantic.model.EntityType
import com.example.semantic.model.SemanticEntity
import com.example.semantic.model.SemanticIntent

/**
 * Myanmar Action Planner for MSIL
 * Generates an immutable, non-executing ActionPlan based on reasoned semantic intent,
 * validated entities, safety constraints, and confidence score.
 */
class MyanmarActionPlanner {

    fun planAction(
        intent: SemanticIntent,
        target: String?,
        confidence: Float,
        confidenceLevel: ConfidenceLevel,
        entities: List<SemanticEntity>,
        safetyResult: ActionSafetyValidator.SafetyResult,
        reasoning: String
    ): ActionPlan {
        val params = mutableMapOf<String, Any?>()

        for (e in entities) {
            when (e.type) {
                EntityType.APP_NAME -> params["appName"] = e.value
                EntityType.APP_PACKAGE -> params["packageName"] = e.value
                EntityType.CONTACT_NAME -> params["contactName"] = e.value
                EntityType.PHONE_NUMBER -> params["phoneNumber"] = e.value
                EntityType.SONG_TITLE -> params["songTitle"] = e.value
                EntityType.TIME_EXPRESSION -> params["time"] = e.value
                EntityType.SETTING_NAME -> params["setting"] = e.value
                EntityType.SETTING_STATE -> params["state"] = e.value
                EntityType.VOLUME_LEVEL -> params["volumeLevel"] = e.value
                EntityType.MESSAGE_BODY -> params["message"] = e.value
                else -> {}
            }
        }

        if (target != null) {
            params["target"] = target
        }

        val speechFeedback = generateSpeechFeedback(intent, target, entities, safetyResult)

        return ActionPlan(
            intent = intent,
            target = target,
            actionType = intent.name,
            parameters = params,
            confidence = confidence,
            confidenceLevel = confidenceLevel,
            requiresConfirmation = safetyResult.requiresConfirmation,
            confirmationPrompt = safetyResult.confirmationPromptMM,
            clarificationPrompt = if (confidenceLevel == ConfidenceLevel.ASK_FOR_CLARIFICATION) "သေချာမကြားလိုက်ရပါ၊ ${intent.defaultDescriptionMM} ကို ဆိုလိုတာလားခင်ဗျာ" else null,
            speechFeedbackMM = speechFeedback,
            explanation = reasoning,
            isExecutable = intent != SemanticIntent.UNKNOWN && confidenceLevel != ConfidenceLevel.ASK_USER_TO_REPEAT,
            safetyStatus = safetyResult.safetyRiskLevel
        )
    }

    private fun generateSpeechFeedback(
        intent: SemanticIntent,
        target: String?,
        entities: List<SemanticEntity>,
        safety: ActionSafetyValidator.SafetyResult
    ): String {
        if (safety.requiresConfirmation && safety.confirmationPromptMM != null) {
            return safety.confirmationPromptMM
        }

        return when (intent) {
            SemanticIntent.OPEN_APP -> {
                when (target) {
                    "CAMERA" -> "ကင်မရာ ဖွင့်ပေးလိုက်ပါပြီခင်ဗျာ"
                    "PHOTOS" -> "ဓာတ်ပုံများ ဖွင့်ပေးလိုက်ပါပြီခင်ဗျာ"
                    "SETTINGS" -> "ဆက်တင်ကို ဖွင့်ပေးလိုက်ပါပြီခင်ဗျာ"
                    "MAPS" -> "မြေပုံကို ဖွင့်ပေးလိုက်ပါပြီခင်ဗျာ"
                    else -> "${target ?: "အက်ပ်"} ကို ဖွင့်ပေးလိုက်ပါပြီခင်ဗျာ"
                }
            }
            SemanticIntent.REOPEN_APP -> "${target ?: "အက်ပ်"} ကို ပြန်ဖွင့်ပေးလိုက်ပါပြီခင်ဗျာ"
            SemanticIntent.CALL_CONTACT -> if (target != null) "$target ထံ ဖုန်းခေါ်ဆိုပေးနေပါပြီခင်ဗျာ" else "ဖုန်းခေါ်ဆိုရန် အမည် ပြောပြပေးပါခင်ဗျာ"
            SemanticIntent.SEND_SMS -> "${target ?: "လက်ခံသူ"} ဆီသို့ မက်ဆေ့ခ်ျ ပို့ဆောင်ပေးနေပါပြီခင်ဗျာ"
            SemanticIntent.PLAY_MUSIC -> "${target ?: "သီချင်း"} ဖွင့်ပေးလိုက်ပါပြီခင်ဗျာ"
            SemanticIntent.NEXT_TRACK -> "နောက်တစ်ပုဒ်သို့ ပြောင်းပေးလိုက်ပါပြီခင်ဗျာ"
            SemanticIntent.PREVIOUS_TRACK -> "ရှေ့တစ်ပုဒ်သို့ ပြောင်းပေးလိုက်ပါပြီခင်ဗျာ"
            SemanticIntent.VOLUME_DOWN -> "အသံ လျှော့ပေးလိုက်ပါပြီခင်ဗျာ"
            SemanticIntent.VOLUME_UP -> "အသံ မြှင့်ပေးလိုက်ပါပြီခင်ဗျာ"
            SemanticIntent.MUTE -> "အသံ ပိတ်ပေးလိုက်ပါပြီခင်ဗျာ"
            SemanticIntent.TOGGLE_WIFI -> {
                val state = entities.firstOrNull { it.metadata["state"] != null }?.metadata?.get("state") ?: "TOGGLE"
                if (state == "OFF") "Wi-Fi ပိတ်ပေးလိုက်ပါပြီခင်ဗျာ" else "Wi-Fi ဖွင့်ပေးလိုက်ပါပြီခင်ဗျာ"
            }
            SemanticIntent.TOGGLE_BLUETOOTH -> {
                val state = entities.firstOrNull { it.metadata["state"] != null }?.metadata?.get("state") ?: "TOGGLE"
                if (state == "OFF") "Bluetooth ပိတ်ပေးလိုက်ပါပြီခင်ဗျာ" else "Bluetooth ဖွင့်ပေးလိုက်ပါပြီခင်ဗျာ"
            }
            SemanticIntent.TOGGLE_FLASHLIGHT -> {
                val state = entities.firstOrNull { it.metadata["state"] != null }?.metadata?.get("state") ?: "TOGGLE"
                if (state == "OFF") "ဓာတ်မီး ပိတ်ပေးလိုက်ပါပြီခင်ဗျာ" else "ဓာတ်မီး ဖွင့်ပေးလိုက်ပါပြီခင်ဗျာ"
            }
            SemanticIntent.SET_ALARM -> {
                val time = entities.firstOrNull { it.type == EntityType.TIME_EXPRESSION }?.value ?: "မနက် ၇ နာရီ"
                "$time အတွက် နှိုးစက် သတ်မှတ်ပေးလိုက်ပါပြီခင်ဗျာ"
            }
            SemanticIntent.NAVIGATE_HOME -> "ပင်မမျက်နှာပြင်သို့ သွားပါပြီခင်ဗျာ"
            SemanticIntent.NAVIGATE_BACK -> "နောက်သို့ ပြန်သွားပါပြီခင်ဗျာ"
            SemanticIntent.SHOW_RECENTS -> "ဖွင့်ထားသော အက်ပ်များ ပြပေးလိုက်ပါပြီခင်ဗျာ"
            SemanticIntent.UNKNOWN -> "တောင်းပန်ပါတယ်ခင်ဗျာ၊ နားမလည်နိုင်ပါသဖြင့် ထပ်မံပြောကြားပေးပါဦး"
            else -> "ဆောင်ရွက်ပေးလိုက်ပါပြီခင်ဗျာ"
        }
    }
}
