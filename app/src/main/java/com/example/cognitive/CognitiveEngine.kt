package com.example.cognitive

import com.example.data.MemoryDao
import com.example.semantic.MyanmarSemanticEngine
import com.example.semantic.model.ActionPlan
import com.example.semantic.model.ConfidenceLevel
import com.example.semantic.model.SemanticEntity
import com.example.semantic.model.SemanticIntent
import com.example.semantic.model.SemanticResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

/**
 * Output of the Myanmar Cognitive Context & Reasoning Layer
 */
data class CognitiveResult(
    val originalUtterance: String,
    val normalizedUtterance: String,
    val intent: SemanticIntent,
    val target: String?,
    val confidence: Float,
    val confidenceLevel: ConfidenceLevel,
    val discourseType: GoalManager.DiscourseType,
    val detectedGoal: Goal?,
    val missingInformation: List<String>,
    val resolvedReferences: Map<String, String>,
    val multiStepPlan: MultiStepPlan?,
    val actionPlan: ActionPlan,
    val reasoningLog: String,
    val speechFeedbackMM: String?
)

/**
 * CognitiveEngine coordinates the Myanmar Cognitive Context & Reasoning Layer:
 * - Working Memory
 * - Episodic Memory
 * - Semantic Memory
 * - Long-Term Memory
 * - Reference Resolution
 * - Goal & Missing Information Detection
 * - Multi-Step Task Planning
 * - Safety Validation
 * - Safe Learning Engine
 */
class CognitiveEngine(
    val semanticEngine: MyanmarSemanticEngine = MyanmarSemanticEngine(),
    val memoryDao: MemoryDao? = null,
    val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {

    val workingMemory = WorkingMemory()
    val episodicMemory = EpisodicMemory()
    val semanticMemory = SemanticMemory()
    val longTermMemory = LongTermMemory(memoryDao, coroutineScope)
    val conversationStateManager = ConversationStateManager(workingMemory, episodicMemory, semanticMemory, longTermMemory)
    val goalManager = GoalManager()
    val reasoningEngine = ReasoningEngine(conversationStateManager, goalManager)
    val safetyGuard = CognitiveSafetyGuard()
    val taskPlanner = TaskPlanner(safetyGuard)
    val multiStepPlanner = MultiStepPlanner(conversationStateManager)
    val learningEngine = LearningEngine(semanticMemory, longTermMemory)

    /**
     * Primary entry point for cognitive contextual processing
     */
    fun process(rawUtterance: String): CognitiveResult {
        val normOutput = semanticEngine.textNormalizer.normalize(rawUtterance)
        val cleaned = normOutput.cleanedText
        val isFollowUp = conversationStateManager.isFollowUpCommand(cleaned)
        val discourse = goalManager.classifyDiscourse(cleaned, isFollowUp)

        // Check if multi-step sequential utterance (e.g. "YouTube ဖွင့်ပြီး သီချင်းဖွင့်ပြီး အသံလျှော့")
        if (multiStepPlanner.isMultiStep(cleaned)) {
            val plan = multiStepPlanner.planMultiStep(cleaned) { segment ->
                val subResult = processSingleUtterance(segment, discourse)
                val stepTarget = subResult.actionPlan.target
                taskPlanner.buildTaskStep(
                    intent = subResult.intent,
                    target = stepTarget,
                    confidence = subResult.confidence,
                    confidenceLevel = subResult.confidenceLevel,
                    entities = subResult.entities,
                    parameters = mapOf("target" to (stepTarget ?: ""))
                )
            }

            val firstStep = plan.steps.firstOrNull()
            val finalIntent = firstStep?.intent ?: SemanticIntent.UNKNOWN
            val finalTarget = firstStep?.target

            val actionPlan = taskPlanner.planSingleTask(
                intent = finalIntent,
                target = finalTarget,
                confidence = plan.overallConfidence,
                confidenceLevel = ConfidenceLevel.EXECUTE,
                entities = emptyList(),
                parameters = mapOf("multiStepPlan" to plan),
                reasoning = "Multi-step task pipeline with ${plan.totalSteps} steps"
            )

            return CognitiveResult(
                originalUtterance = rawUtterance,
                normalizedUtterance = cleaned,
                intent = finalIntent,
                target = finalTarget,
                confidence = plan.overallConfidence,
                confidenceLevel = ConfidenceLevel.EXECUTE,
                discourseType = discourse,
                detectedGoal = null,
                missingInformation = emptyList(),
                resolvedReferences = emptyMap(),
                multiStepPlan = plan,
                actionPlan = actionPlan,
                reasoningLog = "Planned multi-step task with ${plan.totalSteps} sequential steps",
                speechFeedbackMM = plan.speechFeedbackMM
            )
        }

        // Process Single Utterance with Cognitive Pipeline
        val baseSemantic = processSingleUtterance(cleaned, discourse)

        // 1. Reference Resolution across Working Memory, Episodic History, and Semantic Knowledge
        val resolvedRefs = conversationStateManager.resolveReference(cleaned)

        // 2. Cognitive Reasoning & Missing Information Inference
        val inference = reasoningEngine.infer(
            rawUtterance = rawUtterance,
            normalizedUtterance = cleaned,
            semanticIntent = baseSemantic.intent,
            semanticTarget = baseSemantic.actionPlan.target,
            extractedEntities = baseSemantic.entities,
            resolvedReferences = resolvedRefs,
            discourseType = discourse,
            pendingGoal = goalManager.getPendingGoal()
        )

        // 3. Task Planning and Safety Guard Evaluation
        val actionPlan = taskPlanner.planSingleTask(
            intent = inference.intent,
            target = inference.target,
            confidence = inference.confidence,
            confidenceLevel = inference.confidenceLevel,
            entities = baseSemantic.entities,
            parameters = baseSemantic.actionPlan.parameters + mapOf("target" to (inference.target ?: "")),
            reasoning = inference.reasoningLog,
            clarificationPrompt = inference.clarificationQuestionMM
        )

        // 4. Update Conversation State & Memory
        conversationStateManager.recordTurn(
            rawUtterance = rawUtterance,
            normalizedUtterance = cleaned,
            intent = inference.intent,
            target = inference.target,
            parameters = actionPlan.parameters,
            entities = baseSemantic.entities,
            outcomeStatus = if (inference.confidenceLevel == ConfidenceLevel.EXECUTE) "SUCCESS" else "CLARIFY"
        )

        // 5. Learn from successful safe interaction
        if (inference.confidenceLevel == ConfidenceLevel.EXECUTE) {
            learningEngine.learnFromInteraction(
                rawUtterance = rawUtterance,
                intent = inference.intent,
                target = inference.target,
                isSuccess = true
            )
        }

        return CognitiveResult(
            originalUtterance = rawUtterance,
            normalizedUtterance = cleaned,
            intent = inference.intent,
            target = inference.target,
            confidence = inference.confidence,
            confidenceLevel = inference.confidenceLevel,
            discourseType = discourse,
            detectedGoal = goalManager.getPendingGoal(),
            missingInformation = inference.missingInformation,
            resolvedReferences = resolvedRefs,
            multiStepPlan = null,
            actionPlan = actionPlan,
            reasoningLog = inference.reasoningLog,
            speechFeedbackMM = actionPlan.speechFeedbackMM
        )
    }

    private fun processSingleUtterance(
        cleaned: String,
        discourse: GoalManager.DiscourseType
    ): SemanticResult {
        // Check semantic memory concepts first
        val concept = semanticMemory.findConceptForUtterance(cleaned)
        if (concept != null && concept.targetIntent != null) {
            val semRes = semanticEngine.process(cleaned)
            return semRes.copy(
                intent = concept.targetIntent,
                actionPlan = semRes.actionPlan.copy(
                    intent = concept.targetIntent,
                    target = concept.defaultTarget ?: semRes.actionPlan.target
                )
            )
        }
        return semanticEngine.process(cleaned)
    }

    fun reset() {
        workingMemory.reset()
        episodicMemory.clear()
        goalManager.clear()
        semanticEngine.memoryManager.reset()
    }
}
