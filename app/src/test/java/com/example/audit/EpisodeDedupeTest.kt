package com.example.audit

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.FailureCategory
import com.example.ocr.FailureMemoryEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ISSUE-038 — a repeat record for the same key + taxonomy inside the same
 * unresolved episode must UPDATE the existing entry (occurrenceCount +
 * RETRY_ATTEMPTED history) instead of minting a new ER ID. This is the
 * ER-008/ER-015/ER-016 flooding defect: three identical SPEAK_BURMESE
 * failures produced three separate ER IDs for one ongoing episode.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EpisodeDedupeTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        ErrorLearningWorkflowEngine.initialize(context)
        ErrorLearningWorkflowEngine.clearAllForTesting()
        runBlocking { FailureMemoryEngine.clearAllPersistently(context) }
        FailureMemoryEngine.initialize(context)
    }

    private fun recordTtsFailure() = ErrorLearningWorkflowEngine.recordErrorLog(
        task = "TTS_SYNTHESIS -> SPEAK_BURMESE",
        commandKey = "tts_synthesis_speak_burmese",
        expectedResult = "Burmese text is spoken aloud",
        actualResult = "#25 Resource Unavailable Error",
        attemptedMethod = "MyanmarTTSEngine.speak",
        evidence = "VoicePackStatus: VOICE_PACK_MISSING_DATA, Engine: com.google.android.tts"
    )

    @Test
    fun t17_038_01_threeIdenticalSpeakBurmeseFailures_oneEntryOccurrence3() {
        repeat(3) {
            FailureMemoryEngine.recordFailure(
                component = "TTS_SYNTHESIS",
                operation = "SPEAK_BURMESE",
                category = FailureCategory.RESOURCE,
                rootCauseMm = "အသံဒေတာ မရှိပါ",
                technicalDetails = "VoicePackStatus: VOICE_PACK_MISSING_DATA, Engine: com.google.android.tts",
                suggestedRemediationMm = "Voice pack ဒေါင်းပါ"
            )
        }
        val logs = ErrorLearningWorkflowEngine.getAllErrorLogs()
        assertEquals(
            "Three identical failures in one episode must be ONE ER entry (was ${logs.map { it.errorId }})",
            1,
            logs.size
        )
        assertEquals(3, logs[0].occurrenceCount)
    }

    @Test
    fun t17_038_02_directRecords_sameKeyTaxonomy_updateInPlace() {
        val first = recordTtsFailure()
        val second = recordTtsFailure()
        val third = recordTtsFailure()
        assertEquals(first.errorId, second.errorId)
        assertEquals(first.errorId, third.errorId)
        assertEquals(3, third.occurrenceCount)
        assertEquals(1, ErrorLearningWorkflowEngine.getAllErrorLogs().size)
        assertTrue(
            third.statusTransitionHistory.any { it.contains("RETRY_ATTEMPTED") }
        )
    }

    @Test
    fun t17_038_03_afterResolution_newFailureMintsNewEpisode() {
        val first = recordTtsFailure()
        recordTtsFailure()
        val cleared = ErrorLearningWorkflowEngine.clearFailedMethodWithVerification(
            VerificationResult(
                outcome = VerificationOutcome.VERIFIED,
                errorId = first.errorId,
                commandKey = first.commandKey,
                checkedAtMs = System.currentTimeMillis(),
                evidenceSource = "TEST_VERIFIER",
                detail = "voice now installed"
            )
        )
        assertTrue(cleared)
        val newEpisode = recordTtsFailure()
        assertNotEquals(first.errorId, newEpisode.errorId)
        // occurrenceCount is cumulative per key across episodes (pre-existing
        // engine semantics: the counter carries from the last history entry
        // for the key). The EPISODE identity is the errorId — a resolved
        // episode followed by a new failure must mint a NEW id, which is the
        // ISSUE-038 property asserted here.
        assertEquals(3, newEpisode.occurrenceCount)
        assertEquals(2, ErrorLearningWorkflowEngine.getAllErrorLogs().size)
    }

    @Test
    fun t17_038_04_sameKeyDifferentTaxonomy_mintsNewEntry() {
        recordTtsFailure()
        val other = ErrorLearningWorkflowEngine.recordErrorLog(
            task = "TTS_SYNTHESIS -> SPEAK_BURMESE",
            commandKey = "tts_synthesis_speak_burmese",
            expectedResult = "Burmese text is spoken aloud",
            actualResult = "Network timeout while checking voices",
            attemptedMethod = "MyanmarTTSEngine.speak",
            evidence = "network timeout offline"
        )
        assertEquals(
            ErrorLearningWorkflowEngine.ErrorTaxonomy.NETWORK_REALITY_ERROR,
            other.errorType
        )
        assertEquals(2, ErrorLearningWorkflowEngine.getAllErrorLogs().size)
    }
}
