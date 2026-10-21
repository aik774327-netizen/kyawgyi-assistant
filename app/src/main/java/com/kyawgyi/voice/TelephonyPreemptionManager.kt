package com.kyawgyi.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat

/**
 * TelephonyPreemptionManager (Item #3 — OS TelephonyCallback & PhoneStateListener Preemption Bridge)
 *
 * Monitors cellular/VoIP phone call states (`CALL_STATE_RINGING`, `CALL_STATE_OFFHOOK`, `CALL_STATE_IDLE`)
 * via [TelephonyCallback.CallStateListener] (Android 12 / API 31+) or legacy `PhoneStateListener`
 * (API < 31) and coordinates with [TelephonyPreemption] to pause microphone capture and TTS playback
 * whenever a phone call is active.
 */
class TelephonyPreemptionManager(
    private val context: Context,
    private val onCallStateChanged: (isCallActive: Boolean) -> Unit
) {
    companion object {
        private const val TAG = "TelephonyPreemptionMgr"
    }

    private val appContext = context.applicationContext
    private val telephonyManager = appContext.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
    private var telephonyCallback: Any? = null
    private var legacyListener: android.telephony.PhoneStateListener? = null
    private var isRegistered = false

    fun registerCallStateListener(): Boolean {
        if (isRegistered) return true
        val tm = telephonyManager ?: return false

        val hasReadPhoneState = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasReadPhoneState) {
            Log.d(TAG, "READ_PHONE_STATE not granted yet; falling back to AudioManager call mode inspection.")
            return false
        }

        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val callback = @RequiresApi(Build.VERSION_CODES.S) object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                    override fun onCallStateChanged(state: Int) {
                        handleCallState(state)
                    }
                }
                telephonyCallback = callback
                tm.registerTelephonyCallback(appContext.mainExecutor, callback)
            } else {
                @Suppress("DEPRECATION")
                val listener = object : android.telephony.PhoneStateListener() {
                    @Deprecated("Deprecated in Java")
                    override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                        handleCallState(state)
                    }
                }
                legacyListener = listener
                @Suppress("DEPRECATION")
                tm.listen(listener, android.telephony.PhoneStateListener.LISTEN_CALL_STATE)
            }
            isRegistered = true
            true
        }.onFailure { e ->
            Log.w(TAG, "Failed to register TelephonyCallback: ${e.message}")
        }.getOrDefault(false)
    }

    fun handleCallState(state: Int) {
        val isCallActive = state == TelephonyManager.CALL_STATE_RINGING ||
            state == TelephonyManager.CALL_STATE_OFFHOOK
        onCallStateChanged(isCallActive)
    }

    fun unregister() {
        if (!isRegistered) return
        val tm = telephonyManager ?: return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && telephonyCallback != null) {
                tm.unregisterTelephonyCallback(telephonyCallback as TelephonyCallback)
            } else if (legacyListener != null) {
                @Suppress("DEPRECATION")
                tm.listen(legacyListener, android.telephony.PhoneStateListener.LISTEN_NONE)
            }
        }
        telephonyCallback = null
        legacyListener = null
        isRegistered = false
    }
}
