package com.example.semantic

import com.example.semantic.model.ConversationContext
import com.example.semantic.model.SemanticEntity
import com.example.semantic.model.SemanticIntent
import com.example.semantic.model.SemanticRequest
import com.example.semantic.model.SemanticResult

/**
 * Myanmar Semantic Intelligence Layer (MSIL) - Core Engine
 * Coordinates full semantic processing pipeline:
 * Voice/STT -> Text Normalization -> Intent Resolution -> Entity Extraction ->
 * Context Resolution -> Reference Resolution -> Confidence Evaluation ->
 * Reasoning -> Action Safety Validation -> Action Planning.
 */
class MyanmarSemanticEngine(
    val memoryManager: MyanmarMemoryManager = MyanmarMemoryManager()
) {

    val textNormalizer = MyanmarTextNormalizer()
    val intentResolver = MyanmarIntentResolver()
    val entityExtractor = MyanmarEntityExtractor()
    val contextResolver = MyanmarContextResolver()
    val referenceResolver = MyanmarReferenceResolver()
    val confidenceEngine = SemanticConfidenceEngine()
    val reasoningEngine = MyanmarReasoningEngine()
    val safetyValidator = ActionSafetyValidator()
    val actionPlanner = MyanmarActionPlanner()

    /**
     * Primary entry point for semantic command understanding
     */
    fun process(
        rawText: String,
        overrideContext: ConversationContext? = null
    ): SemanticResult {
        val activeContext = overrideContext ?: memoryManager.getActiveContext()

        // 1. Text Normalization
        val normOutput = textNormalizer.normalize(rawText)

        // 2. Entity Extraction
        val extractedEntities = entityExtractor.extractEntities(
            normOutput.cleanedText,
            normOutput.strippedText
        ).toMutableList()

        // 3. Reference Resolution (Anaphora: "အဲဒီမှာ", "အဲဒီ App", "ဒီသီချင်း", "သူ့ဆီ")
        val resolvedRefs = referenceResolver.resolveReferences(
            normOutput.cleanedText,
            activeContext
        )

        // 4. Intent Resolution
        val resolvedIntent = intentResolver.resolveIntent(
            normOutput.cleanedText,
            normOutput.strippedText,
            extractedEntities
        )

        // 5. Context Resolution & Slot Merging
        val contextualSlots = contextResolver.resolveContextualSlots(
            resolvedIntent.intent,
            extractedEntities,
            resolvedRefs,
            activeContext
        )

        val finalTarget = when (resolvedIntent.intent) {
            SemanticIntent.NEXT_TRACK -> "NEXT"
            SemanticIntent.PREVIOUS_TRACK -> "PREV"
            SemanticIntent.PAUSE_MEDIA -> "PAUSE"
            SemanticIntent.RESUME_MEDIA -> "RESUME"
            SemanticIntent.CALL_CONTACT -> contextualSlots.targetContact ?: contextualSlots.targetPhoneNumber ?: resolvedIntent.target
            SemanticIntent.SEND_SMS -> contextualSlots.targetContact ?: contextualSlots.targetPhoneNumber ?: resolvedIntent.target
            SemanticIntent.PLAY_MUSIC -> contextualSlots.targetMedia ?: contextualSlots.targetApp ?: resolvedIntent.target
            SemanticIntent.TOGGLE_WIFI, SemanticIntent.TOGGLE_BLUETOOTH, SemanticIntent.TOGGLE_FLASHLIGHT,
            SemanticIntent.VOLUME_UP, SemanticIntent.VOLUME_DOWN, SemanticIntent.MUTE, SemanticIntent.SET_VOLUME -> contextualSlots.targetSetting ?: resolvedIntent.target
            SemanticIntent.SET_ALARM, SemanticIntent.SET_TIMER -> contextualSlots.targetTime ?: resolvedIntent.target
            SemanticIntent.OPEN_APP, SemanticIntent.REOPEN_APP -> contextualSlots.targetApp ?: resolvedIntent.target
            else -> contextualSlots.targetApp ?: contextualSlots.targetContact ?: contextualSlots.targetSetting ?: contextualSlots.targetMedia ?: resolvedIntent.target
        }

        // 6. Confidence Assessment
        val confidenceAssessment = confidenceEngine.evaluateConfidence(
            intent = resolvedIntent.intent,
            baseConfidence = resolvedIntent.baseConfidence,
            entities = extractedEntities,
            isContextInherited = contextualSlots.isContextInherited,
            isPolite = normOutput.isPolite
        )

        // 7. Reasoning
        val reasoning = reasoningEngine.reason(
            originalText = rawText,
            normalizedText = normOutput.cleanedText,
            intent = resolvedIntent.intent,
            target = finalTarget,
            entities = extractedEntities,
            resolvedReferences = resolvedRefs.mapValues { it.value.resolvedValue },
            context = activeContext
        )

        // 8. Safety Validation
        val safetyResult = safetyValidator.validate(
            intent = resolvedIntent.intent,
            target = finalTarget,
            confidenceLevel = confidenceAssessment.level,
            parameters = emptyMap()
        )

        // 9. Action Planning
        val actionPlan = actionPlanner.planAction(
            intent = resolvedIntent.intent,
            target = finalTarget,
            confidence = confidenceAssessment.confidence,
            confidenceLevel = confidenceAssessment.level,
            entities = extractedEntities,
            safetyResult = safetyResult,
            reasoning = reasoning
        )

        // 10. Update Memory for multi-turn tracking
        memoryManager.recordInteraction(
            query = rawText,
            normalizedQuery = normOutput.cleanedText,
            targetApp = contextualSlots.targetApp,
            targetPackage = contextualSlots.targetPackage,
            contact = contextualSlots.targetContact,
            phone = contextualSlots.targetPhoneNumber,
            artist = activeContext.activeArtist,
            song = contextualSlots.targetMedia
        )

        return SemanticResult(
            originalText = rawText,
            normalizedText = normOutput.cleanedText,
            intent = resolvedIntent.intent,
            confidence = confidenceAssessment.confidence,
            confidenceLevel = confidenceAssessment.level,
            entities = extractedEntities,
            resolvedReferences = resolvedRefs.mapValues { it.value.resolvedValue },
            actionPlan = actionPlan,
            reasoning = reasoning,
            context = activeContext
        )
    }

    /**
     * Overload for structured request objects
     */
    fun processRequest(request: SemanticRequest): SemanticResult {
        return process(request.rawText, request.context)
    }
}
