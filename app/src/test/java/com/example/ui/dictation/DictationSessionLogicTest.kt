package com.example.ui.dictation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the pure dictation logic: caret insertion, character
 * count, bounded restart guard, document persistence codec, and the
 * honest save-state / error label models.
 */
class DictationSessionLogicTest {

    // ---------------------------------------------------------------
    // Caret insertion
    // ---------------------------------------------------------------

    @Test
    fun insertAtEndCaretAppendsSegmentWithTrailingSpace() {
        val result = DictationText.insertTranscript(
            document = "မင်္ဂလာပါ",
            selectionStart = 9,
            selectionEnd = 9,
            rawTranscript = "နေကောင်းလား"
        )!!
        assertEquals("မင်္ဂလာပါနေကောင်းလား ", result.newText)
        assertEquals(result.newText.length, result.newCaret)
    }

    @Test
    fun insertInMiddleOfTextKeepsSurroundingText() {
        val doc = "hello world"
        // Caret sits just after the space (index 6), mid-document.
        val result = DictationText.insertTranscript(
            document = doc,
            selectionStart = 6,
            selectionEnd = 6,
            rawTranscript = "brave new"
        )!!
        assertEquals("hello brave new world", result.newText)
        assertEquals(6 + "brave new ".length, result.newCaret)
    }

    @Test
    fun insertReplacesSelectedRange() {
        val doc = "the quick brown fox"
        // Select "quick " (chars 4..10) and replace it with the transcript.
        val result = DictationText.insertTranscript(
            document = doc,
            selectionStart = 4,
            selectionEnd = 10,
            rawTranscript = "slow"
        )!!
        assertEquals("the slow brown fox", result.newText)
        assertEquals(4 + "slow ".length, result.newCaret)
    }

    @Test
    fun insertAtBeginning() {
        val result = DictationText.insertTranscript(
            document = "world",
            selectionStart = 0,
            selectionEnd = 0,
            rawTranscript = "hello"
        )!!
        assertEquals("hello world", result.newText)
        assertEquals(6, result.newCaret)
    }

    @Test
    fun insertIntoEmptyDocument() {
        val result = DictationText.insertTranscript("", 0, 0, "စမ်းသပ်")!!
        assertEquals("စမ်းသပ် ", result.newText)
        assertEquals(result.newText.length, result.newCaret)
    }

    @Test
    fun blankTranscriptInsertsNothing() {
        assertNull(DictationText.insertTranscript("abc", 1, 1, ""))
        assertNull(DictationText.insertTranscript("abc", 1, 1, "   "))
        assertNull(DictationText.insertTranscript("abc", 1, 1, null))
    }

    @Test
    fun outOfRangeSelectionIsClampedSafely() {
        val result = DictationText.insertTranscript("abc", 99, 120, "x")!!
        assertEquals("abcx ", result.newText)
        assertEquals(5, result.newCaret)
    }

    @Test
    fun insertNormalizesZeroWidthAndDuplicateToneMarks() {
        // Zero-width space inside the word, and a doubled tone dot (့့).
        val raw = "ကျော်\u200Bကြီး\u200Bပါ့့"
        val result = DictationText.insertTranscript("", 0, 0, raw)!!
        assertEquals("ကျော်ကြီးပါ့ ", result.newText)
    }

    @Test
    fun insertPreservesWordSpacingUnlikeCommandNormalizer() {
        // The document path must NOT delete internal spaces the way the
        // command-matching normalizer does.
        val result = DictationText.insertTranscript("", 0, 0, "hello   world  again")!!
        assertEquals("hello world again ", result.newText)
    }

    // ---------------------------------------------------------------
    // Character count (whitespace excluded)
    // ---------------------------------------------------------------

    @Test
    fun countExcludesAllWhitespace() {
        assertEquals(0, DictationText.countCharacters(""))
        assertEquals(0, DictationText.countCharacters("   \n\t "))
        assertEquals(3, DictationText.countCharacters("a b c"))
        assertEquals(5, DictationText.countCharacters("hello"))
        assertEquals(11, DictationText.countCharacters("hello world\nagain"))
    }

    @Test
    fun countMyanmarTextExcludingSpaces() {
        // "မင်္ဂလာပါ" = 9 code points, the space is not counted.
        assertEquals(9, DictationText.countCharacters("မင်္ဂလာပါ"))
        assertEquals(9, DictationText.countCharacters("မင်္ဂလာ ပါ".replace(" ", "")))
        assertEquals(
            DictationText.countCharacters("မင်္ဂလာပါ"),
            DictationText.countCharacters("မင်္ဂလာ ပါ")
        )
    }

    // ---------------------------------------------------------------
    // RestartGuard — at most 5 restarts per 15 s window (HTML parity)
    // ---------------------------------------------------------------

    @Test
    fun restartGuardAllowsFiveBlocksSixthWithinWindow() {
        val guard = RestartGuard()
        val t0 = 100_000L
        repeat(5) { i ->
            assertTrue("restart ${i + 1} should be allowed", guard.recordRestart(t0 + i * 1_000L))
        }
        assertFalse("6th restart within the window must be blocked", guard.recordRestart(t0 + 5_000L))
    }

    @Test
    fun restartGuardBlockedAttemptStillConsumesBudget() {
        val guard = RestartGuard()
        val t0 = 0L
        repeat(5) { guard.recordRestart(t0 + it * 100L) }
        assertFalse(guard.recordRestart(t0 + 600L))
        // Even the blocked attempt was recorded (HTML semantics): the
        // window still holds 6 timestamps until they age out.
        assertEquals(6, guard.restartsInWindow(t0 + 700L))
    }

    @Test
    fun restartGuardAllowsAgainAfterWindowPasses() {
        val guard = RestartGuard()
        val t0 = 0L
        repeat(5) { guard.recordRestart(t0 + it * 100L) }
        assertFalse(guard.recordRestart(t0 + 500L))
        // 16 s later every earlier timestamp has aged out of the 15 s window.
        assertTrue(guard.recordRestart(t0 + 16_000L))
    }

    @Test
    fun restartGuardResetClearsBudget() {
        val guard = RestartGuard()
        val t0 = 0L
        repeat(5) { guard.recordRestart(t0 + it * 100L) }
        assertFalse(guard.recordRestart(t0 + 500L))
        guard.reset()
        assertEquals(0, guard.restartsInWindow(t0 + 600L))
        assertTrue(guard.recordRestart(t0 + 600L))
    }

    @Test
    fun restartGuardWindowSlides() {
        val guard = RestartGuard()
        // 5 restarts spread over 14 s fill the budget. A 6th at t=14.5 s is
        // blocked AND recorded (HTML semantics), so at t=15.5 s — although
        // the t=0 attempt has aged out — the recorded blocked attempt still
        // occupies a slot and the restart stays blocked. Only once enough
        // attempts have aged out (t=22.5 s) does a restart fit again.
        val times = listOf(0L, 3_500L, 7_000L, 10_500L, 14_000L)
        times.forEach { assertTrue(guard.recordRestart(it)) }
        assertFalse(guard.recordRestart(14_500L))
        assertFalse(guard.recordRestart(15_500L))
        assertTrue(guard.recordRestart(22_500L))
    }

    // ---------------------------------------------------------------
    // Document persistence codec round-trip
    // ---------------------------------------------------------------

    @Test
    fun documentCodecRoundTripsAllFields() {
        val original = DictationDocument(
            text = "မင်္ဂလာပါ။ ဒီနေ့ အစည်းအဝေး ရှိသည်။",
            language = "th-TH",
            autoRestart = false,
            autoSave = true,
            updatedAtMs = 1_729_000_000_000L
        )
        val decoded = DictationDocument.decode(original.encode())
        assertEquals(original, decoded)
    }

    @Test
    fun documentDecodeOfEmptyMapGivesHonestDefaults() {
        val decoded = DictationDocument.decode(emptyMap())
        assertEquals("", decoded.text)
        assertEquals("my-MM", decoded.language)
        assertTrue(decoded.autoRestart)
        assertTrue(decoded.autoSave)
        assertEquals(0L, decoded.updatedAtMs)
    }

    @Test
    fun documentDecodeRejectsUnsupportedLanguage() {
        val decoded = DictationDocument.decode(
            mapOf(DictationDocument.KEY_LANGUAGE to "fr-FR")
        )
        assertEquals("my-MM", decoded.language)
    }

    @Test
    fun documentDecodeToleratesCorruptValues() {
        val decoded = DictationDocument.decode(
            mapOf(
                DictationDocument.KEY_AUTO_RESTART to "not-a-boolean",
                DictationDocument.KEY_UPDATED_AT to "not-a-number"
            )
        )
        assertTrue(decoded.autoRestart)
        assertEquals(0L, decoded.updatedAtMs)
    }

    @Test
    fun supportedLanguagesMatchHtmlSpec() {
        assertEquals(listOf("my-MM", "en-US", "th-TH"), DICTATION_SUPPORTED_LANGUAGES)
        assertEquals("my-MM", validateDictationLanguage(null))
        assertEquals("en-US", validateDictationLanguage("EN-us"))
        assertEquals("my-MM", validateDictationLanguage("ja-JP"))
    }

    // ---------------------------------------------------------------
    // Save-state + error label models
    // ---------------------------------------------------------------

    @Test
    fun saveStateLabelsAreHonest() {
        assertEquals("Auto-save ပိတ်ထားသည်", dictationSaveStateLabel(DictationSaveState.SAVED, autoSaveEnabled = false))
        assertEquals("သိမ်းဆည်းနေသည်…", dictationSaveStateLabel(DictationSaveState.SAVING, autoSaveEnabled = true))
        assertEquals("သိမ်းပြီး ✓", dictationSaveStateLabel(DictationSaveState.SAVED, autoSaveEnabled = true))
        assertEquals("သိမ်းမရပါ", dictationSaveStateLabel(DictationSaveState.FAILED, autoSaveEnabled = true))
        assertEquals("စက်ထဲတွင် သိမ်းဆည်းထားသည်", dictationSaveStateLabel(DictationSaveState.IDLE, autoSaveEnabled = true))
    }

    @Test
    fun fatalErrorsAreOnlyPermissionAndLanguage() {
        assertTrue(isFatalDictationError(DictationErrorKind.PERMISSION_DENIED))
        assertTrue(isFatalDictationError(DictationErrorKind.LANGUAGE_NOT_SUPPORTED))
        assertFalse(isFatalDictationError(DictationErrorKind.NETWORK))
        assertFalse(isFatalDictationError(DictationErrorKind.NO_MATCH))
        assertFalse(isFatalDictationError(DictationErrorKind.SPEECH_TIMEOUT))
        assertFalse(isFatalDictationError(DictationErrorKind.RECOGNIZER_BUSY))
    }

    @Test
    fun everyErrorKindHasStatusAndGuidance() {
        DictationErrorKind.entries.forEach { kind ->
            assertTrue("status for $kind", dictationErrorStatus(kind).isNotBlank())
            assertTrue("guidance for $kind", dictationErrorGuidance(kind).isNotBlank())
        }
    }
}
