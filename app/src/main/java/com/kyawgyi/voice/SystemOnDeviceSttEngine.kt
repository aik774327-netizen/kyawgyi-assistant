package com.kyawgyi.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.example.data.LanguagePreferencesRepository
import com.example.nlu.BurmeseNluPreprocessor

/**
 * SystemOnDeviceSttEngine (Layer 2 Android SpeechRecognizer Engine)
 *
 * Implements Android SpeechRecognizer with Myanmar (my-MM) locale support:
 * 1. Always dispatches SpeechRecognizer lifecycle operations on MainLooper as required by Android OS.
 * 2. Synchronizes recognition language dynamically with LanguagePreferencesRepository (defaults to "my-MM").
 * 3. Enforces AudioFocusAndAecManager Echo Guard (drops transcripts generated during TTS playback or hangover).
 * 4. Normalizes finalized Myanmar transcripts via BurmeseNluPreprocessor.
 */
class SystemOnDeviceSttEngine(private val context: Context) : SttEngine {

    companion object {
        private const val TAG = "SystemOnDeviceSttEngine"
    }

    override val engineType: SttEngineType = SttEngineType.SYSTEM_ON_DEVICE

    @Volatile
    override var isRunning: Boolean = false
        private set

    @Volatile
    var lastErrorCode: Int? = null
        private set

    private var transcriptListener: ((String, Boolean) -> Unit)? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null

    @Volatile
    private var useDefaultLocaleFallback: Boolean = false

    fun isRecognitionAvailable(): Boolean {
        return try {
            SpeechRecognizer.isRecognitionAvailable(context)
        } catch (e: Exception) {
            false
        }
    }

    override fun startListening() {
        Log.i(TAG, "Starting SystemOnDeviceSttEngine (my-MM / dynamic locale)...")
        isRunning = true
        lastErrorCode = null

        mainHandler.post {
            if (!isRunning) return@post
            try {
                if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                    Log.w(TAG, "Android SpeechRecognizer service is not available on this device/environment.")
                    return@post
                }

                // Release any active raw AudioRecord wake-word capture so SpeechRecognizer gets exclusive mic access
                try {
                    com.example.wakeword.KyawGyiWakeWordEngine.getInstance(context).stopListening()
                } catch (_: Exception) {}

                if (speechRecognizer == null) {
                    speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context.applicationContext).apply {
                        setRecognitionListener(createRecognitionListener())
                    }
                }

                val selectedLang = if (useDefaultLocaleFallback) {
                    java.util.Locale.getDefault().toLanguageTag()
                } else {
                    try {
                        LanguagePreferencesRepository.getInstance(context).getSelectedLanguageSync()
                    } catch (e: Exception) {
                        "my-MM"
                    }
                }

                val recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, selectedLang)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, selectedLang)
                    putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false)
                    putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf("my-MM", "my", "en-US"))
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                    putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                }

                speechRecognizer?.startListening(recognizerIntent)
            } catch (e: Exception) {
                Log.w(TAG, "SystemOnDeviceSttEngine startListening note (safe in unit test/headless): ${e.message}")
            }
        }
    }

    override fun stopListening() {
        Log.i(TAG, "Stopping SystemOnDeviceSttEngine.")
        isRunning = false
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
                speechRecognizer?.cancel()
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping SpeechRecognizer: ${e.message}")
            }
        }
    }

    override fun processAudio(pcmChunk: ByteArray) {
        // Android SpeechRecognizer captures from OS Binder audio stream directly when active,
        // while also supporting direct injection via emitTranscript() for pipeline testing.
    }

    override fun setTranscriptListener(listener: (transcript: String, isFinal: Boolean) -> Unit) {
        this.transcriptListener = listener
    }

    fun emitTranscript(transcript: String, isFinal: Boolean) {
        val aecManager = AudioFocusAndAecManager.getInstance(context)
        if (aecManager.isSpeaking || aecManager.isEchoHangoverActive) {
            Log.w(TAG, "Dropped STT transcript during TTS playback or echo hangover guard.")
            return
        }
        val processed = if (isFinal) BurmeseNluPreprocessor.normalize(transcript) else transcript
        transcriptListener?.invoke(processed, isFinal)
    }

    override fun release() {
        isRunning = false
        transcriptListener = null
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
                speechRecognizer?.destroy()
                speechRecognizer = null
            } catch (e: Exception) {
                Log.w(TAG, "Error releasing SpeechRecognizer: ${e.message}")
            }
        }
    }

    private fun createRecognitionListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                Log.d(TAG, "System STT ready for Myanmar speech.")
            }

            override fun onBeginningOfSpeech() {
                Log.d(TAG, "System STT detected speech onset.")
            }

            override fun onRmsChanged(rmsdB: Float) {}

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                Log.d(TAG, "System STT detected end of speech.")
            }

            override fun onError(error: Int) {
                lastErrorCode = error
                Log.w(TAG, "System STT error code: $error (${mapErrorCode(error)})")
                // Error 12 = ERROR_LANGUAGE_NOT_SUPPORTED, Error 13 = ERROR_LANGUAGE_UNAVAILABLE
                if ((error == 12 || error == 13) && !useDefaultLocaleFallback) {
                    Log.i(TAG, "Locale my-MM not directly supported by device ASR; retrying with system default + additional languages fallback.")
                    useDefaultLocaleFallback = true
                    if (isRunning) {
                        startListening()
                    }
                } else {
                    isRunning = false
                }
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val topHypothesis = matches?.firstOrNull()?.trim().orEmpty()
                if (topHypothesis.isNotEmpty()) {
                    emitTranscript(topHypothesis, isFinal = true)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val partial = matches?.firstOrNull()?.trim().orEmpty()
                if (partial.isNotEmpty()) {
                    emitTranscript(partial, isFinal = false)
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }

    fun mapErrorCode(error: Int): String {
        return when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "ERROR_AUDIO (Audio recording error)"
            SpeechRecognizer.ERROR_CLIENT -> "ERROR_CLIENT (Client side error)"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "ERROR_INSUFFICIENT_PERMISSIONS (RECORD_AUDIO missing)"
            SpeechRecognizer.ERROR_NETWORK -> "ERROR_NETWORK (Network error)"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "ERROR_NETWORK_TIMEOUT (Network timeout)"
            SpeechRecognizer.ERROR_NO_MATCH -> "ERROR_NO_MATCH (No speech match)"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "ERROR_RECOGNIZER_BUSY (RecognitionService busy)"
            SpeechRecognizer.ERROR_SERVER -> "ERROR_SERVER (Server error)"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "ERROR_SPEECH_TIMEOUT (No speech input)"
            else -> "ERROR_UNKNOWN ($error)"
        }
    }
}
