package com.example.cognitive

import com.example.semantic.model.ConfidenceLevel
import com.example.semantic.model.SemanticEntity
import com.example.semantic.model.SemanticIntent

/**
 * Task Execution Step with dependencies and safety attributes
 */
data class TaskStep(
    val stepIndex: Int,
    val intent: SemanticIntent,
    val target: String? = null,
    val actionType: String = intent.name,
    val parameters: Map<String, Any?> = emptyMap(),
    val entities: List<SemanticEntity> = emptyList(),
    val dependencies: List<Int> = emptyList(), // Indices of preceding required steps
    val confidence: Float = 0.95f,
    val confidenceLevel: ConfidenceLevel = ConfidenceLevel.EXECUTE,
    val safetyLevel: String = "SAFE",
    val requiresConfirmation: Boolean = false,
    val status: String = "READY" // READY, IN_PROGRESS, COMPLETED, FAILED, WAITING_DEPENDENCY
)

/**
 * Multi-Step Plan representation
 */
data class MultiStepPlan(
    val id: String = java.util.UUID.randomUUID().toString(),
    val originalUtterance: String,
    val totalSteps: Int,
    val steps: List<TaskStep>,
    val isSequential: Boolean = true,
    val overallConfidence: Float = 0.95f,
    val speechFeedbackMM: String? = null
)

/**
 * MultiStepPlanner segments compound multi-action utterances into structured task steps.
 * E.g.,
 * “YouTube ဖွင့်ပြီး အရင်နားထောင်တဲ့သီချင်းဖွင့် ပြီးရင် အသံနည်းနည်းလျှော့ပေး”
 * -> Step 1: OPEN_APP(YOUTUBE)
 * -> Step 2: RESOLVE_PREVIOUS_MEDIA
 * -> Step 3: PLAY_MEDIA
 * -> Step 4: DECREASE_VOLUME
 */
class MultiStepPlanner(
    private val conversationStateManager: ConversationStateManager
) {

    companion object {
        private val SEQUENTIAL_SPLITTERS = listOf(
            "ပြီးရင်", "ပြီးတော့", "ပြီး", "ထို့နောက်", "ပြီးမှ", "and then", "then"
        )
    }

    fun isMultiStep(utterance: String): Boolean {
        for (splitter in SEQUENTIAL_SPLITTERS) {
            if (utterance.contains(splitter)) {
                return true
            }
        }
        return false
    }

    fun planMultiStep(
        utterance: String,
        subProcessSegment: (String) -> TaskStep?
    ): MultiStepPlan {
        // Split utterance into ordered segments
        val rawSegments = splitIntoSegments(utterance)
        val steps = mutableListOf<TaskStep>()

        var stepCounter = 1
        for (seg in rawSegments) {
            if (seg.isBlank()) continue
            val step = subProcessSegment(seg)
            if (step != null) {
                val dependencies = if (stepCounter > 1) listOf(stepCounter - 1) else emptyList()
                val adjustedStep = step.copy(
                    stepIndex = stepCounter,
                    dependencies = dependencies
                )
                steps.add(adjustedStep)
                stepCounter++
            }
        }

        // If no sub-steps detected, fallback to single step
        if (steps.isEmpty()) {
            val fallback = subProcessSegment(utterance)
            if (fallback != null) {
                steps.add(fallback.copy(stepIndex = 1))
            }
        }

        return MultiStepPlan(
            originalUtterance = utterance,
            totalSteps = steps.size,
            steps = steps,
            isSequential = true,
            overallConfidence = if (steps.isNotEmpty()) steps.map { it.confidence }.average().toFloat() else 0.85f,
            speechFeedbackMM = generateMultiStepSpeechFeedback(steps)
        )
    }

    private fun splitIntoSegments(utterance: String): List<String> {
        var segments = mutableListOf(utterance)
        for (splitter in SEQUENTIAL_SPLITTERS) {
            val nextList = mutableListOf<String>()
            for (part in segments) {
                val splitParts = part.split(splitter)
                for (sp in splitParts) {
                    val trimmed = sp.trim()
                    if (trimmed.isNotBlank()) {
                        nextList.add(trimmed)
                    }
                }
            }
            segments = nextList
        }
        return segments
    }

    private fun generateMultiStepSpeechFeedback(steps: List<TaskStep>): String {
        if (steps.isEmpty()) return "လုပ်ဆောင်ချက်များကို စတင်ဆောင်ရွက်နေပါပြီခင်ဗျာ"
        val stepDescriptions = steps.mapNotNull { it.intent.defaultDescriptionMM }
        return stepDescriptions.joinToString("၊ ထို့နောက် ") + " ကို ဆောင်ရွက်ပေးလိုက်ပါပြီခင်ဗျာ"
    }
}
