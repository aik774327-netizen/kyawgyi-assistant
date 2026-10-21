package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Interface that any audio stream, synthesizer, or player component in the app
 * can implement and register with [GlobalAudioStateManager] to receive instantaneous
 * pause and stop commands when the 'Kyaw Gyi' wake word is detected.
 */
interface AudioStreamController {
    fun getStreamIdentifier(): String
    fun isPlaying(): Boolean
    fun pauseAudio(): Boolean
    fun stopAudio(): Boolean
    fun resumeAudio(): Boolean { return false }
}

/**
 * Global snapshot of the application's audio playback and interruption state.
 */
data class GlobalAudioState(
    val isAnyMediaPlaying: Boolean = false,
    val isAudioPausedByWakeWord: Boolean = false,
    val lastInterruptionTimestamp: Long = 0L,
    val lastInterruptionReason: String = "",
    val activeStreamCount: Int = 0,
    val pausedStreamIdentifiers: List<String> = emptyList()
)

/**
 * GlobalAudioStateManager
 *
 * Provides a unified, thread-safe interface to instantly pause or stop any active media playback
 * or audio streams across the application whenever the 'Kyaw Gyi' wake word is detected.
 *
 * Responsibilities:
 * 1. Tracks registered in-app audio streams (KyawGyiMusicPlayer, custom synthesizers, voice playback).
 * 2. On 'Kyaw Gyi' wake-word detection:
 *    - Immediately halts/pauses internal player playback (`KyawGyiMusicPlayer`).
 *    - Instructs all registered [AudioStreamController] instances to pause/stop immediately.
 *    - Dispatches system-level media pause events to any external audio streams if active.
 * 3. Exposes an observable [GlobalAudioState] for UI feedback and telemetry.
 */
class GlobalAudioStateManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "GlobalAudioStateManager"

        @Volatile
        private var instance: GlobalAudioStateManager? = null

        fun getInstance(context: Context): GlobalAudioStateManager {
            return instance ?: synchronized(this) {
                instance ?: GlobalAudioStateManager(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private val registeredControllers = CopyOnWriteArraySet<AudioStreamController>()
    private val pausedStreamsCache = CopyOnWriteArraySet<String>()

    private val _audioState = MutableStateFlow(GlobalAudioState())
    val audioState: StateFlow<GlobalAudioState> = _audioState.asStateFlow()

    private val isInterruptedByWakeWord = AtomicBoolean(false)

    init {
        // Automatically register the primary in-app music player as an AudioStreamController
        registerInAppMusicPlayer()
    }

    private fun registerInAppMusicPlayer() {
        val musicPlayer = KyawGyiMusicPlayer.getInstance(context)
        val playerController = object : AudioStreamController {
            override fun getStreamIdentifier(): String = "KyawGyiMusicPlayer"
            override fun isPlaying(): Boolean = musicPlayer.playerState.value.isPlaying
            override fun pauseAudio(): Boolean {
                musicPlayer.pause()
                return true
            }
            override fun stopAudio(): Boolean {
                musicPlayer.stop()
                return true
            }
            override fun resumeAudio(): Boolean {
                musicPlayer.resume()
                return true
            }
        }
        registerAudioStream(playerController)
    }

    /**
     * Register an active audio stream or player component with this manager.
     */
    fun registerAudioStream(controller: AudioStreamController) {
        registeredControllers.add(controller)
        updatePlaybackState()
        Log.d(TAG, "AudioStream registered: ${controller.getStreamIdentifier()}. Total: ${registeredControllers.size}")
    }

    /**
     * Unregister an audio stream when destroyed or released.
     */
    fun unregisterAudioStream(controller: AudioStreamController) {
        registeredControllers.remove(controller)
        updatePlaybackState()
        Log.d(TAG, "AudioStream unregistered: ${controller.getStreamIdentifier()}. Total: ${registeredControllers.size}")
    }

    /**
     * Mute all media playback across the system and in-app components.
     * Enforces silence on internal players, external media streams, and TTS.
     */
    fun muteAllMediaPlayback(reason: String = "WAKE_WORD_MUTED") {
        Log.i(TAG, "Muting all media playback: $reason")
        onWakeWordDetected(reason)

        // Lower or mute music stream volume temporarily if needed
        try {
            audioManager?.let { am ->
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Notice adjusting music stream volume: ${e.message}")
        }
    }

    /**
     * Unmute music stream volume if it was muted.
     */
    fun unmuteMediaPlayback() {
        abandonTransientAudioFocus()
        try {
            audioManager?.let { am ->
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Notice unmuting music stream volume: ${e.message}")
        }
        resumeAudioIfInterrupted()
    }

    /**
     * PRIMARY INTERFACE:
     * Instantly pause or stop any active media playback or audio streams across the application
     * whenever the 'Kyaw Gyi' wake word is detected.
     */
    fun onWakeWordDetected(source: String = "KYAW_GYI_WAKE_WORD") {
        Log.i(TAG, "🔥 'Kyaw Gyi' Wake Word Detected by $source! Instantly pausing all active media playback & audio streams.")

        isInterruptedByWakeWord.set(true)
        val pausedList = mutableListOf<String>()

        // 0. Request transient exclusive audio focus to immediately silence all other audio sources
        requestTransientAudioFocus()

        // 1. Instantly pause/stop all registered in-app audio streams
        for (controller in registeredControllers) {
            try {
                if (controller.isPlaying()) {
                    val streamId = controller.getStreamIdentifier()
                    Log.d(TAG, "Pausing active audio stream: $streamId")
                    controller.pauseAudio()
                    pausedList.add(streamId)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error pausing stream ${controller.getStreamIdentifier()}: ${e.message}")
            }
        }

        // Explicit safeguard: Ensure KyawGyiMusicPlayer is paused
        try {
            val musicPlayer = KyawGyiMusicPlayer.getInstance(context)
            if (musicPlayer.playerState.value.isPlaying) {
                musicPlayer.pause()
                pausedList.add("KyawGyiMusicPlayer")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Notice pausing KyawGyiMusicPlayer: ${e.message}")
        }

        // 2. Dispatch Media Key Pause to system if external music playback is detected
        try {
            if (audioManager?.isMusicActive == true) {
                dispatchSystemMediaPause()
                pausedList.add("SystemExternalMedia")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Notice dispatching system media pause: ${e.message}")
        }

        // 3. Silence any ongoing TTS speech across the system
        try {
            com.example.utils.MyanmarTTSEngine.getInstance(context).stop()
        } catch (ignored: Exception) {}

        pausedStreamsCache.clear()
        pausedStreamsCache.addAll(pausedList)

        val timestamp = System.currentTimeMillis()
        _audioState.value = GlobalAudioState(
            isAnyMediaPlaying = false,
            isAudioPausedByWakeWord = true,
            lastInterruptionTimestamp = timestamp,
            lastInterruptionReason = "WAKE_WORD_DETECTED ($source)",
            activeStreamCount = 0,
            pausedStreamIdentifiers = pausedList.toList()
        )

        AudioDiagnostics.logEvent(
            AudioDiagnostics.CAT_VOICE,
            mapOf(
                "action" to "GLOBAL_AUDIO_PAUSED_BY_WAKE_WORD",
                "source" to source,
                "pausedStreams" to pausedList.joinToString(", ")
            )
        )
    }

    /**
     * Instantly pause all active media streams manually or programmatically.
     */
    fun pauseAllMedia(reason: String = "USER_REQUEST") {
        Log.i(TAG, "pauseAllMedia requested: $reason")
        val pausedList = mutableListOf<String>()

        for (controller in registeredControllers) {
            try {
                if (controller.isPlaying()) {
                    controller.pauseAudio()
                    pausedList.add(controller.getStreamIdentifier())
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error pausing controller: ${e.message}")
            }
        }

        try {
            KyawGyiMusicPlayer.getInstance(context).pause()
        } catch (ignored: Exception) {}

        _audioState.value = _audioState.value.copy(
            isAnyMediaPlaying = false,
            lastInterruptionReason = reason,
            pausedStreamIdentifiers = pausedList
        )
    }

    /**
     * Instantly stop all active audio streams across the application.
     */
    fun stopAllAudio(reason: String = "USER_REQUEST") {
        Log.i(TAG, "stopAllAudio requested: $reason")

        for (controller in registeredControllers) {
            try {
                controller.stopAudio()
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping controller: ${e.message}")
            }
        }

        try {
            KyawGyiMusicPlayer.getInstance(context).stop()
        } catch (ignored: Exception) {}

        try {
            com.example.utils.MyanmarTTSEngine.getInstance(context).stop()
        } catch (ignored: Exception) {}

        _audioState.value = _audioState.value.copy(
            isAnyMediaPlaying = false,
            isAudioPausedByWakeWord = false,
            lastInterruptionReason = reason,
            activeStreamCount = 0,
            pausedStreamIdentifiers = emptyList()
        )
    }

    /**
     * Resume audio streams that were paused by wake word if desired.
     */
    fun resumeAudioIfInterrupted() {
        if (!isInterruptedByWakeWord.getAndSet(false)) return

        Log.i(TAG, "Resuming audio streams that were paused by wake word...")
        for (streamId in pausedStreamsCache) {
            val controller = registeredControllers.firstOrNull { it.getStreamIdentifier() == streamId }
            controller?.resumeAudio()
        }
        pausedStreamsCache.clear()

        updatePlaybackState()
    }

    /**
     * Dispatch standard Android MEDIA_PAUSE key events to pause active external music player.
     */
    private fun dispatchSystemMediaPause() {
        val am = audioManager ?: return
        val eventTime = SystemClock.uptimeMillis()
        val downEvent = KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE, 0)
        val upEvent = KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PAUSE, 0)

        am.dispatchMediaKeyEvent(downEvent)
        am.dispatchMediaKeyEvent(upEvent)
        Log.d(TAG, "Dispatched system KEYCODE_MEDIA_PAUSE event.")
    }

    private var audioFocusRequestObject: Any? = null

    private fun requestTransientAudioFocus() {
        try {
            val am = audioManager ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val playbackAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(playbackAttributes)
                    .setAcceptsDelayedFocusGain(false)
                    .setOnAudioFocusChangeListener { /* transient focus change */ }
                    .build()
                audioFocusRequestObject = focusRequest
                am.requestAudioFocus(focusRequest)
                Log.d(TAG, "Transient audio focus requested successfully (Android O+).")
            } else {
                @Suppress("DEPRECATION")
                am.requestAudioFocus(
                    null,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
                )
                Log.d(TAG, "Transient audio focus requested successfully (Legacy).")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Notice requesting transient audio focus: ${e.message}")
        }
    }

    private fun abandonTransientAudioFocus() {
        try {
            val am = audioManager ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                (audioFocusRequestObject as? AudioFocusRequest)?.let {
                    am.abandonAudioFocusRequest(it)
                    audioFocusRequestObject = null
                    Log.d(TAG, "Transient audio focus abandoned (Android O+).")
                }
            } else {
                @Suppress("DEPRECATION")
                am.abandonAudioFocus(null)
                Log.d(TAG, "Transient audio focus abandoned (Legacy).")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Notice abandoning audio focus: ${e.message}")
        }
    }

    private fun updatePlaybackState() {
        val isAnyPlaying = registeredControllers.any { it.isPlaying() } || (audioManager?.isMusicActive == true)
        _audioState.value = _audioState.value.copy(
            isAnyMediaPlaying = isAnyPlaying,
            activeStreamCount = registeredControllers.count { it.isPlaying() }
        )
    }
}
