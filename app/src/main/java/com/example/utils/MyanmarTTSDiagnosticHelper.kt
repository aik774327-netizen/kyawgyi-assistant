package com.example.utils

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Diagnostic results for Myanmar Text-to-Speech support on user's device.
 */
enum class TtsSupportStatus {
    CHECKING,
    READY,
    MISSING_DATA,
    NOT_SUPPORTED,
    INITIALIZATION_FAILED
}

data class TtsDiagnosticReport(
    val status: TtsSupportStatus = TtsSupportStatus.CHECKING,
    val currentEnginePackage: String = "",
    val availableEngines: List<String> = emptyList(),
    val isGoogleTtsInstalled: Boolean = false,
    val isMyanmarSupported: Boolean = false,
    val messageMM: String = "စစ်ဆေးနေပါသည်...",
    val recommendationMM: String = ""
)

/**
 * Diagnostics & Voice Guidance System for Myanmar Text-to-Speech (TTS).
 * Verifies if Android OS has Google Speech Services or Burmese Voice Pack installed,
 * providing one-tap solutions to install or switch to a compatible voice engine.
 */
class MyanmarTTSDiagnosticHelper private constructor(private val context: Context) : TextToSpeech.OnInitListener {

    private val TAG = "TTSDiagnosticHelper"
    private var tts: TextToSpeech? = null

    private val _report = MutableStateFlow(TtsDiagnosticReport())
    val report: StateFlow<TtsDiagnosticReport> = _report.asStateFlow()

    init {
        runDiagnostics()
    }

    fun runDiagnostics() {
        _report.value = TtsDiagnosticReport(
            status = TtsSupportStatus.CHECKING,
            messageMM = "TTS အသံစနစ်ကို စစ်ဆေးနေပါသည်..."
        )
        try {
            tts?.shutdown()
            val preferredEngine = MyanmarTTSVoicePackManager.getBestTtsEnginePackage(context)
            tts = if (preferredEngine != null) {
                TextToSpeech(context.applicationContext, this, preferredEngine)
            } else {
                TextToSpeech(context.applicationContext, this)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initiating TTS diagnostics: ${e.message}", e)
            _report.value = TtsDiagnosticReport(
                status = TtsSupportStatus.INITIALIZATION_FAILED,
                messageMM = "ဖုန်းစနစ်၏ Text-To-Speech အင်ဂျင်ကို ဖွင့်လှစ်၍ မရပါ",
                recommendationMM = "Google Speech Services အက်ပ်အား Google Play Store မှ ပြန်လည်ဒေါင်းလုဒ်ရယူပါ"
            )
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val engine = tts?.defaultEngine ?: ""
            val enginesList = tts?.engines?.map { it.name } ?: emptyList()
            val hasGoogleTts = enginesList.any { it.contains("google", ignoreCase = true) }

            val myanmarLocale = Locale.forLanguageTag("my-MM")
            val altMyanmar = Locale.forLanguageTag("my")

            var langResult = tts?.isLanguageAvailable(myanmarLocale) ?: TextToSpeech.LANG_NOT_SUPPORTED
            if (langResult == TextToSpeech.LANG_NOT_SUPPORTED || langResult == TextToSpeech.LANG_MISSING_DATA) {
                langResult = tts?.isLanguageAvailable(altMyanmar) ?: TextToSpeech.LANG_NOT_SUPPORTED
            }

            when (langResult) {
                TextToSpeech.LANG_AVAILABLE,
                TextToSpeech.LANG_COUNTRY_AVAILABLE,
                TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE -> {
                    _report.value = TtsDiagnosticReport(
                        status = TtsSupportStatus.READY,
                        currentEnginePackage = engine,
                        availableEngines = enginesList,
                        isGoogleTtsInstalled = hasGoogleTts,
                        isMyanmarSupported = true,
                        messageMM = "မြန်မာစကားသံ (my-MM) အပြည့်အဝ ပံ့ပိုးထားပြီး အဆင်သင့်ဖြစ်ပါသည် ✓",
                        recommendationMM = "ကျော်ကြီး၏ အသံထွက်စနစ်သည် ကောင်းမွန်စွာ အလုပ်လုပ်နေပါသည်"
                    )
                }
                TextToSpeech.LANG_MISSING_DATA -> {
                    _report.value = TtsDiagnosticReport(
                        status = TtsSupportStatus.MISSING_DATA,
                        currentEnginePackage = engine,
                        availableEngines = enginesList,
                        isGoogleTtsInstalled = hasGoogleTts,
                        isMyanmarSupported = true,
                        messageMM = "မြန်မာစကားသံ ပံ့ပိုးသော်လည်း အသံဒေတာ မဒေါင်းလုဒ်ရသေးပါ",
                        recommendationMM = "TTS ဆက်တင်သို့ သွားရောက်၍ မြန်မာစကားသံ (Voice Pack) ကို ဒေါင်းလုဒ် ရယူပေးပါ"
                    )
                }
                TextToSpeech.LANG_NOT_SUPPORTED -> {
                    _report.value = TtsDiagnosticReport(
                        status = TtsSupportStatus.NOT_SUPPORTED,
                        currentEnginePackage = engine,
                        availableEngines = enginesList,
                        isGoogleTtsInstalled = hasGoogleTts,
                        isMyanmarSupported = false,
                        messageMM = "လက်ရှိ အသုံးပြုနေသော TTS အင်ဂျင်တွင် မြန်မာဘာသာ မပါဝင်ပါ",
                        recommendationMM = if (hasGoogleTts) {
                            "Preferred Engine အား Speech Services by Google သို့ ပြောင်းလဲပေးပါ"
                        } else {
                            "Google Play Store မှ Speech Services by Google ကို ဒေါင်းလုဒ် ရယူပေးပါ"
                        }
                    )
                }
                else -> {
                    _report.value = TtsDiagnosticReport(
                        status = TtsSupportStatus.NOT_SUPPORTED,
                        currentEnginePackage = engine,
                        availableEngines = enginesList,
                        isGoogleTtsInstalled = hasGoogleTts,
                        isMyanmarSupported = false,
                        messageMM = "မြန်မာစကားသံ အခြေအနေ မသေချာပါ",
                        recommendationMM = "စနစ် ဆက်တင်တွင် စစ်ဆေးပေးပါ"
                    )
                }
            }
        } else {
            _report.value = TtsDiagnosticReport(
                status = TtsSupportStatus.INITIALIZATION_FAILED,
                messageMM = "TTS စနစ် စတင်ရာတွင် အဆင်မပြေပါ (Status: $status)",
                recommendationMM = "ဖုန်း၏ Text-to-Speech Output ဆက်တင်ကို စစ်ဆေးပါ"
            )
        }
    }

    /**
     * Direct Navigation: Launches the official Android TTS Voice Data Download Flow for Burmese
     */
    fun downloadVoicePack(targetContext: Context): Boolean {
        return MyanmarTTSVoicePackManager.getInstance(targetContext).launchVoicePackDownload(targetContext)
    }

    /**
     * Direct Navigation: Opens Android System Text-To-Speech Settings
     */
    fun openTtsSettings(targetContext: Context) {
        val intent = Intent("com.android.settings.TTS_SETTINGS").apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            targetContext.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            try {
                val fallbackIntent = Intent(Settings.ACTION_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                targetContext.startActivity(fallbackIntent)
            } catch (ex: Exception) {
                Log.e(TAG, "Cannot open TTS settings: ${ex.message}")
            }
        }
    }

    /**
     * Open Google Speech Services (Google TTS) on Play Store
     */
    fun openGoogleTtsOnPlayStore(targetContext: Context) {
        val appId = "com.google.android.tts"
        try {
            val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$appId")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            targetContext.startActivity(marketIntent)
        } catch (e: ActivityNotFoundException) {
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$appId")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            targetContext.startActivity(webIntent)
        }
    }

    /**
     * Test speech utterance in Burmese
     */
    fun testSpeakBurmese(targetContext: Context, customPhrase: String? = null) {
        val phrase = customPhrase ?: "မင်္ဂလာပါခင်ဗျာ၊ ကျော်ကြီး မြန်မာစကားသံ စနစ် ကောင်းမွန်စွာ အလုပ်လုပ်နေပါသည်"
        MyanmarTTSEngine.getInstance(targetContext).speak(phrase)
    }

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
            tts = null
        } catch (e: Exception) {
            Log.w(TAG, "Error shutting down diagnostic TTS: ${e.message}")
        }
    }

    companion object {
        @Volatile
        private var instance: MyanmarTTSDiagnosticHelper? = null

        fun getInstance(context: Context): MyanmarTTSDiagnosticHelper {
            return instance ?: synchronized(this) {
                instance ?: MyanmarTTSDiagnosticHelper(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }
}
