package com.example.semantic.model

/**
 * Memory Item representing remembered conversational entities and references.
 */
data class MemoryItem(
    val key: String,
    val value: String,
    val entityType: EntityType,
    val timestamp: Long = System.currentTimeMillis(),
    val turnIndex: Int = 0,
    val confidence: Float = 1.0f,
    val metadata: Map<String, String> = emptyMap()
)
