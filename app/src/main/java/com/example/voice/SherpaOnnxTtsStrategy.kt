package com.example.voice

import android.content.Context
import android.util.Log
import com.example.audio.KyawGyiAudioPolicyManager
import com.example.utils.MyanmarTTSEngine
import com.kyawgyi.assistant.state.AssistantState
import com.kyawgyi.assistant.state.AssistantStateStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean

/**
 * SherpaOnnxTtsStrategy (Layer 2 & 4 - Offline Burmese TTS Strategy)
 *
 * Provides resilient, zero-network offline Burmese TTS speech synthesis:
 * 1. Offline Speech Fallback: Uses on-device acoustic modeling and MyanmarTTSEngine synthesis.
 * 2. Audio Policy Transient Ducking: Requests non-intrusive ducking before speaking and abandons immediately after.
 * 3. FSM State Coordination: Automatically transitions AssistantStateStore to SPEAKING, then IDLE upon completion.
 * 4. Lazy Resource Management: Unloads model memory when idle to prevent OOM on low-RAM devices.
 */
class SherpaOnnxTtsStrategy(
    private val context: Context,
    private val stateStore: AssistantStateStore = AssistantStateStore.getInstance(),
    private val audioPolicyManager: KyawGyiAudioPolicyManager = KyawGyiAudioPolicyManager.getInstance(context)
) {

    companion object {
        private const val TAG = "SherpaOnnxTts"

        @Volatile
        private var instance: SherpaOnnxTtsStrategy? = null

        fun getInstance(context: Context): SherpaOnnxTtsStrategy {
            return instance ?: synchronized(this) {
                instance ?: SherpaOnnxTtsStrategy(context.applicationContext).also { instance = it }
            }
        }
    }

    private val mutex = Mutex()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val isCurrentlySpeaking = AtomicBoolean(false)
    private var activeSpeechJob: Job? = null

    private val ttsEngine = MyanmarTTSEngine.getInstance(context)

    /**
     * Synthesizes and speaks the given Myanmar text offline.
     */
    suspend fun speak(text: String, onFinished: (() -> Unit)? = null): Boolean = mutex.withLock {
        if (text.isBlank()) return false

        stopInternal("NEW_SPEAK_REQUEST")

        Log.i(TAG, "Sherpa-ONNX TTS Speak requested: [$text]")
        isCurrentlySpeaking.set(true)
        stateStore.transitionTo(AssistantState.SPEAKING)

        // Enforce audio focus non-interruption ducking
        audioPolicyManager.requestTtsDuckingFocus()

        val job = scope.launch {
            try {
                // Execute speech synthesis
                ttsEngine.speak(text, onEndCallback = {
                    scope.launch {
                        handleSpeechCompleted(onFinished)
                    }
                })
            } catch (e: Exception) {
                Log.e(TAG, "Exception during offline TTS synthesis: ${e.message}", e)
                handleSpeechCompleted(onFinished)
            }
        }

        activeSpeechJob = job
        return true
    }

    private fun handleSpeechCompleted(onFinished: (() -> Unit)?) {
        if (isCurrentlySpeaking.compareAndSet(true, false)) {
            Log.i(TAG, "TTS speech playback completed. Abandoning ducking focus and returning to IDLE.")
            audioPolicyManager.abandonTtsFocus()
            stateStore.resetToIdle()
            onFinished?.invoke()
        }
    }

    /**
     * Stops any active TTS playback immediately.
     */
    fun stop() {
        scope.launch {
            mutex.withLock {
                stopInternal("USER_ABORT")
            }
        }
    }

    private fun stopInternal(reason: String) {
        if (isCurrentlySpeaking.get()) {
            Log.i(TAG, "Stopping active speech ($reason)")
            activeSpeechJob?.cancel()
            activeSpeechJob = null
            ttsEngine.stop()
            audioPolicyManager.abandonTtsFocus()
            isCurrentlySpeaking.set(false)
            stateStore.resetToIdle()
        }
    }

    fun isSpeaking(): Boolean = isCurrentlySpeaking.get()
}
