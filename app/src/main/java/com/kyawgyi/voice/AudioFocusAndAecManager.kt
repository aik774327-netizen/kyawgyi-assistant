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
import java.util.concurrent.atomic.AtomicBoolean

/**
 * AudioFocusAndAecManager (Layer 1 Audio Focus & Acoustic Echo Prevention)
 *
 * Coordinates system audio focus and Acoustic Echo Cancellation (AEC):
 * 1. Requests TRANSIENT_MAY_DUCK audio focus during TTS playback to preserve background media.
 * 2. Provides real-time `isSpeaking` flag and StateFlow to indicate active speech playback.
 * 3. Signals microphone capture pipelines to mute or drop incoming frames during TTS output,
 *    preventing self-listening feedback loops.
 */
class AudioFocusAndAecManager(private val context: Context) {

    companion object {
        private const val TAG = "AudioFocusAndAecManager"
        const val POST_TTS_ECHO_HANGOVER_MS = 250L

        @Volatile
        private var instance: AudioFocusAndAecManager? = null

        fun getInstance(context: Context): AudioFocusAndAecManager {
            return instance ?: synchronized(this) {
                instance ?: AudioFocusAndAecManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeakingFlow: StateFlow<Boolean> = _isSpeaking.asStateFlow()
    val isSpeaking: Boolean get() = _isSpeaking.value

    private val _isMicPaused = MutableStateFlow(false)
    val isMicPausedFlow: StateFlow<Boolean> = _isMicPaused.asStateFlow()
    val isMicPaused: Boolean get() = _isMicPaused.value

    @Volatile
    private var lastTtsStoppedTimestamp = 0L

    @Volatile
    private var configuredEchoHangoverMs: Long = POST_TTS_ECHO_HANGOVER_MS

    fun setEchoHangoverDurationMs(durationMs: Long) {
        configuredEchoHangoverMs = durationMs.coerceIn(150L, 600L)
        Log.i(TAG, "Configured post-TTS echo hangover duration: ${configuredEchoHangoverMs}ms")
    }

    fun getEchoHangoverDurationMs(): Long = configuredEchoHangoverMs

    val isEchoHangoverActive: Boolean
        get() = (System.currentTimeMillis() - lastTtsStoppedTimestamp) < configuredEchoHangoverMs

    private var audioFocusRequest: AudioFocusRequest? = null

    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                Log.w(TAG, "Audio focus lost permanently.")
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                Log.d(TAG, "Audio focus lost transiently.")
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                Log.d(TAG, "Audio focus gained.")
            }
        }
    }

    /**
     * Requests Transient Ducking audio focus before TTS playback begins.
     */
    fun requestDuckingFocus(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val playbackAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()

                val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(playbackAttributes)
                    .setAcceptsDelayedFocusGain(false)
                    .setOnAudioFocusChangeListener(focusChangeListener)
                    .build()

                audioFocusRequest = request
                val res = audioManager.requestAudioFocus(request)
                res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            } else {
                @Suppress("DEPRECATION")
                val res = audioManager.requestAudioFocus(
                    focusChangeListener,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                )
                res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting audio focus: ${e.message}", e)
            false
        }
    }

    /**
     * Releases audio focus after TTS playback completes.
     */
    fun abandonFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(focusChangeListener)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error abandoning audio focus: ${e.message}")
        }
    }

    /**
     * Call when TTS output starts playing.
     */
    fun onTtsPlaybackStarted() {
        Log.i(TAG, "TTS playback started -> Activating AEC self-listening guard.")
        _isSpeaking.value = true
        _isMicPaused.value = true
        requestDuckingFocus()
    }

    /**
     * Call when TTS output stops or completes.
     */
    fun onTtsPlaybackStopped() {
        Log.i(TAG, "TTS playback stopped -> Deactivating AEC self-listening guard.")
        lastTtsStoppedTimestamp = System.currentTimeMillis()
        _isSpeaking.value = false
        _isMicPaused.value = false
        abandonFocus()
    }

    /**
     * Resets echo hangover and speaking state immediately for tests.
     */
    fun resetForTesting() {
        lastTtsStoppedTimestamp = 0L
        _isSpeaking.value = false
        _isMicPaused.value = false
        configuredEchoHangoverMs = POST_TTS_ECHO_HANGOVER_MS
    }

    fun setMicPaused(paused: Boolean) {
        _isMicPaused.value = paused
    }

    data class HardwareDspStatus(
        val audioSessionId: Int,
        val aecEnabled: Boolean,
        val nsEnabled: Boolean,
        val agcEnabled: Boolean
    )

    @Volatile
    private var activeAecEffect: android.media.audiofx.AcousticEchoCanceler? = null
    @Volatile
    private var activeNsEffect: android.media.audiofx.NoiseSuppressor? = null
    @Volatile
    private var activeAgcEffect: android.media.audiofx.AutomaticGainControl? = null

    /**
     * Thread-safe attachment of hardware AcousticEchoCanceler, NoiseSuppressor, and AutomaticGainControl
     * to an active AudioRecord session, releasing any previous native DSP handles first to prevent leaks.
     */
    @Synchronized
    fun attachHardwareDspEffects(audioSessionId: Int): HardwareDspStatus {
        releaseHardwareDspEffects()
        if (audioSessionId <= 0) {
            return HardwareDspStatus(audioSessionId, aecEnabled = false, nsEnabled = false, agcEnabled = false)
        }
        var aecOk = false
        var nsOk = false
        var agcOk = false
        try {
            if (android.media.audiofx.AcousticEchoCanceler.isAvailable()) {
                activeAecEffect = android.media.audiofx.AcousticEchoCanceler.create(audioSessionId)?.apply {
                    enabled = true
                    aecOk = enabled
                }
            }
            if (android.media.audiofx.NoiseSuppressor.isAvailable()) {
                activeNsEffect = android.media.audiofx.NoiseSuppressor.create(audioSessionId)?.apply {
                    enabled = true
                    nsOk = enabled
                }
            }
            if (android.media.audiofx.AutomaticGainControl.isAvailable()) {
                activeAgcEffect = android.media.audiofx.AutomaticGainControl.create(audioSessionId)?.apply {
                    enabled = true
                    agcOk = enabled
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Hardware DSP effect binding warning: ${e.message}")
        }
        return HardwareDspStatus(audioSessionId, aecEnabled = aecOk, nsEnabled = nsOk, agcEnabled = agcOk)
    }

    @Synchronized
    fun releaseHardwareDspEffects() {
        runCatching { activeAecEffect?.release() }
        runCatching { activeNsEffect?.release() }
        runCatching { activeAgcEffect?.release() }
        activeAecEffect = null
        activeNsEffect = null
        activeAgcEffect = null
    }

    /**
     * Requests exclusive microphone capture focus and attaches hardware AEC if available.
     */
    fun startCapture(audioSessionId: Int? = null): Boolean {
        val granted = if (Build.VERSION.SDK_INT >= 26) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setOnAudioFocusChangeListener(focusChangeListener)
                .build()
            audioFocusRequest = request
            audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                focusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
        if (granted && audioSessionId != null && audioSessionId > 0) {
            attachHardwareDspEffects(audioSessionId)
        }
        return granted
    }

    fun stopCapture() {
        releaseHardwareDspEffects()
        abandonFocus()
    }

    /** Called before TTS. Focus transiently shifts away from microphone capture. */
    fun beginTts(): Boolean {
        onTtsPlaybackStarted()
        return true
    }

    fun endTts() {
        onTtsPlaybackStopped()
    }
}
