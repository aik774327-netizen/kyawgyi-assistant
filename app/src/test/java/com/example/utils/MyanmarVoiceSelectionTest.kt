package com.example.utils

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * FIX-02 regression tests (Phase 1.5 device remediation).
 *
 * Device defect B2: Burmese replies sounded ROBOTIC because, with no
 * my-MM voice data installed, the engines transliterated Burmese into
 * Latin phonetics and read it with a US voice. These tests pin the
 * replacement logic: an installed Myanmar voice is selected
 * systematically (exact my-MM preferred), a merely-downloadable voice
 * does NOT count as usable, and the speech policy never routes Burmese
 * text to a foreign voice — it chooses the honest notice instead.
 */
class MyanmarVoiceSelectionTest {

    private fun candidate(tag: String, installed: Boolean, name: String = tag) =
        TtsVoiceCandidate(localeTag = tag, name = name, dataInstalled = installed)

    // T16-02-01: exact installed my-MM wins over other voices.
    @Test
    fun t16_02_01_prefersInstalledExactMyMm() {
        val voices = listOf(
            candidate("en-US", installed = true),
            candidate("my-MM", installed = true),
            candidate("th-TH", installed = true)
        )
        val selection = MyanmarVoiceSelector.select(voices)
        assertEquals(MyanmarVoiceChoice.INSTALLED_VOICE, selection.choice)
        assertEquals(1, selection.selectedIndex)
    }

    // T16-02-02: an installed generic "my" voice is usable when no
    // exact my-MM voice is installed.
    @Test
    fun t16_02_02_acceptsInstalledGenericMy() {
        val voices = listOf(
            candidate("en-US", installed = true),
            candidate("my", installed = true)
        )
        val selection = MyanmarVoiceSelector.select(voices)
        assertEquals(MyanmarVoiceChoice.INSTALLED_VOICE, selection.choice)
        assertEquals(1, selection.selectedIndex)
    }

    // T16-02-03: a Myanmar voice whose data is NOT downloaded is not
    // usable — the verdict is VOICE_DATA_MISSING, never INSTALLED.
    @Test
    fun t16_02_03_downloadableOnlyVoiceIsMissingData() {
        val voices = listOf(
            candidate("en-US", installed = true),
            candidate("my-MM", installed = false)
        )
        val selection = MyanmarVoiceSelector.select(voices)
        assertEquals(MyanmarVoiceChoice.VOICE_DATA_MISSING, selection.choice)
    }

    // T16-02-04: no Myanmar voice at all -> NO_MYANMAR_VOICE.
    @Test
    fun t16_02_04_noMyanmarVoice() {
        val voices = listOf(
            candidate("en-US", installed = true),
            candidate("th-TH", installed = true)
        )
        assertEquals(
            MyanmarVoiceChoice.NO_MYANMAR_VOICE,
            MyanmarVoiceSelector.select(voices).choice
        )
        assertEquals(
            MyanmarVoiceChoice.NO_MYANMAR_VOICE,
            MyanmarVoiceSelector.select(emptyList()).choice
        )
    }

    // T16-02-05: an installed generic-my beats a not-installed my-MM —
    // installed-ness outranks exactness.
    @Test
    fun t16_02_05_installedBeatsExactButMissing() {
        val voices = listOf(
            candidate("my-MM", installed = false),
            candidate("my", installed = true)
        )
        val selection = MyanmarVoiceSelector.select(voices)
        assertEquals(MyanmarVoiceChoice.INSTALLED_VOICE, selection.choice)
        assertEquals(1, selection.selectedIndex)
    }

    // T16-02-06: the speech policy. Burmese text is spoken as Burmese
    // ONLY with an installed Myanmar voice; in every other case the
    // honest notice is chosen (never raw Burmese, never phonetics,
    // through a foreign voice). Non-Burmese text is unaffected.
    @Test
    fun t16_02_06_speechPolicyMatrix() {
        assertEquals(
            BurmeseSpeechDecision.SPEAK_WITH_MYANMAR_VOICE,
            BurmeseSpeechPolicy.decide(containsMyanmar = true, voiceChoice = MyanmarVoiceChoice.INSTALLED_VOICE)
        )
        assertEquals(
            BurmeseSpeechDecision.HONEST_NOTICE_NO_MYANMAR_VOICE,
            BurmeseSpeechPolicy.decide(containsMyanmar = true, voiceChoice = MyanmarVoiceChoice.VOICE_DATA_MISSING)
        )
        assertEquals(
            BurmeseSpeechDecision.HONEST_NOTICE_NO_MYANMAR_VOICE,
            BurmeseSpeechPolicy.decide(containsMyanmar = true, voiceChoice = MyanmarVoiceChoice.NO_MYANMAR_VOICE)
        )
        assertEquals(
            BurmeseSpeechDecision.SPEAK_NORMALLY,
            BurmeseSpeechPolicy.decide(containsMyanmar = false, voiceChoice = MyanmarVoiceChoice.NO_MYANMAR_VOICE)
        )
        assertEquals(
            BurmeseSpeechDecision.SPEAK_NORMALLY,
            BurmeseSpeechPolicy.decide(containsMyanmar = false, voiceChoice = MyanmarVoiceChoice.INSTALLED_VOICE)
        )
    }
}
