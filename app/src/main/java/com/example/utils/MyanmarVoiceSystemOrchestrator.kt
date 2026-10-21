package com.example.utils

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Master Voice Orchestrator - Interconnects DSP, STT, and TTS Systems
 */
class MyanmarVoiceSystemOrchestrator(
    private val context: Context,
    silenceThresholdDb: Float = -50.0f
) {
    companion object {
        private const val TAG = "MyanmarVoiceOrchestrator"

        @Volatile
        private var instance: MyanmarVoiceSystemOrchestrator? = null

        fun getInstance(context: Context): MyanmarVoiceSystemOrchestrator {
            return instance ?: synchronized(this) {
                instance ?: MyanmarVoiceSystemOrchestrator(context.applicationContext).also { instance = it }
            }
        }
    }

    val dsp = MyanmarAudioDSP(context, silenceThresholdDb)
    val stt = MyanmarSTEngine()
    val tts = MyanmarTTSEngine(context)

    private var registeredCommands: List<MyanmarSTEngine.RegisteredCommand> = emptyList()
    private var speechRecognizer: SpeechRecognizer? = null
    private var dspObservationJob: Job? = null
    private var isSystemActive = false

    /**
     * Register System Voice Actions
     */
    fun registerCommands(commandsList: List<MyanmarSTEngine.RegisteredCommand>) {
        this.registeredCommands = commandsList
        Log.d(TAG, "Registered ${commandsList.size} system voice commands.")
    }

    /**
     * Start Complete Voice Recognition Engine Pipeline
     */
    fun startSystem(
        scope: CoroutineScope,
        onTranscriptUI: ((transcript: MyanmarSTEngine.TranscriptResult, isSpeechDetected: Boolean) -> Unit)? = null,
        onIntentMatch: ((match: MyanmarSTEngine.IntentMatchResult) -> Unit)? = null,
        onError: ((error: String) -> Unit)? = null
    ) {
        if (isSystemActive) {
            Log.w(TAG, "Voice Orchestrator System is already active.")
            return
        }

        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "RECORD_AUDIO permission missing.")
            onError?.invoke("RECORD_AUDIO permission missing")
            return
        }

        isSystemActive = true

        // Step 1: Start DSP Microphone Pipeline
        dsp.startMicrophone()

        // Monitor DSP VAD state
        dspObservationJob?.cancel()
        dspObservationJob = scope.launch(Dispatchers.Default) {
            dsp.analysisState.collectLatest { analysis ->
                // VAD analysis updated
            }
        }

        // Step 2: Start STT Listening Engine via Android SpeechRecognizer
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.e(TAG, "Speech recognition is not available on this device.")
            onError?.invoke("Speech recognition unavailable")
            return
        }

        try {
            speechRecognizer?.destroy()
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        Log.d(TAG, "STT Ready for speech")
                    }

                    override fun onBeginningOfSpeech() {
                        Log.d(TAG, "STT Beginning of speech detected")
                    }

                    override fun onRmsChanged(rmsdB: Float) {
                        // Handled by DSP
                    }

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        Log.d(TAG, "STT End of speech")
                    }

                    override fun onError(error: Int) {
                        val errorMessage = "STT Error Code: $error"
                        Log.e(TAG, errorMessage)
                        if (isSystemActive) {
                            // Restart speech listening if active
                            restartListening()
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        handleSpeechResults(results, isFinal = true, onTranscriptUI, onIntentMatch)
                        if (isSystemActive) {
                            restartListening()
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        handleSpeechResults(partialResults, isFinal = false, onTranscriptUI, onIntentMatch)
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }

            startListeningIntent()

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start speech recognition: ${e.message}", e)
            onError?.invoke(e.message ?: "Speech recognition error")
        }
    }

    private fun startListeningIntent() {
        val selectedLang = try {
            com.example.data.LanguagePreferencesRepository.getInstance(context).getSelectedLanguageSync()
        } catch (e: Exception) {
            "my-MM"
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, selectedLang)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, selectedLang)
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }

        speechRecognizer?.startListening(intent)
    }

    private fun restartListening() {
        try {
            speechRecognizer?.cancel()
            startListeningIntent()
        } catch (e: Exception) {
            Log.e(TAG, "Error restarting listening: ${e.message}")
        }
    }

    private fun handleSpeechResults(
        resultsBundle: Bundle?,
        isFinal: Boolean,
        onTranscriptUI: ((MyanmarSTEngine.TranscriptResult, Boolean) -> Unit)?,
        onIntentMatch: ((MyanmarSTEngine.IntentMatchResult) -> Unit)?
    ) {
        val matches = resultsBundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        if (matches.isNullOrEmpty()) return

        val rawText = matches[0]
        val cleanText = stt.normalizeMyanmarText(rawText)
        val isSpeech = dsp.analysisState.value.isSpeechDetected

        val transcriptResult = MyanmarSTEngine.TranscriptResult(
            raw = rawText,
            clean = cleanText,
            isFinal = isFinal,
            confidence = 0.95f
        )

        onTranscriptUI?.invoke(transcriptResult, isSpeech)

        if (isFinal && cleanText.isNotEmpty() && registeredCommands.isNotEmpty()) {
            val matchResult = stt.matchIntent(cleanText, registeredCommands, thresholdPercent = 80)
            if (matchResult != null) {
                Log.i(TAG, "Intent Matched: ${matchResult.command.phrase} (score: ${matchResult.score})")
                onIntentMatch?.invoke(matchResult)
            }
        }
    }

    /**
     * Speak System Response
     */
    fun respond(text: String, onComplete: (() -> Unit)? = null) {
        tts.speak(text, onComplete)
    }

    /**
     * Stop All Hardware & Systems
     */
    fun stopSystem() {
        isSystemActive = false
        dspObservationJob?.cancel()
        dspObservationJob = null

        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
            speechRecognizer = null
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping speech recognizer: ${e.message}")
        }

        dsp.stopMicrophone()
        tts.stop()
        Log.i(TAG, "Myanmar Voice System Orchestrator stopped.")
    }
}
