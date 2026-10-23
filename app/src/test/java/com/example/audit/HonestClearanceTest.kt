package com.example.audit

import android.content.Context
import androidx.test.core.app.ApplicationProvider
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
 * ISSUE-040 — every taxonomy must have an HONEST clearance story: either a
 * real automatic re-check exists (permission / network / recorded resource
 * kind), or the UI-facing text says plainly that there is none and a manual
 * retry is the path — with manual retries recorded as RETRY_ATTEMPTED,
 * never as VERIFIED_SUCCESS. Before this fix, RUNTIME_ERROR records (like
 * ER-007's original misclassification) could never clear by verification,
 * while the required-evidence text described a check that did not exist.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HonestClearanceTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        ErrorLearningWorkflowEngine.initialize(context)
        ErrorLearningWorkflowEngine.clearAllForTesting()
    }

    @Test
    fun t17_040_01_runtimeError_requiredEvidenceAdmitsNoAutomaticRecheck() {
        ErrorLearningWorkflowEngine.recordErrorLog(
            task = "Flaky action",
            commandKey = "flaky_cmd",
            expectedResult = "works",
            actualResult = "exploded",
            attemptedMethod = "Flaky.go()",
            evidence = "unexpected internal explosion with no known keyword"
        )
        val warning = ErrorLearningWorkflowEngine.evaluateRequestedAction("flaky_cmd", context = null)
        assertNotNull(warning)
        assertTrue(
            warning!!.requiredEvidenceToRetry.contains("no automatic live re-check")
        )
        assertTrue(warning.requiredEvidenceToRetry.contains("manually"))
        val result = ErrorLearningWorkflowEngine.verifyBlockedMethod("flaky_cmd", context)
        assertEquals(VerificationOutcome.UNVERIFIED, result.outcome)
    }

    @Test
    fun t17_040_02_er007ShapedEntry_nowHasRealPackageClearanceStory() {
        // Recorded exactly as ER-007 exists on the user's phone (legacy key,
        // launcher code in the evidence). After ISSUE-035/036/039 its
        // clearance path is the PACKAGE check — real, and shown as such.
        ErrorLearningWorkflowEngine.recordErrorLog(
            task = "APP_LAUNCH -> ယူကျု",
            commandKey = "app_launch_ယူကျု",
            expectedResult = "YouTube opens",
            actualResult = "App not found on the device or could not be opened",
            attemptedMethod = "APP_LAUNCH.ယူကျု",
            evidence = "UniversalAppLauncher error: APP_NOT_FOUND_OR_LOW_CONFIDENCE"
        )
        val warning = ErrorLearningWorkflowEngine.evaluateRequestedAction(
            FailedMethodKeys.canonical("APP_LAUNCH", "ယူကျု"),
            context = null
        )
        assertNotNull(warning)
        assertTrue(
            warning!!.requiredEvidenceToRetry.contains("recorded resource kind")
        )
        // Still missing -> not cleared. Now installed -> VERIFIED + cleared.
        ErrorLearningWorkflowEngine.packageLaunchableCheckerForTesting = { _, _ -> false }
        assertEquals(
            VerificationOutcome.UNVERIFIED,
            ErrorLearningWorkflowEngine.verifyBlockedMethod(
                FailedMethodKeys.canonical("APP_LAUNCH", "ယူကျု"),
                context
            ).outcome
        )
        ErrorLearningWorkflowEngine.packageLaunchableCheckerForTesting = { _, _ -> true }
        val cleared = ErrorLearningWorkflowEngine.verifyBlockedMethod(
            FailedMethodKeys.canonical("APP_LAUNCH", "ယူကျု"),
            context
        )
        assertEquals(VerificationOutcome.VERIFIED, cleared.outcome)
        assertEquals("PACKAGE_RESOLVER", cleared.evidenceSource)
        assertNull(
            ErrorLearningWorkflowEngine.evaluateRequestedAction(
                FailedMethodKeys.canonical("APP_LAUNCH", "ယူကျု"),
                context = null
            )
        )
    }

    @Test
    fun t17_040_03_manualRetry_recordedAsRetryAttempted_notVerifiedSuccess() {
        val first = ErrorLearningWorkflowEngine.recordErrorLog(
            task = "Flaky action",
            commandKey = "flaky_cmd",
            expectedResult = "works",
            actualResult = "exploded",
            attemptedMethod = "Flaky.go()",
            evidence = "unexpected internal explosion with no known keyword"
        )
        // A manual retry that fails again:
        val retry = ErrorLearningWorkflowEngine.recordErrorLog(
            task = "Flaky action",
            commandKey = "flaky_cmd",
            expectedResult = "works",
            actualResult = "exploded again",
            attemptedMethod = "Flaky.go()",
            evidence = "unexpected internal explosion with no known keyword"
        )
        assertEquals(first.errorId, retry.errorId)
        assertTrue(retry.statusTransitionHistory.any { it.contains("RETRY_ATTEMPTED") })
        assertFalse(retry.statusTransitionHistory.any { it.contains("VERIFIED_SUCCESS") })
        assertEquals(
            ErrorLearningWorkflowEngine.ErrorLifecycleStatus.BLOCKED,
            retry.status
        )
    }

    @Test
    fun t17_040_04_permissionEntry_requiredEvidenceStillDescribesRealCheck() {
        ErrorLearningWorkflowEngine.recordErrorLog(
            task = "Toggle Bluetooth via Accessibility",
            commandKey = "ဘလူးတုသ် ဖွင့်ပါ",
            expectedResult = "Bluetooth toggles ON",
            actualResult = "Accessibility service disabled",
            attemptedMethod = "BluetoothAccessibilityHelper",
            evidence = "Accessibility service not enabled"
        )
        val warning = ErrorLearningWorkflowEngine.evaluateRequestedAction("ဘလူးတုသ် ဖွင့်ပါ", context = null)
        assertNotNull(warning)
        assertFalse(
            warning!!.requiredEvidenceToRetry.contains("no automatic live re-check")
        )
        assertTrue(warning.requiredEvidenceToRetry.contains("AccessibilityManager"))
    }
}
