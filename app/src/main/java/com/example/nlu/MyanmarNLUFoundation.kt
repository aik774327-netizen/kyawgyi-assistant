package com.example.nlu

import com.example.engine.CommandContextManager

/**
 * Stage 1 - Myanmar Language Understanding Foundation (Primary Orchestrator)
 *
 * Coordinates the full understanding pipeline:
 * Voice/Text Input
 *     ↓
 * MyanmarTextNormalizer
 *     ↓
 * MyanmarEntityExtractor
 *     ↓
 * MyanmarIntentEngine
 *     ↓
 * MyanmarSemanticEngine
 *     ↓
 * KyawGyiCommand (Structured representation with confidence & safety flags)
 */
class MyanmarNLUFoundation(
    private val contextManager: CommandContextManager = CommandContextManager.getInstance()
) {

    companion object {
        @Volatile
        private var instance: MyanmarNLUFoundation? = null

        fun getInstance(): MyanmarNLUFoundation {
            return instance ?: synchronized(this) {
                instance ?: MyanmarNLUFoundation().also { instance = it }
            }
        }
    }

    /**
     * Executes the complete Stage 1 NLU Pipeline on the raw input query.
     */
    fun process(
        rawInput: String,
        source: CommandSource = CommandSource.VOICE,
        overrideContext: CommandContextManager.ConversationContext? = null
    ): KyawGyiCommand {
        if (rawInput.isBlank()) {
            return KyawGyiCommand(
                intent = KyawGyiIntent.UNKNOWN,
                originalText = rawInput,
                normalizedText = "",
                confidence = 0.0f,
                source = source
            )
        }

        // 1. Text Normalization
        val normalized = MyanmarTextNormalizer.normalize(rawInput)

        // 2. Entity Extraction
        val entities = MyanmarEntityExtractor.extractEntities(rawInput, normalized)
        val entityList = MyanmarEntityExtractor.extractEntityList(rawInput, normalized)

        // 3. Conversation Context
        val activeContext = overrideContext ?: contextManager.getContext()

        // 4. Semantic Analysis
        val semanticRep = MyanmarSemanticEngine.analyzeSemantics(
            originalText = rawInput,
            normalizedText = normalized,
            entities = entities,
            conversationContext = activeContext
        )

        // 5. Intent Classification
        val intentResult = MyanmarIntentEngine.detectIntent(
            originalText = rawInput,
            normalizedText = normalized,
            entities = entities
        )

        // 6. Harmonize Confidence Score
        val combinedConfidence = calculateOverallConfidence(
            intentScore = intentResult.score,
            semanticScore = semanticRep.confidence,
            hasKeyEntities = entities.isNotEmpty()
        )

        // 7. Safety & Confirmation Rules
        val requiresConfirmation = evaluateConfirmationRequirement(
            intent = intentResult.intent,
            confidence = combinedConfidence,
            entities = entities
        )

        val parameters = mutableMapOf<String, Any?>()
        parameters.putAll(semanticRep.parameters)
        parameters["matchedPattern"] = intentResult.matchedPattern ?: "general_semantic"
        parameters["politeness"] = semanticRep.politeness.name

        return KyawGyiCommand(
            intent = intentResult.intent,
            entities = entities,
            entityList = entityList,
            parameters = parameters,
            semanticRepresentation = semanticRep,
            originalText = rawInput,
            normalizedText = normalized,
            confidence = combinedConfidence,
            requiresConfirmation = requiresConfirmation,
            source = source
        )
    }

    private fun calculateOverallConfidence(
        intentScore: Float,
        semanticScore: Float,
        hasKeyEntities: Boolean
    ): Float {
        var base = (intentScore * 0.65f) + (semanticScore * 0.35f)
        if (hasKeyEntities) {
            base = minOf(1.0f, base + 0.05f)
        }
        return (Math.round(base * 100.0) / 100.0).toFloat()
    }

    private fun evaluateConfirmationRequirement(
        intent: KyawGyiIntent,
        confidence: Float,
        entities: Map<EntityType, String>
    ): Boolean {
        // High-stakes actions require confirmation if confidence is not very high
        return when (intent) {
            KyawGyiIntent.CALL -> {
                // If calling a raw phone number or low confidence, confirm
                confidence < 0.80f || entities.containsKey(EntityType.PHONE_NUMBER)
            }
            KyawGyiIntent.SEND_SMS -> {
                // Sending SMS requires confirmation if message text is absent or confidence < 0.85
                !entities.containsKey(EntityType.MESSAGE_TEXT) || confidence < 0.85f
            }
            KyawGyiIntent.CLOSE_APP -> confidence < 0.80f
            KyawGyiIntent.UNKNOWN -> false
            else -> confidence < 0.50f
        }
    }
}
