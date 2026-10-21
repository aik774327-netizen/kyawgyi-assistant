package com.kyawgyi.voice

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.tts.TextToSpeech
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Locale

/**
 * Snapshot of Myanmar TTS voice pack availability on the device.
 */
data class MyanmarTtsVoiceDataStatus(
    val isOfflineF5OrVitsReady: Boolean = false,
    val isGoogleTtsInstalled: Boolean = false,
    val isMyanmarLocaleSupportedBySystem: Boolean = false,
    val requiresVoiceDataDownload: Boolean = false,
    val detectedSystemEngines: List<String> = emptyList(),
    val recommendedActionMessage: String = "Offline Neural Prosody / System TTS Ready"
)

/**
 * MyanmarTtsVoiceDataChecker (Requirements 12 — Myanmar TTS Voice Pack & Locale Inspector)
 *
 * Inspects both:
 * 1. Bundled/Downloaded Offline Myanmar Neural TTS models (`vits-piper-my_MM` / `f5-myanmar-int8.onnx`).
 * 2. Installed Android System TTS Engines (`com.google.android.tts`, `com.samsung.SMT`, `org.rhvoice.rhvoice`)
 *    and whether `my-MM` / `my` / `bur` voice data is installed or missing (`LANG_MISSING_DATA`).
 *
 * Provides [createInstallTtsDataIntent] so users can one-tap open the Android TTS Voice Data installer
 * if their device lacks offline Myanmar voice data.
 */
class MyanmarTtsVoiceDataChecker(
    private val context: Context,
    private val modelManager: ModelManager = ModelManager(context)
) {

    companion object {
        val MYANMAR_LOCALES: List<Locale> = listOf(
            Locale.forLanguageTag("my-MM"),
            Locale.forLanguageTag("my"),
            Locale("bur", "MMR")
        )

        private val KNOWN_TTS_PACKAGES = listOf(
            "com.google.android.tts",
            "com.samsung.SMT",
            "org.rhvoice.rhvoice",
            "com.reecedunn.espeak"
        )
    }

    private val _status = MutableStateFlow(MyanmarTtsVoiceDataStatus())
    val status: StateFlow<MyanmarTtsVoiceDataStatus> = _status.asStateFlow()

    init {
        inspectVoiceDataStatus()
    }

    /**
     * Checks installed TTS packages and local neural model files without blocking on TTS bind.
     */
    fun inspectVoiceDataStatus(ttsInstance: TextToSpeech? = null): MyanmarTtsVoiceDataStatus {
        val pm = context.packageManager
        val installedEngines = KNOWN_TTS_PACKAGES.filter { pkg ->
            isPackageInstalled(pm, pkg)
        }
        val googleTtsPresent = installedEngines.contains("com.google.android.tts")
        val offlineNeuralReady = modelManager.isSherpaOnnxModelAvailable()

        var localeSupported = false
        var missingData = false

        if (ttsInstance != null) {
            for (locale in MYANMAR_LOCALES) {
                val res = runCatching { ttsInstance.isLanguageAvailable(locale) }
                    .getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
                if (res >= TextToSpeech.LANG_AVAILABLE) {
                    localeSupported = true
                    break
                } else if (res == TextToSpeech.LANG_MISSING_DATA) {
                    missingData = true
                }
            }
        } else {
            // Heuristic when TextToSpeech instance is not bound yet
            localeSupported = googleTtsPresent || offlineNeuralReady
        }

        val actionMsg = when {
            offlineNeuralReady -> "Offline Neural Myanmar TTS Model အသင့်ရှိနေပါသည်"
            localeSupported -> "System Myanmar (my-MM) TTS Voice Data အသင့်ရှိနေပါသည်"
            missingData -> "မြန်မာအသံ Voice Data ဒေါင်းလုဒ်ဆွဲရန် လိုအပ်ပါသည် (Built-in Prosody Synthesizer အလုပ်လုပ်နေပါသည်)"
            else -> "Built-in Myanmar Acoustic Prosody Synthesizer ဖြင့် အသံထွက်ပေးနေပါသည်"
        }

        val newStatus = MyanmarTtsVoiceDataStatus(
            isOfflineF5OrVitsReady = offlineNeuralReady,
            isGoogleTtsInstalled = googleTtsPresent,
            isMyanmarLocaleSupportedBySystem = localeSupported,
            requiresVoiceDataDownload = missingData,
            detectedSystemEngines = installedEngines,
            recommendedActionMessage = actionMsg
        )
        _status.update { newStatus }
        return newStatus
    }

    /**
     * Builds an Android system [Intent] to launch the TTS voice data installer screen.
     */
    fun createInstallTtsDataIntent(): Intent =
        Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    /**
     * Builds an Android system [Intent] to check TTS voice data via `ACTION_CHECK_TTS_DATA`.
     */
    fun createCheckTtsDataIntent(): Intent =
        Intent(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA)

    private fun isPackageInstalled(pm: PackageManager, packageName: String): Boolean =
        runCatching {
            pm.getPackageInfo(packageName, 0)
            true
        }.getOrDefault(false)
}
