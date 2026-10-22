package com.example.service

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.example.engine.KyawGyiVadControlEngine
import com.example.utils.BatteryOptimizationMode
import com.example.utils.KyawGyiBatteryOptimizer

/**
 * Human-Centric Speech & Audio Ingestion Handler
 * Manages continuous SpeechRecognizer listening with Adaptive Battery Optimization.
 */
class SpeechHandler(
    private val context: Context,
    private val onWakeWordDetected: () -> Unit,
    private val onCommandReceived: (String) -> Unit,
    private val onPartialReceived: ((String) -> Unit)? = null,
    /**
     * FIX-01: invoked when the recognizer reports a failure the user must
     * hear about — above all a language-support failure, which restarting
     * can never fix. The owner (floating bubble) surfaces the kind's
     * Burmese message instead of the handler retrying in silence.
     */
    private val onRecognitionError: ((com.example.engine.RecognizerErrorKind) -> Unit)? = null
) {

    companion object {
        private const val TAG = "SpeechHandler"
        private const val BASE_RESTART_DELAY_MS = 1500L
        private const val MAX_CONSECUTIVE_ERRORS = 4
    }

    private var speechRecognizer: SpeechRecognizer? = null
    var isListening = false
        private set

    private val handler = Handler(Looper.getMainLooper())
    private var restartRunnable: Runnable? = null
    private var consecutiveErrorCount = 0
    private var isDestroyed = false

    val vadEngine = KyawGyiVadControlEngine(
        onAutoOpenMic = {
            onWakeWordDetected()
        },
        onAutoCloseMicAndSendToStt = { pcmData ->
            Log.d(TAG, "VAD silence auto-close triggered with ${pcmData.size} bytes PCM.")
        }
    )

    fun startContinuousListening() {
        if (isDestroyed) return

        if (Looper.myLooper() != Looper.getMainLooper()) {
            handler.post { startContinuousListening() }
            return
        }

        if (androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.RECORD_AUDIO
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "Cannot start SpeechRecognizer: RECORD_AUDIO permission not granted")
            isListening = false
            return
        }

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.e(TAG, "Speech Recognition is not available on this device")
            return
        }

        cancelPendingRestarts()
        destroyRecognizerSafely()

        try {
            com.example.wakeword.KyawGyiWakeWordEngine.getInstance(context).stopListening()
        } catch (_: Exception) {}

        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        isListening = true
                        consecutiveErrorCount = 0
                        Log.d(TAG, "SpeechRecognizer Ready for Speech")
                    }

                    override fun onBeginningOfSpeech() {
                        Log.d(TAG, "User started speaking")
                    }

                    override fun onRmsChanged(rmsdB: Float) {}

                    override fun onBufferReceived(buffer: ByteArray?) {
                        if (buffer != null && buffer.isNotEmpty()) {
                            vadEngine.processPcmFrame(buffer, buffer.size)
                        }
                    }

                    override fun onEndOfSpeech() {
                        isListening = false
                        Log.d(TAG, "End of speech detected")
                        // Wait generously before graceful restart
                        scheduleRestart(BASE_RESTART_DELAY_MS)
                    }

                    override fun onError(error: Int) {
                        isListening = false
                        consecutiveErrorCount++
                        val errorMsg = getErrorMessage(error)
                        Log.w(TAG, "SpeechRecognizer error ($error): $errorMsg, count=$consecutiveErrorCount")

                        if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                            Log.e(TAG, "SpeechRecognizer stopped due to missing RECORD_AUDIO permission.")
                            onRecognitionError?.invoke(
                                com.example.engine.RecognizerErrorKind.INSUFFICIENT_PERMISSIONS
                            )
                            return
                        }

                        // FIX-01: a language-support failure (12/13) can
                        // never be fixed by restarting the same recognizer
                        // — the old code looped on it forever (with circuit-
                        // breaker pauses) and never told the user anything.
                        // Stop, and surface the honest Burmese guidance.
                        if (error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED ||
                            error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE
                        ) {
                            Log.e(TAG, "Recognition service does not support the selected language (error $error). Not restarting.")
                            cancelPendingRestarts()
                            onRecognitionError?.invoke(
                                com.example.engine.RecognizerErrorMapping.fromErrorCode(error)
                            )
                            return
                        }

                        if (consecutiveErrorCount >= MAX_CONSECUTIVE_ERRORS) {
                            Log.w(TAG, "Circuit breaker tripped: Too many consecutive errors. Backing off for 5s.")
                            scheduleRestart(5000L)
                            consecutiveErrorCount = 0
                            return
                        }

                        // Human-friendly exponential backoff
                        val delayMs = when (error) {
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY, SpeechRecognizer.ERROR_CLIENT -> 2000L
                            SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> 1200L
                            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> 3000L
                            else -> 1500L
                        }
                        scheduleRestart(delayMs)
                    }

                    override fun onResults(results: Bundle?) {
                        isListening = false
                        consecutiveErrorCount = 0
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            val spokenText = com.example.nlu.BurmeseNluPreprocessor.normalize(matches[0])
                            Log.i(TAG, "Speech Recognition Final Result: $spokenText")
                            try {
                                com.kyawgyi.voice.SttEngineManager.getInstance(context).recordLiveExternalTranscript(spokenText, isFinal = true)
                            } catch (_: Exception) {}
                            processSpokenText(spokenText)
                        } else {
                            Log.d(TAG, "No matches returned in results")
                        }
                        scheduleRestart(BASE_RESTART_DELAY_MS)
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            val partialText = matches[0]
                            try {
                                com.kyawgyi.voice.SttEngineManager.getInstance(context).recordLiveExternalTranscript(partialText, isFinal = false)
                            } catch (_: Exception) {}
                            onPartialReceived?.invoke(partialText)

                            // Wake word detection in partial stream
                            if (partialText.contains("ကျော်ကြီး") ||
                                partialText.contains("ကျော် ကြီး") ||
                                partialText.lowercase().contains("kyaw gyi")
                            ) {
                                Log.i(TAG, "Wake word detected in partial stream!")
                                onWakeWordDetected()
                            }
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }

            val selectedLang = try {
                com.example.data.LanguagePreferencesRepository.getInstance(context).getSelectedLanguageSync()
            } catch (e: Exception) {
                "my-MM"
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, selectedLang)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, selectedLang)
                putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false)
                putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf("my-MM", "my", "en-US"))
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                // 100% Background Audio Capture and Far-Field Voice Ingestion
                putExtra("android.speech.extras.SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS", 2200L)
                putExtra("android.speech.extras.SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS", 1600L)
                putExtra("android.speech.extras.SPEECH_INPUT_MINIMUM_LENGTH_MILLIS", 400L)
                putExtra("android.speech.extra.DICTATION_MODE", true)
            }

            speechRecognizer?.startListening(intent)
            isListening = true
        } catch (e: Exception) {
            Log.e(TAG, "Error starting speech recognition: ${e.message}", e)
            scheduleRestart(2500L)
        }
    }

    private fun processSpokenText(text: String) {
        val lowerText = text.lowercase()
        if (lowerText.contains("ကျော်ကြီး") || lowerText.contains("kyaw gyi") || lowerText.contains("ကျော် ကြီး")) {
            try {
                com.example.audio.GlobalAudioStateManager.getInstance(context).onWakeWordDetected("SpeechHandler")
            } catch (e: Exception) {
                Log.w(TAG, "GlobalAudioStateManager wake notification error: ${e.message}")
            }
            onWakeWordDetected()
        }
        onCommandReceived(text)
    }

    private fun getAdaptiveDelay(baseDelayMs: Long): Long {
        val mode = KyawGyiBatteryOptimizer.batteryMode.value
        val isSystemPowerSave = KyawGyiBatteryOptimizer.isSystemPowerSaveMode(context)
        val autoPause = KyawGyiBatteryOptimizer.autoPauseOnLowBattery.value

        var calculatedDelay = when (mode) {
            BatteryOptimizationMode.MAX_SAVER -> (baseDelayMs * 2.2f).toLong().coerceAtLeast(3000L)
            BatteryOptimizationMode.BALANCED -> baseDelayMs.coerceAtLeast(1500L)
            BatteryOptimizationMode.PERFORMANCE -> (baseDelayMs * 0.7f).toLong().coerceAtLeast(800L)
        }

        if (isSystemPowerSave && autoPause) {
            calculatedDelay = (calculatedDelay * 1.5f).toLong().coerceAtLeast(3500L)
        }

        return calculatedDelay
    }

    private fun scheduleRestart(delayMs: Long) {
        if (isDestroyed) return
        cancelPendingRestarts()
        val adaptiveDelay = getAdaptiveDelay(delayMs)
        restartRunnable = Runnable {
            if (!isDestroyed) {
                startContinuousListening()
            }
        }
        handler.postDelayed(restartRunnable!!, adaptiveDelay)
    }

    private fun cancelPendingRestarts() {
        restartRunnable?.let { handler.removeCallbacks(it) }
        restartRunnable = null
    }

    private fun destroyRecognizerSafely() {
        try {
            isListening = false
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
            speechRecognizer = null
        } catch (e: Exception) {
            Log.w(TAG, "Error destroying speech recognizer: ${e.message}")
        }
    }

    fun stopListening() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            handler.post { stopListening() }
            return
        }
        cancelPendingRestarts()
        destroyRecognizerSafely()
    }

    fun destroy() {
        isDestroyed = true
        stopListening()
        handler.removeCallbacksAndMessages(null)
    }

    private fun getErrorMessage(errorCode: Int): String {
        return when (errorCode) {
            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
            SpeechRecognizer.ERROR_CLIENT -> "Client side error"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
            SpeechRecognizer.ERROR_NETWORK -> "Network error"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
            SpeechRecognizer.ERROR_NO_MATCH -> "No speech match"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
            SpeechRecognizer.ERROR_SERVER -> "Server error"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input / timeout"
            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> "Language not supported"
            SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "Language unavailable"
            else -> "Unknown error ($errorCode)"
        }
    }
}
