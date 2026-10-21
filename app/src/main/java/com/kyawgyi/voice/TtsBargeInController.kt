package com.kyawgyi.voice

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Snapshot of [TtsBargeInController] state.
 */
data class TtsBargeInState(
    val isBargeInEnabled: Boolean = true,
    val consecutiveHighEnergySpeechFrames: Int = 0,
    val totalBargeInInterruptions: Int = 0,
    val lastBargeInTimestampMs: Long = 0L
)

/**
 * TtsBargeInController (Requirements 10 — Real-Time User Speech Barge-In / TTS Interruption Controller)
 *
 * Allows the user to naturally interrupt ("barge in" on) KyawGyi AI while it is speaking a long
 * TTS response:
 * - Monitors near-field microphone RMS energy above [bargeInRmsThreshold] (which is set significantly
 *   higher than residual acoustic echo after hardware AEC).
 * - When [requiredConsecutiveSpeechFrames] consecutive speech frames exceed the barge-in threshold
 *   (or when the user taps the mic / triggers the wake-word), immediately stops TTS playback and
 *   switches the assistant back to `LISTENING`.
 */
class TtsBargeInController(
    val bargeInRmsThreshold: Double = 3200.0,
    val requiredConsecutiveSpeechFrames: Int = 3
) {

    companion object {
        private const val TAG = "TtsBargeInController"
    }

    private val _state = MutableStateFlow(TtsBargeInState())
    val state: StateFlow<TtsBargeInState> = _state.asStateFlow()

    /**
     * Evaluates an incoming audio frame while TTS is speaking.
     * Returns `true` if user barge-in interruption is triggered.
     */
    @Synchronized
    fun evaluateFrameDuringTts(
        frameRmsEnergy: Double,
        isSpeechLikeZcr: Boolean = true,
        onInterruptTts: (() -> Unit)? = null
    ): Boolean {
        if (!_state.value.isBargeInEnabled) return false

        if (frameRmsEnergy >= bargeInRmsThreshold && isSpeechLikeZcr) {
            val newStreak = _state.value.consecutiveHighEnergySpeechFrames + 1
            if (newStreak >= requiredConsecutiveSpeechFrames) {
                val now = System.currentTimeMillis()
                _state.update { prev ->
                    prev.copy(
                        consecutiveHighEnergySpeechFrames = 0,
                        totalBargeInInterruptions = prev.totalBargeInInterruptions + 1,
                        lastBargeInTimestampMs = now
                    )
                }
                Log.i(TAG, "User Barge-In detected (RMS=${"%.0f".format(frameRmsEnergy)}) -> Interrupting TTS playback!")
                onInterruptTts?.invoke()
                return true
            } else {
                _state.update { it.copy(consecutiveHighEnergySpeechFrames = newStreak) }
            }
        } else {
            if (_state.value.consecutiveHighEnergySpeechFrames > 0) {
                _state.update { it.copy(consecutiveHighEnergySpeechFrames = 0) }
            }
        }
        return false
    }

    /**
     * Explicitly triggers an immediate barge-in interruption (e.g., Wake-Word or Mic Tap during TTS).
     */
    @Synchronized
    fun triggerManualBargeIn(onInterruptTts: (() -> Unit)? = null) {
        val now = System.currentTimeMillis()
        _state.update { prev ->
            prev.copy(
                consecutiveHighEnergySpeechFrames = 0,
                totalBargeInInterruptions = prev.totalBargeInInterruptions + 1,
                lastBargeInTimestampMs = now
            )
        }
        onInterruptTts?.invoke()
    }

    @Synchronized
    fun setBargeInEnabled(enabled: Boolean) {
        _state.update { it.copy(isBargeInEnabled = enabled, consecutiveHighEnergySpeechFrames = 0) }
    }
}
