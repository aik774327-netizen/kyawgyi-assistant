package com.example.ui.dictation

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

/**
 * Thin Android layer that drives the system [SpeechRecognizer] for the
 * dictation screen. It deliberately follows the same pattern the app
 * already uses in SpeechHandler / VoiceSessionManager:
 *  - recognizer created and driven only on the main thread,
 *  - the same RecognizerIntent extras (free-form model, partial results,
 *    dictation-mode silence timings),
 *  - the top match's REAL confidence read from CONFIDENCE_SCORES, or 0.0f
 *    when the recognizer reports none (the FAKE-11 rule — never invent one).
 *
 * All session policy (auto-restart budget, error surfacing, persistence)
 * lives in [DictationViewModel] / [RestartGuard]; this class only reports
 * raw recognizer events.
 */
class DictationRecognizerController(
    context: Context,
    private val listener: Listener
) {
    interface Listener {
        /** Recognizer is live and listening. */
        fun onListeningStarted()

        /** Interim (partial) transcript — display only, never document text. */
        fun onPartialTranscript(text: String)

        /** Final transcript with the recognizer's real confidence (0.0f = unknown). */
        fun onFinalTranscript(text: String, confidence: Float)

        /** A recoverable-or-not recognizer error, already classified. */
        fun onRecognizerError(kind: DictationErrorKind)

        /**
         * The current recognition session has ended (after results, after an
         * error, or after end-of-speech). The ViewModel decides whether an
         * auto-restart is allowed.
         */
        fun onSessionEnded()
    }

    private val appContext: Context = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())

    private var speechRecognizer: SpeechRecognizer? = null
    private var destroyed = false
    private var sessionEndSignalled = false

    /** Whether this device reports a recognition service at all. */
    fun isRecognitionAvailable(): Boolean = try {
        SpeechRecognizer.isRecognitionAvailable(appContext)
    } catch (e: Exception) {
        Log.w(TAG, "isRecognitionAvailable failed: ${e.message}")
        false
    }

    fun start(languageCode: String) {
        if (destroyed) return
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { start(languageCode) }
            return
        }
        destroyRecognizerSafely()
        sessionEndSignalled = false
        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(appContext).apply {
                setRecognitionListener(recognitionListener)
            }
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageCode)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageCode)
                putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false)
                putExtra(
                    "android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES",
                    arrayOf("my-MM", "my", "en-US", "th-TH")
                )
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, appContext.packageName)
                putExtra("android.speech.extras.SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS", 2200L)
                putExtra("android.speech.extras.SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS", 1600L)
                putExtra("android.speech.extras.SPEECH_INPUT_MINIMUM_LENGTH_MILLIS", 400L)
                putExtra("android.speech.extra.DICTATION_MODE", true)
            }
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting speech recognition: ${e.message}", e)
            listener.onRecognizerError(DictationErrorKind.CLIENT)
            signalSessionEnded()
        }
    }

    fun stop() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { stop() }
            return
        }
        destroyRecognizerSafely()
    }

    fun destroy() {
        destroyed = true
        stop()
        mainHandler.removeCallbacksAndMessages(null)
    }

    private fun destroyRecognizerSafely() {
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
            speechRecognizer = null
        } catch (e: Exception) {
            Log.w(TAG, "Error destroying speech recognizer: ${e.message}")
        }
    }

    private fun signalSessionEnded() {
        if (sessionEndSignalled) return
        sessionEndSignalled = true
        listener.onSessionEnded()
    }

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            listener.onListeningStarted()
        }

        override fun onBeginningOfSpeech() {}

        override fun onRmsChanged(rmsdB: Float) {}

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            // The recognizer normally still delivers results/error after
            // this; SpeechHandler's pattern restarts from here too, and
            // signalSessionEnded() de-duplicates the terminal callbacks.
            signalSessionEnded()
        }

        override fun onError(error: Int) {
            listener.onRecognizerError(mapError(error))
            signalSessionEnded()
        }

        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val top = matches?.firstOrNull()
            if (!top.isNullOrBlank()) {
                // FAKE-11 rule: real CONFIDENCE_SCORES or 0.0f (unknown).
                val confidence = results
                    ?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)
                    ?.firstOrNull() ?: 0.0f
                listener.onFinalTranscript(top, confidence)
            }
            signalSessionEnded()
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val partial = matches?.firstOrNull()
            if (!partial.isNullOrEmpty()) {
                listener.onPartialTranscript(partial)
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun mapError(error: Int): DictationErrorKind = when (error) {
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> DictationErrorKind.PERMISSION_DENIED
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> DictationErrorKind.NETWORK
        SpeechRecognizer.ERROR_AUDIO -> DictationErrorKind.AUDIO
        SpeechRecognizer.ERROR_NO_MATCH -> DictationErrorKind.NO_MATCH
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> DictationErrorKind.SPEECH_TIMEOUT
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> DictationErrorKind.RECOGNIZER_BUSY
        SpeechRecognizer.ERROR_SERVER -> DictationErrorKind.SERVER
        SpeechRecognizer.ERROR_CLIENT -> DictationErrorKind.CLIENT
        // Constants added in API 31; they are compile-time ints, so mapping
        // them is safe on older devices (such recognizers simply never
        // send these codes and surface language problems as SERVER errors).
        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> DictationErrorKind.LANGUAGE_NOT_SUPPORTED
        else -> DictationErrorKind.UNKNOWN
    }

    companion object {
        private const val TAG = "DictationRecognizer"
    }
}
