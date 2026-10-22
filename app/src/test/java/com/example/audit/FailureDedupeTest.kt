package com.example.audit

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.actions.KyawGyiActionExecutor
import com.example.data.FailureCategory
import com.example.ocr.FailureMemoryEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ISSUE-037 — ONE failure event must produce exactly ONE ER entry across
 * layers. The executor records via FailureMemoryEngine (which syncs to the
 * engine); the minted ER id travels back as the failure-event id
 * (FailureRecord.errorLearningEntryId -> CommandResult.errorLearningEntryId
 * -> UnifiedActionResult.errorLearningEntryId), and the pipeline's Step 10
 * records through recordFailureOnce, which cross-references instead of
 * minting a twin.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FailureDedupeTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        ErrorLearningWorkflowEngine.initialize(context)
        ErrorLearningWorkflowEngine.clearAllForTesting()
        runBlocking { FailureMemoryEngine.clearAllPersistently(context) }
        FailureMemoryEngine.initialize(context)
    }

    @Test
    fun t17_037_01_failureMemoryRecord_carriesSyncedErEntryId() {
        val record = FailureMemoryEngine.recordFailure(
            component = "APP_LAUNCH",
            operation = "ယူကျု",
            category = FailureCategory.RESOURCE,
            rootCauseMm = "အက်ပ် မတွေ့ပါ",
            technicalDetails = "UniversalAppLauncher error: APP_NOT_FOUND_OR_LOW_CONFIDENCE",
            suggestedRemediationMm = "Play Store မှ ထည့်သွင်းပါ"
        )
        assertNotNull("recordFailure must expose the synced ER entry id", record.errorLearningEntryId)
        val logs = ErrorLearningWorkflowEngine.getAllErrorLogs()
        assertEquals(1, logs.size)
        assertEquals(record.errorLearningEntryId, logs[0].errorId)
    }

    @Test
    fun t17_037_02_recordFailureOnce_withExecutorId_crossReferences_noTwin() {
        val record = FailureMemoryEngine.recordFailure(
            component = "APP_LAUNCH",
            operation = "ယူကျု",
            category = FailureCategory.RESOURCE,
            rootCauseMm = "အက်ပ် မတွေ့ပါ",
            technicalDetails = "UniversalAppLauncher error: APP_NOT_FOUND_OR_LOW_CONFIDENCE",
            suggestedRemediationMm = "Play Store မှ ထည့်သွင်းပါ"
        )
        val entry = ErrorLearningWorkflowEngine.recordFailureOnce(
            executorEntryId = record.errorLearningEntryId,
            task = "OPEN_APP (ယူကျု)",
            commandKey = FailedMethodKeys.canonical("OPEN_APP", "ယူကျု"),
            expectedResult = "OPEN_APP အောင်မြင်စွာ ဆောင်ရွက်ရန်",
            actualResult = "App not found",
            attemptedMethod = "UnifiedIntelligencePipeline.executeAction(OPEN_APP, target=ယူကျု)",
            evidence = "errorCode=APP_NOT_FOUND_OR_LOW_CONFIDENCE",
            recoveryAttempt = "Provided fallback guidance",
            recoveryResult = "NOT_EXECUTED"
        )
        assertEquals(record.errorLearningEntryId, entry.errorId)
        val logs = ErrorLearningWorkflowEngine.getAllErrorLogs()
        assertEquals("Exactly one ER entry for one failure event", 1, logs.size)
        assertTrue(
            logs[0].statusTransitionHistory.any { it.contains("PIPELINE_CROSS_REF") }
        )
    }

    @Test
    fun t17_037_03_recordFailureOnce_withoutExecutorId_mintsNewEntry() {
        val entry = ErrorLearningWorkflowEngine.recordFailureOnce(
            executorEntryId = null,
            task = "WIFI_TOGGLE (ON)",
            commandKey = FailedMethodKeys.canonical("WIFI_TOGGLE", "ON"),
            expectedResult = "Wi-Fi toggles",
            actualResult = "Panel only",
            attemptedMethod = "UnifiedIntelligencePipeline.executeAction(WIFI_TOGGLE)",
            evidence = "errorCode=PANEL_ONLY_NO_DIRECT_TOGGLE",
            recoveryAttempt = "Provided fallback guidance",
            recoveryResult = "NOT_EXECUTED"
        )
        assertEquals(1, ErrorLearningWorkflowEngine.getAllErrorLogs().size)
        assertEquals(entry.errorId, ErrorLearningWorkflowEngine.getAllErrorLogs()[0].errorId)
    }

    @Test
    fun t17_037_04_simulatedOpenAppFailure_exactlyOneErEntry() {
        val executor = KyawGyiActionExecutor(context)
        val result = executor.openApplication("မရှိသောအက်ပ်xyz123", "မရှိသောအက်ပ်xyz123")
        assertFalse(result.isSuccess)
        assertNotNull(
            "Executor must propagate the failure-event id",
            result.errorLearningEntryId
        )
        // Simulated pipeline Step 10 for the same failure event:
        ErrorLearningWorkflowEngine.recordFailureOnce(
            executorEntryId = result.errorLearningEntryId,
            task = "OPEN_APP (မရှိသောအက်ပ်xyz123)",
            commandKey = FailedMethodKeys.canonical("OPEN_APP", "မရှိသောအက်ပ်xyz123"),
            expectedResult = "OPEN_APP အောင်မြင်စွာ ဆောင်ရွက်ရန်",
            actualResult = "App not found",
            attemptedMethod = "UnifiedIntelligencePipeline.executeAction(OPEN_APP)",
            evidence = "errorCode=APP_NOT_FOUND_OR_LOW_CONFIDENCE",
            recoveryAttempt = "Provided fallback guidance",
            recoveryResult = "NOT_EXECUTED"
        )
        val logs = ErrorLearningWorkflowEngine.getAllErrorLogs()
        assertEquals(
            "One OPEN_APP failure event must yield exactly one ER entry (was ${logs.map { it.errorId }})",
            1,
            logs.size
        )
        assertEquals(result.errorLearningEntryId, logs[0].errorId)
    }
}
