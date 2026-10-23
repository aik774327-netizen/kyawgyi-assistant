package com.example.audit

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.FailureCategory
import com.example.ocr.FailureMemoryEngine
import com.example.utils.MyanmarVoiceSelection
import com.example.utils.TtsProbe
import com.example.utils.TtsVoiceCandidate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ISSUE-039 — the RESOURCE branch of the Live Condition Verifier must check
 * the resource that actually failed, dispatched by the resource kind stored
 * on the record (TTS_VOICE / PACKAGE / MODEL_FILE / OTHER). Before this fix
 * every RESOURCE record — including ER-007's missing-YouTube record — was
 * "verified" by checking whether Sherpa/Vosk STT model files existed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ResourceKindVerifierTest {

    private lateinit var context: Context

    private class FakeProbe(
        private val candidates: List<TtsVoiceCandidate>,
        private val speak: TtsProbe.SpeakOutcome
    ) : TtsProbe {
        override fun initialize(): TtsProbe.InitOutcome = TtsProbe.InitOutcome.Ready("com.google.android.tts")
        override fun voiceCandidates(): List<TtsVoiceCandidate> = candidates
        override fun speakProbe(
            selection: MyanmarVoiceSelection,
            candidates: List<TtsVoiceCandidate>
        ): TtsProbe.SpeakOutcome = speak

        override fun shutdown() = Unit
    }

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        ErrorLearningWorkflowEngine.initialize(context)
        ErrorLearningWorkflowEngine.clearAllForTesting()
        runBlocking { FailureMemoryEngine.clearAllPersistently(context) }
        FailureMemoryEngine.initialize(context)
    }

    @Test
    fun t17_039_01_resourceKindStoredAtRecordTime() {
        FailureMemoryEngine.recordFailure(
            component = "TTS_SYNTHESIS",
            operation = "SPEAK_BURMESE",
            category = FailureCategory.RESOURCE,
            rootCauseMm = "အသံဒေတာ မရှိပါ",
            technicalDetails = "VoicePackStatus: VOICE_PACK_MISSING_DATA",
            suggestedRemediationMm = "Voice pack ဒေါင်းပါ"
        )
        FailureMemoryEngine.recordFailure(
            component = "APP_LAUNCH",
            operation = "ယူကျု",
            category = FailureCategory.RESOURCE,
            rootCauseMm = "အက်ပ် မတွေ့ပါ",
            technicalDetails = "UniversalAppLauncher error: APP_NOT_FOUND_OR_LOW_CONFIDENCE",
            suggestedRemediationMm = "Play Store မှ ထည့်သွင်းပါ"
        )
        val modelEntry = ErrorLearningWorkflowEngine.recordErrorLog(
            task = "Load STT model files",
            commandKey = "stt_model_load",
            expectedResult = "Model loads",
            actualResult = "Model file missing",
            attemptedMethod = "ModelManager.ensureSherpaModel",
            evidence = "model file missing on disk"
        )
        val logs = ErrorLearningWorkflowEngine.getAllErrorLogs()
        assertEquals(
            "TTS_VOICE",
            logs.first { it.commandKey.contains("speak_burmese") }.resourceKind
        )
        assertEquals(
            "PACKAGE",
            logs.first { it.commandKey.contains("app_launch") }.resourceKind
        )
        assertEquals("MODEL_FILE", modelEntry.resourceKind)
    }

    @Test
    fun t17_039_02_ttsVoiceKind_dispatchesToTtsProbe_evenWithoutKeyTrigger() {
        // Key/task/evidence deliberately avoid every legacy TTS trigger string;
        // only the stored resource kind can route this record to the TTS probe.
        val entry = ErrorLearningWorkflowEngine.recordErrorLog(
            task = "Speak the reply aloud",
            commandKey = "voice_output_failed",
            expectedResult = "Reply is spoken",
            actualResult = "Requested resource missing on device",
            attemptedMethod = "SpeechOutput.speak",
            evidence = "requested resource missing on device",
            resourceKind = ErrorLearningWorkflowEngine.ResourceKind.TTS_VOICE
        )
        assertEquals(
            ErrorLearningWorkflowEngine.ErrorTaxonomy.RESOURCE_UNAVAILABLE_ERROR,
            entry.errorType
        )
        ErrorLearningWorkflowEngine.ttsProbeFactoryForTesting = {
            FakeProbe(
                candidates = listOf(TtsVoiceCandidate("my-MM", "my-mm-x", true)),
                speak = TtsProbe.SpeakOutcome(requestAccepted = true, completed = true)
            )
        }
        val result = ErrorLearningWorkflowEngine.verifyBlockedMethod("voice_output_failed", context)
        assertEquals(VerificationOutcome.VERIFIED, result.outcome)
        assertEquals("TTS_LIVE_PROBE", result.evidenceSource)
        assertNull(ErrorLearningWorkflowEngine.evaluateRequestedAction("voice_output_failed", context = null))
    }

    @Test
    fun t17_039_03_packageKind_dispatchesToPackageChecker() {
        ErrorLearningWorkflowEngine.recordErrorLog(
            task = "APP_LAUNCH -> ယူကျု",
            commandKey = "app_launch_ယူကျု",
            expectedResult = "YouTube opens",
            actualResult = "App not found",
            attemptedMethod = "APP_LAUNCH.ယူကျု",
            evidence = "UniversalAppLauncher error: APP_NOT_FOUND_OR_LOW_CONFIDENCE"
        )
        // Checker says the target is still not launchable -> stays blocked.
        ErrorLearningWorkflowEngine.packageLaunchableCheckerForTesting = { _, _ -> false }
        val failedResult = ErrorLearningWorkflowEngine.verifyBlockedMethod(
            FailedMethodKeys.canonical("APP_LAUNCH", "ယူကျု"),
            context
        )
        assertEquals(VerificationOutcome.UNVERIFIED, failedResult.outcome)
        assertNotNull(
            ErrorLearningWorkflowEngine.evaluateRequestedAction(
                FailedMethodKeys.canonical("APP_LAUNCH", "ယူကျု"),
                context = null
            )
        )
        // Checker says the app is now installed/launchable -> clears, with the
        // PACKAGE_RESOLVER as evidence source (never the model-file check).
        ErrorLearningWorkflowEngine.packageLaunchableCheckerForTesting = { _, _ -> true }
        val okResult = ErrorLearningWorkflowEngine.verifyBlockedMethod(
            FailedMethodKeys.canonical("APP_LAUNCH", "ယူကျု"),
            context
        )
        assertEquals(VerificationOutcome.VERIFIED, okResult.outcome)
        assertEquals("PACKAGE_RESOLVER", okResult.evidenceSource)
        assertNull(
            ErrorLearningWorkflowEngine.evaluateRequestedAction(
                FailedMethodKeys.canonical("APP_LAUNCH", "ယူကျု"),
                context = null
            )
        )
    }

    @Test
    fun t17_039_04_modelFileKind_usesModelManagerCheck() {
        ErrorLearningWorkflowEngine.recordErrorLog(
            task = "Load STT model files",
            commandKey = "stt_model_load",
            expectedResult = "Model loads",
            actualResult = "Model file missing",
            attemptedMethod = "ModelManager.ensureSherpaModel",
            evidence = "model file missing on disk"
        )
        // Robolectric sandbox has no Sherpa/Vosk model files: the real
        // ModelManager check must NOT verify, and the record stays blocked.
        val result = ErrorLearningWorkflowEngine.verifyBlockedMethod("stt_model_load", context)
        assertEquals(VerificationOutcome.UNVERIFIED, result.outcome)
        assertNotNull(ErrorLearningWorkflowEngine.evaluateRequestedAction("stt_model_load", context = null))
    }

    @Test
    fun t17_039_05_otherKind_noVerifierBranch_unverified() {
        val entry = ErrorLearningWorkflowEngine.recordErrorLog(
            task = "Generic resource failure",
            commandKey = "generic_resource_cmd",
            expectedResult = "works",
            actualResult = "resource missing somewhere",
            attemptedMethod = "Generic.go()",
            evidence = "some resource missing in a generic way",
            resourceKind = ErrorLearningWorkflowEngine.ResourceKind.OTHER
        )
        assertEquals(
            ErrorLearningWorkflowEngine.ErrorTaxonomy.RESOURCE_UNAVAILABLE_ERROR,
            entry.errorType
        )
        // No TTS/package signal: derivation yields MODEL_FILE (the legacy
        // default for RESOURCE records), whose real check finds no model
        // files under Robolectric -> UNVERIFIED and still blocked. The
        // per-kind dispatch distinctions are pinned by 039-02/03/06.
        val result = ErrorLearningWorkflowEngine.verifyBlockedMethod("generic_resource_cmd", context)
        assertEquals(VerificationOutcome.UNVERIFIED, result.outcome)
        assertNotNull(ErrorLearningWorkflowEngine.evaluateRequestedAction("generic_resource_cmd", context = null))
    }

    @Test
    fun t17_039_06_legacyEntryWithoutKindTag_fallsBackToDerivation() {
        // Simulates an entry stored by an older build: kind tag OTHER/absent,
        // but the key is the TTS namespace -> derivation routes to TTS probe.
        ErrorLearningWorkflowEngine.recordErrorLog(
            task = "TTS_SYNTHESIS -> SPEAK_BURMESE",
            commandKey = "tts_synthesis_speak_burmese",
            expectedResult = "spoken",
            actualResult = "#25 Resource Unavailable",
            attemptedMethod = "MyanmarTTSEngine.speak",
            evidence = "VoicePackStatus: VOICE_PACK_MISSING_DATA",
            resourceKind = ErrorLearningWorkflowEngine.ResourceKind.OTHER
        )
        ErrorLearningWorkflowEngine.ttsProbeFactoryForTesting = {
            FakeProbe(
                candidates = listOf(TtsVoiceCandidate("en-US", "en-us-x", true)),
                speak = TtsProbe.SpeakOutcome(requestAccepted = false)
            )
        }
        val result = ErrorLearningWorkflowEngine.verifyBlockedMethod(
            "tts_synthesis_speak_burmese",
            context
        )
        // The TTS probe ran (BLOCKED: no my-MM voice) — proving the resource
        // dispatch reached the TTS branch rather than the model-file branch.
        assertEquals(VerificationOutcome.BLOCKED, result.outcome)
        assertEquals("TTS_LIVE_PROBE", result.evidenceSource)
    }
}
