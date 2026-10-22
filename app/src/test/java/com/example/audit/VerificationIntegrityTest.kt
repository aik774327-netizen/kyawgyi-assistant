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
 * ISSUE-034 — VERIFIED_SUCCESS / RESOLVED must be producible ONLY from a typed
 * [VerificationResult] bound to the blocked record (errorId + commandKey +
 * timestamp + evidence source). The old free-text evidence API
 * (clearFailedMethodWithVerifiedEvidence(String)) and the viewer's
 * "Resolve All" hard-coded-evidence button were removed; these tests pin the
 * replacement's integrity rules.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VerificationIntegrityTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        ErrorLearningWorkflowEngine.initialize(context)
        ErrorLearningWorkflowEngine.clearAllForTesting()
    }

    private fun recordRuntimeFailure(key: String = "flaky_cmd"): ErrorLearningWorkflowEngine.ErrorLogEntry {
        return ErrorLearningWorkflowEngine.recordErrorLog(
            task = "Do something flaky",
            commandKey = key,
            expectedResult = "It works",
            actualResult = "It exploded unexpectedly",
            attemptedMethod = "Flaky.doThing()",
            evidence = "unexpected internal explosion with no known keyword"
        )
    }

    private fun typedResult(
        entry: ErrorLearningWorkflowEngine.ErrorLogEntry,
        outcome: VerificationOutcome,
        errorId: String = entry.errorId
    ) = VerificationResult(
        outcome = outcome,
        errorId = errorId,
        commandKey = entry.commandKey,
        checkedAtMs = System.currentTimeMillis(),
        evidenceSource = "TEST_VERIFIER",
        detail = "test-produced result"
    )

    @Test
    fun t17_034_01_nonVerifiedOutcomes_neverClear() {
        val entry = recordRuntimeFailure()
        for (outcome in listOf(
            VerificationOutcome.UNVERIFIED,
            VerificationOutcome.FAILED,
            VerificationOutcome.BLOCKED
        )) {
            assertFalse(
                "Outcome $outcome must not clear a blocked record",
                ErrorLearningWorkflowEngine.clearFailedMethodWithVerification(typedResult(entry, outcome))
            )
        }
        assertNotNull(ErrorLearningWorkflowEngine.evaluateRequestedAction("flaky_cmd", context = null))
    }

    @Test
    fun t17_034_02_verifiedResultBoundToWrongErrorId_neverClears() {
        val entry = recordRuntimeFailure()
        val forged = typedResult(entry, VerificationOutcome.VERIFIED, errorId = "ER-999")
        assertFalse(ErrorLearningWorkflowEngine.clearFailedMethodWithVerification(forged))
        assertNotNull(ErrorLearningWorkflowEngine.evaluateRequestedAction("flaky_cmd", context = null))
    }

    @Test
    fun t17_034_03_verifiedResultBoundToRecord_clearsAndPreservesHistory() {
        val entry = recordRuntimeFailure()
        val result = typedResult(entry, VerificationOutcome.VERIFIED)
        assertTrue(ErrorLearningWorkflowEngine.clearFailedMethodWithVerification(result))
        assertNull(ErrorLearningWorkflowEngine.evaluateRequestedAction("flaky_cmd", context = null))
        val history = ErrorLearningWorkflowEngine.getAllErrorLogs()
        assertEquals(1, history.size)
        val resolved = history.first { it.errorId == entry.errorId }
        assertEquals(ErrorLearningWorkflowEngine.ErrorLifecycleStatus.RESOLVED, resolved.status)
        assertTrue(resolved.statusTransitionHistory.contains("VERIFIED_SUCCESS"))
        assertTrue(resolved.statusTransitionHistory.contains("RESOLVED"))
        assertTrue(resolved.verificationStatus.contains("TEST_VERIFIER"))
    }

    @Test
    fun t17_034_04_verifyBlockedMethod_noVerifierBranch_returnsUnverifiedAndStaysBlocked() {
        recordRuntimeFailure()
        val result = ErrorLearningWorkflowEngine.verifyBlockedMethod("flaky_cmd", context)
        assertEquals(VerificationOutcome.UNVERIFIED, result.outcome)
        assertTrue(result.detail.contains("NOT cleared"))
        assertNotNull(ErrorLearningWorkflowEngine.evaluateRequestedAction("flaky_cmd", context = null))
    }

    @Test
    fun t17_034_05_verifyBlockedMethod_failedCheck_staysBlocked() {
        // NETWORK entry: Robolectric has no validated internet, so the real
        // ConnectivityManager branch cannot verify -> record must stay blocked.
        ErrorLearningWorkflowEngine.recordErrorLog(
            task = "Fetch remote config",
            commandKey = "fetch_config",
            expectedResult = "Config downloaded",
            actualResult = "Network timeout",
            attemptedMethod = "ConfigClient.fetch()",
            evidence = "network timeout offline 504"
        )
        val result = ErrorLearningWorkflowEngine.verifyBlockedMethod("fetch_config", context)
        assertTrue(
            "Offline check must not verify (was ${result.outcome})",
            result.outcome == VerificationOutcome.UNVERIFIED || result.outcome == VerificationOutcome.FAILED
        )
        assertNotNull(ErrorLearningWorkflowEngine.evaluateRequestedAction("fetch_config", context = null))
    }

    @Test
    fun t17_034_06_executionSuccessResult_boundToBlockedRecord_andClears() {
        val entry = recordRuntimeFailure("exec_cmd")
        // No blocked record -> no result can be minted.
        assertNull(ErrorLearningWorkflowEngine.verificationFromExecutionSuccess("never_blocked", "ok"))
        val result = ErrorLearningWorkflowEngine.verificationFromExecutionSuccess("exec_cmd", "execution verified")
        assertNotNull(result)
        assertEquals(entry.errorId, result!!.errorId)
        assertEquals("EXECUTION_SUCCESS", result.evidenceSource)
        assertTrue(ErrorLearningWorkflowEngine.clearFailedMethodWithVerification(result))
        assertNull(ErrorLearningWorkflowEngine.evaluateRequestedAction("exec_cmd", context = null))
    }

    @Test
    fun t17_034_07_verifyBlockedMethod_unknownKey_returnsUnverified() {
        val result = ErrorLearningWorkflowEngine.verifyBlockedMethod("no_such_key", context)
        assertEquals(VerificationOutcome.UNVERIFIED, result.outcome)
    }
}
