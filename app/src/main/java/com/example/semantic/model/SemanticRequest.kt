package com.example.semantic.model

/**
 * Input request for the Myanmar Semantic Intelligence Layer
 */
data class SemanticRequest(
    val rawText: String,
    val context: ConversationContext? = null,
    val userId: String? = "default_user",
    val timestamp: Long = System.currentTimeMillis(),
    val source: String = "VOICE",
    val languageCode: String = "my-MM"
)
