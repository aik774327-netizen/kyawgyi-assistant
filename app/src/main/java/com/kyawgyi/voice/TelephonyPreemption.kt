package com.kyawgyi.voice

import android.content.Context
import android.media.AudioManager
import android.telephony.TelephonyManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Snapshot of cellular / VoIP call preemption state.
 */
data class TelephonyPreemptionState(
    val callState: Int = TelephonyManager.CALL_STATE_IDLE,
    val isInCallOrRinging: Boolean = false,
    val wasListeningBeforeCall: Boolean = false,
    val statusLabel: String = "IDLE (No Active Call)"
)

/**
 * TelephonyPreemption (Requirements 12 — Cellular & VoIP Call Audio Preemption Controller)
 *
 * Ensures that incoming or active phone calls (`CALL_STATE_RINGING`, `CALL_STATE_OFFHOOK`,
 * or `AudioManager.MODE_IN_CALL`) immediately preempt KyawGyi AI's microphone capture and TTS
 * playback so cellular calls never experience microphone conflict or assistant voice overlap.
 *
 * Automatically restores listening state when the call returns to `CALL_STATE_IDLE`.
 */
class TelephonyPreemption(
    context: Context,
    private val onPreemptPause: (() -> Unit)? = null,
    private val onPreemptResume: (() -> Unit)? = null
) {

    companion object {
        private const val TAG = "TelephonyPreemption"
    }

    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private val _state = MutableStateFlow(TelephonyPreemptionState())
    val state: StateFlow<TelephonyPreemptionState> = _state.asStateFlow()

    /**
     * Checks current [AudioManager.getMode] to detect active cellular or VoIP calls without
     * requiring `READ_PHONE_STATE` runtime permission.
     */
    fun checkSystemCallMode(): TelephonyPreemptionState {
        val mode = runCatching { audioManager?.mode ?: AudioManager.MODE_NORMAL }
            .getOrDefault(AudioManager.MODE_NORMAL)

        val callActive = mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_RINGTONE
        val mappedCallState = when (mode) {
            AudioManager.MODE_RINGTONE -> TelephonyManager.CALL_STATE_RINGING
            AudioManager.MODE_IN_CALL -> TelephonyManager.CALL_STATE_OFFHOOK
            else -> TelephonyManager.CALL_STATE_IDLE
        }
        return onCallStateChanged(mappedCallState, isCurrentlyListening = _state.value.wasListeningBeforeCall)
    }

    /**
     * Handles a transition in [TelephonyManager] call state (`CALL_STATE_IDLE`,
     * `CALL_STATE_RINGING`, `CALL_STATE_OFFHOOK`).
     */
    @Synchronized
    fun onCallStateChanged(
        newCallState: Int,
        isCurrentlyListening: Boolean = false
    ): TelephonyPreemptionState {
        val prev = _state.value
        val isCallActive = newCallState == TelephonyManager.CALL_STATE_RINGING ||
            newCallState == TelephonyManager.CALL_STATE_OFFHOOK

        val rememberListening = when {
            isCallActive && !prev.isInCallOrRinging -> isCurrentlyListening
            isCallActive -> prev.wasListeningBeforeCall
            else -> false
        }

        val label = when (newCallState) {
            TelephonyManager.CALL_STATE_RINGING -> "RINGING (Mic & TTS Preempted)"
            TelephonyManager.CALL_STATE_OFFHOOK -> "IN_CALL (Mic & TTS Preempted)"
            else -> "IDLE (No Active Call)"
        }

        val updated = TelephonyPreemptionState(
            callState = newCallState,
            isInCallOrRinging = isCallActive,
            wasListeningBeforeCall = rememberListening,
            statusLabel = label
        )
        _state.update { updated }

        if (isCallActive && !prev.isInCallOrRinging) {
            Log.i(TAG, "Telephony call active ($label) -> Preempting Mic & TTS.")
            onPreemptPause?.invoke()
        } else if (!isCallActive && prev.isInCallOrRinging) {
            Log.i(TAG, "Telephony call ended -> Restoring voice pipeline (resume=${prev.wasListeningBeforeCall}).")
            if (prev.wasListeningBeforeCall) {
                onPreemptResume?.invoke()
            }
        }

        return updated
    }
}
