package com.kyawgyi.voice

import android.content.Context
import android.util.Log
import com.example.audio.CentralAudioRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean

/**
 * CentralAudioRecorderBridge (Layer 1 ➔ Layer 2 Integration Bridge)
 *
 * Streams raw 16kHz 16-bit Mono PCM audio from CentralAudioRecorder (Layer 1)
 * directly into SttEngineManager's active engine (Layer 2).
 *
 * Critical AEC & Acoustic Guard:
 * When AudioFocusAndAecManager.isSpeaking is TRUE, incoming microphone frames
 * are dropped or suppressed to prevent the TTS speech output from being fed back
 * into the Speech-to-Text recognizer (Acoustic Self-Listening Prevention).
 */
class CentralAudioRecorderBridge(
    private val context: Context,
    private val centralAudioRecorder: CentralAudioRecorder = CentralAudioRecorder.getInstance(context),
    private val sttEngineManager: SttEngineManager = SttEngineManager.getInstance(context),
    private val audioFocusAndAecManager: AudioFocusAndAecManager = AudioFocusAndAecManager.getInstance(context),
    val vadDetector: KyawGyiVadDetector = KyawGyiVadDetector()
) {

    companion object {
        private const val TAG = "CentralAudioBridge"
        private const val BRIDGE_TAG = "KyawGyi_SttBridge"

        @Volatile
        private var instance: CentralAudioRecorderBridge? = null

        fun getInstance(context: Context): CentralAudioRecorderBridge {
            return instance ?: synchronized(this) {
                instance ?: CentralAudioRecorderBridge(context.applicationContext).also { instance = it }
            }
        }
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mutex = Mutex()
    private val isBridgeActive = AtomicBoolean(false)
    private var streamJob: Job? = null

    init {
        // Wire VAD callbacks
        vadDetector.onSpeechStarted = {
            Log.d(TAG, "VAD: User speech onset detected -> Transitioning STT to LISTENING")
            sttEngineManager.updateEngineState(SttEngineState.LISTENING)
        }
        vadDetector.onSpeechEndpoint = {
            Log.i(TAG, "VAD: User speech endpoint detected -> Finalizing utterance")
            sttEngineManager.finalizeCurrentUtterance()
        }
    }

    /**
     * Starts audio routing from CentralAudioRecorder to active SttEngine.
     */
    suspend fun startBridge(): Boolean = mutex.withLock {
        if (isBridgeActive.get()) {
            Log.d(TAG, "Audio recorder bridge is already active.")
            return@withLock true
        }

        Log.i(TAG, "Starting CentralAudioRecorderBridge...")

        // 1. Ensure hardware audio recording is initiated in Layer 1
        val recordStarted = centralAudioRecorder.startRecording(BRIDGE_TAG)
        if (!recordStarted) {
            Log.w(TAG, "CentralAudioRecorder could not record (e.g., test environment or permission missing). Proceeding with bridge.")
        }

        // 2. Ensure SttEngineManager & VAD detector are initialized
        vadDetector.startSession()
        sttEngineManager.startListening()

        // 3. Launch thread-safe stream collector
        streamJob = scope.launch {
            try {
                centralAudioRecorder.audioSharedFlow.collect { pcmChunk ->
                    if (!isBridgeActive.get()) return@collect

                    // Layer 1 AEC / TTS Self-Feedback Guard & Post-TTS Echo Hangover
                    if (audioFocusAndAecManager.isSpeaking ||
                        audioFocusAndAecManager.isMicPaused ||
                        audioFocusAndAecManager.isEchoHangoverActive
                    ) {
                        // Drop frame during active TTS speech playback & room echo hangover period
                        // Prevents assistant from transcribing its own synthesized voice
                        return@collect
                    }

                    // Process frame through Layer 1 VAD detector for onset and endpoint analysis
                    vadDetector.processPcmChunk(pcmChunk)

                    // Forward valid speech PCM chunk to active Layer 2 STT Engine
                    sttEngineManager.feedPcmChunk(pcmChunk)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception in audio stream collector: ${e.message}", e)
            }
        }

        isBridgeActive.set(true)
        Log.i(TAG, "CentralAudioRecorderBridge successfully engaged.")
        return@withLock true
    }

    /**
     * Safely stops the audio bridge and unregisters stream collection.
     */
    suspend fun stopBridge() = mutex.withLock {
        if (!isBridgeActive.getAndSet(false)) {
            return@withLock
        }

        Log.i(TAG, "Stopping CentralAudioRecorderBridge...")
        streamJob?.cancel()
        streamJob = null

        // Stop recorder session in Layer 1
        centralAudioRecorder.stopRecording(BRIDGE_TAG)

        // Reset VAD detector & stop STT listener in Layer 2
        vadDetector.reset()
        sttEngineManager.stopListening()

        Log.i(TAG, "CentralAudioRecorderBridge stopped.")
    }

    fun isBridging(): Boolean = isBridgeActive.get()

    fun release() {
        scope.launch {
            stopBridge()
            scope.coroutineContext.cancelChildren()
        }
    }
}
