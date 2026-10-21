package com.example.cognitive

import com.example.semantic.model.ActionPlan
import com.example.semantic.model.ConfidenceLevel
import com.example.semantic.model.SemanticEntity
import com.example.semantic.model.SemanticIntent

/**
 * TaskPlanner converts high-level cognitive intentions, goals, and step graphs
 * into ready-to-validate ActionPlan objects.
 */
class TaskPlanner(
    private val safetyGuard: CognitiveSafetyGuard = CognitiveSafetyGuard()
) {

    fun planSingleTask(
        intent: SemanticIntent,
        target: String?,
        confidence: Float,
        confidenceLevel: ConfidenceLevel,
        entities: List<SemanticEntity>,
        parameters: Map<String, Any?>,
        reasoning: String,
        clarificationPrompt: String? = null
    ): ActionPlan {
        val safetyEval = safetyGuard.evaluate(
            intent = intent,
            target = target,
            confidence = confidence,
            confidenceLevel = confidenceLevel,
            parameters = parameters
        )

        val speechFeedback = if (clarificationPrompt != null) {
            clarificationPrompt
        } else if (safetyEval.requiresExplicitConfirmation && safetyEval.confirmationPromptMM != null) {
            safetyEval.confirmationPromptMM
        } else {
            generateDefaultSpeechFeedback(intent, target, entities)
        }

        return ActionPlan(
            intent = intent,
            target = target,
            actionType = intent.name,
            parameters = parameters,
            confidence = confidence,
            confidenceLevel = confidenceLevel,
            requiresConfirmation = safetyEval.requiresExplicitConfirmation,
            confirmationPrompt = safetyEval.confirmationPromptMM,
            clarificationPrompt = clarificationPrompt,
            speechFeedbackMM = speechFeedback,
            explanation = reasoning,
            isExecutable = safetyEval.isPermitted && intent != SemanticIntent.UNKNOWN && confidenceLevel != ConfidenceLevel.ASK_USER_TO_REPEAT,
            safetyStatus = safetyEval.riskLevel.name
        )
    }

    fun buildTaskStep(
        intent: SemanticIntent,
        target: String?,
        confidence: Float,
        confidenceLevel: ConfidenceLevel,
        entities: List<SemanticEntity>,
        parameters: Map<String, Any?>
    ): TaskStep {
        val safetyEval = safetyGuard.evaluate(
            intent = intent,
            target = target,
            confidence = confidence,
            confidenceLevel = confidenceLevel,
            parameters = parameters
        )

        return TaskStep(
            stepIndex = 0,
            intent = intent,
            target = target,
            actionType = intent.name,
            parameters = parameters,
            entities = entities,
            confidence = confidence,
            confidenceLevel = confidenceLevel,
            safetyLevel = safetyEval.riskLevel.name,
            requiresConfirmation = safetyEval.requiresExplicitConfirmation,
            status = "READY"
        )
    }

    private fun generateDefaultSpeechFeedback(
        intent: SemanticIntent,
        target: String?,
        entities: List<SemanticEntity>
    ): String {
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
                val time = entities.firstOrNull { it.type == com.example.semantic.model.EntityType.TIME_EXPRESSION }?.value ?: "မနက် ၇ နာရီ"
                "$time အတွက် နှိုးစက် သတ်မှတ်ပေးလိုက်ပါပြီခင်ဗျာ"
            }
            SemanticIntent.NAVIGATE_HOME -> "ပင်မမျက်နှာပြင်သို့ သွားပါပြီခင်ဗျာ"
            SemanticIntent.NAVIGATE_BACK -> "နောက်သို့ ပြန်သွားပါပြီခင်ဗျာ"
            SemanticIntent.SHOW_RECENTS -> "ဖွင့်ထားသော အက်ပ်များ ပြပေးလိုက်ပါပြီခင်ဗျာ"
            SemanticIntent.READ_SCREEN -> {
                val screenText = com.example.service.KyawGyiVoiceSession.getLatestScreenText().ifEmpty {
                    com.example.service.KyawGyiAccessibilityService.instance?.extractCurrentScreenText() ?: ""
                }
                if (screenText.isBlank()) {
                    "မျက်နှာပြင်ပေါ်တွင် ဖတ်ပြစရာ စာသား မတွေ့ရှိပါခင်ဗျာ"
                } else {
                    "မျက်နှာပြင်ပေါ်ရှိ စာသားများမှာ အောက်ပါအတိုင်း ဖြစ်ပါသည်ခင်ဗျာ-\n\n$screenText"
                }
            }
            SemanticIntent.UNKNOWN -> "တောင်းပန်ပါတယ်ခင်ဗျာ၊ နားမလည်နိုင်ပါသဖြင့် ထပ်မံပြောကြားပေးပါဦး"
            else -> "ဆောင်ရွက်ပေးလိုက်ပါပြီခင်ဗျာ"
        }
    }
}
