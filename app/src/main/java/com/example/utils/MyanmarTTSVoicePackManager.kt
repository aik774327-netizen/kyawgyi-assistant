package com.example.utils

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.Toast
import com.example.data.FailureCategory
import com.example.ocr.FailureMemoryEngine
import com.example.ocr.MyanmarScriptDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * VOICE PACK STATUS
 */
enum class VoicePackStatus {
    CHECKING,
    VOICE_PACK_INSTALLED,     // my-MM is available and ready for high-fidelity offline speech
    VOICE_PACK_MISSING_DATA,  // Engine supports Burmese (e.g. Google TTS) but data files are not yet downloaded
    ENGINE_UNSUPPORTED,       // Active engine lacks Burmese support (OEM stock engine without my-MM)
    INITIALIZATION_FAILED     // Failed to initialize TTS subsystem
}

/**
 * SPEECH RECOVERY NOTIFICATION EVENT
 * Emitted when speech synthesis cannot speak aloud due to missing voice pack,
 * allowing UI layers to show live subtitles, banners, and one-tap download affordance.
 */
data class SpeechRecoveryEvent(
    val spokenText: String,
    val reasonMm: String,
    val canDownloadVoicePack: Boolean,
    val failureCategory: FailureCategory = FailureCategory.RESOURCE,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * MYANMAR TTS VOICE PACK & OFFLINE SPEECH RECOVERY MANAGER
 *
 * Solves Finding #8:
 * 1. Accurately detects missing Burmese (my-MM) voice packs on Xiaomi, Samsung, Oppo, Vivo and other OEM devices.
 * 2. Intelligent Engine Routing: Directs TTS initialization to Google Speech Services (`com.google.android.tts`)
 *    when available, bypassing OEM engines that lack Burmese language data.
 * 3. Pronunciation Safety Gate: Prevents passing Myanmar Unicode to English/fallback TTS engines, eliminating
 *    dreadful robotic screeching or silent audio focus lockups.
 * 4. Offline Speech Recovery: When Burmese speech cannot be vocalized, emits auditory chime cues, logs truthful
 *    diagnostic records in [FailureMemoryEngine], and broadcasts visual subtitle events with one-tap voice pack download intents.
 */
class MyanmarTTSVoicePackManager private constructor(private val appContext: Context) {

    companion object {
        private const val TAG = "MyanmarTTSVoicePack"
        const val GOOGLE_TTS_PACKAGE = "com.google.android.tts"

        val BURMESE_LOCALE: Locale = Locale.forLanguageTag("my-MM")
        val BURMESE_ALT_LOCALE: Locale = Locale.forLanguageTag("my")

        @Volatile
        private var instance: MyanmarTTSVoicePackManager? = null

        fun getInstance(context: Context): MyanmarTTSVoicePackManager {
            return instance ?: synchronized(this) {
                instance ?: MyanmarTTSVoicePackManager(context.applicationContext).also {
                    instance = it
                }
            }
        }

        /**
         * Inspects installed engines and returns Google TTS package if present on device,
         * ensuring OEM devices (MIUI, ColorOS, OneUI) bind to the engine supporting Burmese.
         */
        fun getBestTtsEnginePackage(context: Context): String? {
            return try {
                val dummy = TextToSpeech(context.applicationContext, null)
                val engines = dummy.engines
                dummy.shutdown()
                if (engines.any { it.name == GOOGLE_TTS_PACKAGE }) {
                    Log.i(TAG, "Selected Google Speech Services engine ($GOOGLE_TTS_PACKAGE)")
                    GOOGLE_TTS_PACKAGE
                } else {
                    null
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not enumerate TTS engines: ${e.message}")
                null
            }
        }
    }

    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _voicePackStatus = MutableStateFlow(VoicePackStatus.CHECKING)
    val voicePackStatus: StateFlow<VoicePackStatus> = _voicePackStatus.asStateFlow()

    private val _currentEnginePackage = MutableStateFlow("")
    val currentEnginePackage: StateFlow<String> = _currentEnginePackage.asStateFlow()

    private val _availableEngines = MutableStateFlow<List<String>>(emptyList())
    val availableEngines: StateFlow<List<String>> = _availableEngines.asStateFlow()

    private val _speechRecoveryEvents = MutableSharedFlow<SpeechRecoveryEvent>(extraBufferCapacity = 10)
    val speechRecoveryEvents: SharedFlow<SpeechRecoveryEvent> = _speechRecoveryEvents.asSharedFlow()

    private var toneGenerator: ToneGenerator? = null

    init {
        initAudioTone()
    }

    private fun initAudioTone() {
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
        } catch (e: Exception) {
            Log.w(TAG, "Could not initialize ToneGenerator: ${e.message}")
        }
    }

    /**
     * Updates and audits current TextToSpeech instance state.
     */
    fun updateTtsInstance(tts: TextToSpeech?, initStatus: Int): VoicePackStatus {
        if (tts == null || initStatus != TextToSpeech.SUCCESS) {
            _voicePackStatus.value = VoicePackStatus.INITIALIZATION_FAILED
            FailureMemoryEngine.recordFailure(
                component = "TTS_SYNTHESIS",
                operation = "INIT_TTS",
                category = FailureCategory.TECHNICAL,
                rootCauseMm = "ဖုန်းစနစ်၏ Text-To-Speech အင်ဂျင်ကို ဖွင့်လှစ်၍ မရပါ (Status: $initStatus)",
                technicalDetails = "TextToSpeech.OnInitListener returned status $initStatus",
                suggestedRemediationMm = "Google Speech Services အား Play Store မှ ပြန်လည်စစ်ဆေး ထည့်သွင်းပါ"
            )
            return VoicePackStatus.INITIALIZATION_FAILED
        }

        val engine = tts.defaultEngine ?: ""
        val enginesList = tts.engines?.map { it.name } ?: emptyList()

        // Check language availability for Burmese
        var langResult = tts.isLanguageAvailable(BURMESE_LOCALE)
        if (langResult == TextToSpeech.LANG_NOT_SUPPORTED || langResult == TextToSpeech.LANG_MISSING_DATA) {
            langResult = tts.isLanguageAvailable(BURMESE_ALT_LOCALE)
        }

        return evaluateLanguageAvailability(langResult, engine, enginesList, recordToErrorLog = false)
    }

    /**
     * Evaluates language availability code and records truthful diagnostics in FailureMemoryEngine.
     */
    fun evaluateLanguageAvailability(
        langResult: Int,
        engine: String = "",
        enginesList: List<String> = emptyList(),
        recordToErrorLog: Boolean = true
    ): VoicePackStatus {
        _currentEnginePackage.value = engine
        _availableEngines.value = enginesList

        val resolvedStatus = when (langResult) {
            TextToSpeech.LANG_AVAILABLE,
            TextToSpeech.LANG_COUNTRY_AVAILABLE,
            TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE -> VoicePackStatus.VOICE_PACK_INSTALLED

            TextToSpeech.LANG_MISSING_DATA -> VoicePackStatus.VOICE_PACK_MISSING_DATA

            TextToSpeech.LANG_NOT_SUPPORTED -> VoicePackStatus.ENGINE_UNSUPPORTED

            else -> VoicePackStatus.ENGINE_UNSUPPORTED
        }

        _voicePackStatus.value = resolvedStatus
        Log.i(TAG, "Myanmar TTS Voice Pack evaluation result: $resolvedStatus (Engine: $engine, code: $langResult)")

        if (recordToErrorLog) {
            if (resolvedStatus == VoicePackStatus.VOICE_PACK_MISSING_DATA) {
                FailureMemoryEngine.recordFailure(
                    component = "TTS_SYNTHESIS",
                    operation = "CHECK_VOICE_PACK",
                    category = FailureCategory.RESOURCE,
                    rootCauseMm = "Google TTS တွင် မြန်မာအသံဒေတာ (my-MM Voice Pack) ဒေါင်းလုဒ် မပြုလုပ်ရသေးပါ",
                    technicalDetails = "Engine: $engine, isLanguageAvailable: LANG_MISSING_DATA",
                    suggestedRemediationMm = "TTS ဆက်တင်သို့ သွားရောက်၍ မြန်မာစကားသံ (Voice Pack) အား ဒေါင်းလုဒ်ဆွဲပေးပါ"
                )
            } else if (resolvedStatus == VoicePackStatus.ENGINE_UNSUPPORTED) {
                FailureMemoryEngine.recordFailure(
                    component = "TTS_SYNTHESIS",
                    operation = "CHECK_VOICE_PACK",
                    category = FailureCategory.RESOURCE,
                    rootCauseMm = "လက်ရှိ TTS Engine ($engine) သည် မြန်မာဘာသာ အသံထွက်ကို မပံ့ပိုးပါ",
                    technicalDetails = "Engine: $engine, isLanguageAvailable: LANG_NOT_SUPPORTED",
                    suggestedRemediationMm = "Speech Services by Google အား ရွေးချယ်ပေးပါ သို့မဟုတ် Play Store မှ ဒေါင်းလုဒ်ရယူပါ"
                )
            }
        }

        return resolvedStatus
    }

    /**
     * Pronunciation Safety Check:
     * Returns true only if Burmese voice pack is genuinely installed and functional.
     */
    fun canSafelyVocalizeMyanmar(): Boolean {
        return _voicePackStatus.value == VoicePackStatus.VOICE_PACK_INSTALLED
    }

    /**
     * Checks if given text can be safely vocalized without awful pronunciation glitches.
     */
    fun canSafelyVocalizeText(text: String): Boolean {
        return if (MyanmarScriptDetector.containsMyanmar(text)) {
            canSafelyVocalizeMyanmar()
        } else {
            // English or numeric text can be pronounced by standard engines
            _voicePackStatus.value != VoicePackStatus.INITIALIZATION_FAILED
        }
    }

    /**
     * OFFLINE SPEECH RECOVERY SYSTEM
     * Triggered when Burmese text was requested to be spoken, but voice data is missing.
     * Prevents garbled pronunciation, sounds a gentle notification chime, records failure,
     * emits recovery event, shows visual banner, and safely completes callback without hanging.
     */
    fun handleMissingVoicePackFailure(
        context: Context,
        text: String,
        onCompleteCallback: (() -> Unit)? = null
    ) {
        val currentStatus = _voicePackStatus.value
        val reasonMm = when (currentStatus) {
            VoicePackStatus.VOICE_PACK_MISSING_DATA ->
                "⚠️ ဖုန်းစနစ်၏ Google TTS တွင် မြန်မာအသံဒေတာ မရှိသေးပါသဖြင့် အသံမထွက်နိုင်ပါ။ Voice Pack ဒေါင်းလုဒ်ဆွဲရန် လိုအပ်ပါသည်။"
            VoicePackStatus.ENGINE_UNSUPPORTED ->
                "⚠️ လက်ရှိ အသုံးပြုနေသော TTS အင်ဂျင်တွင် မြန်မာဘာသာ မပါဝင်ပါ။ Google Speech Services သို့ ပြောင်းလဲပေးပါ။"
            VoicePackStatus.INITIALIZATION_FAILED ->
                "⚠️ ဖုန်းစနစ်၏ Text-To-Speech အင်ဂျင် ဖွင့်လှစ်၍ မရပါခင်ဗျာ။"
            else ->
                "⚠️ မြန်မာအသံထွက် စနစ် အဆင်မပြေပါသဖြင့် စာသားဖြင့်သာ ဖတ်ရှုနိုင်ပါသည်ခင်ဗျာ။"
        }

        Log.w(TAG, "Handled missing voice pack failure: status=$currentStatus, textPreview=${text.take(30)}")

        // 1. Play auditory completion cue so user knows response arrived
        playNotificationChime()

        // 2. Record truthful failure in Memory Engine
        FailureMemoryEngine.recordFailure(
            component = "TTS_SYNTHESIS",
            operation = "SPEAK_BURMESE",
            category = FailureCategory.RESOURCE,
            rootCauseMm = reasonMm,
            technicalDetails = "VoicePackStatus: $currentStatus, Engine: ${_currentEnginePackage.value}, Script: MYANMAR",
            suggestedRemediationMm = "TTS ဆက်တင်မှ မြန်မာစကားသံ (Voice Pack) ဒေါင်းလုဒ်ဆွဲပါ သို့မဟုတ် စာသားဖြင့် ဖတ်ရှုပါ"
        )

        // 3. Emit recovery event for visual subtitles / dialogs
        val event = SpeechRecoveryEvent(
            spokenText = text,
            reasonMm = reasonMm,
            canDownloadVoicePack = (currentStatus == VoicePackStatus.VOICE_PACK_MISSING_DATA || currentStatus == VoicePackStatus.ENGINE_UNSUPPORTED)
        )
        _speechRecoveryEvents.tryEmit(event)

        // 4. Show non-intrusive notification Toast on Main thread
        mainHandler.post {
            try {
                Toast.makeText(context.applicationContext, reasonMm, Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to display recovery toast: ${e.message}")
            }
        }

        // 5. Complete caller callback safely so session doesn't hang
        mainHandler.postDelayed({
            onCompleteCallback?.invoke()
        }, 150)
    }

    /**
     * Plays a pleasant gentle auditory tone as speech recovery feedback.
     */
    fun playNotificationChime() {
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
        } catch (e: Exception) {
            Log.w(TAG, "Error playing tone: ${e.message}")
        }
    }

    /**
     * Direct Navigation: Launches the official Android TTS Data Download Flow for Burmese
     */
    fun launchVoicePackDownload(context: Context): Boolean {
        // Option 1: Direct ACTION_INSTALL_TTS_DATA directed to Google TTS
        val googleInstallIntent = Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).apply {
            setPackage(GOOGLE_TTS_PACKAGE)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            context.startActivity(googleInstallIntent)
            Log.i(TAG, "Successfully launched Google TTS data install intent")
            return true
        } catch (e: Exception) {
            Log.w(TAG, "Direct Google TTS install intent failed: ${e.message}")
        }

        // Option 2: Generic ACTION_INSTALL_TTS_DATA
        val genericInstallIntent = Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            context.startActivity(genericInstallIntent)
            Log.i(TAG, "Successfully launched generic TTS data install intent")
            return true
        } catch (e: Exception) {
            Log.w(TAG, "Generic TTS data install intent failed: ${e.message}")
        }

        // Option 3: System TTS Settings
        val ttsSettingsIntent = Intent("com.android.settings.TTS_SETTINGS").apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            context.startActivity(ttsSettingsIntent)
            Log.i(TAG, "Successfully launched TTS_SETTINGS intent")
            return true
        } catch (e: Exception) {
            Log.w(TAG, "TTS Settings intent failed: ${e.message}")
        }

        // Option 4: General Settings fallback
        return try {
            val generalSettings = Intent(Settings.ACTION_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(generalSettings)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch any TTS configuration activity: ${e.message}")
            false
        }
    }

    /**
     * Opens Speech Services by Google on Google Play Store
     */
    fun launchGoogleTtsPlayStore(context: Context): Boolean {
        val appId = GOOGLE_TTS_PACKAGE
        try {
            val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$appId")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(marketIntent)
            return true
        } catch (e: ActivityNotFoundException) {
            return try {
                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$appId")).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(webIntent)
                true
            } catch (ex: Exception) {
                Log.e(TAG, "Cannot launch Play Store: ${ex.message}")
                false
            }
        }
    }

    fun shutdown() {
        try {
            toneGenerator?.release()
            toneGenerator = null
        } catch (e: Exception) {
            Log.w(TAG, "Error shutting down ToneGenerator: ${e.message}")
        }
    }
}
