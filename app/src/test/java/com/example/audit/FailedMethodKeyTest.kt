package com.example.audit

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.actions.KyawGyiActionExecutor
import com.example.data.FailureCategory
import com.example.ocr.FailureMemoryEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ISSUE-036 — canonical failed-method key enforcement (string-level contract).
 *
 * ER-007 regression: a failure recorded under the legacy executor key
 * `app_launch_ယူကျု` must gate a retry that arrives under the canonical
 * intent+target key `v2|app_launch|ယူကျု` — the form the chat pipeline now
 * derives from registryActionType + finalTarget regardless of how the user
 * phrased the command — while a DIFFERENT app must NOT be blocked.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FailedMethodKeyTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        ErrorLearningWorkflowEngine.initialize(context)
        ErrorLearningWorkflowEngine.clearAllForTesting()
    }

    @Test
    fun t17_036_02_legacyEr007Key_gatesCanonicalLookup() {
        // Recorded exactly as the old on-device build recorded ER-007.
        ErrorLearningWorkflowEngine.recordErrorLog(
            task = "APP_LAUNCH -> ယူကျု",
            commandKey = "app_launch_ယူကျု",
            expectedResult = "YouTube opens",
            actualResult = "App not found on the device or could not be opened",
            attemptedMethod = "APP_LAUNCH.ယူကျု",
            evidence = "UniversalAppLauncher error: APP_NOT_FOUND_OR_LOW_CONFIDENCE"
        )
        // The pipeline's canonical derivation for OPEN_APP + target ယူကျု.
        val warning = ErrorLearningWorkflowEngine.evaluateRequestedAction(
            commandKey = "v2|app_launch|ယူကျု",
            context = null
        )
        assertNotNull(
            "ER-007's legacy block must gate the canonical pipeline key",
            warning
        )
    }

    @Test
    fun t17_036_01_factory_aliasesAndNormalization() {
        assertEquals(
            "v2|app_launch|ယူကျု",
            FailedMethodKeys.canonical("OPEN_APP", "ယူကျု")
        )
        assertEquals(
            FailedMethodKeys.canonical("OPEN_APP", "ယူကျု"),
            FailedMethodKeys.canonical("APP_LAUNCH", "  ယူကျု  ")
        )
        assertEquals(
            FailedMethodKeys.canonical("TTS_SYNTHESIS", "SPEAK_BURMESE"),
            FailedMethodKeys.canonicalOrSelf("tts_synthesis_speak_burmese")
        )
        assertEquals(
            "v2|app_launch|ယူကျု",
            FailedMethodKeys.canonicalOrSelf("app_launch_ယူကျု")
        )
        // Free-text keys are not structured keys: they normalize to themselves.
        assertEquals(
            "call_contact_test",
            FailedMethodKeys.canonicalOrSelf("call_contact_test")
        )
    }

    @Test
    fun t17_036_03_rephrasedRetry_sameIntentTarget_caught() {
        // Any phrasing whose parse yields OPEN_APP + ယူကျု derives this key;
        // the block recorded from one phrasing must catch the others.
        ErrorLearningWorkflowEngine.recordErrorLog(
            task = "APP_LAUNCH -> ယူကျု",
            commandKey = "app_launch_ယူကျု",
            expectedResult = "YouTube opens",
            actualResult = "App not found",
            attemptedMethod = "APP_LAUNCH.ယူကျု",
            evidence = "UniversalAppLauncher error: APP_NOT_FOUND_OR_LOW_CONFIDENCE"
        )
        assertNotNull(
            ErrorLearningWorkflowEngine.evaluateRequestedAction(
                commandKey = FailedMethodKeys.canonical("OPEN_APP", "ယူကျု"),
                context = null
            )
        )
    }

    @Test
    fun t17_036_05_failureMemoryEngineRecord_gatesCanonicalLookup() {
        FailureMemoryEngine.initialize(context)
        FailureMemoryEngine.recordFailure(
            component = "APP_LAUNCH",
            operation = "ယူကျု",
            category = FailureCategory.RESOURCE,
            rootCauseMm = "အက်ပ် မတွေ့ပါ",
            technicalDetails = "UniversalAppLauncher error: APP_NOT_FOUND_OR_LOW_CONFIDENCE",
            suggestedRemediationMm = "Play Store မှ ထည့်သွင်းပါ"
        )
        assertNotNull(
            "Executor-namespace record must gate the canonical pipeline key",
            ErrorLearningWorkflowEngine.evaluateRequestedAction(
                commandKey = FailedMethodKeys.canonical("OPEN_APP", "ယူကျု"),
                context = null
            )
        )
    }

    @Test
    fun t17_036_06_ttsNamespace_canonicalConsistency() {
        FailureMemoryEngine.initialize(context)
        FailureMemoryEngine.recordFailure(
            component = "TTS_SYNTHESIS",
            operation = "SPEAK_BURMESE",
            category = FailureCategory.RESOURCE,
            rootCauseMm = "အသံဒေတာ မရှိပါ",
            technicalDetails = "VoicePackStatus: VOICE_PACK_MISSING_DATA",
            suggestedRemediationMm = "Voice pack ဒေါင်းပါ"
        )
        assertNotNull(
            ErrorLearningWorkflowEngine.evaluateRequestedAction(
                commandKey = FailedMethodKeys.canonical("TTS_SYNTHESIS", "SPEAK_BURMESE"),
                context = null
            )
        )
    }

    @Test
    fun t17_036_07_textKeys_staySelfConsistent() {
        ErrorLearningWorkflowEngine.recordErrorLog(
            task = "legacy text record",
            commandKey = "ယူကျု ဖွင့်ပေးပါ",
            expectedResult = "opens",
            actualResult = "failed",
            attemptedMethod = "legacy",
            evidence = "some failure with no structured code inside"
        )
        assertNotNull(
            ErrorLearningWorkflowEngine.evaluateRequestedAction(
                commandKey = "ယူကျု ဖွင့်ပေးပါ",
                context = null
            )
        )
    }

    @Test
    fun t17_036_08_executorOpenApplication_enforcesBlock() {
        ErrorLearningWorkflowEngine.recordErrorLog(
            task = "APP_LAUNCH -> ယူကျု",
            commandKey = "app_launch_ယူကျု",
            expectedResult = "YouTube opens",
            actualResult = "App not found",
            attemptedMethod = "APP_LAUNCH.ယူကျု",
            evidence = "UniversalAppLauncher error: APP_NOT_FOUND_OR_LOW_CONFIDENCE"
        )
        val executor = KyawGyiActionExecutor(context)
        val result = executor.openApplication("ယူကျု", "ယူကျု")
        assertEquals(false, result.isSuccess)
        assertEquals("FAILED_METHOD_BLOCKED", result.errorCode)
        assertEquals(false, result.executionStarted)
    }

    @Test
    fun t17_036_04_differentTarget_notFalselyBlocked() {
        ErrorLearningWorkflowEngine.recordErrorLog(
            task = "APP_LAUNCH -> ယူကျု",
            commandKey = "app_launch_ယူကျု",
            expectedResult = "YouTube opens",
            actualResult = "App not found on the device or could not be opened",
            attemptedMethod = "APP_LAUNCH.ယူကျု",
            evidence = "UniversalAppLauncher error: APP_NOT_FOUND_OR_LOW_CONFIDENCE"
        )
        assertNull(
            "A different app must not be blocked by YouTube's failure",
            ErrorLearningWorkflowEngine.evaluateRequestedAction(
                commandKey = "v2|app_launch|ဖေ့စ်ဘွတ်",
                context = null
            )
        )
        assertNull(
            "A different action on the same target must not be blocked",
            ErrorLearningWorkflowEngine.evaluateRequestedAction(
                commandKey = "v2|app_close|ယူကျု",
                context = null
            )
        )
    }
}
