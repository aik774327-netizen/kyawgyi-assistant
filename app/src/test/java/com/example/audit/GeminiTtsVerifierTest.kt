package com.example.audit

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.GeminiFailureClassifier
import com.example.data.GeminiFailureClass
import com.example.data.GeminiHttpException
import com.example.utils.GeminiTtsProbe
import com.example.utils.MyanmarVoiceSelection
import com.example.utils.TtsConditionVerifier
import com.example.utils.TtsProbe
import com.example.utils.TtsVoiceCandidate
import com.kyawgyi.voice.TtsEngineType
import com.kyawgyi.voice.VoiceSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.SocketTimeoutException

/**
 * §7 Part C regression tests (t18_c_07..11) — the Gemini ONLINE
 * branch of the ISSUE-033 TTS live verifier. All probes are fakes;
 * no network, no key. Pins: no-key -> BLOCKED; a real probe success
 * (PCM bytes returned) -> VERIFIED with the AUDIO_OUTPUT_NOT_VERIFIED
 * limitation kept in the detail; HTTP failure -> FAILED with the
 * classifier message; network-unreachable probe -> UNVERIFIED
 * (transient states neither prove nor clear); and the workflow
 * engine dispatching verification by the user's SELECTED TTS engine.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GeminiTtsVerifierTest {

    private lateinit var context: Context

    private class FakeGeminiProbe(
        private val keyPresent: Boolean,
        private val pcmOrError: Any
    ) : GeminiTtsProbe {
        var shutdownCalled = false
        override fun hasConfiguredKey(): Boolean = keyPresent
        override fun synthesizeProbePcmBytes(): Int =
            when (pcmOrError) {
                is Int -> pcmOrError
                is Exception -> throw pcmOrError
                else -> error("bad fake")
            }
        override fun shutdown() {
            shutdownCalled = true
        }
    }

    private class FakeSystemProbe : TtsProbe {
        override fun initialize(): TtsProbe.InitOutcome =
            TtsProbe.InitOutcome.Ready("com.google.android.tts")
        override fun voiceCandidates(): List<TtsVoiceCandidate> =
            listOf(TtsVoiceCandidate("my-MM", "my-mm-x", true))
        override fun speakProbe(
            selection: MyanmarVoiceSelection,
            candidates: List<TtsVoiceCandidate>
        ): TtsProbe.SpeakOutcome =
            TtsProbe.SpeakOutcome(requestAccepted = true, completed = true)
        override fun shutdown() {}
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        ErrorLearningWorkflowEngine.initialize(context)
        ErrorLearningWorkflowEngine.clearAllForTesting()
        VoiceSettings.getInstance(context).preferredTtsEngine = TtsEngineType.SYSTEM
    }

    // t18_c_07: online selected but no key in the pool -> BLOCKED,
    // with the no-key stage recorded and the probe shut down.
    @Test
    fun t18_c_07_noKeyBlocked() {
        val probe = FakeGeminiProbe(keyPresent = false, pcmOrError = 0)
        val result = TtsConditionVerifier.verifyGeminiOnline("ER-T18", "key1", probe)
        assertEquals(VerificationOutcome.BLOCKED, result.outcome)
        assertEquals(TtsConditionVerifier.GEMINI_EVIDENCE_SOURCE, result.evidenceSource)
        assertTrue(result.stages.any { it.startsWith("KEY_PRESENT_FAILED") })
        assertTrue(probe.shutdownCalled)
    }

    // t18_c_08: key present + probe returns PCM -> VERIFIED, and the
    // detail keeps the AUDIO_OUTPUT_NOT_VERIFIED limitation (returned
    // audio data does not prove audible device playback).
    @Test
    fun t18_c_08_probeSuccessVerifiedButAudioOutputNotVerified() {
        val probe = FakeGeminiProbe(keyPresent = true, pcmOrError = 4800)
        val result = TtsConditionVerifier.verifyGeminiOnline("ER-T18", "key1", probe)
        assertEquals(VerificationOutcome.VERIFIED, result.outcome)
        assertTrue(result.stages.contains("KEY_PRESENT"))
        assertTrue(result.stages.contains("SYNTH_REQUEST_ACCEPTED"))
        assertTrue(result.stages.any { it.startsWith("PCM_AUDIO_RECEIVED") })
        assertTrue(result.detail.contains("AUDIO_OUTPUT_NOT_VERIFIED"))
    }

    // t18_c_09: probe HTTP failure -> FAILED with the classifier's
    // message for that class.
    @Test
    fun t18_c_09_probeHttpFailureFailed() {
        val probe = FakeGeminiProbe(keyPresent = true, pcmOrError = GeminiHttpException(500))
        val result = TtsConditionVerifier.verifyGeminiOnline("ER-T18", "key1", probe)
        assertEquals(VerificationOutcome.FAILED, result.outcome)
        assertTrue(
            result.detail.contains(
                GeminiFailureClassifier.userMessageFor(GeminiFailureClass.SERVER, 500)
            )
        )
    }

    // t18_c_10: probe cannot reach the service -> UNVERIFIED (a
    // transient network state must neither clear nor condemn).
    @Test
    fun t18_c_10_probeTimeoutUnverified() {
        val probe = FakeGeminiProbe(
            keyPresent = true,
            pcmOrError = SocketTimeoutException("read timed out")
        )
        val result = TtsConditionVerifier.verifyGeminiOnline("ER-T18", "key1", probe)
        assertEquals(VerificationOutcome.UNVERIFIED, result.outcome)
    }

    // t18_c_11: the workflow engine verifies the engine the user
    // actually selected — online selection routes TTS verification to
    // the Gemini probe; system selection keeps the system probe.
    @Test
    fun t18_c_11_workflowDispatchesBySelectedTtsEngine() {
        val entry = ErrorLearningWorkflowEngine.recordErrorLog(
            task = "TTS_SYNTHESIS -> SPEAK_BURMESE",
            commandKey = "tts_synthesis_speak_burmese_t18",
            expectedResult = "Burmese text is spoken aloud",
            actualResult = "#25 Resource Unavailable Error",
            attemptedMethod = "MyanmarTTSEngine.speak",
            evidence = "VoicePackStatus: VOICE_PACK_MISSING_DATA"
        )

        // Online selected -> Gemini probe branch.
        VoiceSettings.getInstance(context).preferredTtsEngine = TtsEngineType.GEMINI_ONLINE
        ErrorLearningWorkflowEngine.geminiTtsProbeFactoryForTesting = {
            FakeGeminiProbe(keyPresent = true, pcmOrError = 2400)
        }
        val onlineResult = ErrorLearningWorkflowEngine.verifyLiveEnvironmentConditions(context, entry)
        assertNotNull(onlineResult)
        assertEquals(TtsConditionVerifier.GEMINI_EVIDENCE_SOURCE, onlineResult!!.evidenceSource)
        assertEquals(VerificationOutcome.VERIFIED, onlineResult.outcome)

        // System selected -> the ISSUE-033 system probe branch.
        VoiceSettings.getInstance(context).preferredTtsEngine = TtsEngineType.SYSTEM
        ErrorLearningWorkflowEngine.ttsProbeFactoryForTesting = { FakeSystemProbe() }
        val systemEntry = ErrorLearningWorkflowEngine.recordErrorLog(
            task = "TTS_SYNTHESIS -> SPEAK_BURMESE",
            commandKey = "tts_synthesis_speak_burmese_t18_sys",
            expectedResult = "Burmese text is spoken aloud",
            actualResult = "#25 Resource Unavailable Error",
            attemptedMethod = "MyanmarTTSEngine.speak",
            evidence = "VoicePackStatus: VOICE_PACK_MISSING_DATA"
        )
        val systemResult = ErrorLearningWorkflowEngine.verifyLiveEnvironmentConditions(context, systemEntry)
        assertNotNull(systemResult)
        assertEquals(TtsConditionVerifier.EVIDENCE_SOURCE, systemResult!!.evidenceSource)
    }
}
