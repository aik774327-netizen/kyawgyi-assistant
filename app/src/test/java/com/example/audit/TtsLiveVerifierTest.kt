package com.example.audit

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.utils.MyanmarVoiceSelection
import com.example.utils.TtsConditionVerifier
import com.example.utils.TtsProbe
import com.example.utils.TtsVoiceCandidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ISSUE-033 — the TTS branch of the Live Condition Verifier must run REAL
 * staged checks (engine init -> installed-voice read-back via the FIX-02
 * selector -> speak request -> utterance completion callback) instead of
 * returning a fabricated constant string. All stages are driven here with a
 * fake [TtsProbe]; the Android probe is exercised only on a real device
 * (device testing remains NOT TESTED in CI).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TtsLiveVerifierTest {

    private lateinit var context: Context

    private class FakeTtsProbe(
        private val init: TtsProbe.InitOutcome,
        private val candidates: List<TtsVoiceCandidate>,
        private val speak: TtsProbe.SpeakOutcome
    ) : TtsProbe {
        var shutdownCalled = false
        override fun initialize(): TtsProbe.InitOutcome = init
        override fun voiceCandidates(): List<TtsVoiceCandidate> = candidates
        override fun speakProbe(
            selection: MyanmarVoiceSelection,
            candidates: List<TtsVoiceCandidate>
        ): TtsProbe.SpeakOutcome = speak

        override fun shutdown() {
            shutdownCalled = true
        }
    }

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        ErrorLearningWorkflowEngine.initialize(context)
        ErrorLearningWorkflowEngine.clearAllForTesting()
    }

    private fun recordTtsFailure(): ErrorLearningWorkflowEngine.ErrorLogEntry {
        return ErrorLearningWorkflowEngine.recordErrorLog(
            task = "TTS_SYNTHESIS -> SPEAK_BURMESE",
            commandKey = "tts_synthesis_speak_burmese",
            expectedResult = "Burmese text is spoken aloud",
            actualResult = "#25 Resource Unavailable Error: requested voice data is not on the phone yet",
            attemptedMethod = "MyanmarTTSEngine.speak",
            evidence = "VoicePackStatus: VOICE_PACK_MISSING_DATA, Engine: com.google.android.tts, Script: MYANMAR"
        )
    }

    private fun installFakeProbe(probe: FakeTtsProbe) {
        ErrorLearningWorkflowEngine.ttsProbeFactoryForTesting = { probe }
    }

    @Test
    fun t17_033_01_noMyanmarVoice_resultBlocked_entryStaysBlocked() {
        val entry = recordTtsFailure()
        val probe = FakeTtsProbe(
            init = TtsProbe.InitOutcome.Ready("com.google.android.tts"),
            candidates = listOf(TtsVoiceCandidate("en-US", "en-us-x", true)),
            speak = TtsProbe.SpeakOutcome(requestAccepted = false)
        )
        val result = TtsConditionVerifier.verify(entry.errorId, entry.commandKey, probe)
        assertEquals(VerificationOutcome.BLOCKED, result.outcome)
        assertTrue(result.stages.any { it.startsWith("VOICE_AVAILABLE_FAILED") })
        assertTrue(probe.shutdownCalled)

        installFakeProbe(probe)
        val warning = ErrorLearningWorkflowEngine.evaluateRequestedAction(
            commandKey = "tts_synthesis_speak_burmese",
            context = context
        )
        assertNotNull("Entry must stay blocked when no my-MM voice exists", warning)
    }

    @Test
    fun t17_033_02_voiceDataMissing_resultBlocked_entryStaysBlocked() {
        val entry = recordTtsFailure()
        val probe = FakeTtsProbe(
            init = TtsProbe.InitOutcome.Ready("com.google.android.tts"),
            candidates = listOf(TtsVoiceCandidate("my-MM", "my-mm-x", false)),
            speak = TtsProbe.SpeakOutcome(requestAccepted = false)
        )
        val result = TtsConditionVerifier.verify(entry.errorId, entry.commandKey, probe)
        assertEquals(VerificationOutcome.BLOCKED, result.outcome)
        assertTrue(result.detail.contains("data is not installed"))

        installFakeProbe(probe)
        assertNotNull(
            ErrorLearningWorkflowEngine.evaluateRequestedAction(
                commandKey = "tts_synthesis_speak_burmese",
                context = context
            )
        )
    }

    @Test
    fun t17_033_03_installedVoiceAndSpeakCompletes_verified_entryClears_audioOutputNotVerified() {
        val entry = recordTtsFailure()
        val probe = FakeTtsProbe(
            init = TtsProbe.InitOutcome.Ready("com.google.android.tts"),
            candidates = listOf(TtsVoiceCandidate("my-MM", "my-mm-x", true)),
            speak = TtsProbe.SpeakOutcome(requestAccepted = true, completed = true)
        )
        val result = TtsConditionVerifier.verify(entry.errorId, entry.commandKey, probe)
        assertEquals(VerificationOutcome.VERIFIED, result.outcome)
        assertTrue(result.stages.contains("SPEAK_REQUEST_ACCEPTED"))
        assertTrue(result.stages.contains("SPEAK_COMPLETED"))
        assertTrue(
            "Result must honestly state audio output is not proven",
            result.detail.contains("AUDIO_OUTPUT_NOT_VERIFIED")
        )
        assertFalse(result.detail.contains("Phonetic G2P"))

        installFakeProbe(probe)
        val warning = ErrorLearningWorkflowEngine.evaluateRequestedAction(
            commandKey = "tts_synthesis_speak_burmese",
            context = context
        )
        assertNull("Entry must clear only after the real staged check passes", warning)
        val history = ErrorLearningWorkflowEngine.getAllErrorLogs()
        assertEquals(
            ErrorLearningWorkflowEngine.ErrorLifecycleStatus.RESOLVED,
            history.first { it.errorId == entry.errorId }.status
        )
    }

    @Test
    fun t17_033_04_ttsInitFails_resultFailed_entryStaysBlocked() {
        val entry = recordTtsFailure()
        val probe = FakeTtsProbe(
            init = TtsProbe.InitOutcome.Failed("TTS engine onInit returned status=-1"),
            candidates = emptyList(),
            speak = TtsProbe.SpeakOutcome(requestAccepted = false)
        )
        val result = TtsConditionVerifier.verify(entry.errorId, entry.commandKey, probe)
        assertEquals(VerificationOutcome.FAILED, result.outcome)
        assertTrue(result.stages.contains("ENGINE_INIT_FAILED"))

        installFakeProbe(probe)
        assertNotNull(
            ErrorLearningWorkflowEngine.evaluateRequestedAction(
                commandKey = "tts_synthesis_speak_burmese",
                context = context
            )
        )
    }

    @Test
    fun t17_033_05_speakAcceptedButCompletionErrors_resultFailed_speakFailedStage() {
        val entry = recordTtsFailure()
        val probe = FakeTtsProbe(
            init = TtsProbe.InitOutcome.Ready("com.google.android.tts"),
            candidates = listOf(TtsVoiceCandidate("my-MM", "my-mm-x", true)),
            speak = TtsProbe.SpeakOutcome(requestAccepted = true, failedWithError = 7)
        )
        val result = TtsConditionVerifier.verify(entry.errorId, entry.commandKey, probe)
        assertEquals(VerificationOutcome.FAILED, result.outcome)
        assertTrue(result.stages.any { it.startsWith("SPEAK_FAILED") })

        installFakeProbe(probe)
        assertNotNull(
            ErrorLearningWorkflowEngine.evaluateRequestedAction(
                commandKey = "tts_synthesis_speak_burmese",
                context = context
            )
        )
    }

    @Test
    fun t17_033_06_speakCompletionTimeout_resultUnverified() {
        val entry = recordTtsFailure()
        val probe = FakeTtsProbe(
            init = TtsProbe.InitOutcome.Ready("com.google.android.tts"),
            candidates = listOf(TtsVoiceCandidate("my-MM", "my-mm-x", true)),
            speak = TtsProbe.SpeakOutcome(requestAccepted = true, timedOut = true)
        )
        val result = TtsConditionVerifier.verify(entry.errorId, entry.commandKey, probe)
        assertEquals(VerificationOutcome.UNVERIFIED, result.outcome)
    }

    @Test
    fun t17_033_07_engineVerifier_neverReturnsFabricatedG2PString() {
        val entry = recordTtsFailure()
        val probe = FakeTtsProbe(
            init = TtsProbe.InitOutcome.Ready("com.google.android.tts"),
            candidates = listOf(TtsVoiceCandidate("my-MM", "my-mm-x", false)),
            speak = TtsProbe.SpeakOutcome(requestAccepted = false)
        )
        installFakeProbe(probe)
        val result = ErrorLearningWorkflowEngine.verifyLiveEnvironmentConditions(context, entry)
        assertNotNull(result)
        assertFalse(result!!.detail.contains("Phonetic G2P"))
        assertFalse(result.detail.contains("Verified via MyanmarTTSEngine"))
        assertEquals(VerificationOutcome.BLOCKED, result.outcome)
    }
}
