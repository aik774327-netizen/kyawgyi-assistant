package com.kyawgyi.voice

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

/**
 * Immutable thread-safe status of hardware/system microphone mute and OS privacy blocking.
 */
data class MicrophoneMuteStatus(
    val isHardwareMuted: Boolean = false,
    val isSilentZeroStreamDetected: Boolean = false,
    val consecutiveZeroFrames: Int = 0,
    val warningMessage: String? = null
) {
    val isEffectivelyMuted: Boolean
        get() = isHardwareMuted || isSilentZeroStreamDetected
}

/**
 * MicrophoneMuteMonitor (Requirements 9 — Hardware Mute & Android 12+ Privacy Toggle Detector)
 *
 * Monitors:
 * 1. System-level microphone mute broadcasts ([AudioManager.ACTION_MICROPHONE_MUTE_CHANGED])
 *    and [AudioManager.isMicrophoneMute].
 * 2. Consecutive all-zero PCM frames (`0x0000`) emitted by `AudioRecord` when the user disables
 *    "Mic Access" in the Android 12+ Quick Settings privacy toggle.
 */
class MicrophoneMuteMonitor(context: Context) {

    companion object {
        private const val TAG = "MicrophoneMuteMonitor"
        const val ZERO_FRAME_MUTE_THRESHOLD = 12 // ~480ms of consecutive exact-zero frames at 40ms/frame
    }

    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private val _muteStatus = MutableStateFlow(MicrophoneMuteStatus())
    val muteStatus: StateFlow<MicrophoneMuteStatus> = _muteStatus.asStateFlow()

    private var receiverRegistered = false
    private var consecutiveZeroFrameCount = 0

    private val muteBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_MICROPHONE_MUTE_CHANGED) {
                refreshHardwareMuteState()
            }
        }
    }

    init {
        registerReceiver()
        refreshHardwareMuteState()
    }

    fun registerReceiver() {
        if (receiverRegistered) return
        runCatching {
            val filter = IntentFilter(AudioManager.ACTION_MICROPHONE_MUTE_CHANGED)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                appContext.registerReceiver(muteBroadcastReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                appContext.registerReceiver(muteBroadcastReceiver, filter)
            }
            receiverRegistered = true
        }.onFailure { e ->
            Log.w(TAG, "Failed to register ACTION_MICROPHONE_MUTE_CHANGED receiver: ${e.message}")
        }
    }

    /**
     * Checks [AudioManager.isMicrophoneMute] and updates [muteStatus] atomically.
     */
    fun refreshHardwareMuteState(): MicrophoneMuteStatus {
        val hwMuted = runCatching { audioManager?.isMicrophoneMute == true }.getOrDefault(false)
        _muteStatus.update { current ->
            val silentBlocked = current.isSilentZeroStreamDetected
            current.copy(
                isHardwareMuted = hwMuted,
                warningMessage = buildWarningMessage(hwMuted, silentBlocked)
            )
        }
        return _muteStatus.value
    }

    /**
     * Inspects a live 16-bit PCM frame to detect if Android's OS Privacy Mic toggle is silencing
     * the microphone stream with exact `0` samples.
     */
    @Synchronized
    fun inspectPcmFrame(pcmBytes: ByteArray): MicrophoneMuteStatus {
        if (pcmBytes.size < 2) return _muteStatus.value

        val isAllZero = isExactZeroPcm(pcmBytes)
        if (isAllZero) {
            consecutiveZeroFrameCount++
        } else {
            consecutiveZeroFrameCount = 0
        }

        val silentStreamBlocked = consecutiveZeroFrameCount >= ZERO_FRAME_MUTE_THRESHOLD
        val hwMuted = runCatching { audioManager?.isMicrophoneMute == true }.getOrDefault(false)

        _muteStatus.update {
            MicrophoneMuteStatus(
                isHardwareMuted = hwMuted,
                isSilentZeroStreamDetected = silentStreamBlocked,
                consecutiveZeroFrames = consecutiveZeroFrameCount,
                warningMessage = buildWarningMessage(hwMuted, silentStreamBlocked)
            )
        }
        return _muteStatus.value
    }

    @Synchronized
    fun resetZeroFrameCounter() {
        consecutiveZeroFrameCount = 0
        val hwMuted = runCatching { audioManager?.isMicrophoneMute == true }.getOrDefault(false)
        _muteStatus.update {
            MicrophoneMuteStatus(
                isHardwareMuted = hwMuted,
                isSilentZeroStreamDetected = false,
                consecutiveZeroFrames = 0,
                warningMessage = buildWarningMessage(hwMuted, false)
            )
        }
    }

    fun close() {
        if (receiverRegistered) {
            runCatching { appContext.unregisterReceiver(muteBroadcastReceiver) }
            receiverRegistered = false
        }
    }

    private fun isExactZeroPcm(pcmBytes: ByteArray): Boolean {
        val sampleCount = pcmBytes.size / 2
        val shortBuf = ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        for (i in 0 until sampleCount) {
            if (abs(shortBuf.get(i).toInt()) > 1) {
                return false
            }
        }
        return true
    }

    private fun buildWarningMessage(hwMuted: Boolean, silentBlocked: Boolean): String? = when {
        hwMuted -> "မိုက်ခရိုဖုန်း Hardware Mute ပိတ်ထားပါသည် (Unmute ပြန်ဖွင့်ပေးပါ)"
        silentBlocked -> "Android Privacy Mic Access ပိတ်ထားသဖြင့် အသံလှိုင်း (0 dB) သာ ဝင်နေပါသည်"
        else -> null
    }
}
