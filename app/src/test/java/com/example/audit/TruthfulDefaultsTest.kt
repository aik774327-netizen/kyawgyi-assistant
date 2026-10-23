package com.example.audit

import com.example.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * ISSUE-044 — record-keeping defaults must not fabricate facts: the engine
 * previously defaulted every record to recoveryAttempt="Switched to
 * verified alternative fallback flow", confidence 0.92, and a fingerprint
 * claiming modelVersion "sherpa-onnx-2024" (a model this app does not
 * ship) with appVersion "1.0.0" hard-coded; the header KDoc advertised a
 * "95-Type" taxonomy that actually has 41 entries.
 */
@RunWith(RobolectricTestRunner::class)
class TruthfulDefaultsTest {

    private val engine = ErrorLearningWorkflowEngine

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        engine.initialize(context)
        engine.clearAllForTesting()
    }

    private fun recordMinimal(key: String, evidence: String) =
        engine.recordErrorLog(
            task = "defaults probe",
            commandKey = key,
            expectedResult = "the action succeeds",
            actualResult = evidence,
            attemptedMethod = "probe method",
            evidence = evidence
        )

    @Test
    fun t17_044_01_minimalRecord_claimsNoRecovery_andNeutralConfidence() {
        val entry = recordMinimal("v2|app_launch|defaults1", "model file missing on disk")
        assertEquals(
            ErrorLearningWorkflowEngine.ErrorTaxonomy.RESOURCE_UNAVAILABLE_ERROR,
            entry.errorType
        )
        assertEquals("No recovery attempted", entry.recoveryAttempt)
        assertFalse(entry.toFormattedReport().contains("Switched to verified alternative fallback flow"))
        assertFalse(entry.toFormattedReport().contains("Alternative path ready"))
        assertEquals(0.5f, entry.confidence, 0.001f)
    }

    @Test
    fun t17_044_02_fingerprintDefaults_areTruthful() {
        val entry = recordMinimal("v2|app_launch|defaults2", "model file missing on disk")
        assertEquals("unknown", entry.environmentFingerprint.modelVersion)
        assertEquals(BuildConfig.VERSION_NAME, entry.environmentFingerprint.appVersion)
        assertFalse(entry.toFormattedReport().contains("sherpa-onnx-2024"))
    }

    @Test
    fun t17_044_03_taxonomyHas41Entries_not95() {
        assertEquals(41, ErrorLearningWorkflowEngine.ErrorTaxonomy.entries.size)
        // The misleading "95" came from the highest entry id.
        assertEquals(95, ErrorLearningWorkflowEngine.ErrorTaxonomy.entries.maxOf { it.id })
    }

    @Test
    fun t17_044_04_unknownTaxonomy_keepsZeroConfidence() {
        // "insufficient evidence" is the classifier's own token for UNKNOWN.
        val entry = recordMinimal("v2|app_launch|defaults4", "insufficient evidence")
        assertEquals(ErrorLearningWorkflowEngine.ErrorTaxonomy.UNKNOWN, entry.errorType)
        assertEquals(0.0f, entry.confidence, 0.001f)
        assertTrue(entry.recoveryAttempt == "No recovery attempted")
    }
}
