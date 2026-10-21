package com.example.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat
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
 * AudioManagerFocusListener (Layer 1 - Audio Focus & Telephony Interruption Handler)
 *
 * Production Hardened System Implementation for KyawGyi Voice Assistant:
 * 1. Audio Focus Transient Management:
 *    - Catches AUDIOFOCUS_LOSS_TRANSIENT and AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK.
 *    - Gracefully pauses the CentralAudioRecorder 16kHz PCM stream without tearing down hardware if possible.
 * 2. Automatic Pipeline Re-Sync:
 *    - Upon AUDIOFOCUS_GAIN, automatically unpauses and verifies hardware AudioRecord status.
 *    - If hardware AudioRecord was preempted or killed during the loss, executes deterministic pipeline re-synchronization.
 * 3. Permanent Audio Focus Loss Handling:
 *    - Cleanly shuts down active capture jobs on AUDIOFOCUS_LOSS.
 * 4. Telephony Interruption Handler:
 *    - Tracks incoming phone calls (RINGING / OFFHOOK) to prevent microphone contention or privacy leaks.
 *    - Automatically re-syncs capture upon CALL_STATE_IDLE.
 */
class AudioManagerFocusListener private constructor(
    private val context: Context,
    private val centralAudioRecorder: CentralAudioRecorder = CentralAudioRecorder.getInstance(context)
) : AudioManager.OnAudioFocusChangeListener {

    companion object {
        private const val TAG = "AudioManagerFocusListener"

        @Volatile
        private var instance: AudioManagerFocusListener? = null

        fun getInstance(context: Context): AudioManagerFocusListener {
            return instance ?: synchronized(this) {
                instance ?: AudioManagerFocusListener(context.applicationContext).also { instance = it }
            }
        }
    }

    enum class FocusTransitionState {
        IDLE,
        GAINED,
        TRANSIENT_LOSS,
        TRANSIENT_LOSS_CAN_DUCK,
        PERMANENT_LOSS,
        TELEPHONY_INTERRUPTED
    }

    interface FocusStateObserver {
        fun onFocusGained() {}
        fun onTransientFocusLost(canDuck: Boolean) {}
        fun onPermanentFocusLost() {}
        fun onTelephonyCallInterrupted() {}
        fun onTelephonyCallEnded() {}
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _focusTransitionState = MutableStateFlow(FocusTransitionState.IDLE)
    val focusTransitionState: StateFlow<FocusTransitionState> = _focusTransitionState.asStateFlow()

    private val wasRecordingPriorToLoss = AtomicBoolean(false)
    private val isRegistered = AtomicBoolean(false)
    private var focusRequest: AudioFocusRequest? = null

    private val observers = CopyOnWriteArraySet<FocusStateObserver>()

    // Telephony Callback for Android 12+ (API 31+)
    private var modernTelephonyCallback: Any? = null

    // Legacy PhoneStateListener for API < 31
    private var legacyPhoneStateListener: PhoneStateListener? = null

    // Broadcast receiver fallback for phone state
    private val phoneStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == TelephonyManager.ACTION_PHONE_STATE_CHANGED) {
                val stateStr = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
                Log.d(TAG, "Telephony broadcast received state: $stateStr")
                when (stateStr) {
                    TelephonyManager.EXTRA_STATE_RINGING,
                    TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                        handleTelephonyInterruption(active = true)
                    }
                    TelephonyManager.EXTRA_STATE_IDLE -> {
                        handleTelephonyInterruption(active = false)
                    }
                }
            }
        }
    }

    init {
        registerListeners()
    }

    /**
     * Registers system listeners for Audio Focus and Telephony changes.
     */
    @Synchronized
    fun registerListeners() {
        if (isRegistered.getAndSet(true)) return

        Log.i(TAG, "Registering AudioManagerFocusListener and telephony monitors.")

        // Register Telephony monitoring if permission granted or via broadcast
        try {
            val hasPhoneStatePermission = ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.READ_PHONE_STATE
            ) == PackageManager.PERMISSION_GRANTED

            if (hasPhoneStatePermission && telephonyManager != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    registerModernTelephonyCallback(telephonyManager)
                } else {
                    registerLegacyPhoneStateListener(telephonyManager)
                }
            } else {
                // Register BroadcastReceiver fallback for incoming call state changes
                val filter = IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.registerReceiver(phoneStateReceiver, filter, Context.RECEIVER_EXPORTED)
                } else {
                    context.registerReceiver(phoneStateReceiver, filter)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Notice registering telephony interrupt listener: ${e.message}")
        }
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.S)
    private fun registerModernTelephonyCallback(tm: TelephonyManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val callback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                    override fun onCallStateChanged(state: Int) {
                        onTelephonyStateChanged(state)
                    }
                }
                modernTelephonyCallback = callback
                tm.registerTelephonyCallback(context.mainExecutor, callback)
                Log.d(TAG, "Modern TelephonyCallback registered successfully.")
            } catch (e: Exception) {
                Log.w(TAG, "Failed registering TelephonyCallback: ${e.message}")
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun registerLegacyPhoneStateListener(tm: TelephonyManager) {
        try {
            val listener = object : PhoneStateListener() {
                override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                    onTelephonyStateChanged(state)
                }
            }
            legacyPhoneStateListener = listener
            tm.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
            Log.d(TAG, "Legacy PhoneStateListener registered.")
        } catch (e: Exception) {
            Log.w(TAG, "Failed registering PhoneStateListener: ${e.message}")
        }
    }

    private fun onTelephonyStateChanged(state: Int) {
        when (state) {
            TelephonyManager.CALL_STATE_RINGING,
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                handleTelephonyInterruption(active = true)
            }
            TelephonyManager.CALL_STATE_IDLE -> {
                handleTelephonyInterruption(active = false)
            }
        }
    }

    /**
     * Handles Telephony interruptions gracefully.
     */
    private fun handleTelephonyInterruption(active: Boolean) {
        if (active) {
            Log.w(TAG, "Telephony call interrupt ACTIVE. Pausing CentralAudioRecorder...")
            _focusTransitionState.value = FocusTransitionState.TELEPHONY_INTERRUPTED
            wasRecordingPriorToLoss.set(centralAudioRecorder.isCapturing() && !centralAudioRecorder.isPaused())
            scope.launch {
                centralAudioRecorder.pauseRecordingTransiently("TELEPHONY_CALL_INTERRUPT")
            }
            observers.forEach { it.onTelephonyCallInterrupted() }
        } else {
            if (_focusTransitionState.value == FocusTransitionState.TELEPHONY_INTERRUPTED) {
                Log.i(TAG, "Telephony call ENDED (IDLE). Automatically re-syncing and resuming CentralAudioRecorder...")
                _focusTransitionState.value = FocusTransitionState.GAINED
                if (wasRecordingPriorToLoss.get()) {
                    scope.launch {
                        val resynced = centralAudioRecorder.resumeRecordingTransiently("TELEPHONY_CALL_IDLE")
                        Log.i(TAG, "CentralAudioRecorder telephony resume result: $resynced")
                    }
                }
                observers.forEach { it.onTelephonyCallEnded() }
            }
        }
    }

    /**
     * Core Audio Focus Change Callback (Android OS AudioManager)
     */
    override fun onAudioFocusChange(focusChange: Int) {
        Log.i(TAG, "onAudioFocusChange triggered: focusChange=$focusChange")
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                Log.w(TAG, "AUDIOFOCUS_LOSS_TRANSIENT: Pausing CentralAudioRecorder PCM stream...")
                handleTransientFocusLoss(canDuck = false)
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                Log.w(TAG, "AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK: Pausing CentralAudioRecorder stream to avoid mixing...")
                handleTransientFocusLoss(canDuck = true)
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                Log.i(TAG, "AUDIOFOCUS_GAIN: Regained audio focus. Resuming & re-syncing CentralAudioRecorder...")
                handleFocusGained()
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                Log.w(TAG, "AUDIOFOCUS_LOSS: Permanent focus loss. Stopping CentralAudioRecorder...")
                handlePermanentFocusLoss()
            }
            else -> {
                Log.d(TAG, "Unhandled audio focus change code: $focusChange")
            }
        }
    }

    /**
     * Handles transient focus loss gracefully by pausing CentralAudioRecorder.
     */
    private fun handleTransientFocusLoss(canDuck: Boolean) {
        _focusTransitionState.value = if (canDuck) {
            FocusTransitionState.TRANSIENT_LOSS_CAN_DUCK
        } else {
            FocusTransitionState.TRANSIENT_LOSS
        }

        // Record whether we were actively streaming prior to the loss
        val activelyStreaming = centralAudioRecorder.isCapturing() && !centralAudioRecorder.isPaused()
        wasRecordingPriorToLoss.set(activelyStreaming)

        scope.launch {
            centralAudioRecorder.pauseRecordingTransiently("AUDIOFOCUS_LOSS_TRANSIENT")
        }

        observers.forEach { it.onTransientFocusLost(canDuck) }
        AudioDiagnostics.logFocusAction(
            action = "AUDIOFOCUS_LOSS_TRANSIENT",
            reason = if (canDuck) "CAN_DUCK" else "EXCLUSIVE_TRANSIENT",
            musicActive = audioManager?.isMusicActive ?: false
        )
    }

    /**
     * Handles focus gain by automatically re-syncing and resuming CentralAudioRecorder.
     */
    private fun handleFocusGained() {
        val wasTransient = _focusTransitionState.value in listOf(
            FocusTransitionState.TRANSIENT_LOSS,
            FocusTransitionState.TRANSIENT_LOSS_CAN_DUCK,
            FocusTransitionState.TELEPHONY_INTERRUPTED
        )

        _focusTransitionState.value = FocusTransitionState.GAINED

        if (wasTransient || wasRecordingPriorToLoss.get()) {
            scope.launch {
                val resynced = centralAudioRecorder.resumeRecordingTransiently("AUDIOFOCUS_GAIN")
                Log.i(TAG, "CentralAudioRecorder re-sync on AUDIOFOCUS_GAIN returned: $resynced")
            }
        }

        observers.forEach { it.onFocusGained() }
        AudioDiagnostics.logFocusAction(
            action = "AUDIOFOCUS_GAIN",
            reason = "PIPELINE_RESYNC_COMPLETED",
            musicActive = audioManager?.isMusicActive ?: false
        )
    }

    /**
     * Handles permanent focus loss by stopping the recorder.
     */
    private fun handlePermanentFocusLoss() {
        _focusTransitionState.value = FocusTransitionState.PERMANENT_LOSS
        wasRecordingPriorToLoss.set(false)

        scope.launch {
            centralAudioRecorder.stopRecording("AUDIOFOCUS_LOSS_PERMANENT", forceRelease = true)
        }

        observers.forEach { it.onPermanentFocusLost() }
        AudioDiagnostics.logFocusAction(
            action = "AUDIOFOCUS_LOSS_PERMANENT",
            reason = "OTHER_APP_EXCLUSIVE_FOCUS",
            musicActive = audioManager?.isMusicActive ?: false
        )
    }

    /**
     * Explicitly requests Audio Focus for assistant interaction (e.g. active speech capture or TTS).
     */
    @Synchronized
    fun requestAssistantAudioFocus(transient: Boolean = true, allowDuck: Boolean = true): Boolean {
        val am = audioManager ?: return false
        try {
            val focusGainType = when {
                transient && allowDuck -> AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                transient -> AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
                else -> AudioManager.AUDIOFOCUS_GAIN
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val playbackAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()

                val request = AudioFocusRequest.Builder(focusGainType)
                    .setAudioAttributes(playbackAttributes)
                    .setAcceptsDelayedFocusGain(true)
                    .setWillPauseWhenDucked(false)
                    .setOnAudioFocusChangeListener(this)
                    .build()

                focusRequest = request
                val result = am.requestAudioFocus(request)
                val granted = (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
                if (granted) {
                    _focusTransitionState.value = FocusTransitionState.GAINED
                }
                return granted
            } else {
                @Suppress("DEPRECATION")
                val result = am.requestAudioFocus(
                    this,
                    AudioManager.STREAM_MUSIC,
                    focusGainType
                )
                val granted = (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
                if (granted) {
                    _focusTransitionState.value = FocusTransitionState.GAINED
                }
                return granted
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting assistant audio focus: ${e.message}", e)
            return false
        }
    }

    /**
     * Abandons assistant audio focus.
     */
    @Synchronized
    fun abandonAssistantAudioFocus() {
        val am = audioManager ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                focusRequest?.let {
                    am.abandonAudioFocusRequest(it)
                    focusRequest = null
                }
            } else {
                @Suppress("DEPRECATION")
                am.abandonAudioFocus(this)
            }
            _focusTransitionState.value = FocusTransitionState.IDLE
        } catch (e: Exception) {
            Log.e(TAG, "Error abandoning audio focus: ${e.message}", e)
        }
    }

    fun addObserver(observer: FocusStateObserver) {
        observers.add(observer)
    }

    fun removeObserver(observer: FocusStateObserver) {
        observers.remove(observer)
    }

    /**
     * Unregisters all listeners and cleans up resources.
     */
    @Synchronized
    fun unregisterListeners() {
        if (!isRegistered.getAndSet(false)) return

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && modernTelephonyCallback != null) {
                (modernTelephonyCallback as? TelephonyCallback)?.let {
                    telephonyManager?.unregisterTelephonyCallback(it)
                }
                modernTelephonyCallback = null
            } else if (legacyPhoneStateListener != null) {
                @Suppress("DEPRECATION")
                telephonyManager?.listen(legacyPhoneStateListener, PhoneStateListener.LISTEN_NONE)
                legacyPhoneStateListener = null
            }

            try {
                context.unregisterReceiver(phoneStateReceiver)
            } catch (_: Exception) {}
        } catch (e: Exception) {
            Log.w(TAG, "Notice unregistering listeners: ${e.message}")
        }
    }
}
