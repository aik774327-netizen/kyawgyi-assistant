package com.example.semantic.model

/**
 * Output of the Myanmar Semantic Intelligence Layer
 */
data class SemanticResult(
    val originalText: String,
    val normalizedText: String,
    val intent: SemanticIntent,
    val confidence: Float,
    val confidenceLevel: ConfidenceLevel,
    val entities: List<SemanticEntity>,
    val resolvedReferences: Map<String, String>,
    val actionPlan: ActionPlan,
    val reasoning: String,
    val context: ConversationContext,
    val executionRecommendation: String = when (confidenceLevel) {
        ConfidenceLevel.EXECUTE -> if (actionPlan.requiresConfirmation) "PROMPT_CONFIRMATION" else "EXECUTE_IMMEDIATELY"
        ConfidenceLevel.ASK_FOR_CLARIFICATION -> "ASK_FOR_CLARIFICATION"
        ConfidenceLevel.ASK_USER_TO_REPEAT -> "ASK_USER_TO_REPEAT"
    }
)
