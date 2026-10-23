package com.kyawgyi.voice

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * VoiceSettings (Layer 2 & Layer 3 User Preferences & System Configuration)
 *
 * Persists and reactively exposes user configurations for speech-to-text,
 * text-to-speech, and acoustic feedback handling:
 * - Preferred STT Engine (Sherpa-ONNX, Vosk, System SpeechRecognizer)
 * - TTS Pitch & Speed Rate
 * - Automatic Offline Model Download toggles
 * - AEC Post-TTS Hangover Guard toggles
 * - Local MyanmarTTSEngine Graceful Fallback toggles
 */
class VoiceSettings private constructor(context: Context) {

    companion object {
        private const val TAG = "VoiceSettings"
        private const val PREFS_NAME = "kyawgyi_voice_preferences"

        private const val KEY_PREFERRED_STT_ENGINE = "preferred_stt_engine"
        private const val KEY_PREFERRED_TTS_ENGINE = "preferred_tts_engine"
        private const val KEY_TTS_SPEECH_RATE = "tts_speech_rate"
        private const val KEY_TTS_PITCH = "tts_pitch"
        private const val KEY_AUTO_DOWNLOAD_MODELS = "auto_download_models"
        private const val KEY_AEC_HANGOVER_ENABLED = "aec_hangover_enabled"
        private const val KEY_LOCAL_TTS_FALLBACK = "local_tts_fallback"

        @Volatile
        private var instance: VoiceSettings? = null

        fun getInstance(context: Context): VoiceSettings {
            return instance ?: synchronized(this) {
                instance ?: VoiceSettings(context.applicationContext).also { instance = it }
            }
        }
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // 1. Preferred STT Engine
    private val _preferredSttEngine = MutableStateFlow(loadPreferredSttEngine())
    val preferredSttEngineFlow: StateFlow<SttEngineType> = _preferredSttEngine.asStateFlow()
    var preferredSttEngine: SttEngineType
        get() = _preferredSttEngine.value
        set(value) {
            _preferredSttEngine.value = value
            prefs.edit().putString(KEY_PREFERRED_STT_ENGINE, value.name).apply()
            Log.i(TAG, "Preferred STT Engine updated to: $value")
        }

    // 1b. Preferred TTS Engine (§7, 2026-10-23): system TTS vs Gemini
    // online TTS. Default SYSTEM — online synthesis sends reply text
    // to Google and must be an explicit user choice.
    private val _preferredTtsEngine = MutableStateFlow(loadPreferredTtsEngine())
    val preferredTtsEngineFlow: StateFlow<TtsEngineType> = _preferredTtsEngine.asStateFlow()
    var preferredTtsEngine: TtsEngineType
        get() = _preferredTtsEngine.value
        set(value) {
            _preferredTtsEngine.value = value
            prefs.edit().putString(KEY_PREFERRED_TTS_ENGINE, value.name).apply()
            Log.i(TAG, "Preferred TTS Engine updated to: $value")
        }

    // 2. TTS Speech Rate
    private val _ttsSpeechRate = MutableStateFlow(prefs.getFloat(KEY_TTS_SPEECH_RATE, 1.0f))
    val ttsSpeechRateFlow: StateFlow<Float> = _ttsSpeechRate.asStateFlow()
    var ttsSpeechRate: Float
        get() = _ttsSpeechRate.value
        set(value) {
            val clamped = value.coerceIn(0.5f, 2.0f)
            _ttsSpeechRate.value = clamped
            prefs.edit().putFloat(KEY_TTS_SPEECH_RATE, clamped).apply()
            Log.i(TAG, "TTS Speech Rate updated to: $clamped")
        }

    // 3. TTS Pitch
    private val _ttsPitch = MutableStateFlow(prefs.getFloat(KEY_TTS_PITCH, 1.0f))
    val ttsPitchFlow: StateFlow<Float> = _ttsPitch.asStateFlow()
    var ttsPitch: Float
        get() = _ttsPitch.value
        set(value) {
            val clamped = value.coerceIn(0.5f, 2.0f)
            _ttsPitch.value = clamped
            prefs.edit().putFloat(KEY_TTS_PITCH, clamped).apply()
            Log.i(TAG, "TTS Pitch updated to: $clamped")
        }

    // 4. Auto Download Offline Models
    private val _autoDownloadModels = MutableStateFlow(prefs.getBoolean(KEY_AUTO_DOWNLOAD_MODELS, true))
    val autoDownloadModelsFlow: StateFlow<Boolean> = _autoDownloadModels.asStateFlow()
    var autoDownloadModels: Boolean
        get() = _autoDownloadModels.value
        set(value) {
            _autoDownloadModels.value = value
            prefs.edit().putBoolean(KEY_AUTO_DOWNLOAD_MODELS, value).apply()
        }

    // 5. AEC Post-TTS Hangover Guard
    private val _isAecHangoverEnabled = MutableStateFlow(prefs.getBoolean(KEY_AEC_HANGOVER_ENABLED, true))
    val isAecHangoverEnabledFlow: StateFlow<Boolean> = _isAecHangoverEnabled.asStateFlow()
    var isAecHangoverEnabled: Boolean
        get() = _isAecHangoverEnabled.value
        set(value) {
            _isAecHangoverEnabled.value = value
            prefs.edit().putBoolean(KEY_AEC_HANGOVER_ENABLED, value).apply()
        }

    // 6. Local TTS Fallback
    private val _useLocalTtsFallback = MutableStateFlow(prefs.getBoolean(KEY_LOCAL_TTS_FALLBACK, true))
    val useLocalTtsFallbackFlow: StateFlow<Boolean> = _useLocalTtsFallback.asStateFlow()
    var useLocalTtsFallback: Boolean
        get() = _useLocalTtsFallback.value
        set(value) {
            _useLocalTtsFallback.value = value
            prefs.edit().putBoolean(KEY_LOCAL_TTS_FALLBACK, value).apply()
        }

    private fun loadPreferredSttEngine(): SttEngineType {
        val saved = prefs.getString(KEY_PREFERRED_STT_ENGINE, SttEngineType.SYSTEM_ON_DEVICE.name)
        return try {
            SttEngineType.valueOf(saved ?: SttEngineType.SYSTEM_ON_DEVICE.name)
        } catch (e: Exception) {
            SttEngineType.SYSTEM_ON_DEVICE
        }
    }

    private fun loadPreferredTtsEngine(): TtsEngineType {
        val saved = prefs.getString(KEY_PREFERRED_TTS_ENGINE, TtsEngineType.SYSTEM.name)
        return try {
            TtsEngineType.valueOf(saved ?: TtsEngineType.SYSTEM.name)
        } catch (e: Exception) {
            TtsEngineType.SYSTEM
        }
    }

    fun resetToDefaults() {
        preferredSttEngine = SttEngineType.SYSTEM_ON_DEVICE
        preferredTtsEngine = TtsEngineType.SYSTEM
        ttsSpeechRate = 1.0f
        ttsPitch = 1.0f
        autoDownloadModels = true
        isAecHangoverEnabled = true
        useLocalTtsFallback = true
    }
}
