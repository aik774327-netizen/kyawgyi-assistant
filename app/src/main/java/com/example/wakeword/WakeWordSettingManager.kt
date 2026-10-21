package com.example.wakeword

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * WakeWordSettingManager
 *
 * Manages user preference for the on-device "ကျော်ကြီး" Wake-word detection engine:
 * - Persists state in SharedPreferences across app restarts.
 * - Reactive StateFlow for Compose UI observation (Switch toggle in Settings).
 * - Synchronous checking for low-overhead service / background checks.
 * - Broadcasts / triggers listeners when toggled on/off.
 */
class WakeWordSettingManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "WakeWordSettingManager"
        private const val PREFS_NAME = "kyaw_gyi_wakeword_prefs"
        private const val KEY_WAKE_WORD_ENABLED = "pref_kyaw_gyi_wakeword_enabled"
        private const val KEY_SENSITIVITY_LEVEL = "pref_kyaw_gyi_wakeword_sensitivity" // 0: Normal, 1: High

        @Volatile
        private var instance: WakeWordSettingManager? = null

        fun getInstance(context: Context): WakeWordSettingManager {
            return instance ?: synchronized(this) {
                instance ?: WakeWordSettingManager(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _isWakeWordEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_WAKE_WORD_ENABLED, true) // Default enabled
    )
    val isWakeWordEnabled: StateFlow<Boolean> = _isWakeWordEnabled.asStateFlow()

    private val _sensitivityHigh = MutableStateFlow(
        prefs.getBoolean(KEY_SENSITIVITY_LEVEL, false)
    )
    val sensitivityHigh: StateFlow<Boolean> = _sensitivityHigh.asStateFlow()

    /**
     * Checks synchronously if "ကျော်ကြီး" wake-word detection is enabled by the user.
     */
    fun isEnabled(): Boolean {
        return prefs.getBoolean(KEY_WAKE_WORD_ENABLED, true)
    }

    /**
     * Toggles the "ကျော်ကြီး" wake-word detection engine on or off.
     */
    fun setWakeWordEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_WAKE_WORD_ENABLED, enabled).apply()
        _isWakeWordEnabled.value = enabled
        Log.i(TAG, "Wake-word 'ကျော်ကြီး' set to: $enabled")
    }

    /**
     * Sets sensitivity level (normal or high).
     */
    fun setHighSensitivity(high: Boolean) {
        prefs.edit().putBoolean(KEY_SENSITIVITY_LEVEL, high).apply()
        _sensitivityHigh.value = high
        Log.i(TAG, "Wake-word sensitivity high set to: $high")
    }
}
