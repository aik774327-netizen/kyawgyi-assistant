package com.example.semantic.analyzer

import com.example.actions.ActionRegistry
import com.example.semantic.ActionSafetyValidator
import com.example.semantic.MyanmarActionPlanner
import com.example.semantic.MyanmarContextResolver
import com.example.semantic.MyanmarEntityExtractor
import com.example.semantic.MyanmarReasoningEngine
import com.example.semantic.MyanmarReferenceResolver
import com.example.semantic.MyanmarTextNormalizer
import com.example.semantic.classifier.IntentClassifier
import com.example.semantic.model.ActionPlan
import com.example.semantic.model.ConfidenceLevel
import com.example.semantic.model.ConversationContext
import com.example.semantic.model.EntityType
import com.example.semantic.model.SemanticEntity
import com.example.semantic.model.SemanticIntent
import java.util.Locale

/**
 * Myanmar Semantic Analyzer for Natural Language Understanding.
 * Orchestrates Myanmar Unicode and phonetic normalization, colloquial intent classification,
 * entity extraction, multi-turn context resolution, safety verification, and ActionPlan generation.
 */
class MyanmarSemanticAnalyzer(
    private val textNormalizer: MyanmarTextNormalizer = MyanmarTextNormalizer(),
    private val intentClassifier: IntentClassifier = IntentClassifier(),
    private val entityExtractor: MyanmarEntityExtractor = MyanmarEntityExtractor(),
    private val referenceResolver: MyanmarReferenceResolver = MyanmarReferenceResolver(),
    private val contextResolver: MyanmarContextResolver = MyanmarContextResolver(),
    private val reasoningEngine: MyanmarReasoningEngine = MyanmarReasoningEngine(),
    private val safetyValidator: ActionSafetyValidator = ActionSafetyValidator(),
    private val actionPlanner: MyanmarActionPlanner = MyanmarActionPlanner()
) {

    /**
     * Analyzes raw user input and returns a fully formed, safe, structured ActionPlan.
     *
     * @param rawQuery The original spoken or text transcription from the user.
     * @param context Active multi-turn conversational context (session state, last used app, last contact).
     * @param isConfirmed Whether high-risk confirmation was already given by the user in this session.
     * @return Structured ActionPlan ready for execution or confirmation prompt.
     */
    fun analyze(
        rawQuery: String,
        context: ConversationContext = ConversationContext(),
        isConfirmed: Boolean = false
    ): ActionPlan {
        // 1. Normalization (Unicode canonicalization, tone normalization, phonetic mappings, colloquial handling)
        val normalized = textNormalizer.normalize(rawQuery)

        // 2. Intent Classification with Multi-Turn Context
        val classification = intentClassifier.classify(
            normalizedQuery = normalized.cleanedText,
            rawQuery = rawQuery,
            context = context
        )

        // 3. Entity Extraction
        val entities = entityExtractor.extractEntities(normalized.cleanedText, normalized.strippedText).toMutableList()

        // 4. Multi-Turn Context & Anaphora Resolution
        val resolvedRefs = referenceResolver.resolveReferences(normalized.cleanedText, context)
        val contextualSlots = contextResolver.resolveContextualSlots(
            intent = classification.intent,
            entities = entities,
            resolvedReferences = resolvedRefs,
            context = context
        )

        var resolvedTarget = classification.target
        if (resolvedTarget == null || resolvedTarget == "UNKNOWN_CONTACT") {
            resolvedTarget = contextualSlots.targetApp ?: contextualSlots.targetContact ?: contextualSlots.targetMedia ?: classification.target
        }

        // 5. Confidence Calculation & Thresholding
        val confidence = classification.confidence
        val confidenceLevel = when {
            confidence >= 0.85f -> ConfidenceLevel.EXECUTE
            confidence >= 0.60f -> ConfidenceLevel.ASK_FOR_CLARIFICATION
            else -> ConfidenceLevel.ASK_USER_TO_REPEAT
        }

        // 6. Action Registry & Safety Validation
        val actionType = mapIntentToActionType(classification.intent, resolvedTarget)
        val actionDef = ActionRegistry.getAction(actionType)

        val params = mutableMapOf<String, Any?>()
        if (resolvedTarget != null) {
            params["target"] = resolvedTarget
        }
        if (contextualSlots.targetPackage != null) {
            params["package"] = contextualSlots.targetPackage
        }
        if (contextualSlots.targetPhoneNumber != null) {
            params["phoneNumber"] = contextualSlots.targetPhoneNumber
        }

        val safetyResult = safetyValidator.validate(
            intent = classification.intent,
            target = resolvedTarget,
            confidenceLevel = confidenceLevel,
            parameters = params
        )

        // 7. Deductive Reasoning Explanation
        val reasoning = reasoningEngine.reason(
            originalText = rawQuery,
            normalizedText = normalized.cleanedText,
            intent = classification.intent,
            target = resolvedTarget,
            entities = entities,
            resolvedReferences = resolvedRefs.mapValues { it.value.resolvedValue },
            context = context
        )

        // 8. Construct structured ActionPlan
        val basePlan = actionPlanner.planAction(
            intent = classification.intent,
            target = resolvedTarget,
            confidence = confidence,
            confidenceLevel = confidenceLevel,
            entities = entities,
            safetyResult = safetyResult,
            reasoning = reasoning
        )

        // Enrich parameters with Registry expectations
        val enrichedParams = basePlan.parameters.toMutableMap()
        if (resolvedTarget != null) {
            enrichedParams["target"] = resolvedTarget
        }
        if (actionDef != null) {
            enrichedParams["riskLevel"] = actionDef.riskLevel.name
            enrichedParams["category"] = actionDef.category.name
            enrichedParams["requiredPermissions"] = actionDef.requiredPermissions
        }

        val requiresConfirmation = if (isConfirmed) false else (actionDef?.defaultRequiresConfirmation ?: safetyResult.requiresConfirmation)
        val prompt = if (requiresConfirmation) {
            ActionRegistry.getConfirmationPrompt(actionType, target = resolvedTarget) ?: safetyResult.confirmationPromptMM
        } else null

        return basePlan.copy(
            actionType = actionType,
            parameters = enrichedParams,
            requiresConfirmation = requiresConfirmation,
            confirmationPrompt = prompt
        )
    }

    /**
     * Map SemanticIntent to ActionRegistry actionType.
     */
    private fun mapIntentToActionType(intent: SemanticIntent, target: String?): String {
        return when (intent) {
            SemanticIntent.OPEN_APP, SemanticIntent.REOPEN_APP -> "OPEN_APP"
            SemanticIntent.CALL_CONTACT -> "CALL"
            SemanticIntent.SEND_SMS -> "SEND_MESSAGE"
            SemanticIntent.VOLUME_UP -> "VOLUME_UP"
            SemanticIntent.VOLUME_DOWN -> "VOLUME_DOWN"
            SemanticIntent.TOGGLE_FLASHLIGHT -> "FLASHLIGHT"
            SemanticIntent.NAVIGATE_BACK -> "BACK"
            SemanticIntent.NAVIGATE_HOME -> "HOME"
            SemanticIntent.SHOW_RECENTS -> "RECENTS"
            SemanticIntent.READ_SCREEN -> "READ_SCREEN"
            SemanticIntent.TOGGLE_WIFI -> "WIFI_TOGGLE"
            SemanticIntent.TOGGLE_BLUETOOTH -> "BLUETOOTH_TOGGLE"
            SemanticIntent.SET_ALARM -> "SET_ALARM"
            SemanticIntent.SET_TIMER -> "SET_TIMER"
            SemanticIntent.DELETE_ITEM, SemanticIntent.UNINSTALL_APP -> "DELETE"
            SemanticIntent.SEARCH -> "SEARCH"
            SemanticIntent.OPEN_CAMERA -> "OPEN_APP"
            SemanticIntent.OPEN_PHOTOS -> "OPEN_APP"
            SemanticIntent.OPEN_MAPS -> "OPEN_APP"
            SemanticIntent.OPEN_SETTINGS -> "OPEN_APP"
            else -> "CUSTOM"
        }
    }
}
