package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

/**
 * KYAW GYI AI — Single Source of Truth Audio Policy Manager (Stage 19 Production Hardened)
 *
 * Core Non-Interruption Invariant:
 * If an external Music App (YouTube, Spotify, Apple Music, Local Player) is playing,
 * KYAW GYI AI microphone ON/OFF operations and Wake-word listening MUST NOT intentionally pause,
 * stop, duck, restart, or seek music playback.
 *
 * Audio Focus Matrix:
 * - WAKE_WORD_STANDBY / ARMED_LISTENING: NO_AUDIO_FOCUS_REQUEST (Passive Input Only)
 * - COMMAND_LISTENING (VAD / STT): NO_AUDIO_FOCUS_REQUEST (Passive Input Only)
 * - TTS_SPEAKING: AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK (Temporary non-intrusive ducking, immediately abandoned on completion)
 * - MEDIA_COMMANDS (explicit user action): Dedicated MediaSession action only.
 */
class KyawGyiAudioPolicyManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "KyawGyiAudioPolicy"

        @Volatile
        private var instance: KyawGyiAudioPolicyManager? = null

        fun getInstance(context: Context): KyawGyiAudioPolicyManager {
            return instance ?: synchronized(this) {
                instance ?: KyawGyiAudioPolicyManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private val _isMusicActiveState = MutableStateFlow(false)
    val isMusicActiveState: StateFlow<Boolean> = _isMusicActiveState.asStateFlow()

    private val _hasTtsFocus = MutableStateFlow(false)
    val hasTtsFocus: StateFlow<Boolean> = _hasTtsFocus.asStateFlow()

    private var focusRequest: AudioFocusRequest? = null
    private val isAudioFocusRequested = AtomicBoolean(false)
    private val audioFocusListener = AudioManagerFocusListener.getInstance(context)

    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                Log.d(TAG, "Audio focus lost completely.")
                _hasTtsFocus.value = false
                isAudioFocusRequested.set(false)
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                Log.d(TAG, "Audio focus lost transiently.")
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                Log.d(TAG, "Audio focus ducked by system.")
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                Log.d(TAG, "Audio focus regained.")
                _hasTtsFocus.value = true
            }
        }
        // Delegate to central AudioManagerFocusListener for CentralAudioRecorder pause & re-sync
        audioFocusListener.onAudioFocusChange(focusChange)
    }

    /**
     * Check whether external media is actively playing
     */
    fun isExternalMusicActive(): Boolean {
        val active = audioManager?.isMusicActive ?: false
        _isMusicActiveState.value = active
        return active
    }

    /**
     * Passive Wake-Word / STT Listening Audio Focus Policy
     * MUST NOT request audio focus, ensuring external media continues unaffected.
     */
    fun onMicrophoneCaptureStart(reason: String) {
        val musicActive = isExternalMusicActive()
        AudioDiagnostics.logFocusAction(
            action = "SKIPPED_AUDIO_FOCUS_REQUEST",
            reason = "PASSIVE_MIC_CAPTURE ($reason)",
            musicActive = musicActive
        )
        AudioDiagnostics.logPolicyDecision(
            decision = "ALLOW_PARALLEL_MIC_AND_MUSIC",
            invariantEnforced = true,
            musicActive = musicActive
        )
    }

    fun onMicrophoneCaptureStop(reason: String) {
        val musicActive = isExternalMusicActive()
        AudioDiagnostics.logFocusAction(
            action = "NO_FOCUS_ABANDON_NEEDED",
            reason = "MIC_STOPPED ($reason)",
            musicActive = musicActive
        )
    }

    /**
     * Request Transient Ducking Audio Focus ONLY when Kyaw Gyi speaks TTS feedback.
     * Uses AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK so external music lowers volume gracefully without pausing.
     */
    @Synchronized
    fun requestTtsDuckingFocus(): Boolean {
        val am = audioManager ?: return false
        val musicActive = isExternalMusicActive()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val playbackAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()

                val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(playbackAttributes)
                    .setAcceptsDelayedFocusGain(false)
                    .setWillPauseWhenDucked(false)
                    .setOnAudioFocusChangeListener(focusChangeListener)
                    .build()

                focusRequest = request
                val result = am.requestAudioFocus(request)
                val granted = (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
                _hasTtsFocus.value = granted
                isAudioFocusRequested.set(granted)

                AudioDiagnostics.logFocusAction(
                    action = "REQUEST_TRANSIENT_MAY_DUCK",
                    reason = "TTS_OUTPUT",
                    musicActive = musicActive,
                    granted = granted
                )
                return granted
            } else {
                @Suppress("DEPRECATION")
                val result = am.requestAudioFocus(
                    focusChangeListener,
                    AudioManager.STREAM_MUSIC, // Avoid STREAM_VOICE_CALL to prevent device call routing changes
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                )
                val granted = (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
                _hasTtsFocus.value = granted
                isAudioFocusRequested.set(granted)

                AudioDiagnostics.logFocusAction(
                    action = "REQUEST_LEGACY_MAY_DUCK",
                    reason = "TTS_OUTPUT",
                    musicActive = musicActive,
                    granted = granted
                )
                return granted
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting TTS ducking focus: ${e.localizedMessage}")
            return false
        }
    }

    /**
     * Abandon Audio Focus immediately after TTS speech completes.
     * External background music immediately restores normal volume.
     */
    @Synchronized
    fun abandonTtsFocus() {
        val am = audioManager ?: return
        val musicActive = isExternalMusicActive()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                focusRequest?.let {
                    am.abandonAudioFocusRequest(it)
                    focusRequest = null
                }
            } else {
                @Suppress("DEPRECATION")
                am.abandonAudioFocus(focusChangeListener)
            }
            _hasTtsFocus.value = false
            isAudioFocusRequested.set(false)

            AudioDiagnostics.logFocusAction(
                action = "ABANDONED_FOCUS",
                reason = "TTS_FINISHED",
                musicActive = musicActive
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error abandoning TTS audio focus: ${e.localizedMessage}")
        }
    }

    /**
     * Invariant check: Ensure audio mode was never changed to communication/call mode
     */
    fun verifyAudioModeSafety() {
        val mode = audioManager?.mode ?: AudioManager.MODE_NORMAL
        if (mode == AudioManager.MODE_IN_COMMUNICATION || mode == AudioManager.MODE_IN_CALL) {
            Log.w(TAG, "Audio mode was in communication/call mode ($mode). Restoring to MODE_NORMAL.")
            try {
                audioManager?.mode = AudioManager.MODE_NORMAL
            } catch (e: Exception) {
                Log.e(TAG, "Failed to restore audio mode: ${e.localizedMessage}")
            }
        }
    }
}
