package com.example.cognitive

import com.example.semantic.model.ConfidenceLevel
import com.example.semantic.model.SemanticEntity
import com.example.semantic.model.SemanticIntent

/**
 * Reasoning Engine for Myanmar Cognitive Layer
 * Synthesizes discourse context, goals, missing information, reference resolution, and semantic memories
 * into explainable inferences.
 */
class ReasoningEngine(
    private val conversationStateManager: ConversationStateManager,
    private val goalManager: GoalManager
) {

    data class InferenceResult(
        val intent: SemanticIntent,
        val target: String?,
        val confidence: Float,
        val confidenceLevel: ConfidenceLevel,
        val missingInformation: List<String>,
        val clarificationQuestionMM: String?,
        val reasoningLog: String
    )

    fun infer(
        rawUtterance: String,
        normalizedUtterance: String,
        semanticIntent: SemanticIntent,
        semanticTarget: String?,
        extractedEntities: List<SemanticEntity>,
        resolvedReferences: Map<String, String>,
        discourseType: GoalManager.DiscourseType,
        pendingGoal: Goal?
    ): InferenceResult {
        val sb = StringBuilder()
        sb.append("Utterance: \"$rawUtterance\" [Discourse: $discourseType]. ")

        var finalIntent = semanticIntent
        var finalTarget = semanticTarget
        var confidence = 0.95f
        val missing = mutableListOf<String>()
        var clarificationQuestion: String? = null

        // 1. Check if this is answering a pending goal/question
        if (pendingGoal != null && pendingGoal.missingSlots.contains("ALARM_TIME")) {
            val timeEntity = extractedEntities.firstOrNull { it.type == com.example.semantic.model.EntityType.TIME_EXPRESSION }
            if (timeEntity != null || rawUtterance.any { it.isDigit() }) {
                finalIntent = SemanticIntent.SET_ALARM
                finalTarget = timeEntity?.value ?: "ALARM"
                confidence = 0.96f
                sb.append("Resolved pending WAKE_UP goal with provided time: $finalTarget. ")
                return InferenceResult(
                    intent = finalIntent,
                    target = finalTarget,
                    confidence = confidence,
                    confidenceLevel = ConfidenceLevel.EXECUTE,
                    missingInformation = emptyList(),
                    clarificationQuestionMM = null,
                    reasoningLog = sb.toString()
                )
            }
        }

        // 2. Check Goal Detection with Missing Information
        val detectedGoal = goalManager.detectGoal(
            utterance = normalizedUtterance,
            extractedSlots = extractedEntities.associate { it.type.name to it.value },
            workingMemory = conversationStateManager.workingMemory
        )

        if (detectedGoal != null && detectedGoal.missingSlots.isNotEmpty()) {
            sb.append("Detected user goal ${detectedGoal.type} with missing parameters: ${detectedGoal.missingSlots}. ")
            return InferenceResult(
                intent = detectedGoal.targetIntent,
                target = finalTarget,
                confidence = 0.75f, // Moderate confidence triggering clarification
                confidenceLevel = ConfidenceLevel.ASK_FOR_CLARIFICATION,
                missingInformation = detectedGoal.missingSlots,
                clarificationQuestionMM = detectedGoal.clarificationQuestionMM,
                reasoningLog = sb.toString()
            )
        }

        // 3. Resolve Unresolved References ("အဲဒီမှာ", "အဲဒါ", "ဒီသီချင်း", "သူ့ကို")
        if (resolvedReferences.isNotEmpty()) {
            sb.append("Resolved references: $resolvedReferences. ")
            if (finalIntent == SemanticIntent.PLAY_MUSIC && finalTarget == "MUSIC" && resolvedReferences.containsKey("အဲဒီမှာ")) {
                finalTarget = resolvedReferences["အဲဒီမှာ"]
            } else if (finalIntent == SemanticIntent.REOPEN_APP && (finalTarget == null || finalTarget == "LAST_APP")) {
                finalTarget = resolvedReferences.values.firstOrNull() ?: conversationStateManager.workingMemory.lastApp
            } else if (finalIntent == SemanticIntent.CALL_CONTACT && finalTarget == null) {
                finalTarget = resolvedReferences.values.firstOrNull() ?: conversationStateManager.workingMemory.lastContact
            }
        }

        // 4. Missing Information Checks for Direct Incomplete Commands
        if (finalIntent == SemanticIntent.SET_ALARM && !extractedEntities.any { it.type == com.example.semantic.model.EntityType.TIME_EXPRESSION }) {
            missing.add("ALARM_TIME")
            clarificationQuestion = "ဘယ်အချိန် Alarm ထားပေးရမလဲခင်ဗျာ။"
            confidence = 0.70f
            sb.append("Alarm intent recognized but time parameter is missing. Triggering clarification question. ")
        } else if (finalIntent == SemanticIntent.CALL_CONTACT && finalTarget.isNullOrBlank()) {
            missing.add("CONTACT_NAME")
            clarificationQuestion = "မည်သူ့ထံ ဖုန်းခေါ်ဆိုပေးရမလဲခင်ဗျာ။"
            confidence = 0.70f
            sb.append("Call intent recognized without target contact. Triggering clarification question. ")
        }

        val confidenceLevel = when {
            confidence >= 0.85f && missing.isEmpty() -> ConfidenceLevel.EXECUTE
            confidence >= 0.60f || missing.isNotEmpty() -> ConfidenceLevel.ASK_FOR_CLARIFICATION
            else -> ConfidenceLevel.ASK_USER_TO_REPEAT
        }

        return InferenceResult(
            intent = finalIntent,
            target = finalTarget,
            confidence = confidence,
            confidenceLevel = confidenceLevel,
            missingInformation = missing,
            clarificationQuestionMM = clarificationQuestion,
            reasoningLog = sb.toString()
        )
    }
}
