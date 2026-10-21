package com.example.cognitive

import com.example.semantic.model.EntityType

/**
 * Memory Category / Types supported across Cognitive Layer
 */
enum class MemoryType {
    WORKING,
    EPISODIC,
    SEMANTIC,
    LONG_TERM,
    PREFERENCE,
    LEARNED_PATTERN
}

enum class MemoryPriority {
    LOW,
    NORMAL,
    HIGH,
    CRITICAL
}

/**
 * Unified Memory Record model with TTL, confidence, priority, and source tracking.
 */
data class MemoryRecord(
    val key: String,
    val value: String,
    val type: MemoryType,
    val entityType: EntityType? = null,
    val confidence: Float = 1.0f,
    val priority: MemoryPriority = MemoryPriority.NORMAL,
    val ttlMs: Long = 5 * 60 * 1000L, // Default 5 minutes
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val source: String = "USER_VOICE",
    val metadata: Map<String, String> = emptyMap()
) {
    fun isExpired(): Boolean {
        if (ttlMs <= 0) return false // Indefinite retention
        return (System.currentTimeMillis() - updatedAt) > ttlMs
    }
}
