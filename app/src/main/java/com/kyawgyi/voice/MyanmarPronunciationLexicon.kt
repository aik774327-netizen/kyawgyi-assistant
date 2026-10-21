package com.kyawgyi.voice

/**
 * MyanmarPronunciationLexicon (Additional 12 — Burmese Exceptional G2P Lexicon & Homophone Disambiguation Matrix)
 *
 * Covers Items #7 & #8 of the Production Burmese Voice AI Checklist:
 * 1. **Exceptional Burmese Orthography-to-Phoneme (G2P — "ရေးတော့အမှန် ဖတ်တော့အသံ")**:
 *    Maps literary/orthographic Burmese words whose spoken pronunciation differs from their literal
 *    spelling (voicing assimilation / sandhi / vowel reduction) into phonetic Burmese script for
 *    offline Myanmar TTS (VITS / Piper / F5-TTS), and maps phonetic ASR outputs back to standard
 *    orthography for STT!
 * 2. **Context-Aware Burmese ASR Homophone Disambiguation Matrix**:
 *    Resolves frequent Burmese acoustic homophones and near-homophones (`ဖွင့်/ဖွင့့်`, `ပိတ်/ပိန်`,
 *    `ဆက်/စက်`, `ချ/ချာ`, `နာရီ/နာရီ့`, `မီး/မီး့`, `ဘဏ်/ပန်`) using surrounding command context words.
 */
object MyanmarPronunciationLexicon {

    /**
     * Orthographic Burmese (ရေးထုံးအမှန်) -> Spoken Phonetic Burmese (ဖတ်သံ/အသံထွက်)
     * Used before TTS synthesis so Myanmar neural/system TTS pronounces sandhi & reduced vowels naturally.
     */
    val ORTHOGRAPHY_TO_SPOKEN_G2P: Map<String, String> = linkedMapOf(
        // Food, Daily Life & Common Nouns
        "လက်ဖက်ရည်" to "လဖက်ရည်",
        "လက်ဖက်သုပ်" to "လဖက်သုပ်",
        "လက်ဖက်" to "လဖက်",
        "ပုစွန်" to "ပဇွန်",
        "ပုရွက်ဆိတ်" to "ပရွက်ဆိတ်",
        "ပုလင်း" to "ပလင်း",
        "ပုဝါ" to "ပဝါ",
        "ပုဆိုး" to "ပဆိုး",
        "ပုတီး" to "ပတီး",
        "ကုလားပဲ" to "ကလားပဲ",
        "ကုလားထိုင်" to "ကလားထိုင်",
        "ကုန်သည်" to "ကုန်သယ်",
        "တံခါး" to "တခါး",
        "တံတား" to "တဒါး",
        "တံမြက်စည်း" to "တမြက်စီး",
        "တံဆိပ်" to "တဆိပ်",
        "တံငါ" to "တငါ",
        "သစ်ပင်" to "သစ်ပင်",
        "သစ်သီး" to "သစ်သီး",
        "သူငယ်ချင်း" to "သငယ်ချင်း",
        "သမီး" to "သမီး",
        "သားသမီး" to "သားသမီး",
        "စကားပြော" to "စဂါးပြော",
        "စကား" to "စဂါး",
        "စာရင်း" to "စရင်း",
        "စပါး" to "စဗါး",
        "ဆန်စပါး" to "ဆန်စဗါး",
        "ဘုရား" to "ဖရား",
        "ရွှေတိဂုံဘုရား" to "ရွှေတိဂုံဖရား",
        "ဘုန်းကြီး" to "ဖုန်းကြီး",
        "ပုဂံ" to "ပဂံ",
        "ဗိုလ်ချုပ်" to "ဗိုချုပ်",
        "အနုပညာ" to "အနုပညာ",
        "ပညာရေး" to "ပညာရေး",
        "ကျန်းမာရေး" to "ကျန်းမာရေး",
        "လမ်းညွှန်" to "လမ်းညွှန်",
        "အချိန်ဇယား" to "အချိန်ဇယား",
        "လက်နှိပ်ဓာတ်မီး" to "လက်နှိပ်ဓာတ်မီး"
    )

    /**
     * Reverse map: Colloquial/Phonetic ASR output -> Canonical Burmese Orthography for NLU intent matching.
     */
    val SPOKEN_TO_ORTHOGRAPHY_STT: Map<String, String> = linkedMapOf(
        "လဖက်ရည်" to "လက်ဖက်ရည်",
        "လဖက်သုပ်" to "လက်ဖက်သုပ်",
        "ပဇွန်" to "ပုစွန်",
        "ပလင်း" to "ပုလင်း",
        "ပဆိုး" to "ပုဆိုး",
        "ကလားထိုင်" to "ကုလားထိုင်",
        "တခါး" to "တံခါး",
        "တဒါး" to "တံတား",
        "တမြက်စီး" to "တံမြက်စည်း",
        "သငယ်ချင်း" to "သူငယ်ချင်း",
        "စဂါးပြော" to "စကားပြော",
        "စဂါး" to "စကား",
        "စရင်း" to "စာရင်း",
        "ဖရား" to "ဘုရား",
        "ပဂံ" to "ပုဂံ",
        "လက်နှိပ်ဒတ်မီး" to "လက်နှိပ်ဓာတ်မီး",
        "ဒတ်မီး" to "ဓာတ်မီး"
    )

    /**
     * Context-sensitive Burmese ASR Homophone Disambiguation rules.
     * Each entry specifies a regex matching a misrecognized homophone in its surrounding context
     * and the corrected canonical Burmese phrase.
     */
    private val CONTEXT_HOMOPHONE_MATRIX: List<Pair<Regex, String>> = listOf(
        // "ဖုန်း စက်ပေးပါ" / "ဖုန်း စပ်ပေးပါ" -> "ဖုန်း ဆက်ပေးပါ"
        Regex("(ဖုန်း)\\s*(?:စက်|စပ်|ဆပ်)(ပေးပါ|ပါ|မယ်)") to "$1 ဆက်$2",
        // "ဖုန်း ချာပေးပါ" / "ဖုန်း ခြပေးပါ" -> "ဖုန်း ချပေးပါ"
        Regex("(ဖုန်း)\\s*(?:ချာ|ခြ)(ပေးပါ|ပါ|မယ်)") to "$1 ချ$2",
        // "ဝိုင်ဖိုင် ပိန်ပေးပါ" / "ဘလူးတုသ် ပိန်ပေးပါ" / "မီး ပိန်ပေးပါ" -> "... ပိတ်ပေးပါ"
        Regex("(ဝိုင်ဖိုင်|Wi-Fi|ဘလူးတုသ်|Bluetooth|မီး|ဓာတ်မီး|ဟော့စပေါ့တ်|Hotspot)\\s*(?:ပိန်|ပိ|ပိတ့်)(ပေးပါ|ပါ|မယ်)") to "$1 ပိတ်$2",
        // "ဝိုင်ဖိုင် ဖွင့့်ပေးပါ" / "ဖွင်ပေးပါ" -> "ဖွင့်ပေးပါ"
        Regex("(?:ဖွင့့်|ဖွင်)(ပေးပါ|ပါ|မယ်)") to "ဖွင့်$1",
        // "နာရီ နှိုးစက်" homophones ("နာရီ့" / "နှိုးဆက်")
        Regex("နာရီ့") to "နာရီ",
        Regex("နှိုးဆက်") to "နှိုးစက်",
        // "အသံ တိုး/ချဲ့" homophones ("အသံ တိုပေးပါ" -> "အသံ တိုးပေးပါ", "အသံ ချဲပေးပါ" -> "အသံ ချဲ့ပေးပါ")
        Regex("(အသံ)\\s*တို(ပေးပါ|ပါ|မယ်)") to "$1 တိုး$2",
        Regex("(အသံ)\\s*ချဲ(ပေးပါ|ပါ|မယ်)") to "$1 ချဲ့$2",
        // "ဘက်ထရီ" homophones ("ဘတ်ထရီ" / "ဘက်တရီ")
        Regex("(?:ဘတ်ထရီ|ဘက်တရီ|ဗက်ထရီ)") to "ဘက်ထရီ"
    )

    /**
     * Converts standard Burmese text into phonetic G2P form for natural TTS pronunciation.
     */
    fun applyTtsPronunciationG2P(text: String): String {
        if (text.isBlank()) return text
        var out = text
        val sorted = ORTHOGRAPHY_TO_SPOKEN_G2P.entries.sortedByDescending { it.key.length }
        for ((orthographic, spoken) in sorted) {
            if (orthographic != spoken && out.contains(orthographic)) {
                out = out.replace(orthographic, spoken)
            }
        }
        return out
    }

    /**
     * Disambiguates Burmese ASR homophones and restores standard Burmese orthography for NLU intent parsing.
     */
    fun disambiguateSttHomophones(rawTranscript: String): String {
        if (rawTranscript.isBlank()) return rawTranscript
        var out = rawTranscript.trim()

        // 1. Restore standard orthography from colloquial phonetic forms
        val sortedSpoken = SPOKEN_TO_ORTHOGRAPHY_STT.entries.sortedByDescending { it.key.length }
        for ((spoken, canonical) in sortedSpoken) {
            if (out.contains(spoken)) {
                out = out.replace(spoken, canonical)
            }
        }

        // 2. Apply context-sensitive homophone disambiguation matrix
        for ((pattern, replacement) in CONTEXT_HOMOPHONE_MATRIX) {
            out = out.replace(pattern, replacement)
        }

        return out.replace(Regex("\\s+"), " ").trim()
    }
}
