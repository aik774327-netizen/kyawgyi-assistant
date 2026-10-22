package com.example.service

import com.example.security.MyanmarNegationGuard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * FIX-02 regression tests (Phase 1.5 device remediation).
 *
 * The floating bubble used to announce "အက်ပ် ဖွင့်ပေးလိုက်ပါပြီ" /
 * "အက်ပ်ကို ပိတ်လိုက်ပါပြီ" purely because the user's sentence
 * contained "ဖွင့်"/"ပိတ်", without consulting the execution result.
 * These tests pin the replacement: speech is derived from the actual
 * result string returned by executeVoiceCommand, and a vetoed
 * negation is never announced as success.
 */
class FloatingFeedbackSpeechTest {

    // T16-02-07: a REAL success status maps to the neutral completion.
    @Test
    fun t16_02_07_performedMapsToCompletion() {
        assertEquals(
            FloatingFeedbackSpeech.COMPLETED_MM,
            FloatingFeedbackSpeech.speechFor("ပြန်သွား", "Performed Action: BACK")
        )
        assertEquals(
            FloatingFeedbackSpeech.COMPLETED_MM,
            FloatingFeedbackSpeech.speechFor("အောက်ရွှေ့", "Performed Scroll: DOWN")
        )
        assertEquals(
            FloatingFeedbackSpeech.COMPLETED_MM,
            FloatingFeedbackSpeech.speechFor("YouTube ဖွင့်", "Opening App: YouTube")
        )
    }

    // T16-02-08: inactive accessibility service -> the guidance line,
    // NOT a success claim (the old code spoke "ဖွင့်ပေးလိုက်ပါပြီ" here).
    @Test
    fun t16_02_08_inactiveServiceGivesGuidance() {
        assertEquals(
            FloatingFeedbackSpeech.SERVICE_INACTIVE_MM,
            FloatingFeedbackSpeech.speechFor("YouTube ဖွင့်", "Accessibility service not active")
        )
    }

    // T16-02-09: failures and not-found are announced honestly.
    @Test
    fun t16_02_09_failuresAreHonest() {
        assertEquals(
            FloatingFeedbackSpeech.FAILED_MM,
            FloatingFeedbackSpeech.speechFor("နောက်ပြန်", "Failed to perform BACK action")
        )
        assertEquals(
            FloatingFeedbackSpeech.NOT_FOUND_MM,
            FloatingFeedbackSpeech.speechFor("XYZ ဖွင့်", "App matching 'XYZ' not found")
        )
    }

    // T16-02-10: a vetoed negation gets the guard's no-action
    // response — the exact case the old keyword logic got wrong
    // ("မဖွင့်ပါနဲ့" contains "ဖွင့်" and was announced as opened).
    @Test
    fun t16_02_10_negationNeverAnnouncedAsSuccess() {
        assertEquals(
            MyanmarNegationGuard.NO_ACTION_RESPONSE_MM,
            FloatingFeedbackSpeech.speechFor("YouTube မဖွင့်ပါနဲ့", "Opening App: YouTube")
        )
    }

    // T16-02-11: Burmese executor / coordinator results are spoken
    // verbatim (they already carry the truthful outcome).
    @Test
    fun t16_02_11_burmeseResultsPassThrough() {
        val executorMessage = "မီးခလုတ် ဖွင့်ထားပါသည်ခင်ဗျာ"
        assertEquals(executorMessage, FloatingFeedbackSpeech.speechFor("မီးဖွင့်", executorMessage))
    }

    // T16-02-12: unrecognized / ambiguous results map to silence —
    // never to an invented claim. (E.g. "Lock screen action trigger
    // complete" is returned even when the lock action did NOT run.)
    @Test
    fun t16_02_12_unknownResultsAreSilent() {
        assertNull(FloatingFeedbackSpeech.speechFor("သော့ခတ်", "Lock screen action trigger complete"))
        assertNull(FloatingFeedbackSpeech.speechFor("ကျော်ကြီး", "Wake Word 'ကျော်ကြီး' Recognized! Listening for command..."))
        assertEquals(
            FloatingFeedbackSpeech.NOT_UNDERSTOOD_MM,
            FloatingFeedbackSpeech.speechFor("ဘာညာ", "Command received: ဘာညာ")
        )
    }
}
