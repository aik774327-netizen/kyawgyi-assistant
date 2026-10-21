package com.example.semantic.classifier

import com.example.semantic.dictionary.MyanmarAppDictionary
import com.example.semantic.dictionary.MyanmarIntentDictionary
import com.example.semantic.dictionary.MyanmarSynonymDictionary
import com.example.semantic.model.ConversationContext
import com.example.semantic.model.SemanticIntent
import java.util.Locale

/**
 * Result of semantic intent classification.
 */
data class ClassificationResult(
    val intent: SemanticIntent,
    val target: String? = null,
    val confidence: Float,
    val matchedKeywords: List<String> = emptyList(),
    val verbStem: String? = null,
    val isAnaphoric: Boolean = false,
    val isDirectMatch: Boolean = false,
    val rawQuery: String = "",
    val normalizedQuery: String = "",
    val reasoning: String = ""
)

/**
 * Intelligent Intent Classifier for Myanmar Spoken & Text Input.
 * Resolves direct, polite, colloquial, loanword-mixed, and contextual anaphoric commands.
 */
class IntentClassifier {

    /**
     * Classifies a normalized Myanmar user input into a strongly-typed SemanticIntent.
     * Incorporates conversational context for pronoun & anaphora resolution.
     */
    fun classify(
        normalizedQuery: String,
        rawQuery: String = normalizedQuery,
        context: ConversationContext? = null
    ): ClassificationResult {
        val cleanQuery = normalizedQuery.trim().lowercase(Locale.ROOT)
        if (cleanQuery.isEmpty()) {
            return ClassificationResult(
                intent = SemanticIntent.UNKNOWN,
                confidence = 0.0f,
                rawQuery = rawQuery,
                normalizedQuery = normalizedQuery,
                reasoning = "Empty input query"
            )
        }

        // 1. Check for deictic/anaphoric references (e.g. "အဲ့ဒါဖွင့်", "အဲ့ဒီ app ထဲဝင်", "သူ့ဆီ ဖုန်းဆက်")
        val isAnaphoric = isAnaphoricQuery(cleanQuery)
        if (isAnaphoric && context != null) {
            val anaphoraResult = resolveAnaphoricIntent(cleanQuery, context, rawQuery)
            if (anaphoraResult != null) {
                return anaphoraResult
            }
        }

        // 2. High-priority fast-path exact & pattern classification
        // A. Volume Controls
        if (isVolumeDown(cleanQuery)) {
            return ClassificationResult(
                intent = SemanticIntent.VOLUME_DOWN,
                target = "VOLUME",
                confidence = 0.98f,
                matchedKeywords = listOf("အသံတိုး", "အသံလျှော့"),
                isDirectMatch = true,
                rawQuery = rawQuery,
                normalizedQuery = normalizedQuery,
                reasoning = "Volume decrease command pattern matched"
            )
        }

        if (isVolumeUp(cleanQuery)) {
            return ClassificationResult(
                intent = SemanticIntent.VOLUME_UP,
                target = "VOLUME",
                confidence = 0.98f,
                matchedKeywords = listOf("အသံကျယ်", "အသံမြှင့်", "အသံချဲ့"),
                isDirectMatch = true,
                rawQuery = rawQuery,
                normalizedQuery = normalizedQuery,
                reasoning = "Volume increase command pattern matched"
            )
        }

        // B. Navigation actions (BACK, HOME, RECENTS)
        if (isBackNavigation(cleanQuery)) {
            return ClassificationResult(
                intent = SemanticIntent.NAVIGATE_BACK,
                target = "BACK",
                confidence = 0.98f,
                matchedKeywords = listOf("back", "နောက်", "နောက်ပြန်"),
                isDirectMatch = true,
                rawQuery = rawQuery,
                normalizedQuery = normalizedQuery,
                reasoning = "Back navigation pattern matched"
            )
        }

        if (isHomeNavigation(cleanQuery)) {
            return ClassificationResult(
                intent = SemanticIntent.NAVIGATE_HOME,
                target = "HOME",
                confidence = 0.98f,
                matchedKeywords = listOf("home", "ပင်မ", "အိမ်"),
                isDirectMatch = true,
                rawQuery = rawQuery,
                normalizedQuery = normalizedQuery,
                reasoning = "Home navigation pattern matched"
            )
        }

        if (isRecentsNavigation(cleanQuery)) {
            return ClassificationResult(
                intent = SemanticIntent.SHOW_RECENTS,
                target = "RECENTS",
                confidence = 0.95f,
                matchedKeywords = listOf("recent", "လတ်တလော"),
                isDirectMatch = true,
                rawQuery = rawQuery,
                normalizedQuery = normalizedQuery,
                reasoning = "Recent apps pattern matched"
            )
        }

        // C. Flashlight / Torch
        if (isFlashlight(cleanQuery)) {
            return ClassificationResult(
                intent = SemanticIntent.TOGGLE_FLASHLIGHT,
                target = "FLASHLIGHT",
                confidence = 0.97f,
                matchedKeywords = listOf("ဓာတ်မီး", "flashlight", "torch", "မီးလင်း"),
                isDirectMatch = true,
                rawQuery = rawQuery,
                normalizedQuery = normalizedQuery,
                reasoning = "Flashlight control pattern matched"
            )
        }

        // D. Wi-Fi & Bluetooth
        if (MyanmarSynonymDictionary.WIFI_KEYWORDS.any { cleanQuery.contains(it) }) {
            return ClassificationResult(
                intent = SemanticIntent.TOGGLE_WIFI,
                target = "WIFI",
                confidence = 0.95f,
                matchedKeywords = listOf("wifi"),
                rawQuery = rawQuery,
                normalizedQuery = normalizedQuery,
                reasoning = "Wi-Fi toggle pattern matched"
            )
        }

        if (MyanmarSynonymDictionary.BLUETOOTH_KEYWORDS.any { cleanQuery.contains(it) }) {
            return ClassificationResult(
                intent = SemanticIntent.TOGGLE_BLUETOOTH,
                target = "BLUETOOTH",
                confidence = 0.95f,
                matchedKeywords = listOf("bluetooth"),
                rawQuery = rawQuery,
                normalizedQuery = normalizedQuery,
                reasoning = "Bluetooth toggle pattern matched"
            )
        }

        // E. Call & Message
        if (isCallIntent(cleanQuery)) {
            val targetContact = extractCallTarget(cleanQuery)
            return ClassificationResult(
                intent = SemanticIntent.CALL_CONTACT,
                target = targetContact ?: context?.activeContactName ?: "UNKNOWN_CONTACT",
                confidence = if (targetContact != null || context?.activeContactName != null) 0.94f else 0.70f,
                matchedKeywords = listOf("ဖုန်းဆက်", "ဖုန်းခေါ်", "call"),
                rawQuery = rawQuery,
                normalizedQuery = normalizedQuery,
                reasoning = "Phone call pattern matched"
            )
        }

        if (isMessageIntent(cleanQuery)) {
            val targetContact = extractMessageTarget(cleanQuery)
            return ClassificationResult(
                intent = SemanticIntent.SEND_SMS,
                target = targetContact ?: context?.activeContactName ?: "UNKNOWN_CONTACT",
                confidence = if (targetContact != null || context?.activeContactName != null) 0.92f else 0.70f,
                matchedKeywords = listOf("မက်ဆေ့ခ်ျ", "စာပို့", "sms"),
                rawQuery = rawQuery,
                normalizedQuery = normalizedQuery,
                reasoning = "Message dispatch pattern matched"
            )
        }

        // F. App Launch (Direct, mixed Burmese + English, phonetic synonyms)
        val appMatch = matchAppIntent(cleanQuery)
        if (appMatch != null) {
            return appMatch.copy(rawQuery = rawQuery, normalizedQuery = normalizedQuery)
        }

        // G. Delete Data
        if (cleanQuery.contains("ဖျက်") || cleanQuery.contains("delete") || cleanQuery.contains("ဖျက်ပစ်")) {
            return ClassificationResult(
                intent = SemanticIntent.DELETE_ITEM,
                target = "SELECTION",
                confidence = 0.90f,
                matchedKeywords = listOf("ဖျက်", "delete"),
                rawQuery = rawQuery,
                normalizedQuery = normalizedQuery,
                reasoning = "Delete data pattern matched"
            )
        }

        // H. Fallback to dictionary pattern matcher
        val dictionaryMatch = MyanmarIntentDictionary.PATTERNS.maxByOrNull { pattern ->
            var score = 0.0f
            val hasKeyword = pattern.keywords.any { cleanQuery.contains(it.lowercase(Locale.ROOT)) }
            val hasVerb = pattern.verbStems.any { cleanQuery.contains(it.lowercase(Locale.ROOT)) }
            if (hasKeyword && hasVerb) score += 0.8f
            else if (hasKeyword) score += 0.5f
            score
        }

        if (dictionaryMatch != null) {
            val hasKeyword = dictionaryMatch.keywords.any { cleanQuery.contains(it.lowercase(Locale.ROOT)) }
            if (hasKeyword) {
                return ClassificationResult(
                    intent = dictionaryMatch.intent,
                    target = dictionaryMatch.defaultTarget,
                    confidence = dictionaryMatch.minConfidence,
                    matchedKeywords = dictionaryMatch.keywords.filter { cleanQuery.contains(it.lowercase(Locale.ROOT)) },
                    rawQuery = rawQuery,
                    normalizedQuery = normalizedQuery,
                    reasoning = "Dictionary pattern matched: ${dictionaryMatch.intent}"
                )
            }
        }

        // Unknown intent
        return ClassificationResult(
            intent = SemanticIntent.UNKNOWN,
            target = null,
            confidence = 0.30f,
            rawQuery = rawQuery,
            normalizedQuery = normalizedQuery,
            reasoning = "No known intent pattern matched"
        )
    }

    private fun isAnaphoricQuery(query: String): Boolean {
        return MyanmarSynonymDictionary.APP_REFERENCES.any { query.contains(it) } ||
                query.startsWith("အဲ့ဒါ") || query.startsWith("အဲဒါ") ||
                query.contains("အဲ့ဒီထဲ") || query.contains("အဲဒီထဲ") ||
                query.contains("သူ့ဆီ") || query.contains("သူ့ကို")
    }

    private fun resolveAnaphoricIntent(
        query: String,
        context: ConversationContext,
        rawQuery: String
    ): ClassificationResult? {
        // App reference: "အဲ့ဒါဖွင့်", "အဲ့ဒီ app ထဲဝင်", "အဲ့ဒါ ပြန်ဖွင့်"
        if (MyanmarSynonymDictionary.APP_REFERENCES.any { query.contains(it) } ||
            query.contains("အဲ့ဒီ app") || query.contains("အဲ့ဒါ") || query.contains("အဲဒီဆော့ဖ်ဝဲ")) {
            val appName = context.activeAppNameMM ?: context.activeAppPackage
            if (appName != null) {
                return ClassificationResult(
                    intent = SemanticIntent.OPEN_APP,
                    target = appName.uppercase(Locale.ROOT),
                    confidence = 0.92f,
                    matchedKeywords = listOf("အဲ့ဒီ app", "အဲ့ဒါ"),
                    isAnaphoric = true,
                    rawQuery = rawQuery,
                    normalizedQuery = query,
                    reasoning = "Anaphoric app reference resolved to: $appName"
                )
            }
        }

        // Contact reference: "သူ့ဆီ ဖုန်းဆက်", "သူ့ကို စာပို့"
        if (query.contains("သူ့ဆီ") || query.contains("သူ့ကို")) {
            val contact = context.activeContactName
            if (contact != null) {
                val isCall = query.contains("ဖုန်း") || query.contains("ခေါ်") || query.contains("ဆက်")
                return ClassificationResult(
                    intent = if (isCall) SemanticIntent.CALL_CONTACT else SemanticIntent.SEND_SMS,
                    target = contact,
                    confidence = 0.91f,
                    matchedKeywords = listOf("သူ့ဆီ"),
                    isAnaphoric = true,
                    rawQuery = rawQuery,
                    normalizedQuery = query,
                    reasoning = "Anaphoric contact reference resolved to: $contact"
                )
            }
        }

        return null
    }

    private fun isVolumeDown(query: String): Boolean {
        val keywords = listOf("အသံတိုး", "အသံလျှော့", "အသံချ", "အသံဖြည်းဖြည်း", "volume down", "အသံနည်းနည်းတိုး")
        return keywords.any { query.contains(it) }
    }

    private fun isVolumeUp(query: String): Boolean {
        val keywords = listOf("အသံကျယ်", "အသံမြှင့်", "အသံချဲ့", "volume up", "အသံတက်")
        return keywords.any { query.contains(it) }
    }

    private fun isBackNavigation(query: String): Boolean {
        return query.contains("နောက်သို့") || query.contains("နောက်ပြန်") ||
                query == "back" || query.contains("back သွား") || query.contains("နောက်ဆုတ်")
    }

    private fun isHomeNavigation(query: String): Boolean {
        return query.contains("ပင်မ") || query.contains("ပင်မမျက်နှာပြင်") ||
                query == "home" || query.contains("home သွား") || query.contains("အိမ်ပြန်") || query.contains("home ပြန်")
    }

    private fun isRecentsNavigation(query: String): Boolean {
        return query.contains("recent") || query.contains("လတ်တလော app") || query.contains("ဖွင့်ထားသော app")
    }

    private fun isFlashlight(query: String): Boolean {
        val keywords = listOf("ဓာတ်မီး", "ဓါတ်မီး", "flashlight", "torch", "မီးလင်း", "မီးလင်းပေး", "မီးဖွင့်", "မီးပိတ်")
        return keywords.any { query.contains(it) }
    }

    private fun isCallIntent(query: String): Boolean {
        return (query.contains("ဖုန်း") && (query.contains("ဆက်") || query.contains("ခေါ်"))) ||
                query.contains("call") || query.startsWith("ခေါ်")
    }

    private fun isMessageIntent(query: String): Boolean {
        return query.contains("မက်ဆေ့ခ်ျ") || query.contains("စာပို့") || query.contains("sms")
    }

    private fun extractCallTarget(query: String): String? {
        val cleaned = query.replace("ကျေးဇူးပြုပြီး", "").replace("ပါ", "").replace("ဦး", "").trim()
        val match = Regex("(.*?)(?:ဆီ|ဆီသို့|ကို)?\\s*(?:ဖုန်းဆက်|ဖုန်းခေါ်|call).*").find(cleaned)
        return match?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() && it != "ဖုန်း" }
    }

    private fun extractMessageTarget(query: String): String? {
        val cleaned = query.replace("ကျေးဇူးပြုပြီး", "").replace("ပါ", "").replace("ဦး", "").trim()
        val match = Regex("(.*?)(?:ဆီ|ဆီသို့|ကို)?\\s*(?:မက်ဆေ့ခ်ျ|စာပို့|sms).*").find(cleaned)
        return match?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun matchAppIntent(query: String): ClassificationResult? {
        val app = MyanmarAppDictionary.resolveApp(query)
        if (app != null) {
            val hasOpenVerb = query.contains("ဖွင့်") || query.contains("open") || query.contains("ဝင်") ||
                    query.contains("ပြ") || query.contains("launch") || query.contains("ထဲ")
            val confidence = if (hasOpenVerb) 0.96f else 0.85f
            return ClassificationResult(
                intent = SemanticIntent.OPEN_APP,
                target = app.canonicalName.uppercase(Locale.ROOT),
                confidence = confidence,
                matchedKeywords = listOf(app.displayNameMM),
                verbStem = if (hasOpenVerb) "ဖွင့်" else null,
                isDirectMatch = true,
                reasoning = "App launch matched for '${app.canonicalName}'"
            )
        }
        return null
    }
}
