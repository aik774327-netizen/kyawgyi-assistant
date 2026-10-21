package com.example.semantic

import com.example.engine.BurmesePhoneticNormalizer
import com.example.nlu.KyawGyiIntent
import java.util.Locale
import kotlin.math.sqrt

/**
 * Stage 2 Semantic Fallback - On-Device Semantic Intent Matcher
 * Executes lightweight, sub-5ms semantic similarity classification using token overlap (Jaccard),
 * character n-gram matching, and canonical Intent Prototype vectors.
 * Activates ONLY when fast-path regex/exact pattern matching finds no match.
 */
object GenericSemanticIntentMatcher {

    const val DEFAULT_CONFIDENCE_THRESHOLD = 0.40f

    data class SemanticMatchResult(
        val intent: KyawGyiIntent,
        val confidence: Float,
        val matchedPrototype: String,
        val similarityMethod: String
    )

    private data class IntentPrototype(
        val intent: KyawGyiIntent,
        val canonicalPhrases: List<String>,
        val semanticKeywords: List<String>
    )

    private val PROTOTYPES = listOf(
        IntentPrototype(
            intent = KyawGyiIntent.OPEN_CAMERA,
            canonicalPhrases = listOf("ကင်မရာဖွင့်", "ဓာတ်ပုံရိုက်", "camera ဖွင့်ပါ", "ပုံရိုက်ချင်တယ်", "ကင်မရာလေး ဖွင့်ပေးပါ", "ကင်မရာထဲ ဝင်မယ်", "ဓာတ်ပုံလေးတွေ ရိုက်ချင်တယ်"),
            semanticKeywords = listOf("ကင်မရာ", "ဓာတ်ပုံ", "ရိုက်", "camera", "photo")
        ),
        IntentPrototype(
            intent = KyawGyiIntent.OPEN_PHOTOS,
            canonicalPhrases = listOf("ဓာတ်ပုံတွေကြည့်မယ်", "gallery ဖွင့်", "ပုံတွေပြပါ", "album ကြည့်မယ်", "ဓါတ်ပုံများ ပြပါ", "ဓာတ်ပုံတွေ ပြပေးပါဦး", "ဓါတ်ပုံ ကြည့်"),
            semanticKeywords = listOf("ဓာတ်ပုံ", "gallery", "album", "ဂယ်လာရီ", "ပုံများ", "ကြည့်")
        ),
        IntentPrototype(
            intent = KyawGyiIntent.OPEN_MAPS,
            canonicalPhrases = listOf("မြေပုံဖွင့်ပေးပါ", "လမ်းညွှန်ပြပါ", "ဘယ်လိုသွားရမလဲ ကြည့်မယ်", "maps ဖွင့်", "မြေပုံ ကြည့်ချင်တယ်"),
            semanticKeywords = listOf("မြေပုံ", "လမ်း", "maps", "လမ်းညွှန်", "တည်နေရာ")
        ),
        IntentPrototype(
            intent = KyawGyiIntent.OPEN_SETTINGS,
            canonicalPhrases = listOf("စနစ်ဆက်တင်ဖွင့်", "settings သို့သွားမယ်", "ဆက်တင်ကြည့်မယ်", "ဆက်တင် ပြင်မယ်"),
            semanticKeywords = listOf("ဆက်တင်", "settings", "setting", "စနစ်")
        ),
        IntentPrototype(
            intent = KyawGyiIntent.OPEN_APP,
            canonicalPhrases = listOf("အက်ပ်ဖွင့်ပေး", "app ကြည့်ချင်တယ်", "ဖွင့်ချင်တယ်", "ဝင်ကြည့်မယ်", "သုံးချင်တယ်"),
            semanticKeywords = listOf("ဖွင့်", "ဝင်", "သုံး", "open", "launch", "app")
        ),
        IntentPrototype(
            intent = KyawGyiIntent.CALL,
            canonicalPhrases = listOf("ဖုန်းဆက်ပေးပါ", "ဖုန်းခေါ်မယ်", "စကားပြောချင်တယ်", "ခေါ်လိုက်", "ဖုန်းပြောချင်တယ်"),
            semanticKeywords = listOf("ဖုန်း", "ဆက်", "ခေါ်", "call", "ဖုန်းခေါ်")
        ),
        IntentPrototype(
            intent = KyawGyiIntent.SEND_SMS,
            canonicalPhrases = listOf("မက်ဆေ့ခ်ျပို့ပေးပါ", "စာတိုပို့မယ်", "sms ပို့လိုက်", "စာပို့ချင်တယ်", "message ပို့ပေး"),
            semanticKeywords = listOf("မက်ဆေ့", "စာ", "ပို့", "sms", "message", "စာတို")
        ),
        IntentPrototype(
            intent = KyawGyiIntent.PLAY_MUSIC,
            canonicalPhrases = listOf("သီချင်းဖွင့်ပါ", "သီချင်းနားထောင်ချင်တယ်", "music ဖွင့်ပေး", "သီချင်းဖွင့်ပြ", "သီချင်းလေး ဖွင့်ပေးပါဦး", "သီချင်းကောင်းလေးတွေ ဖွင့်ပြပါ"),
            semanticKeywords = listOf("သီချင်း", "music", "song", "ဖွင့်", "နားထောင်")
        ),
        IntentPrototype(
            intent = KyawGyiIntent.PAUSE_MEDIA,
            canonicalPhrases = listOf("သီချင်းခဏရပ်ပါ", "ခဏနားထားမယ်", "pause လုပ်ပေးပါ", "သီချင်းရပ်"),
            semanticKeywords = listOf("ရပ်", "ခဏရပ်", "နား", "pause", "ရပ်ထား")
        ),
        IntentPrototype(
            intent = KyawGyiIntent.RESUME_MEDIA,
            canonicalPhrases = listOf("သီချင်းဆက်ဖွင့်ပါ", "ပြန်ဖွင့်ပေး", "resume လုပ်", "ဆက်တီး"),
            semanticKeywords = listOf("ပြန်ဖွင့်", "ဆက်ဖွင့်", "resume", "ဆက်")
        ),
        IntentPrototype(
            intent = KyawGyiIntent.NEXT_TRACK,
            canonicalPhrases = listOf("နောက်တစ်ပုဒ်ကျော်ပါ", "နောက်သီချင်းပြောင်း", "next song သွား", "နောက်တစ်ခု"),
            semanticKeywords = listOf("နောက်", "တစ်ပုဒ်", "ကျော်", "next", "ပြောင်း")
        ),
        IntentPrototype(
            intent = KyawGyiIntent.PREVIOUS_TRACK,
            canonicalPhrases = listOf("ရှေ့တစ်ပုဒ်ပြန်ဖွင့်", "အရင်သီချင်းသွား", "previous song", "ရှေ့တစ်ခု"),
            semanticKeywords = listOf("ရှေ့", "အရင်", "ပြန်ဖွင့်", "previous", "prev")
        ),
        IntentPrototype(
            intent = KyawGyiIntent.VOLUME_UP,
            canonicalPhrases = listOf("အသံကျယ်ကျယ်ဖွင့်ပါ", "အသံမြှင့်ပေး", "အသံချဲ့ပါ", "volume up", "အသံ ကျယ်ကျယ် ချဲ့ပေးပါ", "အသံ တိုးတိုး မဟုတ်ဘဲ ကျယ်ကျယ် ချဲ့"),
            semanticKeywords = listOf("အသံ", "ကျယ်", "မြှင့်", "ချဲ့", "volume", "loud")
        ),
        IntentPrototype(
            intent = KyawGyiIntent.VOLUME_DOWN,
            canonicalPhrases = listOf("အသံတိုးတိုးဖွင့်", "အသံလျှော့ပေးပါ", "အသံချပါ", "volume down", "အသံ ဖြည်းဖြည်း လျှော့"),
            semanticKeywords = listOf("အသံ", "တိုး", "လျှော့", "ချ", "volume", "quiet")
        ),
        IntentPrototype(
            intent = KyawGyiIntent.MUTE,
            canonicalPhrases = listOf("အသံပိတ်ထားပါ", "အသံတိတ်", "mute လုပ်ပါ", "ငြိမ်နေ"),
            semanticKeywords = listOf("အသံ", "ပိတ်", "တိတ်", "mute", "ငြိမ်")
        ),
        IntentPrototype(
            intent = KyawGyiIntent.OPEN_SETTINGS,
            canonicalPhrases = listOf("စနစ်ဆက်တင်ဖွင့်", "settings သို့သွားမယ်", "ဆက်တင်ကြည့်မယ်"),
            semanticKeywords = listOf("ဆက်တင်", "settings", "setting", "စနစ်")
        ),
        IntentPrototype(
            intent = KyawGyiIntent.SEARCH,
            canonicalPhrases = listOf("အင်တာနက်မှာရှာပေးပါ", "google မှာရှာကြည့်", "ရှာဖွေပါ"),
            semanticKeywords = listOf("ရှာ", "ရှာဖွေ", "search", "google", "ရှာပေး")
        )
    )

    /**
     * Compute semantic match for unclassified utterance against canonical prototypes.
     */
    fun match(
        rawText: String,
        confidenceThreshold: Float = DEFAULT_CONFIDENCE_THRESHOLD
    ): SemanticMatchResult? {
        val normalized = BurmesePhoneticNormalizer.normalizeBurmeseText(rawText).lowercase(Locale.ROOT)
        if (normalized.isBlank()) return null

        var bestIntent: KyawGyiIntent? = null
        var bestConfidence = 0.0f
        var bestPrototype = ""
        var bestMethod = ""

        val inputTokens = extractTokens(normalized)

        for (proto in PROTOTYPES) {
            for (phrase in proto.canonicalPhrases) {
                val phraseNorm = BurmesePhoneticNormalizer.normalizeBurmeseText(phrase).lowercase(Locale.ROOT)
                val phraseTokens = extractTokens(phraseNorm)

                // 1. Jaccard Token Overlap
                val jaccard = calculateJaccardSimilarity(inputTokens, phraseTokens)

                // 2. Levenshtein / Substring Similarity Ratio
                val charRatio = if (normalized.contains(phraseNorm) || phraseNorm.contains(normalized)) {
                    0.90f
                } else {
                    BurmesePhoneticNormalizer.calculateSimilarityRatio(normalized, phraseNorm)
                }

                // 3. Keyword Overlap
                val keywordHits = proto.semanticKeywords.count { kw ->
                    normalized.contains(kw.lowercase(Locale.ROOT))
                }
                val keywordScore = if (proto.semanticKeywords.isNotEmpty()) {
                    (keywordHits.toFloat() / 2.0f).coerceAtMost(1.0f)
                } else 0.0f

                // Blended Hybrid Confidence Score
                val blended = (jaccard * 0.35f) + (charRatio * 0.35f) + (keywordScore * 0.30f)

                if (blended > bestConfidence) {
                    bestConfidence = blended
                    bestIntent = proto.intent
                    bestPrototype = phrase
                    bestMethod = "Jaccard+CharRatio+Keyword (score=${"%.2f".format(blended)})"
                }
            }
        }

        return if (bestIntent != null && bestConfidence >= confidenceThreshold) {
            SemanticMatchResult(
                intent = bestIntent,
                confidence = bestConfidence.coerceIn(0.0f, 1.0f),
                matchedPrototype = bestPrototype,
                similarityMethod = bestMethod
            )
        } else {
            null
        }
    }

    private fun extractTokens(text: String): Set<String> {
        val words = text.split("\\s+".toRegex()).filter { it.isNotBlank() }
        val tokens = mutableSetOf<String>()
        tokens.addAll(words)
        // Add 2-character and 3-character n-grams for Burmese syllable matching
        if (text.length >= 2) {
            for (i in 0 until text.length - 1) {
                tokens.add(text.substring(i, i + 2))
            }
        }
        return tokens
    }

    private fun calculateJaccardSimilarity(set1: Set<String>, set2: Set<String>): Float {
        if (set1.isEmpty() || set2.isEmpty()) return 0.0f
        val intersection = set1.intersect(set2).size
        val union = set1.union(set2).size
        return if (union > 0) intersection.toFloat() / union.toFloat() else 0.0f
    }
}
