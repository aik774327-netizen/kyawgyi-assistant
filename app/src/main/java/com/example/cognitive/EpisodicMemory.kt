package com.example.cognitive

import com.example.semantic.model.SemanticEntity
import com.example.semantic.model.SemanticIntent
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Episodic Event representing historical interactions, executed actions, and responses.
 */
data class EpisodicEvent(
    val id: String = java.util.UUID.randomUUID().toString(),
    val rawUtterance: String,
    val normalizedUtterance: String,
    val intent: SemanticIntent,
    val target: String? = null,
    val actionType: String = intent.name,
    val parameters: Map<String, Any?> = emptyMap(),
    val entities: List<SemanticEntity> = emptyList(),
    val outcomeStatus: String = "SUCCESS", // SUCCESS, CLARIFIED, FAILED, CANCELLED
    val timestamp: Long = System.currentTimeMillis(),
    val turnNumber: Int = 0
)

/**
 * EpisodicMemory stores recent interaction history events to enable
 * multi-turn referencing, previous action recall, and sequential context resolution.
 */
class EpisodicMemory(
    private val maxCapacity: Int = 50
) {

    private val events = CopyOnWriteArrayList<EpisodicEvent>()

    fun recordEvent(event: EpisodicEvent) {
        events.add(0, event)
        if (events.size > maxCapacity) {
            // Trim oldest
            while (events.size > maxCapacity) {
                events.removeAt(events.size - 1)
            }
        }
    }

    fun getLastEvent(): EpisodicEvent? {
        return events.firstOrNull()
    }

    fun findLastEventMatching(predicate: (EpisodicEvent) -> Boolean): EpisodicEvent? {
        return events.firstOrNull(predicate)
    }

    fun findLastEventByIntent(intent: SemanticIntent): EpisodicEvent? {
        return events.firstOrNull { it.intent == intent }
    }

    fun findLastEventWithTarget(target: String): EpisodicEvent? {
        return events.firstOrNull { it.target?.equals(target, ignoreCase = true) == true }
    }

    fun getRecentEvents(count: Int = 5): List<EpisodicEvent> {
        return events.take(count)
    }

    fun clear() {
        events.clear()
    }

    fun size(): Int = events.size
}
