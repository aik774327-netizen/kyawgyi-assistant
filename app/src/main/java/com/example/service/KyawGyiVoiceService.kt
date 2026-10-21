package com.example.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.example.engine.BurmesePhoneticNormalizer
import com.example.engine.KyawGyiAppLauncher
import com.example.engine.OnDeviceJsonGuardrail
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * High-Speed Pipeline Execution Engine (< 100ms) for Kyaw Gyi Voice Assistant.
 * Seamlessly integrates Audio Ring Buffer, Phonetic Normalizer, App Launcher, and LLM Guardrails.
 */
class KyawGyiVoiceService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var appLauncher: KyawGyiAppLauncher

    override fun onCreate() {
        super.onCreate()
        appLauncher = KyawGyiAppLauncher(this)
        serviceScope.launch {
            appLauncher.refreshInstalledAppsCache()
        }
        Log.i("KyawGyiVoiceService", "High-speed voice engine pipeline initialized")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * High-speed Pipeline Execution Engine (< 100ms)
     */
    fun processRawAudioText(rawTextFromStt: String, onSpeechOutput: (String) -> Unit) {
        serviceScope.launch(Dispatchers.Default) {
            // Step 1: Phonetic Normalization (0ms allocation path)
            val normalizedCommand = BurmesePhoneticNormalizer.normalizeBurmeseText(rawTextFromStt)

            // Step 2: Fault-Tolerant Guardrail Parsing
            val safeResult = OnDeviceJsonGuardrail.parseSafely(normalizedCommand)

            // Step 3: Intent & Action Execution
            val parsedCommand = MyanmarCommands.parseCommand(normalizedCommand)

            when (parsedCommand.first) {
                MyanmarCommandType.OPEN_APP, MyanmarCommandType.CUSTOM_SHORTCUT -> {
                    val target = parsedCommand.second
                    val (launched, msg) = appLauncher.resolveAndLaunch(target)
                    onSpeechOutput(msg)
                }
                MyanmarCommandType.CLOSE_APP -> {
                    KyawGyiAccessibilityService.instance?.executeVoiceCommand("ပင်မ")
                    val target = parsedCommand.second
                    val speech = if (target.isNotBlank()) "$target ကို ပိတ်ပေးလိုက်ပါပြီခင်ဗျာ" else "အက်ပ်ကို ပိတ်လိုက်ပါပြီခင်ဗျာ"
                    onSpeechOutput(speech)
                }
                MyanmarCommandType.HOME -> {
                    KyawGyiAccessibilityService.instance?.executeVoiceCommand("ပင်မမျက်နှာပြင်")
                    onSpeechOutput("ပင်မမျက်နှာပြင်သို့ ပြန်သွားပါပြီ")
                }
                MyanmarCommandType.BACK -> {
                    KyawGyiAccessibilityService.instance?.executeVoiceCommand("နောက်သို့")
                    onSpeechOutput("နောက်သို့ ပြန်သွားပါပြီ")
                }
                MyanmarCommandType.RECENTS -> {
                    KyawGyiAccessibilityService.instance?.executeVoiceCommand("မကြာသေးမီက")
                    onSpeechOutput("ဖွင့်ထားသော အက်ပ်များ ပြလိုက်ပါပြီ")
                }
                MyanmarCommandType.PLAY_MUSIC -> {
                    val executor = com.example.actions.KyawGyiActionExecutor(this@KyawGyiVoiceService)
                    val result = executor.playLocalAudioTrack(rawTextFromStt)
                    onSpeechOutput(result.userMessage)
                }
                MyanmarCommandType.PAUSE_MUSIC -> {
                    val player = com.example.audio.KyawGyiMusicPlayer.getInstance(this@KyawGyiVoiceService)
                    onSpeechOutput(player.pause())
                }
                MyanmarCommandType.STOP_MUSIC -> {
                    val player = com.example.audio.KyawGyiMusicPlayer.getInstance(this@KyawGyiVoiceService)
                    onSpeechOutput(player.stop())
                }
                MyanmarCommandType.CALL_CONTACT -> {
                    val executor = com.example.actions.KyawGyiActionExecutor(this@KyawGyiVoiceService)
                    val result = executor.callContact(rawTextFromStt)
                    onSpeechOutput(result.userMessage)
                }
                MyanmarCommandType.READ_SCREEN -> {
                    val speech = KyawGyiVoiceSession.readScreenContentAloud(this@KyawGyiVoiceService)
                    onSpeechOutput(speech)
                }
                else -> {
                    val speech = if (safeResult.isValidJson) {
                        safeResult.fallbackBurmeseSpeech
                    } else {
                        safeResult.rawCleanText.ifBlank { safeResult.fallbackBurmeseSpeech }
                    }
                    onSpeechOutput(speech)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
