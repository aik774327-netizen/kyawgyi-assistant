package com.example.cognitive

import com.example.semantic.model.SemanticIntent
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Learned Command Pattern
 */
data class LearnedPattern(
    val triggerPhrase: String,
    val targetIntent: SemanticIntent,
    val target: String?,
    val usageCount: Int = 1,
    val confidence: Float = 0.90f,
    val lastUsedTimestamp: Long = System.currentTimeMillis()
)

/**
 * LearningEngine provides safe on-device learning of user command preferences,
 * frequent task patterns, and semantic mappings.
 *
 * Privacy Policy Enforcement:
 * - Strictly forbids learning credentials, passwords, authentication codes, or private message texts.
 */
class LearningEngine(
    private val semanticMemory: SemanticMemory,
    private val longTermMemory: LongTermMemory
) {

    private val appUsageFrequency = ConcurrentHashMap<String, AtomicInteger>()
    private val commandPatterns = ConcurrentHashMap<String, LearnedPattern>()

    companion object {
        // Redaction filter for sensitive keywords
        private val SENSITIVE_PATTERNS = listOf(
            "password", "passcode", "စကားဝှက်", "ပင်နံပါတ်", "pin", "otp", "code",
            "credit", "card", "cvv", "ငွေလွှဲ", "secret", "private key"
        )
    }

    fun learnFromInteraction(
        rawUtterance: String,
        intent: SemanticIntent,
        target: String?,
        isSuccess: Boolean
    ) {
        if (!isSuccess || isSensitive(rawUtterance)) return

        // 1. Learn App Usage Frequency
        if ((intent == SemanticIntent.OPEN_APP || intent == SemanticIntent.REOPEN_APP) && target != null) {
            val count = appUsageFrequency.computeIfAbsent(target.uppercase(Locale.ROOT)) { AtomicInteger(0) }
                .incrementAndGet()
            if (count >= 3) {
                // Set as user preferred app for domain
                if (target.contains("YOUTUBE", ignoreCase = true)) {
                    longTermMemory.setPreference("PREFERRED_MUSIC_APP", "YouTube")
                }
            }
        }

        // 2. Learn User Phrasing Variation into Semantic Memory
        if (target != null && rawUtterance.length in 4..40) {
            val clean = rawUtterance.trim()
            if (intent == SemanticIntent.OPEN_APP && target == "CAMERA") {
                semanticMemory.learnSynonym("CAMERA", clean)
            } else if (intent == SemanticIntent.VOLUME_UP) {
                semanticMemory.learnSynonym("INCREASE_VOLUME", clean)
            } else if (intent == SemanticIntent.VOLUME_DOWN) {
                semanticMemory.learnSynonym("DECREASE_VOLUME", clean)
            }

            commandPatterns[clean.lowercase(Locale.ROOT)] = LearnedPattern(
                triggerPhrase = clean,
                targetIntent = intent,
                target = target,
                usageCount = (commandPatterns[clean.lowercase(Locale.ROOT)]?.usageCount ?: 0) + 1
            )
        }
    }

    fun findLearnedPattern(utterance: String): LearnedPattern? {
        val clean = utterance.trim().lowercase(Locale.ROOT)
        return commandPatterns[clean]
    }

    fun isSensitive(text: String): Boolean {
        val lower = text.lowercase(Locale.ROOT)
        return SENSITIVE_PATTERNS.any { lower.contains(it) }
    }

    fun clearLearnedData() {
        appUsageFrequency.clear()
        commandPatterns.clear()
    }
}
