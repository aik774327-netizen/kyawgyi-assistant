package com.kyawgyi.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Snapshot of [AudioDuckingManager] state.
 */
data class AudioDuckingState(
    val isDuckingActive: Boolean = false,
    val lastFocusResult: Int = AudioManager.AUDIOFOCUS_REQUEST_FAILED,
    val duckCount: Int = 0
)

/**
 * AudioDuckingManager (Requirements 10 — Smooth Transient Audio Focus Ducking Controller)
 *
 * Requests `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK` when KyawGyi AI starts listening or speaking
 * short responses so background media players (music, podcasts, YouTube) smoothly lower their
 * volume ("duck") instead of permanently losing playback state, and restores volume via
 * `abandonAudioFocusRequest` as soon as the voice turn completes.
 */
class AudioDuckingManager(context: Context) {

    companion object {
        private const val TAG = "AudioDuckingManager"
    }

    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private val _state = MutableStateFlow(AudioDuckingState())
    val state: StateFlow<AudioDuckingState> = _state.asStateFlow()

    private var activeFocusRequest: AudioFocusRequest? = null

    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                _state.update { it.copy(isDuckingActive = false) }
            }
        }
    }

    /**
     * Requests transient audio ducking (`AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`) from the Android OS.
     */
    @Synchronized
    fun requestTransientDucking(): Boolean {
        val am = audioManager
        if (am == null) {
            _state.update {
                it.copy(
                    isDuckingActive = true,
                    lastFocusResult = AudioManager.AUDIOFOCUS_REQUEST_GRANTED,
                    duckCount = it.duckCount + 1
                )
            }
            return true
        }

        val result = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANT)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setWillPauseWhenDucked(false)
                    .setOnAudioFocusChangeListener(focusChangeListener)
                    .build()
                activeFocusRequest = req
                am.requestAudioFocus(req)
            } else {
                @Suppress("DEPRECATION")
                am.requestAudioFocus(
                    focusChangeListener,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                )
            }
        }.onFailure { e ->
            Log.w(TAG, "Audio ducking focus request failed: ${e.message}")
        }.getOrDefault(AudioManager.AUDIOFOCUS_REQUEST_FAILED)

        val granted = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        _state.update { prev ->
            prev.copy(
                isDuckingActive = granted,
                lastFocusResult = result,
                duckCount = if (granted) prev.duckCount + 1 else prev.duckCount
            )
        }
        return granted
    }

    /**
     * Releases transient ducking focus so background music returns to 100% volume.
     */
    @Synchronized
    fun releaseDucking() {
        val am = audioManager
        if (am != null) {
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    activeFocusRequest?.let { am.abandonAudioFocusRequest(it) }
                } else {
                    @Suppress("DEPRECATION")
                    am.abandonAudioFocus(focusChangeListener)
                }
            }
        }
        activeFocusRequest = null
        _state.update { it.copy(isDuckingActive = false) }
    }
}
