package com.kyawgyi.voice

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Simple persistent preferences; replace with DataStore in a full app. */
class VoicePreferences(context: Context) {
    private val prefs = context.getSharedPreferences("voice_preferences", Context.MODE_PRIVATE)
    private val _speechRate = MutableStateFlow(prefs.getFloat(KEY_RATE, 1.0f).coerceIn(0.5f, 2.0f))
    private val _pitch = MutableStateFlow(prefs.getFloat(KEY_PITCH, 1.0f).coerceIn(0.5f, 2.0f))
    val speechRate: StateFlow<Float> = _speechRate
    val pitch: StateFlow<Float> = _pitch

    fun setSpeechRate(value: Float) {
        val safe = value.coerceIn(0.5f, 2.0f)
        prefs.edit().putFloat(KEY_RATE, safe).apply()
        _speechRate.value = safe
    }

    fun setPitch(value: Float) {
        val safe = value.coerceIn(0.5f, 2.0f)
        prefs.edit().putFloat(KEY_PITCH, safe).apply()
        _pitch.value = safe
    }

    companion object {
        private const val KEY_RATE = "speech_rate"
        private const val KEY_PITCH = "pitch"
    }
}
