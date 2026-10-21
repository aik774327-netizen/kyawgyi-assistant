package com.example.discovery

import com.example.engine.BurmesePhoneticNormalizer
import com.example.semantic.MyanmarTextNormalizer
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * Type of match identified by AppNameMatcher.
 */
enum class MatchType {
    EXACT,
    NORMALIZED,
    PREFIX,
    CONTAINS,
    TOKEN,
    FUZZY,
    PHONETIC,
    LINGUISTIC_ALIAS,
    CONTEXT,
    NONE
}

/**
 * Detailed match score result between a query and an installed application.
 */
data class MatchScore(
    val confidence: Float,
    val matchType: MatchType,
    val query: String,
    val candidateLabel: String,
    val matchedField: String = "LABEL"
)

/**
 * Scored candidate wrapper.
 */
data class ScoredAppCandidate(
    val app: InstalledAppInfo,
    val score: MatchScore
)

/**
 * Multi-Signal Fuzzy & Semantic App Matcher for Myanmar and English voice commands.
 * Combines exact, normalized, token-based, Levenshtein distance, phonetic similarity,
 * and Myanmar linguistic loanword aliases to resolve user intention without hardcoded package maps.
 */
class AppNameMatcher(
    private val textNormalizer: MyanmarTextNormalizer = MyanmarTextNormalizer()
) {

    companion object {
        const val EXACT_MATCH_SCORE = 1.00f
        const val NORMALIZED_MATCH_SCORE = 0.98f
        const val PREFIX_MATCH_SCORE = 0.90f
        const val CONTAINS_MATCH_SCORE = 0.88f
        const val LINGUISTIC_ALIAS_MATCH_SCORE = 0.95f
        const val MINIMUM_ACCEPTABLE_SCORE = 0.55f

        // Linguistic aliases mapping spoken Burmese loanwords to semantic app labels (NO package names)
        val LINGUISTIC_ALIASES: Map<String, List<String>> = mapOf(
            "facebook" to listOf("ဖေ့စ်ဘုတ်", "ဖေ့စ်ဘွတ်", "ဖေ့ဘုတ်", "ဖေ့ဘွတ်", "ဖေ့စ်ဘွတ်ခ်", "fb"),
            "youtube" to listOf("ယူကျု", "ယူကျူ", "ယူးကျု", "ယူးကျူ", "ယူကျူ့ဗ်", "yt", "you tube"),
            "messenger" to listOf("မက်ဆင်ဂျာ", "မက်စင်ဂျာ", "မဆင်ဂျာ", "msngr"),
            "viber" to listOf("ဗိုင်ဘာ", "ဗိုက်ဘာ", "ဝိုင်ဘာ"),
            "telegram" to listOf("တယ်လီဂရမ်", "တဲလီဂရမ်", "တယ်လီ", "tg"),
            "tiktok" to listOf("တစ်တော့", "တိတ်တောက်", "တစ်တောက်", "တစ်တော့ခ်", "tik tok"),
            "chrome" to listOf("ခရုမ်း", "ကရုမ်း", "ဘရောက်ဆာ"),
            "gmail" to listOf("ဂျီမေးလ်", "ဂျီမေး", "အီးမေးလ်", "မေးလ်"),
            "maps" to listOf("မြေပုံ", "ဂူဂဲလ်မြေပုံ", "ဂူဂဲမြေပုံ", "လမ်းညွှန်"),
            "kpay" to listOf("ကေပီ", "ကေဘီဇက်ပီ", "ကေဘီဇက်", "ကေပေး", "kbz pay", "kbzpay"),
            "wavemoney" to listOf("ဝေ့မန်းနီး", "ဝေ့ပီ", "ဝေ့ဖ်ပီ", "ဝေ့မုန်း", "wave pay", "wavepay"),
            "camera" to listOf("ကင်မရာ", "ကင်မရာလေး", "ဓာတ်ပုံရိုက်", "ဓါတ်ပုံရိုက်", "cam"),
            "photos" to listOf("ဓာတ်ပုံ", "ဓာတ်ပုံများ", "ဓါတ်ပုံ", "ဂယ်လာရီ", "gallery"),
            "settings" to listOf("ဆက်တင်", "စနစ်ဆက်တင်", "ဖုန်းဆက်တင်", "setting"),
            "clock" to listOf("နာရီ", "နှိုးစက်", "အချိန်"),
            "music" to listOf("သီချင်း", "ဂီတ", "တေးဂီတ"),
            "calculator" to listOf("ဂဏန်းပေါင်းစက်", "တွက်စက်")
        )
    }

    /**
     * Compute comprehensive match score between a user query and an installed application.
     */
    fun match(rawQuery: String, app: InstalledAppInfo): MatchScore {
        val query = rawQuery.trim()
        if (query.isBlank()) {
            return MatchScore(0.0f, MatchType.NONE, query, app.applicationLabel)
        }

        val queryLower = query.lowercase(Locale.ROOT)
        val appLabel = app.applicationLabel.trim()
        val labelLower = appLabel.lowercase(Locale.ROOT)
        val pkgLower = app.packageName.lowercase(Locale.ROOT)

        // 1. Exact direct match
        if (queryLower == labelLower || queryLower == pkgLower) {
            return MatchScore(EXACT_MATCH_SCORE, MatchType.EXACT, query, app.applicationLabel)
        }

        // 2. Normalized match (Myanmar Unicode NFC, Zawgyi & polite particle cleaning)
        val normQuery = normalizeText(query)
        val normLabel = app.normalizedLabel.ifBlank { normalizeText(appLabel) }
        if (normQuery.isNotBlank() && normQuery == normLabel) {
            return MatchScore(NORMALIZED_MATCH_SCORE, MatchType.NORMALIZED, query, app.applicationLabel)
        }

        // 3. Linguistic Alias Match (Phonetic Burmese Loanword -> Semantic English Label)
        for ((semanticKeyword, aliases) in LINGUISTIC_ALIASES) {
            val matchesAlias = aliases.any { alias ->
                val normAlias = normalizeText(alias)
                val aliasLower = alias.lowercase(Locale.ROOT)
                normQuery == normAlias || queryLower == aliasLower ||
                (alias.any { it.code in 0x1000..0x109F } && (normQuery.contains(normAlias) || queryLower.contains(aliasLower)))
            }
            if (matchesAlias) {
                // Check if candidate app label matches this semantic keyword
                if (labelLower.contains(semanticKeyword) || normLabel.contains(semanticKeyword) || pkgLower.contains(semanticKeyword)) {
                    val score = if (labelLower == semanticKeyword) {
                        LINGUISTIC_ALIAS_MATCH_SCORE
                    } else {
                        LINGUISTIC_ALIAS_MATCH_SCORE - 0.03f
                    }
                    return MatchScore(score, MatchType.LINGUISTIC_ALIAS, query, app.applicationLabel, matchedField = "ALIAS_$semanticKeyword")
                }
            }
        }

        // 4. Prefix Match
        if (labelLower.startsWith(queryLower) || (normQuery.length >= 3 && normLabel.startsWith(normQuery))) {
            val lengthRatio = queryLower.length.toFloat() / max(1, labelLower.length)
            val score = PREFIX_MATCH_SCORE * (0.85f + 0.15f * lengthRatio)
            return MatchScore(score, MatchType.PREFIX, query, app.applicationLabel)
        }

        // 5. Substring Contains Match
        if (labelLower.contains(queryLower) || (normQuery.length >= 3 && normLabel.contains(normQuery))) {
            val lengthRatio = queryLower.length.toFloat() / max(1, labelLower.length)
            val score = CONTAINS_MATCH_SCORE * (0.80f + 0.20f * lengthRatio)
            return MatchScore(score, MatchType.CONTAINS, query, app.applicationLabel)
        }

        // 6. Token / Word Overlap Match
        val queryTokens = queryLower.split(Regex("[\\s_\\-\\.]+")).filter { it.isNotBlank() }
        val labelTokens = labelLower.split(Regex("[\\s_\\-\\.]+")).filter { it.isNotBlank() }
        if (queryTokens.isNotEmpty() && labelTokens.isNotEmpty()) {
            val intersection = queryTokens.intersect(labelTokens.toSet())
            if (intersection.isNotEmpty()) {
                val jaccard = intersection.size.toFloat() / (queryTokens.size + labelTokens.size - intersection.size)
                if (jaccard >= 0.5f) {
                    val score = 0.80f + (jaccard * 0.10f)
                    return MatchScore(score, MatchType.TOKEN, query, app.applicationLabel, matchedField = "TOKEN")
                }
            }
        }

        // 7. Levenshtein Edit-Distance Fuzzy Match
        val levDist = calculateLevenshteinDistance(queryLower, labelLower)
        val maxLen = max(queryLower.length, labelLower.length)
        if (maxLen > 0) {
            val levRatio = 1.0f - (levDist.toFloat() / maxLen.toFloat())
            if (levRatio >= 0.70f) {
                val score = 0.70f + (levRatio * 0.22f)
                return MatchScore(score, MatchType.FUZZY, query, app.applicationLabel, matchedField = "LEVENSHTEIN")
            }
        }

        // 8. Myanmar Phonetic Similarity
        val phoneticRatio = BurmesePhoneticNormalizer.calculateSimilarityRatio(normQuery, normLabel)
        if (phoneticRatio >= 0.65f) {
            val score = 0.65f + (phoneticRatio * 0.25f)
            return MatchScore(score, MatchType.PHONETIC, query, app.applicationLabel, matchedField = "PHONETIC")
        }

        return MatchScore(0.0f, MatchType.NONE, query, app.applicationLabel)
    }

    /**
     * Score a list of installed apps against a query and return them sorted by confidence descending.
     */
    fun scoreCandidates(query: String, apps: List<InstalledAppInfo>): List<ScoredAppCandidate> {
        return apps.map { app ->
            ScoredAppCandidate(app = app, score = match(query, app))
        }.filter { it.score.confidence >= MINIMUM_ACCEPTABLE_SCORE }
            .sortedByDescending { it.score.confidence }
    }

    /**
     * Helper for normalizing Myanmar and English text.
     */
    fun normalizeText(input: String): String {
        val norm1 = textNormalizer.normalize(input).cleanedText
        return BurmesePhoneticNormalizer.normalizeBurmeseText(norm1).lowercase(Locale.ROOT)
    }

    /**
     * Compute Levenshtein distance between two strings.
     */
    fun calculateLevenshteinDistance(s1: String, s2: String): Int {
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }

        for (i in 0..s1.length) dp[i][0] = i
        for (j in 0..s2.length) dp[0][j] = j

        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = min(
                    min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                    dp[i - 1][j - 1] + cost
                )
            }
        }
        return dp[s1.length][s2.length]
    }
}
