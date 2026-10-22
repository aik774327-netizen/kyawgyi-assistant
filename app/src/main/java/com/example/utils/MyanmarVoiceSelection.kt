package com.example.utils

/**
 * FIX-02 (Phase 1.5 device remediation): systematic selection of an
 * actually-installed Myanmar TTS voice, and the honest speech policy
 * when none exists.
 *
 * Device defect D1/B2: Burmese replies on the user's phone sounded
 * ROBOTIC. The cause was in MyanmarTTSEngine.speak (and the same pattern
 * in the IR VoiceEngine): with no my-MM voice data installed, Burmese
 * text was transliterated into Latin phonetics and read aloud by a US
 * English voice as if that were Burmese speech. This file is the
 * replacement decision logic — pure Kotlin over voice descriptors so it
 * is unit-testable without a live TextToSpeech instance:
 *
 * - [MyanmarVoiceSelector] enumerates the engine's voices and picks an
 *   INSTALLED Myanmar voice (exact my-MM preferred). A voice whose data
 *   is merely downloadable is NOT treated as usable.
 * - [BurmeseSpeechPolicy] decides what speak() may do. When no Myanmar
 *   voice is installed, Burmese text is NEVER fed to a foreign voice
 *   (neither raw nor transliterated); the user instead hears a short
 *   English notice and gets UI guidance for installing the voice data.
 */

/** A TTS voice reduced to what selection needs. */
data class TtsVoiceCandidate(
    val localeTag: String,
    val name: String,
    /** True only when the voice's data is actually on the device. */
    val dataInstalled: Boolean
)

enum class MyanmarVoiceChoice {
    /** An installed Myanmar voice exists and can be selected. */
    INSTALLED_VOICE,

    /** Myanmar voices exist in the engine but their data is not downloaded. */
    VOICE_DATA_MISSING,

    /** The engine offers no Myanmar voice at all. */
    NO_MYANMAR_VOICE
}

data class MyanmarVoiceSelection(
    val choice: MyanmarVoiceChoice,
    /** Index into the candidate list of the chosen voice, or -1. */
    val selectedIndex: Int
)

object MyanmarVoiceSelector {

    private fun normalizeTag(tag: String): String = tag.trim().lowercase().replace('_', '-')

    private fun isMyanmar(tag: String): Boolean {
        val t = normalizeTag(tag)
        return t == "my" || t.startsWith("my-")
    }

    /**
     * Picks the Myanmar voice to use from [candidates] (typically the
     * engine's full voice list reduced by the caller). Preference order:
     * installed my-MM, then any installed my voice. If Myanmar voices
     * exist but none is installed, the verdict is VOICE_DATA_MISSING —
     * downloading is the user's remedy, not speaking through them.
     */
    fun select(candidates: List<TtsVoiceCandidate>): MyanmarVoiceSelection {
        val myanmar = candidates.withIndex().filter { isMyanmar(it.value.localeTag) }
        if (myanmar.isEmpty()) {
            return MyanmarVoiceSelection(MyanmarVoiceChoice.NO_MYANMAR_VOICE, -1)
        }
        val exactInstalled = myanmar.firstOrNull {
            it.value.dataInstalled && normalizeTag(it.value.localeTag) == "my-mm"
        }
        if (exactInstalled != null) {
            return MyanmarVoiceSelection(MyanmarVoiceChoice.INSTALLED_VOICE, exactInstalled.index)
        }
        val anyInstalled = myanmar.firstOrNull { it.value.dataInstalled }
        if (anyInstalled != null) {
            return MyanmarVoiceSelection(MyanmarVoiceChoice.INSTALLED_VOICE, anyInstalled.index)
        }
        return MyanmarVoiceSelection(MyanmarVoiceChoice.VOICE_DATA_MISSING, myanmar.first().index)
    }
}

enum class BurmeseSpeechDecision {
    /** Speak the Burmese text with the selected Myanmar voice. */
    SPEAK_WITH_MYANMAR_VOICE,

    /** Non-Burmese text: speak normally with the current voice. */
    SPEAK_NORMALLY,

    /**
     * Burmese text but no installed Myanmar voice: do NOT vocalize the
     * Burmese through a foreign voice. Speak the honest English notice
     * and surface UI guidance instead.
     */
    HONEST_NOTICE_NO_MYANMAR_VOICE
}

object BurmeseSpeechPolicy {

    /**
     * The one spoken fallback. It is English because the premise is
     * that no Burmese voice exists on the device; it says exactly what
     * is wrong and what to do — it never pretends to be the Burmese
     * reply it replaces.
     */
    const val ENGLISH_NOTICE: String =
        "Myanmar voice data is not installed on this phone, so I cannot read Burmese aloud yet. " +
            "Please download the Burmese voice in Google Text-to-speech settings."

    fun decide(containsMyanmar: Boolean, voiceChoice: MyanmarVoiceChoice): BurmeseSpeechDecision {
        if (!containsMyanmar) return BurmeseSpeechDecision.SPEAK_NORMALLY
        return if (voiceChoice == MyanmarVoiceChoice.INSTALLED_VOICE) {
            BurmeseSpeechDecision.SPEAK_WITH_MYANMAR_VOICE
        } else {
            BurmeseSpeechDecision.HONEST_NOTICE_NO_MYANMAR_VOICE
        }
    }
}
