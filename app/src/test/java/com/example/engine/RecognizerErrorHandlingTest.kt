package com.example.engine

import android.speech.SpeechRecognizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FIX-01 regression tests (Phase 1.5 device remediation).
 *
 * Device defect D1: on the user's phone every voice command effectively
 * died in silence and the app eventually said only "အသံ မကြားရပါ",
 * because VoiceSessionManager.onError swallowed recognizer errors —
 * including ERROR_LANGUAGE_NOT_SUPPORTED from a recognition service
 * without my-MM. These tests pin the shared taxonomy that replaced the
 * swallowing: every error code has a kind, the important kinds have
 * DISTINCT Burmese messages, language/permission failures are terminal,
 * and the end-of-session decision table is explicit.
 *
 * They are plain JVM tests: SpeechRecognizer error constants are
 * compile-time ints, so no Android runtime is needed.
 */
class RecognizerErrorHandlingTest {

    // T16-01-01: every documented SpeechRecognizer error code maps to
    // its own kind — above all 12/13 (language), which previously fell
    // into the same silent hole as everything else.
    @Test
    fun t16_01_01_errorCodesMapToKinds() {
        assertEquals(
            RecognizerErrorKind.NETWORK_TIMEOUT,
            RecognizerErrorMapping.fromErrorCode(SpeechRecognizer.ERROR_NETWORK_TIMEOUT)
        )
        assertEquals(
            RecognizerErrorKind.NETWORK,
            RecognizerErrorMapping.fromErrorCode(SpeechRecognizer.ERROR_NETWORK)
        )
        assertEquals(
            RecognizerErrorKind.AUDIO,
            RecognizerErrorMapping.fromErrorCode(SpeechRecognizer.ERROR_AUDIO)
        )
        assertEquals(
            RecognizerErrorKind.SERVER,
            RecognizerErrorMapping.fromErrorCode(SpeechRecognizer.ERROR_SERVER)
        )
        assertEquals(
            RecognizerErrorKind.CLIENT,
            RecognizerErrorMapping.fromErrorCode(SpeechRecognizer.ERROR_CLIENT)
        )
        assertEquals(
            RecognizerErrorKind.SPEECH_TIMEOUT,
            RecognizerErrorMapping.fromErrorCode(SpeechRecognizer.ERROR_SPEECH_TIMEOUT)
        )
        assertEquals(
            RecognizerErrorKind.NO_MATCH,
            RecognizerErrorMapping.fromErrorCode(SpeechRecognizer.ERROR_NO_MATCH)
        )
        assertEquals(
            RecognizerErrorKind.RECOGNIZER_BUSY,
            RecognizerErrorMapping.fromErrorCode(SpeechRecognizer.ERROR_RECOGNIZER_BUSY)
        )
        assertEquals(
            RecognizerErrorKind.INSUFFICIENT_PERMISSIONS,
            RecognizerErrorMapping.fromErrorCode(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS)
        )
        assertEquals(
            RecognizerErrorKind.LANGUAGE_NOT_SUPPORTED,
            RecognizerErrorMapping.fromErrorCode(SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED)
        )
        assertEquals(
            RecognizerErrorKind.LANGUAGE_UNAVAILABLE,
            RecognizerErrorMapping.fromErrorCode(SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE)
        )
        assertEquals(
            RecognizerErrorKind.UNKNOWN,
            RecognizerErrorMapping.fromErrorCode(999)
        )
    }

    // T16-01-02: the user-facing messages are distinct per failure
    // class. A language failure must say so (and name my-MM); it must
    // never collapse into the silence message, the network message, or
    // the busy message.
    @Test
    fun t16_01_02_messagesAreDistinctPerKind() {
        val language = RecognizerErrorMapping.userMessageMm(RecognizerErrorKind.LANGUAGE_NOT_SUPPORTED)
        val silence = RecognizerErrorMapping.userMessageMm(RecognizerErrorKind.SPEECH_TIMEOUT)
        val noMatch = RecognizerErrorMapping.userMessageMm(RecognizerErrorKind.NO_MATCH)
        val network = RecognizerErrorMapping.userMessageMm(RecognizerErrorKind.NETWORK)
        val busy = RecognizerErrorMapping.userMessageMm(RecognizerErrorKind.RECOGNIZER_BUSY)
        val permission = RecognizerErrorMapping.userMessageMm(RecognizerErrorKind.INSUFFICIENT_PERMISSIONS)

        assertTrue("language message must name the language problem", language.contains("my-MM"))
        assertNotEquals(language, silence)
        assertNotEquals(language, network)
        assertNotEquals(language, busy)
        assertNotEquals(noMatch, silence)
        assertNotEquals(network, busy)
        assertNotEquals(permission, silence)
        // Only the true-silence kind may use the legacy "can't hear" text.
        assertEquals("အသံ မကြားရပါခင်ဗျာ", silence)
    }

    // T16-01-03: terminal classification — language + permission +
    // no-service end the session; transient kinds do not pretend to.
    @Test
    fun t16_01_03_terminalClassification() {
        assertTrue(RecognizerErrorMapping.isTerminalError(RecognizerErrorKind.LANGUAGE_NOT_SUPPORTED))
        assertTrue(RecognizerErrorMapping.isTerminalError(RecognizerErrorKind.LANGUAGE_UNAVAILABLE))
        assertTrue(RecognizerErrorMapping.isTerminalError(RecognizerErrorKind.INSUFFICIENT_PERMISSIONS))
        assertTrue(RecognizerErrorMapping.isTerminalError(RecognizerErrorKind.RECOGNIZER_UNAVAILABLE))
        assertFalse(RecognizerErrorMapping.isTerminalError(RecognizerErrorKind.NO_MATCH))
        assertFalse(RecognizerErrorMapping.isTerminalError(RecognizerErrorKind.NETWORK))
        assertFalse(RecognizerErrorMapping.isTerminalError(RecognizerErrorKind.RECOGNIZER_BUSY))
        assertTrue(RecognizerErrorMapping.isLanguageSupportError(RecognizerErrorKind.LANGUAGE_NOT_SUPPORTED))
        assertFalse(RecognizerErrorMapping.isLanguageSupportError(RecognizerErrorKind.SERVER))
    }

    // T16-01-04: the end-of-session decision table. Buffered speech is
    // finalized ONLY for a soft end-of-speech error while the user was
    // speaking; everything else ends the session with a message — the
    // session can no longer hang.
    @Test
    fun t16_01_04_decisionTable() {
        assertEquals(
            RecognizerErrorAction.FINALIZE_BUFFERED_SPEECH,
            RecognizerErrorMapping.decideAction(RecognizerErrorKind.NO_MATCH, isUserSpeaking = true, hasBufferedSpeech = true)
        )
        assertEquals(
            RecognizerErrorAction.FINALIZE_BUFFERED_SPEECH,
            RecognizerErrorMapping.decideAction(RecognizerErrorKind.SPEECH_TIMEOUT, isUserSpeaking = true, hasBufferedSpeech = true)
        )
        // No buffer -> nothing to finalize, end with the message.
        assertEquals(
            RecognizerErrorAction.END_SESSION_WITH_MESSAGE,
            RecognizerErrorMapping.decideAction(RecognizerErrorKind.NO_MATCH, isUserSpeaking = true, hasBufferedSpeech = false)
        )
        // Not speaking -> end with the message even if a stale buffer exists.
        assertEquals(
            RecognizerErrorAction.END_SESSION_WITH_MESSAGE,
            RecognizerErrorMapping.decideAction(RecognizerErrorKind.NO_MATCH, isUserSpeaking = false, hasBufferedSpeech = true)
        )
        // A hard failure mid-speech must NOT execute the partial buffer
        // as if it were a complete command.
        assertEquals(
            RecognizerErrorAction.END_SESSION_WITH_MESSAGE,
            RecognizerErrorMapping.decideAction(RecognizerErrorKind.LANGUAGE_NOT_SUPPORTED, isUserSpeaking = true, hasBufferedSpeech = true)
        )
        assertEquals(
            RecognizerErrorAction.END_SESSION_WITH_MESSAGE,
            RecognizerErrorMapping.decideAction(RecognizerErrorKind.NETWORK, isUserSpeaking = true, hasBufferedSpeech = true)
        )
        assertEquals(
            RecognizerErrorAction.END_SESSION_WITH_MESSAGE,
            RecognizerErrorMapping.decideAction(RecognizerErrorKind.RECOGNIZER_BUSY, isUserSpeaking = false, hasBufferedSpeech = false)
        )
    }

    // T16-01-05: language-support verdicts from a service's reported
    // language list. Exact tag and primary-subtag matches count; an
    // absent list is UNKNOWN, never a fabricated NOT_SUPPORTED.
    @Test
    fun t16_01_05_languageSupportVerdicts() {
        assertEquals(
            LanguageSupportVerdict.SUPPORTED,
            RecognitionLanguageSupport.verdictFor(listOf("en-US", "my-MM", "th-TH"), "my-MM")
        )
        assertEquals(
            LanguageSupportVerdict.SUPPORTED,
            RecognitionLanguageSupport.verdictFor(listOf("my"), "my-MM")
        )
        assertEquals(
            LanguageSupportVerdict.SUPPORTED,
            RecognitionLanguageSupport.verdictFor(listOf("MY_mm"), "my-MM")
        )
        assertEquals(
            LanguageSupportVerdict.NOT_SUPPORTED,
            RecognitionLanguageSupport.verdictFor(listOf("en-US", "th-TH"), "my-MM")
        )
        assertEquals(
            LanguageSupportVerdict.UNKNOWN,
            RecognitionLanguageSupport.verdictFor(null, "my-MM")
        )
        assertEquals(
            LanguageSupportVerdict.UNKNOWN,
            RecognitionLanguageSupport.verdictFor(emptyList(), "my-MM")
        )
    }
}
