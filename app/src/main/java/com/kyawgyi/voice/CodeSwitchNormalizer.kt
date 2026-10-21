package com.kyawgyi.voice

import com.example.nlu.BurmeseNluPreprocessor
import com.example.nlu.BurmeseTtsTextNormalizer

/**
 * CodeSwitchNormalizer (Requirements 9 — Bidirectional Burmese-English Code-Switching Normalizer)
 *
 * Handles seamless Burmese + English mixed speech ("Code-Switching"):
 * 1. [normalizeSttTranscript]: Converts Burmese phonetic transcriptions of English apps,
 *    connectivity toggles, and technical terms into canonical English entities for NLU/Intent routing
 *    while preserving Burmese grammar particles (`နဲ့`, `ကို`, `မှာ`, `ကနေ`).
 * 2. [normalizeTtsResponse]: Converts English loanwords, app names, units, and acronyms (`KBZPay`,
 *    `Wi-Fi`, `OTP`, `PIN`, `SMS`, `VPN`, `GPS`, `MB`, `GB`) embedded in Burmese responses into
 *    natural Burmese phonetic script so offline Myanmar neural TTS (VITS / F5-TTS) pronounces
 *    every syllable smoothly without dropping Latin characters.
 */
class CodeSwitchNormalizer {

    companion object {
        /**
         * Burmese spoken loanword -> Canonical English entity for STT / NLU intent parsing.
         */
        private val BURMESE_TO_ENGLISH_ENTITY: LinkedHashMap<String, String> = linkedMapOf(
            "ကေဘီဇက်ပေး" to "KBZPay",
            "ကေဘီဇတ်ပေး" to "KBZPay",
            "ကေပေး" to "KBZPay",
            "ဝေ့ဗ်ပေး" to "WavePay",
            "ဝေဗ်ပေး" to "WavePay",
            "ဧရာပေး" to "AYA Pay",
            "စီဘီပေး" to "CB Pay",
            "ယူကျု့ဘ်" to "YouTube",
            "ယူကျု" to "YouTube",
            "ဖေ့စ်ဘွတ်ခ်" to "Facebook",
            "ဖေ့စ်ဘုတ်" to "Facebook",
            "တယ်လီဂရမ်" to "Telegram",
            "တစ်တော့ခ်" to "TikTok",
            "တစ်တော့" to "TikTok",
            "မက်ဆင်ဂျာ" to "Messenger",
            "ဂျီမေးလ်" to "Gmail",
            "ဂူးဂဲလ်မြေပုံ" to "Google Maps",
            "ဝိုင်ဖိုင်" to "Wi-Fi",
            "ဘလူးတုသ်" to "Bluetooth",
            "ဘလူးတု" to "Bluetooth",
            "ဟော့စပေါ့တ်" to "Hotspot",
            "ဟော့စပေါ့" to "Hotspot",
            "ဆက်တင်စ်" to "Settings",
            "ဆက်တင်" to "Settings",
            "အမ်ပီတီ" to "MPT",
            "အက်တမ်" to "ATOM",
            "အူရီဒူး" to "Ooredoo",
            "မိုင်တယ်" to "Mytel"
        )

        /**
         * English token / acronym -> Burmese phonetic pronunciation for offline Myanmar TTS.
         */
        private val ENGLISH_TO_BURMESE_TTS: LinkedHashMap<String, String> = linkedMapOf(
            "Google Maps" to "ဂူးဂဲလ် မြေပုံ",
            "AYA Pay" to "ဧရာပေး",
            "CB Pay" to "စီဘီပေး",
            "KBZPay" to "ကေဘီဇက်ပေး",
            "KPay" to "ကေပေး",
            "WavePay" to "ဝေ့ဗ်ပေး",
            "YouTube" to "ယူကျု့ဘ်",
            "Facebook" to "ဖေ့စ်ဘွတ်ခ်",
            "Telegram" to "တယ်လီဂရမ်",
            "TikTok" to "တစ်တော့ခ်",
            "Messenger" to "မက်ဆင်ဂျာ",
            "Viber" to "ဗိုင်ဘာ",
            "Gmail" to "ဂျီမေးလ်",
            "Settings" to "ဆက်တင်စ်",
            "Bluetooth" to "ဘလူးတုသ်",
            "Hotspot" to "ဟော့စပေါ့တ်",
            "Wi-Fi" to "ဝိုင်ဖိုင်",
            "WiFi" to "ဝိုင်ဖိုင်",
            "Ooredoo" to "အူရီဒူး",
            "Mytel" to "မိုင်တယ်",
            "ATOM" to "အက်တမ်",
            "MPT" to "အမ်ပီတီ",
            "OTP" to "အိုတီပီ",
            "PIN" to "ပင်နံပါတ်",
            "SMS" to "အက်စ်အမ်အက်စ်",
            "SIM" to "ဆင်းမ်ကတ်",
            "VPN" to "ဗီပီအန်",
            "GPS" to "ဂျီပီအက်စ်",
            "USB" to "ယူအက်စ်ဘီ",
            "API" to "အေပီအိုင်",
            "CPU" to "စီပီယူ",
            "RAM" to "ရမ်",
            "GB" to "ဂစ်ဂါဘိုက်",
            "MB" to "မက်ဂါဘိုက်",
            "KB" to "ကီလိုဘိုက်",
            "AI" to "အေအိုင်"
        )

        /**
         * Burmese grammar particles that frequently attach directly to an English token
         * without whitespace (e.g., "KBZPayနဲ့" -> "KBZPay နဲ့").
         */
        private val ATTACHED_PARTICLES = listOf("နဲ့", "ကို", "မှာ", "ကနေ", "ဖြင့်", "ရဲ့", "သို့")
    }

    /**
     * Normalizes a mixed Burmese-English STT transcript into canonical entities + clean spacing.
     */
    fun normalizeSttTranscript(rawText: String): String {
        if (rawText.isBlank()) return rawText
        var text = rawText

        // Separate Burmese particles attached directly to Latin words (e.g. "KBZPayနဲ့" -> "KBZPay နဲ့")
        for (particle in ATTACHED_PARTICLES) {
            text = text.replace(Regex("([A-Za-z0-9])($particle)"), "$1 $2")
        }

        // Convert Burmese phonetic loanwords to canonical English entity names (longest first)
        val sortedEntries = BURMESE_TO_ENGLISH_ENTITY.entries.sortedByDescending { it.key.length }
        for ((burmesePhonetic, canonicalEnglish) in sortedEntries) {
            if (text.contains(burmesePhonetic)) {
                text = text.replace(burmesePhonetic, canonicalEnglish)
            }
        }

        return text.replace(Regex("\\s+"), " ").trim()
    }

    /**
     * Normalizes a mixed Burmese-English response string into pure Burmese phonetic script
     * suitable for offline Myanmar neural TTS synthesis.
     */
    fun normalizeTtsResponse(rawResponse: String): String {
        if (rawResponse.isBlank()) return rawResponse
        var out = BurmeseTtsTextNormalizer.normalizeForTts(rawResponse)

        val sortedTts = ENGLISH_TO_BURMESE_TTS.entries.sortedByDescending { it.key.length }
        for ((englishTerm, burmeseSpoken) in sortedTts) {
            val pattern = Regex("(?i)(?<![A-Za-z])${Regex.escape(englishTerm)}(?![A-Za-z])")
            out = out.replace(pattern, burmeseSpoken)
        }

        return out.replace(Regex("\\s+"), " ").trim()
    }
}
