package com.kyawgyi.voice

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.view.KeyEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Snapshot of [HeadsetPushToTalkReceiver] hardware button state.
 */
data class HeadsetPushToTalkState(
    val isListeningTriggeredByHeadset: Boolean = false,
    val totalButtonPresses: Int = 0,
    val lastKeyCode: Int = 0,
    val lastActionTimestampMs: Long = 0L
)

/**
 * HeadsetPushToTalkReceiver (Additional 12 — Bluetooth Earbud & Wired Headset Button Push-to-Talk)
 *
 * Covers Item #11 of the Production Burmese Voice AI Checklist:
 * Handles hardware media/headset button presses (`KEYCODE_HEADSETHOOK`, `KEYCODE_MEDIA_PLAY_PAUSE`,
 * `KEYCODE_VOICE_ASSIST`) from Bluetooth earbuds or wired headsets so users can start/stop
 * KyawGyi AI voice listening hands-free without unlocking or touching the screen.
 */
class HeadsetPushToTalkReceiver(
    private val onToggleListening: ((Boolean) -> Unit)? = null
) : BroadcastReceiver() {

    companion object {
        val SUPPORTED_PTT_KEYCODES = setOf(
            KeyEvent.KEYCODE_HEADSETHOOK,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_VOICE_ASSIST
        )

        private val _globalState = MutableStateFlow(HeadsetPushToTalkState())
        val globalState: StateFlow<HeadsetPushToTalkState> = _globalState.asStateFlow()
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action != Intent.ACTION_MEDIA_BUTTON) return
        @Suppress("DEPRECATION")
        val keyEvent = intent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT) ?: return
        handleMediaKeyEvent(keyEvent)
    }

    /**
     * Processes a hardware [KeyEvent] from a headset button press (`ACTION_DOWN` with repeatCount == 0).
     * Returns `true` if the event was consumed as a Push-to-Talk toggle.
     */
    fun handleMediaKeyEvent(keyEvent: KeyEvent): Boolean {
        if (keyEvent.action != KeyEvent.ACTION_DOWN || keyEvent.repeatCount > 0) return false
        if (keyEvent.keyCode !in SUPPORTED_PTT_KEYCODES) return false

        val now = System.currentTimeMillis()
        var nextListening = false
        _globalState.update { prev ->
            nextListening = !prev.isListeningTriggeredByHeadset
            prev.copy(
                isListeningTriggeredByHeadset = nextListening,
                totalButtonPresses = prev.totalButtonPresses + 1,
                lastKeyCode = keyEvent.keyCode,
                lastActionTimestampMs = now
            )
        }
        onToggleListening?.invoke(nextListening)
        return true
    }

    fun resetState() {
        _globalState.update { HeadsetPushToTalkState() }
    }
}
