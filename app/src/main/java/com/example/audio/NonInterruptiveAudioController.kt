package com.example.audio

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.StateFlow

/**
 * KYAW GYI AI — Non-Interruptive Audio Controller (Stage 19 Production Hardened)
 *
 * Enforces the Non-Interruption Contract for Android Music Playback:
 * "If external Music App is playing, KYAW GYI microphone ON/OFF operations
 *  MUST NOT intentionally pause, stop, duck, restart, or seek music playback."
 */
class NonInterruptiveAudioController private constructor(private val context: Context) {

    companion object {
        private const val TAG = "NonInterruptAudioCtrl"

        @Volatile
        private var instance: NonInterruptiveAudioController? = null

        fun getInstance(context: Context): NonInterruptiveAudioController {
            return instance ?: synchronized(this) {
                instance ?: NonInterruptiveAudioController(context.applicationContext).also { instance = it }
            }
        }
    }

    private val policyManager = KyawGyiAudioPolicyManager.getInstance(context)
    private val micController = MicrophoneLifecycleController.getInstance(context)

    val isMusicPlayingState: StateFlow<Boolean> = policyManager.isMusicActiveState

    /**
     * Check if background music is active.
     */
    fun isMusicPlaying(): Boolean = policyManager.isExternalMusicActive()

    /**
     * Execute a voice action while guaranteeing music non-interruption.
     */
    fun onWakeWordEngaged() {
        val musicActive = isMusicPlaying()
        AudioDiagnostics.logEvent(
            AudioDiagnostics.CAT_VOICE,
            mapOf(
                "action" to "WAKE_WORD_ARMED",
                "musicPlayback" to if (musicActive) "PLAYING" else "IDLE",
                "interruptionProtection" to "ACTIVE"
            )
        )
    }

    /**
     * Called before TTS audio feedback.
     */
    fun prepareForTtsSpeech(): Boolean {
        policyManager.verifyAudioModeSafety()
        return policyManager.requestTtsDuckingFocus()
    }

    /**
     * Called immediately when TTS audio completes.
     */
    fun onTtsSpeechCompleted() {
        policyManager.abandonTtsFocus()
        policyManager.verifyAudioModeSafety()
    }

    /**
     * Called when a command listening session finishes.
     */
    fun onCommandSessionFinished() {
        policyManager.verifyAudioModeSafety()
        AudioDiagnostics.logEvent(
            AudioDiagnostics.CAT_VOICE,
            mapOf(
                "action" to "COMMAND_SESSION_FINISHED",
                "interruptionProtection" to "ACTIVE"
            )
        )
    }
}
