package com.example.nlu

import android.util.Log
import java.util.Locale
import java.util.regex.Pattern

/**
 * BurmesePhoneticLexicon (Layer 6 Myanmar Linguistics & Layer 2 ASR Code-Switching)
 *
 * Implements a bidirectional phonetic acoustic dictionary and fuzzy resolver
 * for English loanwords, technical jargon, and code-switched Myanmar speech:
 * - Wi-Fi: "ဝိုင်ဖိုင်", "ဝိုင်ဖာ", "ဝိုင်ဖိုင်း", "wifi", "wi-fi"
 * - Bluetooth: "ဘလူးတု", "ဘလူးတုသ်", "ဘလူးတုတ္", "bluetooth", "bt"
 * - YouTube: "ယူကျု", "ယူကျူ့", "ယူကျူ့ဘ်", "youtube", "yt"
 * - Mobile Data: "မိုဘိုင်းဒေတာ", "ဒေတာ", "ဒါတာ", "data"
 * - Volume: "ဗော်လျံ", "ဘော်လန်", "volume", "sound"
 * - Flashlight: "ဖလက်ရှ်လိုက်", "ဖလက်ရှ်", "ဓာတ်မီး", "flashlight"
 * - Applications: Facebook, Messenger, Viber, Telegram, TikTok, Camera, Gallery
 */
object BurmesePhoneticLexicon {

    private const val TAG = "BurmesePhoneticLexicon"

    enum class TargetCategory {
        SETTING_WIFI,
        SETTING_BLUETOOTH,
        SETTING_MOBILE_DATA,
        SETTING_FLASHLIGHT,
        SETTING_HOTSPOT,
        SETTING_AIRPLANE_MODE,
        SETTING_VOLUME,
        SETTING_BRIGHTNESS,
        APP_YOUTUBE,
        APP_FACEBOOK,
        APP_MESSENGER,
        APP_VIBER,
        APP_TELEGRAM,
        APP_TIKTOK,
        APP_CAMERA,
        APP_GALLERY,
        APP_MAPS,
        APP_SETTINGS,
        APP_KBZPAY,
        APP_WAVEPAY,
        APP_AYAPAY,
        APP_CBPAY,
        APP_GMAIL,
        APP_CHROME,
        APP_SPOTIFY,
        APP_GRAB,
        TELCO_MPT,
        TELCO_ATOM,
        TELCO_OOREDOO,
        TELCO_MYTEL
    }

    data class LexiconEntry(
        val category: TargetCategory,
        val canonicalMyanmar: String,
        val canonicalEnglish: String,
        val aliases: List<String>
    )

    private val LEXICON_ENTRIES = listOf(
        // 1. Wi-Fi
        LexiconEntry(
            category = TargetCategory.SETTING_WIFI,
            canonicalMyanmar = "ဝိုင်ဖိုင်",
            canonicalEnglish = "wifi",
            aliases = listOf(
                "ဝိုင်ဖိုင်", "ဝိုင်ဖာ", "ဝိုင်ဖိုင်း", "ဝိုင်ဖဲ", "ဝိုင်ဖီ",
                "wifi", "wi-fi", "wi fi", "wyfy"
            )
        ),

        // 2. Bluetooth
        LexiconEntry(
            category = TargetCategory.SETTING_BLUETOOTH,
            canonicalMyanmar = "ဘလူးတုသ်",
            canonicalEnglish = "bluetooth",
            aliases = listOf(
                "ဘလူးတုသ်", "ဘလူးတု", "ဘလူးတုဒ်", "ဘလူးတုတ္", "ဗလူးတု", "ဗလူးတုသ်",
                "bluetooth", "blue tooth", "bt", "blutooth"
            )
        ),

        // 3. YouTube
        LexiconEntry(
            category = TargetCategory.APP_YOUTUBE,
            canonicalMyanmar = "ယူကျု",
            canonicalEnglish = "youtube",
            aliases = listOf(
                "ယူကျု", "ယူကျူ့", "ယူကျူ့ဘ်", "ယူကျု့", "ယူကျူ", "ယူကျူ့ခ်",
                "youtube", "you tube", "yt", "utube"
            )
        ),

        // 4. Mobile Data
        LexiconEntry(
            category = TargetCategory.SETTING_MOBILE_DATA,
            canonicalMyanmar = "မိုဘိုင်းဒေတာ",
            canonicalEnglish = "data",
            aliases = listOf(
                "မိုဘိုင်းဒေတာ", "မိုဘိုင်းဒါတာ", "ဒေတာ", "ဒါတာ", "မိုဘိုင်းဒေတာ့",
                "mobile data", "data", "cellular data", "internet data"
            )
        ),

        // 5. Volume / Sound
        LexiconEntry(
            category = TargetCategory.SETTING_VOLUME,
            canonicalMyanmar = "အသံပမာဏ",
            canonicalEnglish = "volume",
            aliases = listOf(
                "ဗော်လျံ", "ဘော်လျံ", "ဗော်လန်", "ဘော်လန်", "အသံပမာဏ", "အသံ",
                "volume", "sound"
            )
        ),

        // 6. Flashlight / Torch
        LexiconEntry(
            category = TargetCategory.SETTING_FLASHLIGHT,
            canonicalMyanmar = "ဓာတ်မီး",
            canonicalEnglish = "flashlight",
            aliases = listOf(
                "ဓာတ်မီး", "ဒါတ်မီး", "ဖလက်ရှ်", "ဖလက်ရှ်လိုက်", "ဖလက်",
                "flashlight", "torch", "flash"
            )
        ),

        // 7. Hotspot
        LexiconEntry(
            category = TargetCategory.SETTING_HOTSPOT,
            canonicalMyanmar = "ဟော့စပေါ့",
            canonicalEnglish = "hotspot",
            aliases = listOf(
                "ဟော့စပေါ့", "ဟော့စပေါ့တ်", "ဟော့စပေါ့ဒ်", "ဟော့စပေ့ာ",
                "hotspot", "personal hotspot", "mobile hotspot"
            )
        ),

        // 8. Airplane Mode
        LexiconEntry(
            category = TargetCategory.SETTING_AIRPLANE_MODE,
            canonicalMyanmar = "လေယာဉ်ပျံမုဒ်",
            canonicalEnglish = "airplane_mode",
            aliases = listOf(
                "လေယာဉ်ပျံမုဒ်", "လေယာဉ်မုဒ်", "အဲပလိန်းမုဒ်", "အဲပလိန်မုဒ်",
                "airplane mode", "flight mode", "aeroplane mode"
            )
        ),

        // 9. Brightness
        LexiconEntry(
            category = TargetCategory.SETTING_BRIGHTNESS,
            canonicalMyanmar = "အလင်းရောင်",
            canonicalEnglish = "brightness",
            aliases = listOf(
                "အလင်းရောင်", "အလင်း", "ဘရိုက်နက်စ်", "ဘရိုက်နက်",
                "brightness"
            )
        ),

        // 10. Facebook
        LexiconEntry(
            category = TargetCategory.APP_FACEBOOK,
            canonicalMyanmar = "ဖေ့စ်ဘွတ်ခ်",
            canonicalEnglish = "facebook",
            aliases = listOf(
                "ဖေ့စ်ဘွတ်ခ်", "ဖေ့ဘွတ်", "ဖေ့ဘုတ်", "ဖေ့စ်ဘွတ်", "ဖေဘုတ်",
                "facebook", "fb"
            )
        ),

        // 11. Messenger
        LexiconEntry(
            category = TargetCategory.APP_MESSENGER,
            canonicalMyanmar = "မက်ဆင်ဂျာ",
            canonicalEnglish = "messenger",
            aliases = listOf(
                "မက်ဆင်ဂျာ", "မက်စင်ဂျာ", "မက်ဆင်ဂျာ့", "မက်စင်ဂျာ့",
                "messenger"
            )
        ),

        // 12. Viber
        LexiconEntry(
            category = TargetCategory.APP_VIBER,
            canonicalMyanmar = "ဗိုက်ဘာ",
            canonicalEnglish = "viber",
            aliases = listOf(
                "ဗိုက်ဘာ", "ဗိုင်ဘာ", "ဘိုင်ဘာ", "ဘိုက်ဘာ",
                "viber"
            )
        ),

        // 13. Telegram
        LexiconEntry(
            category = TargetCategory.APP_TELEGRAM,
            canonicalMyanmar = "တယ်လီဂရမ်",
            canonicalEnglish = "telegram",
            aliases = listOf(
                "တယ်လီဂရမ်", "တယ်လီဂရမ့်", "တယ်လီဂရမ်ဒ်",
                "telegram"
            )
        ),

        // 14. TikTok
        LexiconEntry(
            category = TargetCategory.APP_TIKTOK,
            canonicalMyanmar = "တစ်တော့",
            canonicalEnglish = "tiktok",
            aliases = listOf(
                "တစ်တော့", "တစ်တော့ခ်", "တစ်တောခ်", "တစ်တောက်",
                "tiktok", "tik tok"
            )
        ),

        // 15. Camera
        LexiconEntry(
            category = TargetCategory.APP_CAMERA,
            canonicalMyanmar = "ကင်မရာ",
            canonicalEnglish = "camera",
            aliases = listOf(
                "ကင်မရာ", "ကင်မရာ့", "ကာမရာ",
                "camera", "cam"
            )
        ),

        // 16. Gallery / Photos
        LexiconEntry(
            category = TargetCategory.APP_GALLERY,
            canonicalMyanmar = "ဓာတ်ပုံများ",
            canonicalEnglish = "gallery",
            aliases = listOf(
                "ဓာတ်ပုံများ", "ဓာတ်ပုံ", "ဂယ်လာရီ", "ဂယ်လရီ",
                "gallery", "photos", "photo gallery"
            )
        ),

        // 17. Maps
        LexiconEntry(
            category = TargetCategory.APP_MAPS,
            canonicalMyanmar = "မြေပုံ",
            canonicalEnglish = "maps",
            aliases = listOf(
                "မြေပုံ", "မက်ပ်", "မက်စ်",
                "maps", "google maps", "map"
            )
        ),

        // 18. Settings
        LexiconEntry(
            category = TargetCategory.APP_SETTINGS,
            canonicalMyanmar = "ဆက်တင်များ",
            canonicalEnglish = "settings",
            aliases = listOf(
                "ဆက်တင်များ", "ဆက်တင်", "စက်တင်", "စက်တင်များ",
                "settings", "setting"
            )
        ),

        // 19. KBZPay
        LexiconEntry(
            category = TargetCategory.APP_KBZPAY,
            canonicalMyanmar = "ကေပေး",
            canonicalEnglish = "kbzpay",
            aliases = listOf(
                "ကေပေး", "ကေဘီဇက်ပေး", "ကေဘီဇတ်ပေး", "ကေပေးလ်",
                "kbzpay", "kbz pay", "kpay", "k pay"
            )
        ),

        // 20. WavePay
        LexiconEntry(
            category = TargetCategory.APP_WAVEPAY,
            canonicalMyanmar = "ဝေ့ဖ်ပေး",
            canonicalEnglish = "wavepay",
            aliases = listOf(
                "ဝေ့ဖ်ပေး", "ဝေ့ပေး", "ဝေ့မနီး", "ဝေ့ဖ်မနီး",
                "wavepay", "wave pay", "wave money", "wavemoney"
            )
        ),

        // 21. AYA Pay
        LexiconEntry(
            category = TargetCategory.APP_AYAPAY,
            canonicalMyanmar = "အေရာပေး",
            canonicalEnglish = "ayapay",
            aliases = listOf(
                "အေရာပေး", "ဧရာပေး", "အေရာပေးလ်",
                "ayapay", "aya pay"
            )
        ),

        // 22. CB Pay
        LexiconEntry(
            category = TargetCategory.APP_CBPAY,
            canonicalMyanmar = "စီဘီပေး",
            canonicalEnglish = "cbpay",
            aliases = listOf(
                "စီဘီပေး", "စီဘီဘဏ်",
                "cbpay", "cb pay"
            )
        ),

        // 23. Gmail
        LexiconEntry(
            category = TargetCategory.APP_GMAIL,
            canonicalMyanmar = "ဂျီမေးလ်",
            canonicalEnglish = "gmail",
            aliases = listOf(
                "ဂျီမေးလ်", "ဂျီမေး", "မေးလ်",
                "gmail", "email", "google mail"
            )
        ),

        // 24. Chrome
        LexiconEntry(
            category = TargetCategory.APP_CHROME,
            canonicalMyanmar = "ခရုမ်း",
            canonicalEnglish = "chrome",
            aliases = listOf(
                "ခရုမ်း", "ဂူဂဲလ်ခရုမ်း", "ဘရောက်ဇာ",
                "chrome", "google chrome", "browser"
            )
        ),

        // 25. Spotify
        LexiconEntry(
            category = TargetCategory.APP_SPOTIFY,
            canonicalMyanmar = "စပေါ်တီဖိုင်",
            canonicalEnglish = "spotify",
            aliases = listOf(
                "စပေါ်တီဖိုင်", "စပေါ့တီဖိုင်",
                "spotify"
            )
        ),

        // 26. Grab
        LexiconEntry(
            category = TargetCategory.APP_GRAB,
            canonicalMyanmar = "ဂရက်ဘ်",
            canonicalEnglish = "grab",
            aliases = listOf(
                "ဂရက်ဘ်", "ဂရက်", "ဂရပ်ဘ်",
                "grab", "grab taxi"
            )
        ),

        // 27. Telco Operators (MPT, ATOM, Ooredoo, Mytel)
        LexiconEntry(
            category = TargetCategory.TELCO_MPT,
            canonicalMyanmar = "အမ်ပီတီ",
            canonicalEnglish = "mpt",
            aliases = listOf("အမ်ပီတီ", "mpt", "mpt4u")
        ),
        LexiconEntry(
            category = TargetCategory.TELCO_ATOM,
            canonicalMyanmar = "အက်တမ်",
            canonicalEnglish = "atom",
            aliases = listOf("အက်တမ်", "atom", "atom store", "telenor")
        ),
        LexiconEntry(
            category = TargetCategory.TELCO_OOREDOO,
            canonicalMyanmar = "အူရီဒူး",
            canonicalEnglish = "ooredoo",
            aliases = listOf("အူရီဒူး", "ooredoo", "my ooredoo")
        ),
        LexiconEntry(
            category = TargetCategory.TELCO_MYTEL,
            canonicalMyanmar = "မိုင်တဲလ်",
            canonicalEnglish = "mytel",
            aliases = listOf("မိုင်တဲလ်", "မိုင်တယ်", "mytel", "myid")
        )
    )

    // Reverse lookup map from lowercase alias to canonical LexiconEntry
    private val ALIAS_LOOKUP_MAP = mutableMapOf<String, LexiconEntry>()

    init {
        for (entry in LEXICON_ENTRIES) {
            for (alias in entry.aliases) {
                ALIAS_LOOKUP_MAP[alias.trim().lowercase(Locale.ROOT)] = entry
            }
        }
    }

    /**
     * Resolves an isolated term or keyword to its canonical LexiconEntry.
     * Supports exact match and phonetic fuzzy matching (Levenshtein distance <= 2).
     */
    fun resolveTerm(rawTerm: String): LexiconEntry? {
        val clean = rawTerm.trim().lowercase(Locale.ROOT)
        if (clean.isEmpty()) return null

        // 1. Direct exact alias match
        ALIAS_LOOKUP_MAP[clean]?.let { return it }

        // 2. Fuzzy phonetic recovery for slight ASR spelling variations
        var bestMatch: LexiconEntry? = null
        var minDistance = Int.MAX_VALUE

        for ((alias, entry) in ALIAS_LOOKUP_MAP) {
            val dist = computeLevenshteinDistance(clean, alias)
            val threshold = if (alias.length <= 4) 1 else 2
            if (dist <= threshold && dist < minDistance) {
                minDistance = dist
                bestMatch = entry
            }
        }

        return bestMatch
    }

    /**
     * Normalizes all code-switched technical loanwords and noisy ASR terms in a full sentence.
     * e.g., "ဝိုင်ဖာ ဖွင့်ပေးပါ" -> "ဝိုင်ဖိုင် ဖွင့်ပေးပါ"
     *       "blue tooth ပိတ်ပါ" -> "ဘလူးတုသ် ပိတ်ပါ"
     *       "yt တွင် သီချင်းရှာပါ" -> "ယူကျု တွင် သီချင်းရှာပါ"
     */
    fun normalizeCodeSwitching(rawUtterance: String): String {
        if (rawUtterance.isBlank()) return rawUtterance

        var result = rawUtterance

        // Sort all known aliases by length descending so longer compound phrases match first
        val sortedAliases = ALIAS_LOOKUP_MAP.keys.sortedByDescending { it.length }

        for (alias in sortedAliases) {
            val entry = ALIAS_LOOKUP_MAP[alias] ?: continue
            val canonical = entry.canonicalMyanmar

            // Build boundary-aware regex for either English word boundaries or Burmese syllable bounds
            val escapedAlias = Pattern.quote(alias)
            val regex = if (alias.all { it in 'a'..'z' || it in 'A'..'Z' || it == '-' || it == ' ' }) {
                // English alias: match with word boundaries or space/punctuation
                Regex("(?i)(?<=^|\\s|[^a-zA-Z0-9])$escapedAlias(?=$|\\s|[^a-zA-Z0-9])")
            } else {
                // Myanmar alias: match with surrounding whitespace or string bounds
                Regex("(?<=^|\\s|[^က-အ])$escapedAlias(?=$|\\s|[^က-အ])")
            }

            if (regex.containsMatchIn(result)) {
                result = regex.replace(result, canonical)
            }
        }

        return result
    }

    /**
     * Levenshtein Distance for fuzzy string similarity.
     */
    private fun computeLevenshteinDistance(s1: String, s2: String): Int {
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }

        for (i in 0..s1.length) dp[i][0] = i
        for (j in 0..s2.length) dp[0][j] = j

        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,      // deletion
                    dp[i][j - 1] + 1,      // insertion
                    dp[i - 1][j - 1] + cost // substitution
                )
            }
        }
        return dp[s1.length][s2.length]
    }
}
