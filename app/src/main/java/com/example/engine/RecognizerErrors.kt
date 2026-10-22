package com.example.engine

import android.speech.SpeechRecognizer

/**
 * FIX-01 (Phase 1.5 device remediation): a shared, honest taxonomy for
 * SpeechRecognizer failures.
 *
 * Before this existed, [VoiceSessionManager] swallowed recognizer errors:
 * `onError` did nothing unless the user was mid-speech with buffered text,
 * so on a phone whose recognition service lacks my-MM every command died
 * silently and the session eventually announced only "အသံ မကြားရပါ"
 * ("I can't hear you") — the user experienced this as "the app does not
 * understand Burmese". Every error code now maps to a distinct kind with
 * its own truthful Burmese message, and language-support failures are
 * classified as terminal (retrying the same recognizer cannot fix them).
 *
 * This file is pure Kotlin (SpeechRecognizer error constants are
 * compile-time ints) so the whole taxonomy is unit-testable on the JVM.
 * Dictation keeps its own [com.example.ui.dictation.DictationErrorKind]
 * for its session policy, but its controller now derives that kind from
 * this shared mapping so both surfaces agree on what an error code means.
 */
enum class RecognizerErrorKind {
    NETWORK,
    NETWORK_TIMEOUT,
    AUDIO,
    SERVER,
    CLIENT,
    INSUFFICIENT_PERMISSIONS,
    NO_MATCH,
    RECOGNIZER_BUSY,
    SPEECH_TIMEOUT,
    TOO_MANY_REQUESTS,
    LANGUAGE_NOT_SUPPORTED,
    LANGUAGE_UNAVAILABLE,

    /** SpeechRecognizer.isRecognitionAvailable() returned false. */
    RECOGNIZER_UNAVAILABLE,
    UNKNOWN
}

/** What a voice session should do when a recognizer error arrives. */
enum class RecognizerErrorAction {
    /** The user had already produced partial speech; finalize with it. */
    FINALIZE_BUFFERED_SPEECH,

    /** End the session cleanly and tell the user what actually happened. */
    END_SESSION_WITH_MESSAGE
}

object RecognizerErrorMapping {

    fun fromErrorCode(error: Int): RecognizerErrorKind = when (error) {
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> RecognizerErrorKind.NETWORK_TIMEOUT
        SpeechRecognizer.ERROR_NETWORK -> RecognizerErrorKind.NETWORK
        SpeechRecognizer.ERROR_AUDIO -> RecognizerErrorKind.AUDIO
        SpeechRecognizer.ERROR_SERVER -> RecognizerErrorKind.SERVER
        SpeechRecognizer.ERROR_CLIENT -> RecognizerErrorKind.CLIENT
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> RecognizerErrorKind.SPEECH_TIMEOUT
        SpeechRecognizer.ERROR_NO_MATCH -> RecognizerErrorKind.NO_MATCH
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> RecognizerErrorKind.RECOGNIZER_BUSY
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> RecognizerErrorKind.INSUFFICIENT_PERMISSIONS
        SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> RecognizerErrorKind.TOO_MANY_REQUESTS
        // Compile-time ints (API 31+); safe to reference on older devices —
        // such recognizers simply never emit these codes.
        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> RecognizerErrorKind.LANGUAGE_NOT_SUPPORTED
        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> RecognizerErrorKind.LANGUAGE_UNAVAILABLE
        else -> RecognizerErrorKind.UNKNOWN
    }

    fun isLanguageSupportError(kind: RecognizerErrorKind): Boolean =
        kind == RecognizerErrorKind.LANGUAGE_NOT_SUPPORTED ||
            kind == RecognizerErrorKind.LANGUAGE_UNAVAILABLE

    /**
     * Terminal errors cannot be fixed by listening again with the same
     * recognizer and settings: the language is unsupported, permission is
     * missing, or there is no recognition service at all. The session must
     * end and the user must be told; anything else is a silent hang.
     */
    fun isTerminalError(kind: RecognizerErrorKind): Boolean =
        isLanguageSupportError(kind) ||
            kind == RecognizerErrorKind.INSUFFICIENT_PERMISSIONS ||
            kind == RecognizerErrorKind.RECOGNIZER_UNAVAILABLE

    /**
     * The end-of-session decision for a recognizer error. The one piece of
     * legacy behaviour worth keeping: if the user was mid-utterance and a
     * soft end-of-speech error (no-match / speech-timeout) arrives with
     * buffered partial text, finalize that text as the command. Every
     * other error ends the session with its own honest message.
     */
    fun decideAction(
        kind: RecognizerErrorKind,
        isUserSpeaking: Boolean,
        hasBufferedSpeech: Boolean
    ): RecognizerErrorAction {
        val softEnd = kind == RecognizerErrorKind.NO_MATCH ||
            kind == RecognizerErrorKind.SPEECH_TIMEOUT
        return if (isUserSpeaking && hasBufferedSpeech && softEnd) {
            RecognizerErrorAction.FINALIZE_BUFFERED_SPEECH
        } else {
            RecognizerErrorAction.END_SESSION_WITH_MESSAGE
        }
    }

    /**
     * A distinct, truthful Burmese user message per error kind. These are
     * deliberately NOT interchangeable: a language-support failure must
     * never be reported as "I can't hear you", and a busy recognizer must
     * never be reported as a network problem.
     */
    fun userMessageMm(kind: RecognizerErrorKind): String = when (kind) {
        RecognizerErrorKind.LANGUAGE_NOT_SUPPORTED,
        RecognizerErrorKind.LANGUAGE_UNAVAILABLE ->
            "ဒီဖုန်းရဲ့ Speech Recognition က မြန်မာဘာသာ (my-MM) ကို မထောက်ပံ့သေးပါခင်ဗျာ။ " +
                "Google app ထဲမှာ မြန်မာ အသံဒေတာ (Offline speech recognition) download လုပ်ပေးပါ"
        RecognizerErrorKind.RECOGNIZER_UNAVAILABLE ->
            "ဒီဖုန်းမှာ Speech Recognition ဝန်ဆောင်မှု မရှိပါခင်ဗျာ။ Google app ကို ထည့်သွင်းပေးပါ"
        RecognizerErrorKind.INSUFFICIENT_PERMISSIONS ->
            "Microphone ခွင့်ပြုချက် လိုအပ်ပါသည်ခင်ဗျာ။ Settings မှာ ခွင့်ပြုပေးပါ"
        RecognizerErrorKind.NETWORK ->
            "အင်တာနက် ပြဿနာကြောင့် အသံကို မမှတ်မိနိုင်ပါခင်ဗျာ"
        RecognizerErrorKind.NETWORK_TIMEOUT ->
            "အင်တာနက် ချိတ်ဆက်မှု နှေးနေလို့ အချိန်ကုန်သွားပါတယ်ခင်ဗျာ"
        RecognizerErrorKind.RECOGNIZER_BUSY ->
            "အသံမှတ် စနစ် အလုပ်များနေပါတယ်၊ ခဏနေ ပြန်ပြောပေးပါခင်ဗျာ"
        RecognizerErrorKind.TOO_MANY_REQUESTS ->
            "တောင်းဆိုမှု များနေပါတယ်၊ ခဏစောင့်ပြီး ပြန်ပြောပေးပါခင်ဗျာ"
        RecognizerErrorKind.NO_MATCH ->
            "ပြောလိုက်တာကို မမှတ်မိလိုက်ပါခင်ဗျာ၊ ပြန်ပြောပေးပါ"
        RecognizerErrorKind.SPEECH_TIMEOUT ->
            "အသံ မကြားရပါခင်ဗျာ"
        RecognizerErrorKind.AUDIO ->
            "Microphone အသံဖမ်းရာမှာ ပြဿနာ ရှိပါတယ်ခင်ဗျာ"
        RecognizerErrorKind.SERVER ->
            "Speech Recognition ဆာဗာဘက်မှာ ပြဿနာ ဖြစ်နေပါတယ်ခင်ဗျာ"
        RecognizerErrorKind.CLIENT ->
            "Speech Recognition စနစ် ပြဿနာ ဖြစ်နေပါတယ်ခင်ဗျာ"
        RecognizerErrorKind.UNKNOWN ->
            "အသံ မှတ်ရာမှာ ပြဿနာ ဖြစ်ပါတယ်ခင်ဗျာ"
    }
}

/** Verdict of a recognition-language support check. */
enum class LanguageSupportVerdict {
    SUPPORTED,
    NOT_SUPPORTED,

    /** The service did not answer / has not been asked yet. Proceed
     * optimistically; recognizer error codes 12/13 remain the backstop. */
    UNKNOWN
}

object RecognitionLanguageSupport {

    /**
     * Decides support for [requestedLanguage] (e.g. "my-MM") from the
     * language list a recognition service reports. A match is an exact
     * tag ("my-MM") or a shared primary language subtag ("my" satisfies
     * a "my-MM" request and vice versa). An absent/empty list proves
     * nothing either way, so it yields [LanguageSupportVerdict.UNKNOWN]
     * rather than a fabricated verdict.
     */
    fun verdictFor(
        supportedLanguages: Collection<String>?,
        requestedLanguage: String
    ): LanguageSupportVerdict {
        if (supportedLanguages.isNullOrEmpty()) return LanguageSupportVerdict.UNKNOWN
        val requested = requestedLanguage.trim().lowercase().replace('_', '-')
        if (requested.isEmpty()) return LanguageSupportVerdict.UNKNOWN
        val requestedPrimary = requested.substringBefore('-')
        val hit = supportedLanguages.any { raw ->
            val tag = raw.trim().lowercase().replace('_', '-')
            tag == requested || tag.substringBefore('-') == requestedPrimary
        }
        return if (hit) LanguageSupportVerdict.SUPPORTED else LanguageSupportVerdict.NOT_SUPPORTED
    }
}
