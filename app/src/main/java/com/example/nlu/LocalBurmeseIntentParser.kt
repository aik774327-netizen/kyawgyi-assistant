package com.example.nlu

import android.util.Log
import kotlin.math.max
import kotlin.math.min

/**
 * LocalBurmeseIntentParser (Layer 2 - Offline Local Intent Parser & NLU Logic)
 *
 * Implements deterministic, zero-latency on-device intent parsing and slot extraction
 * for Myanmar natural language queries without requiring internet access or cloud LLM calls:
 *
 * 1. Fast-Path Exact and Regex Rules:
 *    - System & Hardware toggles: Wi-Fi, Bluetooth, Flashlight, Volume Up/Down, Mute
 *    - Communication: Phone calls, SMS messaging, Contact lookups
 *    - Media & Utilities: Camera, Photos/Gallery, Maps, Settings, Play/Pause/Stop
 *    - Application Launchers: YouTube, Facebook, Telegram, TikTok, Viber, Messenger
 * 2. Fuzzy Matching Engine:
 *    - Robust Levenshtein Distance & Token Similarity to gracefully accommodate
 *      ASR phoneme drift, phonetic typos, and colloquial Myanmar speech variations
 *      (e.g., "ဝိုင်ဖာ" -> "ဝိုင်ဖိုင်", "ဘလူးတု" -> "ဘလူးတုသ်", "ဖေ့ဘွတ်" -> "ဖေ့ဘုတ်").
 * 3. Structured Slot Extraction:
 *    - Extracts recipients, targets, parameters, app names, and action states.
 */
class LocalBurmeseIntentParser private constructor() {

    companion object {
        private const val TAG = "LocalBurmeseIntentParser"
        private const val FUZZY_SIMILARITY_THRESHOLD = 0.70f

        @Volatile
        private var instance: LocalBurmeseIntentParser? = null

        fun getInstance(): LocalBurmeseIntentParser {
            return instance ?: synchronized(this) {
                instance ?: LocalBurmeseIntentParser().also { instance = it }
            }
        }
    }

    enum class MatchType {
        EXACT_FASTPATH,
        REGEX_RULE,
        FUZZY_MATCH,
        FALLBACK_UNKNOWN
    }

    data class ParsedLocalIntent(
        val intent: KyawGyiIntent,
        val intentName: String,
        val confidence: Float,
        val matchType: MatchType,
        val slots: Map<String, String> = emptyMap(),
        val rawQuery: String,
        val normalizedQuery: String
    )

    private data class FuzzyTarget(
        val canonicalKey: String,
        val aliases: List<String>,
        val intentGenerator: (isTurnOff: Boolean, normalizedText: String) -> ParsedLocalIntent
    )

    private val fuzzyTargets = listOf(
        // Wi-Fi
        FuzzyTarget(
            canonicalKey = "ဝိုင်ဖိုင်",
            aliases = listOf("ဝိုင်ဖိုင်", "ဝိုင်ဖာ", "ဝိုင်ဖိုင်း", "wifi", "wi-fi"),
            intentGenerator = { isTurnOff, text ->
                ParsedLocalIntent(
                    intent = KyawGyiIntent.OPEN_SETTINGS,
                    intentName = "ACTION_WIFI",
                    confidence = 0.88f,
                    matchType = MatchType.FUZZY_MATCH,
                    slots = mapOf("target" to "WIFI", "state" to if (isTurnOff) "OFF" else "ON"),
                    rawQuery = text,
                    normalizedQuery = text
                )
            }
        ),
        // Bluetooth
        FuzzyTarget(
            canonicalKey = "ဘလူးတုသ်",
            aliases = listOf("ဘလူးတုသ်", "ဘလူးတု", "လူးတု", "bluetooth", "blue tooth"),
            intentGenerator = { isTurnOff, text ->
                ParsedLocalIntent(
                    intent = KyawGyiIntent.OPEN_SETTINGS,
                    intentName = "ACTION_BLUETOOTH",
                    confidence = 0.88f,
                    matchType = MatchType.FUZZY_MATCH,
                    slots = mapOf("target" to "BLUETOOTH", "state" to if (isTurnOff) "OFF" else "ON"),
                    rawQuery = text,
                    normalizedQuery = text
                )
            }
        ),
        // Bluetooth Low Energy (BLE)
        FuzzyTarget(
            canonicalKey = "စမတ်ပစ္စည်း",
            aliases = listOf("ဘီအယ်လ်အီး", "ble", "စမတ်ပစ္စည်း", "smart device", "ble ရှာ"),
            intentGenerator = { isTurnOff, text ->
                ParsedLocalIntent(
                    intent = KyawGyiIntent.OPEN_SETTINGS,
                    intentName = if (isTurnOff) "ACTION_BLE_DISCONNECT" else "ACTION_BLE_SCAN",
                    confidence = 0.88f,
                    matchType = MatchType.FUZZY_MATCH,
                    slots = mapOf("target" to "BLE", "state" to if (isTurnOff) "DISCONNECT" else "SCAN"),
                    rawQuery = text,
                    normalizedQuery = text
                )
            }
        ),
        // Flashlight
        FuzzyTarget(
            canonicalKey = "ဓာတ်မီး",
            aliases = listOf("ဓာတ်မီး", "မီးသီး", "ဖလက်ရှ်", "flashlight", "torch"),
            intentGenerator = { isTurnOff, text ->
                ParsedLocalIntent(
                    intent = KyawGyiIntent.OPEN_SETTINGS,
                    intentName = "ACTION_FLASHLIGHT",
                    confidence = 0.90f,
                    matchType = MatchType.FUZZY_MATCH,
                    slots = mapOf("target" to "FLASHLIGHT", "state" to if (isTurnOff) "OFF" else "ON"),
                    rawQuery = text,
                    normalizedQuery = text
                )
            }
        ),
        // Camera
        FuzzyTarget(
            canonicalKey = "ကင်မရာ",
            aliases = listOf("ကင်မရာ", "ကမ်မရာ", "camera", "ဓာတ်ပုံရိုက်"),
            intentGenerator = { _, text ->
                ParsedLocalIntent(
                    intent = KyawGyiIntent.OPEN_CAMERA,
                    intentName = "ACTION_OPEN_CAMERA",
                    confidence = 0.92f,
                    matchType = MatchType.FUZZY_MATCH,
                    rawQuery = text,
                    normalizedQuery = text
                )
            }
        ),
        // Photos / Gallery
        FuzzyTarget(
            canonicalKey = "ဓာတ်ပုံ",
            aliases = listOf("ဓာတ်ပုံ", "photos", "gallery", "ဂယ်လာရီ", "အယ်လ်ဘမ်"),
            intentGenerator = { _, text ->
                ParsedLocalIntent(
                    intent = KyawGyiIntent.OPEN_PHOTOS,
                    intentName = "ACTION_OPEN_PHOTOS",
                    confidence = 0.90f,
                    matchType = MatchType.FUZZY_MATCH,
                    rawQuery = text,
                    normalizedQuery = text
                )
            }
        ),
        // YouTube
        FuzzyTarget(
            canonicalKey = "YouTube",
            aliases = listOf("youtube", "ယူကျူ့", "ယူကျု", "ယူကျူ"),
            intentGenerator = { _, text ->
                ParsedLocalIntent(
                    intent = KyawGyiIntent.OPEN_APP,
                    intentName = "ACTION_OPEN_APP",
                    confidence = 0.92f,
                    matchType = MatchType.FUZZY_MATCH,
                    slots = mapOf("app_name" to "YouTube"),
                    rawQuery = text,
                    normalizedQuery = text
                )
            }
        ),
        // Facebook
        FuzzyTarget(
            canonicalKey = "Facebook",
            aliases = listOf("facebook", "ဖေ့ဘုတ်", "ဖေ့ဘွတ်", "ဖေ့စ်ဘုတ်"),
            intentGenerator = { _, text ->
                ParsedLocalIntent(
                    intent = KyawGyiIntent.OPEN_APP,
                    intentName = "ACTION_OPEN_APP",
                    confidence = 0.92f,
                    matchType = MatchType.FUZZY_MATCH,
                    slots = mapOf("app_name" to "Facebook"),
                    rawQuery = text,
                    normalizedQuery = text
                )
            }
        ),
        // Telegram
        FuzzyTarget(
            canonicalKey = "Telegram",
            aliases = listOf("telegram", "တယ်လီဂရမ်", "တယ်လီဂရမ်အက်ပ်"),
            intentGenerator = { _, text ->
                ParsedLocalIntent(
                    intent = KyawGyiIntent.OPEN_APP,
                    intentName = "ACTION_OPEN_APP",
                    confidence = 0.92f,
                    matchType = MatchType.FUZZY_MATCH,
                    slots = mapOf("app_name" to "Telegram"),
                    rawQuery = text,
                    normalizedQuery = text
                )
            }
        ),
        // TikTok
        FuzzyTarget(
            canonicalKey = "TikTok",
            aliases = listOf("tiktok", "တစ်တော့", "တစ်တော"),
            intentGenerator = { _, text ->
                ParsedLocalIntent(
                    intent = KyawGyiIntent.OPEN_APP,
                    intentName = "ACTION_OPEN_APP",
                    confidence = 0.92f,
                    matchType = MatchType.FUZZY_MATCH,
                    slots = mapOf("app_name" to "TikTok"),
                    rawQuery = text,
                    normalizedQuery = text
                )
            }
        )
    )

    /**
     * Parses an incoming speech transcript or text command into a structured ParsedLocalIntent.
     */
    fun parse(rawText: String?): ParsedLocalIntent {
        if (rawText.isNullOrBlank()) {
            return ParsedLocalIntent(
                intent = KyawGyiIntent.UNKNOWN,
                intentName = "UNKNOWN",
                confidence = 0.0f,
                matchType = MatchType.FALLBACK_UNKNOWN,
                rawQuery = "",
                normalizedQuery = ""
            )
        }

        val normalized = BurmeseNluPreprocessor.normalize(rawText)
        val lower = normalized.lowercase()
        val rawLower = rawText.lowercase()

        // 1. FAST-PATH & REGEX RULES (High Precision)

        // 1.1 Volume Up
        if (matchesVolumeUp(lower)) {
            return ParsedLocalIntent(
                intent = KyawGyiIntent.VOLUME_UP,
                intentName = "ACTION_VOLUME_UP",
                confidence = 0.98f,
                matchType = MatchType.REGEX_RULE,
                slots = mapOf("delta" to "+1"),
                rawQuery = rawText,
                normalizedQuery = normalized
            )
        }

        // 1.2 Volume Down
        if (matchesVolumeDown(lower)) {
            return ParsedLocalIntent(
                intent = KyawGyiIntent.VOLUME_DOWN,
                intentName = "ACTION_VOLUME_DOWN",
                confidence = 0.98f,
                matchType = MatchType.REGEX_RULE,
                slots = mapOf("delta" to "-1"),
                rawQuery = rawText,
                normalizedQuery = normalized
            )
        }

        // 1.3 Mute / Silent
        if (matchesMute(lower)) {
            return ParsedLocalIntent(
                intent = KyawGyiIntent.MUTE,
                intentName = "ACTION_MUTE",
                confidence = 0.98f,
                matchType = MatchType.REGEX_RULE,
                rawQuery = rawText,
                normalizedQuery = normalized
            )
        }

        // 1.4 Phone Calls (Extract Recipient Name)
        val callSlotResult = tryParseCallIntent(normalized, lower, rawText)
        if (callSlotResult != null) {
            return callSlotResult
        }

        // 1.5 SMS Messaging (Extract Recipient & Message Text)
        val smsSlotResult = tryParseSmsIntent(normalized, lower, rawText)
        if (smsSlotResult != null) {
            return smsSlotResult
        }

        // 1.6 Media Controls (Play, Pause, Next, Previous, Stop)
        val mediaResult = tryParseMediaIntent(normalized, lower, rawText)
        if (mediaResult != null) {
            return mediaResult
        }

        // 1.7 Wi-Fi Explicit Regex
        if ((rawLower.contains("wifi") || rawLower.contains("ဝိုင်ဖိုင်")) && !rawLower.contains("ဝိုင်ဖာ")) {
            val isOff = lower.contains("ပိတ်") || lower.contains("off")
            return ParsedLocalIntent(
                intent = KyawGyiIntent.OPEN_SETTINGS,
                intentName = "ACTION_WIFI",
                confidence = 0.95f,
                matchType = MatchType.REGEX_RULE,
                slots = mapOf("target" to "WIFI", "state" to if (isOff) "OFF" else "ON"),
                rawQuery = rawText,
                normalizedQuery = normalized
            )
        }

        // 1.8 Bluetooth Explicit Regex
        if (lower.contains("bluetooth") || lower.contains("ဘလူးတုသ်") || lower.contains("ဘလူးတု")) {
            val isOff = lower.contains("ပိတ်") || lower.contains("off")
            return ParsedLocalIntent(
                intent = KyawGyiIntent.OPEN_SETTINGS,
                intentName = "ACTION_BLUETOOTH",
                confidence = 0.95f,
                matchType = MatchType.REGEX_RULE,
                slots = mapOf("target" to "BLUETOOTH", "state" to if (isOff) "OFF" else "ON"),
                rawQuery = rawText,
                normalizedQuery = normalized
            )
        }

        // 1.9 Flashlight Explicit Regex
        if (lower.contains("flashlight") || lower.contains("ဓာတ်မီး")) {
            val isOff = lower.contains("ပိတ်") || lower.contains("off")
            return ParsedLocalIntent(
                intent = KyawGyiIntent.OPEN_SETTINGS,
                intentName = "ACTION_FLASHLIGHT",
                confidence = 0.95f,
                matchType = MatchType.REGEX_RULE,
                slots = mapOf("target" to "FLASHLIGHT", "state" to if (isOff) "OFF" else "ON"),
                rawQuery = rawText,
                normalizedQuery = normalized
            )
        }

        // 1.10 Open Camera / Photos / Maps / Settings
        if (lower.contains("ကင်မရာ") || lower.contains("camera") || lower.contains("ဓာတ်ပုံရိုက်")) {
            return ParsedLocalIntent(
                intent = KyawGyiIntent.OPEN_CAMERA,
                intentName = "ACTION_OPEN_CAMERA",
                confidence = 0.95f,
                matchType = MatchType.REGEX_RULE,
                rawQuery = rawText,
                normalizedQuery = normalized
            )
        }

        if (lower.contains("မြေပုံ") || lower.contains("maps") || lower.contains("လမ်းညွှန်")) {
            return ParsedLocalIntent(
                intent = KyawGyiIntent.OPEN_MAPS,
                intentName = "ACTION_OPEN_MAPS",
                confidence = 0.95f,
                matchType = MatchType.REGEX_RULE,
                rawQuery = rawText,
                normalizedQuery = normalized
            )
        }

        if (lower.contains("ဆက်တင်") || lower.contains("settings")) {
            return ParsedLocalIntent(
                intent = KyawGyiIntent.OPEN_SETTINGS,
                intentName = "ACTION_OPEN_SETTINGS",
                confidence = 0.95f,
                matchType = MatchType.REGEX_RULE,
                rawQuery = rawText,
                normalizedQuery = normalized
            )
        }

        // 2. FUZZY MATCHING ENGINE (For typos, ASR phonetic drift & colloquial variations)
        val isTurnOff = lower.contains("ပိတ်") || lower.contains("off")
        val words = normalized.split(" ", "၊", "။").map { it.trim() }.filter { it.isNotEmpty() }

        var bestFuzzyTarget: FuzzyTarget? = null
        var highestScore = 0.0f

        for (target in fuzzyTargets) {
            for (alias in target.aliases) {
                // Check whole-string similarity
                val fullSim = calculateSimilarity(lower, alias)
                if (fullSim > highestScore) {
                    highestScore = fullSim
                    bestFuzzyTarget = target
                }

                // Check individual word tokens
                for (word in words) {
                    val wordSim = calculateSimilarity(word.lowercase(), alias)
                    if (wordSim > highestScore) {
                        highestScore = wordSim
                        bestFuzzyTarget = target
                    }
                }
            }
        }

        if (bestFuzzyTarget != null && highestScore >= FUZZY_SIMILARITY_THRESHOLD) {
            Log.i(TAG, "Fuzzy match resolved: ${bestFuzzyTarget.canonicalKey} (Score: $highestScore)")
            return bestFuzzyTarget.intentGenerator(isTurnOff, normalized)
        }

        // 3. Generic Open App Launch (for any other app not in fuzzy target dictionary)
        val appLaunchResult = tryParseAppLaunchIntent(normalized, lower, rawText)
        if (appLaunchResult != null) {
            return appLaunchResult
        }

        // 3. FALLBACK TO SEARCH OR UNKNOWN
        val cleanQuery = normalized
            .replace(Regex("^(ဟေး\\s*ကျော်ကြီး|ကျော်ကြီး)\\s*"), "")
            .trim()

        return if (cleanQuery.isNotEmpty()) {
            ParsedLocalIntent(
                intent = KyawGyiIntent.SEARCH,
                intentName = "ACTION_SEARCH",
                confidence = 0.50f,
                matchType = MatchType.FALLBACK_UNKNOWN,
                slots = mapOf("query" to cleanQuery),
                rawQuery = rawText,
                normalizedQuery = normalized
            )
        } else {
            ParsedLocalIntent(
                intent = KyawGyiIntent.UNKNOWN,
                intentName = "UNKNOWN",
                confidence = 0.0f,
                matchType = MatchType.FALLBACK_UNKNOWN,
                rawQuery = rawText,
                normalizedQuery = normalized
            )
        }
    }

    // --- Helper Parsers ---

    private fun matchesVolumeUp(lower: String): Boolean {
        return lower.contains("အသံတိုး") || lower.contains("အသံမြှင့်") ||
                lower.contains("volume up") || lower.contains("အသံတင်") ||
                lower.contains("အသံကျယ်")
    }

    private fun matchesVolumeDown(lower: String): Boolean {
        return lower.contains("အသံလျှော့") || lower.contains("အသံချုံ့") ||
                lower.contains("volume down") || lower.contains("အသံသေး")
    }

    private fun matchesMute(lower: String): Boolean {
        return (lower.contains("အသံ") && lower.contains("ပိတ်")) ||
                lower.contains("mute") || lower.contains("silent") ||
                lower.contains("အသံတိတ်")
    }

    private fun tryParseCallIntent(normalized: String, lower: String, rawText: String): ParsedLocalIntent? {
        val hasCallVerb = lower.contains("ဖုန်းခေါ်") || lower.contains("ခေါ်ပေး") ||
                lower.contains("ဆက်ပေး") || (lower.contains("ဖုန်း") && (lower.contains("ခေါ်") || lower.contains("ဆက်"))) ||
                lower.contains("call")
        if (!hasCallVerb) return null

        val contact = normalized
            .replace(Regex("^(ဟေး\\s*ကျော်ကြီး|ကျော်ကြီး)\\s*"), "")
            .replace(Regex("(ဖုန်းခေါ်ပေးပါ|ဖုန်းခေါ်ပါ|ဖုန်းခေါ်မယ်|ဖုန်းခေါ်|ခေါ်ပေးပါ|ခေါ်ပါ|ခေါ်မယ်|ဆက်ပေးပါ|ဆက်ပါ|call)"), "")
            .replace(Regex("(ဆီသို့|ဆီ|ထံ|သို့|ကို)"), "")
            .replace(Regex("(ပါ|ပေးပါ|ပေး)"), "")
            .trim()

        if (contact.isNotEmpty()) {
            return ParsedLocalIntent(
                intent = KyawGyiIntent.CALL,
                intentName = "ACTION_CALL",
                confidence = 0.95f,
                matchType = MatchType.REGEX_RULE,
                slots = mapOf("contact_name" to contact),
                rawQuery = rawText,
                normalizedQuery = normalized
            )
        }
        return null
    }

    private fun tryParseSmsIntent(normalized: String, lower: String, rawText: String): ParsedLocalIntent? {
        val hasSmsVerb = lower.contains("မက်ဆေ့ခ်ျ") || lower.contains("မက်ဆေ့") ||
                lower.contains("sms") || lower.contains("စာတို")
        if (!hasSmsVerb) return null

        val clean = normalized
            .replace(Regex("^(ဟေး\\s*ကျော်ကြီး|ကျော်ကြီး)\\s*"), "")
            .replace(Regex("(မက်ဆေ့ခ်ျပို့ပေးပါ|မက်ဆေ့ခ်ျပို့ပါ|မက်ဆေ့ခ်ျပို့|ပို့ပေးပါ|ပို့ပါ|sms)"), "")
            .replace(Regex("(ဆီသို့|ဆီ|ထံ|သို့|ကို)"), "")
            .trim()

        return ParsedLocalIntent(
            intent = KyawGyiIntent.SEND_SMS,
            intentName = "ACTION_SEND_SMS",
            confidence = 0.92f,
            matchType = MatchType.REGEX_RULE,
            slots = mapOf("target" to clean),
            rawQuery = rawText,
            normalizedQuery = normalized
        )
    }

    private fun tryParseMediaIntent(normalized: String, lower: String, rawText: String): ParsedLocalIntent? {
        if ((lower.contains("သီချင်း") && lower.contains("ဖွင့်")) || lower.contains("play music") || lower.contains("play song")) {
            val songName = normalized
                .replace(Regex("^(ဟေး\\s*ကျော်ကြီး|ကျော်ကြီး)\\s*"), "")
                .replace(Regex("(သီချင်း\\s*ဖွင့်ပေးပါ|သီချင်း\\s*ဖွင့်ပါ|သီချင်း\\s*ဖွင့်|သီချင်း|play music|play)"), "")
                .trim()
            return ParsedLocalIntent(
                intent = KyawGyiIntent.PLAY_MUSIC,
                intentName = "ACTION_PLAY_MUSIC",
                confidence = 0.95f,
                matchType = MatchType.REGEX_RULE,
                slots = if (songName.isNotEmpty()) mapOf("song_name" to songName) else emptyMap(),
                rawQuery = rawText,
                normalizedQuery = normalized
            )
        }

        if (lower.contains("သီချင်းရပ်") || lower.contains("ခေတ္တရပ်") || lower.contains("pause")) {
            return ParsedLocalIntent(
                intent = KyawGyiIntent.PAUSE_MEDIA,
                intentName = "ACTION_PAUSE_MEDIA",
                confidence = 0.95f,
                matchType = MatchType.REGEX_RULE,
                rawQuery = rawText,
                normalizedQuery = normalized
            )
        }

        if (lower.contains("ရှေ့တစ်ပုဒ်") || lower.contains("နောက်တစ်ပုဒ်") || lower.contains("next song") || lower.contains("next track")) {
            return ParsedLocalIntent(
                intent = KyawGyiIntent.NEXT_TRACK,
                intentName = "ACTION_NEXT_TRACK",
                confidence = 0.95f,
                matchType = MatchType.REGEX_RULE,
                rawQuery = rawText,
                normalizedQuery = normalized
            )
        }

        if (lower.contains("ရပ်ပါ") || lower.contains("တော်ပြီ") || lower.contains("stop")) {
            return ParsedLocalIntent(
                intent = KyawGyiIntent.STOP,
                intentName = "ACTION_STOP",
                confidence = 0.95f,
                matchType = MatchType.REGEX_RULE,
                rawQuery = rawText,
                normalizedQuery = normalized
            )
        }

        return null
    }

    private fun tryParseAppLaunchIntent(normalized: String, lower: String, rawText: String): ParsedLocalIntent? {
        val hasOpenVerb = lower.contains("ဖွင့်") || lower.contains("open") || lower.contains("launch")
        if (!hasOpenVerb) return null

        val appCandidate = normalized
            .replace(Regex("^(ဟေး\\s*ကျော်ကြီး|ကျော်ကြီး)\\s*"), "")
            .replace(Regex("(ဖွင့်ပေးပါ|ဖွင့်ပါဦး|ဖွင့်ပါ|ဖွင့်မယ်|ဖွင့်|open|launch)"), "")
            .replace(Regex("(အက်ပ်|app|application)"), "")
            .replace(Regex("(ပါ|ပေးပါ|ပေး)"), "")
            .trim()

        if (appCandidate.isNotEmpty() && !appCandidate.contains("wifi") && !appCandidate.contains("ဓာတ်မီး")) {
            return ParsedLocalIntent(
                intent = KyawGyiIntent.OPEN_APP,
                intentName = "ACTION_OPEN_APP",
                confidence = 0.92f,
                matchType = MatchType.REGEX_RULE,
                slots = mapOf("app_name" to appCandidate),
                rawQuery = rawText,
                normalizedQuery = normalized
            )
        }
        return null
    }

    /**
     * Calculates the Levenshtein Distance between two character sequences.
     */
    fun levenshteinDistance(s1: String, s2: String): Int {
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }
        for (i in 0..s1.length) dp[i][0] = i
        for (j in 0..s2.length) dp[0][j] = j

        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = min(
                    dp[i - 1][j] + 1, // deletion
                    min(
                        dp[i][j - 1] + 1, // insertion
                        dp[i - 1][j - 1] + cost // substitution
                    )
                )
            }
        }
        return dp[s1.length][s2.length]
    }

    /**
     * Calculates normalized string similarity score in range [0.0, 1.0].
     */
    fun calculateSimilarity(s1: String, s2: String): Float {
        if (s1 == s2) return 1.0f
        val maxLen = max(s1.length, s2.length)
        if (maxLen == 0) return 1.0f
        val dist = levenshteinDistance(s1, s2)
        return 1.0f - (dist.toFloat() / maxLen.toFloat())
    }
}
