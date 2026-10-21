package com.example.ui.dictation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the dictation → voice-command routing decision and for
 * the persisted command-check toggle (default ON, user-approved
 * 2026-10-22). The pipeline itself is exercised separately in
 * KyawGyiDispatcherTest; here only the pure decision is pinned down.
 */
class DictationCommandRoutingTest {

    // ---------------------------------------------------------------
    // decideDictationSegmentRoute — full truth table
    // ---------------------------------------------------------------

    @Test
    fun commandCheckOffAlwaysInsertsAsText() {
        // With the toggle off the pipeline is never consulted, so every
        // outcome (even a would-be HANDLED) must file the segment as text.
        DictationCommandCheckResult.entries.forEach { result ->
            assertEquals(
                "off + $result",
                DictationSegmentDecision.INSERT_AS_TEXT,
                decideDictationSegmentRoute(commandCheckEnabled = false, checkResult = result)
            )
        }
        assertEquals(
            DictationSegmentDecision.INSERT_AS_TEXT,
            decideDictationSegmentRoute(commandCheckEnabled = false, checkResult = null)
        )
    }

    @Test
    fun handledSegmentExecutesAndIsNotInserted() {
        assertEquals(
            DictationSegmentDecision.EXECUTED_AS_COMMAND,
            decideDictationSegmentRoute(
                commandCheckEnabled = true,
                checkResult = DictationCommandCheckResult.HANDLED
            )
        )
    }

    @Test
    fun notHandledSegmentFallsBackToTextInsertion() {
        assertEquals(
            DictationSegmentDecision.INSERT_AS_TEXT,
            decideDictationSegmentRoute(
                commandCheckEnabled = true,
                checkResult = DictationCommandCheckResult.NOT_HANDLED
            )
        )
    }

    @Test
    fun pipelineFailureSurfacesErrorAndDropsSegment() {
        // Deliberate decision: a segment offered as a command whose
        // execution failed must NOT be silently filed as document text.
        assertEquals(
            DictationSegmentDecision.SURFACE_ERROR,
            decideDictationSegmentRoute(
                commandCheckEnabled = true,
                checkResult = DictationCommandCheckResult.FAILED
            )
        )
    }

    @Test
    fun missingCheckResultWithToggleOnInsertsAsText() {
        assertEquals(
            DictationSegmentDecision.INSERT_AS_TEXT,
            decideDictationSegmentRoute(commandCheckEnabled = true, checkResult = null)
        )
    }

    // ---------------------------------------------------------------
    // Command-check toggle persistence (document codec)
    // ---------------------------------------------------------------

    @Test
    fun commandCheckDefaultsOnForFreshAndLegacyDocuments() {
        // Fresh install (empty map) and documents stored before the
        // setting existed (no key) must both default to ON.
        assertTrue(DictationDocument.decode(emptyMap()).commandCheckEnabled)
        val legacy = DictationDocument.decode(
            mapOf(
                DictationDocument.KEY_TEXT to "text",
                DictationDocument.KEY_AUTO_SAVE to "true"
            )
        )
        assertTrue(legacy.commandCheckEnabled)
    }

    @Test
    fun commandCheckRoundTripsThroughCodec() {
        val off = DictationDocument(commandCheckEnabled = false)
        assertFalse(DictationDocument.decode(off.encode()).commandCheckEnabled)
        val on = DictationDocument(commandCheckEnabled = true)
        assertTrue(DictationDocument.decode(on.encode()).commandCheckEnabled)
    }

    @Test
    fun commandCheckCorruptStoredValueFallsBackToOn() {
        val decoded = DictationDocument.decode(
            mapOf(DictationDocument.KEY_COMMAND_CHECK to "not-a-boolean")
        )
        assertTrue(decoded.commandCheckEnabled)
    }
}
