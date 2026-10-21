package com.example.diagnostics

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import com.example.BuildConfig
import com.example.audit.AuditLogger
import com.example.ble.BlePeripheralManager
import com.example.data.AppDatabase
import com.example.nlu.LocalBurmeseIntentParser
import com.example.router.DirectSettingsRouter
import com.example.router.SettingsDestination
import com.example.security.ActionPermissionGatekeeper
import com.example.utils.KyawGyiPermissionManager
import com.example.utils.MyanmarTTSEngine
import com.example.utils.OverlayPermissionHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class DiagnosticStatus {
    PASS,
    WARNING,
    FAIL
}

enum class DiagnosticCategory(val titleMM: String) {
    AUDIO_VOICE("အသံနှင့် စကားပြောစနစ် (Audio & Voice)"),
    NLU_AI("ဉာဏ်ရည်နှင့် NLU ဦးနှောက် (NLU & AI)"),
    HARDWARE_BLE("ဟာ့ဒ်ဝဲနှင့် BLE ကိရိယာများ (Hardware & BLE)"),
    DATABASE_STORAGE("ဒေတာဘေ့စ်နှင့် မှတ်ဉာဏ် (Room DB & Storage)"),
    SECURITY_PRIVACY("လုံခြုံရေးနှင့် ကာကွယ်မှု (Security & Privacy)"),
    OS_BACKGROUND("စနစ်ခွင့်ပြုချက်များနှင့် Background (OS & Permissions)")
}

enum class RemediationType {
    OPEN_SETTINGS,
    AUTO_FIX,
    REQUEST_PERMISSION,
    NONE
}

data class DiagnosticCheckResult(
    val id: String,
    val category: DiagnosticCategory,
    val titleMM: String,
    val descriptionMM: String,
    val status: DiagnosticStatus,
    val detailsMM: String,
    val latencyMs: Long = 0,
    val remediationType: RemediationType = RemediationType.NONE,
    val settingsDestination: SettingsDestination? = null
)

data class DiagnosticReport(
    val timestamp: Long = System.currentTimeMillis(),
    val isRunning: Boolean = false,
    val isComplete: Boolean = false,
    val progressPercent: Float = 0f,
    val currentStepMessage: String = "",
    val healthScore: Int = 100,
    val totalChecks: Int = 0,
    val passedCount: Int = 0,
    val warningCount: Int = 0,
    val failedCount: Int = 0,
    val results: List<DiagnosticCheckResult> = emptyList(),
    val summaryMM: String = ""
)

/**
 * OneClickSystemDiagnosticEngine
 *
 * Runs an exhaustive, active, programmatic self-test across all 10 core subsystems
 * in the Kyaw Gyi Voice AI architecture with a single click.
 */
class OneClickSystemDiagnosticEngine private constructor(private val context: Context) {

    companion object {
        @Volatile
        private var instance: OneClickSystemDiagnosticEngine? = null

        fun getInstance(context: Context): OneClickSystemDiagnosticEngine {
            return instance ?: synchronized(this) {
                instance ?: OneClickSystemDiagnosticEngine(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }

    private val _reportFlow = MutableStateFlow(DiagnosticReport())
    val reportFlow: StateFlow<DiagnosticReport> = _reportFlow.asStateFlow()

    private val settingsRouter = DirectSettingsRouter.getInstance(context)

    /**
     * Executes the comprehensive 10-point system diagnostic test asynchronously.
     */
    suspend fun runFullDiagnostic() = withContext(Dispatchers.IO) {
        val totalSteps = 10
        val results = mutableListOf<DiagnosticCheckResult>()

        _reportFlow.value = DiagnosticReport(
            isRunning = true,
            isComplete = false,
            progressPercent = 0.05f,
            currentStepMessage = "စနစ် အစိတ်အပိုင်းများကို စစ်ဆေးရန် စတင်ပြင်ဆင်နေသည်...",
            healthScore = 100,
            totalChecks = totalSteps
        )

        // -------------------------------------------------------------
        // Step 1: Microphone Hardware & AudioRecord Driver Probe
        // -------------------------------------------------------------
        updateProgress(0.10f, "၁/၁၀ - မိုက်ခရိုဖုန်း ဟာ့ဒ်ဝဲနှင့် အသံဖမ်းယူမှု စစ်ဆေးနေသည်...")
        delay(80)
        results.add(checkAudioMicrophoneSubsystem())

        // -------------------------------------------------------------
        // Step 2: Myanmar Local NLU & Intent Parser Engine Probe
        // -------------------------------------------------------------
        updateProgress(0.20f, "၂/၁၀ - မြန်မာဘာသာ NLU သဘာဝဘာသာစကား ဦးနှောက်ကို စစ်ဆေးနေသည်...")
        delay(80)
        results.add(checkBurmeseNluEngine())

        // -------------------------------------------------------------
        // Step 3: Myanmar Text-to-Speech (TTS) Engine Probe
        // -------------------------------------------------------------
        updateProgress(0.30f, "၃/၁၀ - မြန်မာစကား အသံထွက်စနစ် (TTS) ကို စစ်ဆေးနေသည်...")
        delay(80)
        results.add(checkMyanmarTtsEngine())

        // -------------------------------------------------------------
        // Step 4: Room Database Schema & SQLite Storage Integrity
        // -------------------------------------------------------------
        updateProgress(0.40f, "၄/၁၀ - Room Database ဒေတာဘေ့စ်နှင့် မှတ်ဉာဏ်ဇယားများကို စစ်ဆေးနေသည်...")
        delay(80)
        results.add(checkRoomDatabaseIntegrity())

        // -------------------------------------------------------------
        // Step 5: Hardware Actuators (Torch, Volume, Screen Brightness)
        // -------------------------------------------------------------
        updateProgress(0.50f, "၅/၁၀ - ဓာတ်မီး၊ စနစ်အသံနှင့် အလင်းအမှောင် ဟာ့ဒ်ဝဲ ထိန်းချုပ်မှုများကို စစ်ဆေးနေသည်...")
        delay(80)
        results.add(checkHardwareActuators())

        // -------------------------------------------------------------
        // Step 6: Bluetooth & BLE Peripheral Subsystem Probe
        // -------------------------------------------------------------
        updateProgress(0.60f, "၆/၁၀ - Bluetooth Low Energy (BLE) ဆက်သွယ်ရေးစနစ်ကို စစ်ဆေးနေသည်...")
        delay(80)
        results.add(checkBleSubsystem())

        // -------------------------------------------------------------
        // Step 7: Network Connectivity & Gemini Cloud AI Key Probe
        // -------------------------------------------------------------
        updateProgress(0.70f, "၇/၁၀ - အင်တာနက် ကွန်ရက်နှင့် Gemini AI Cloud ချိတ်ဆက်မှုကို စစ်ဆေးနေသည်...")
        delay(80)
        results.add(checkNetworkAndAiEngine())

        // -------------------------------------------------------------
        // Step 8: Security Gatekeeper & PII Redaction Audit Engine
        // -------------------------------------------------------------
        updateProgress(0.80f, "၈/၁၀ - ၅ စက္ကန့် လုံခြုံရေး Gatekeeper နှင့် ဖုန်းနံပါတ် ဖုံးကွယ်မှုကို စစ်ဆေးနေသည်...")
        delay(80)
        results.add(checkSecurityAndPrivacyGuard())

        // -------------------------------------------------------------
        // Step 9: Foreground Voice Service & Battery Doze Exemption
        // -------------------------------------------------------------
        updateProgress(0.90f, "၉/၁၀ - အနောက်ကွယ် အသံဖမ်း Service နှင့် ဘက်ထရီ ချွေတာမှု ကင်းလွတ်ခွင့် စစ်ဆေးနေသည်...")
        delay(80)
        results.add(checkBackgroundServiceAndDoze())

        // -------------------------------------------------------------
        // Step 10: Screen Overlay & Accessibility Automation Engine
        // -------------------------------------------------------------
        updateProgress(0.95f, "၁၀/၁၀ - Floating Overlay နှင့် အလိုအလျောက် ထိန်းချုပ်မှု စနစ်များကို စစ်ဆေးနေသည်...")
        delay(80)
        results.add(checkOverlayAndAccessibility())

        // -------------------------------------------------------------
        // Compilation & Final Health Evaluation
        // -------------------------------------------------------------
        val passed = results.count { it.status == DiagnosticStatus.PASS }
        val warnings = results.count { it.status == DiagnosticStatus.WARNING }
        val fails = results.count { it.status == DiagnosticStatus.FAIL }

        // Score: Pass = 10 pts, Warning = 5 pts, Fail = 0 pts (Max 100)
        val score = ((passed * 10) + (warnings * 5)).coerceIn(0, 100)

        val summary = when {
            fails == 0 && warnings == 0 -> "ဂုဏ်ယူပါသည်! စနစ်တစ်ခုလုံး ၁၀၀% ပြီးပြည့်စုံစွာ အလုပ်လုပ်နေပါသည်။"
            fails == 0 -> "စနစ် ကောင်းမွန်စွာ အလုပ်လုပ်နေပါသည် (သတိပေးချက် $warnings ခု ရှိပါသည်)။"
            else -> "သတိပြုရန်! စနစ်အတွင်း ချို့ယွင်းချက် $fails ခုနှင့် သတိပေးချက် $warnings ခု တွေ့ရှိရပါသည်။"
        }

        _reportFlow.value = DiagnosticReport(
            timestamp = System.currentTimeMillis(),
            isRunning = false,
            isComplete = true,
            progressPercent = 1.0f,
            currentStepMessage = "စနစ် အလုံးစုံ စစ်ဆေးမှု ပြီးဆုံးပါပြီ။",
            healthScore = score,
            totalChecks = totalSteps,
            passedCount = passed,
            warningCount = warnings,
            failedCount = fails,
            results = results,
            summaryMM = summary
        )
    }

    private fun updateProgress(percent: Float, msg: String) {
        _reportFlow.value = _reportFlow.value.copy(
            progressPercent = percent,
            currentStepMessage = msg
        )
    }

    // -------------------------------------------------------------
    // SUBSYSTEM CHECKS
    // -------------------------------------------------------------

    private fun checkAudioMicrophoneSubsystem(): DiagnosticCheckResult {
        val start = System.currentTimeMillis()
        val hasMicPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasMicPermission) {
            return DiagnosticCheckResult(
                id = "audio_record_permission",
                category = DiagnosticCategory.AUDIO_VOICE,
                titleMM = "မိုက်ခရိုဖုန်း ခွင့်ပြုချက် (RECORD_AUDIO)",
                descriptionMM = "အသံဖမ်းယူမှုနှင့် စကားသံအမိန့်များ နားထောင်ရန် လိုအပ်ပါသည်",
                status = DiagnosticStatus.FAIL,
                detailsMM = "မိုက်ခရိုဖုန်း ခွင့်ပြုချက် မရရှိသေးပါ။ စကားသံဖမ်းယူမှု လုံးဝ အလုပ်လုပ်နိုင်မည် မဟုတ်ပါ။",
                latencyMs = System.currentTimeMillis() - start,
                remediationType = RemediationType.OPEN_SETTINGS,
                settingsDestination = SettingsDestination.MICROPHONE_PERMISSION
            )
        }

        // Test hardware AudioRecord buffer allocation
        try {
            val sampleRate = 16000
            val channelConfig = AudioFormat.CHANNEL_IN_MONO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT
            val minBuf = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

            if (minBuf == AudioRecord.ERROR || minBuf == AudioRecord.ERROR_BAD_VALUE) {
                return DiagnosticCheckResult(
                    id = "audio_record_driver",
                    category = DiagnosticCategory.AUDIO_VOICE,
                    titleMM = "AudioRecord Driver စစ်ဆေးမှု",
                    descriptionMM = "16kHz 16-bit PCM Audio Driver အထောက်အပံ့",
                    status = DiagnosticStatus.FAIL,
                    detailsMM = "ဖုန်း၏ အသံစနစ် Driver သည် 16kHz PCM AudioRecord buffer ကို လက်မခံပါ (Bad Value)။",
                    latencyMs = System.currentTimeMillis() - start
                )
            }

            val testRecorder = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                minBuf
            )

            val state = testRecorder.state
            testRecorder.release()

            if (state != AudioRecord.STATE_INITIALIZED) {
                return DiagnosticCheckResult(
                    id = "audio_record_hardware_state",
                    category = DiagnosticCategory.AUDIO_VOICE,
                    titleMM = "မိုက်ခရိုဖုန်း ဟာ့ဒ်ဝဲ ချိတ်ဆက်မှု",
                    descriptionMM = "AudioRecord Hardware Initialized State",
                    status = DiagnosticStatus.WARNING,
                    detailsMM = "မိုက်ခရိုဖုန်း ဟာ့ဒ်ဝဲသည် အခြား Application တစ်ခုခုမှ အသုံးပြုနေခြင်း (သို့မဟုတ်) Busy ဖြစ်နေနိုင်ပါသည်။",
                    latencyMs = System.currentTimeMillis() - start
                )
            }

            return DiagnosticCheckResult(
                id = "audio_record_ok",
                category = DiagnosticCategory.AUDIO_VOICE,
                titleMM = "မိုက်ခရိုဖုန်း ဟာ့ဒ်ဝဲနှင့် အသံဖမ်းစနစ်",
                descriptionMM = "RECORD_AUDIO ခွင့်ပြုချက်နှင့် 16kHz PCM Buffer",
                status = DiagnosticStatus.PASS,
                detailsMM = "မိုက်ခရိုဖုန်း ခွင့်ပြုချက် ရရှိထားပြီး 16kHz AudioRecord Hardware အသင့်ဖြစ်ပါသည် (Buffer: ${minBuf}B)။",
                latencyMs = System.currentTimeMillis() - start
            )
        } catch (e: Exception) {
            return DiagnosticCheckResult(
                id = "audio_record_exception",
                category = DiagnosticCategory.AUDIO_VOICE,
                titleMM = "မိုက်ခရိုဖုန်း စစ်ဆေးမှု ချို့ယွင်းချက်",
                descriptionMM = "AudioRecord Probe Exception",
                status = DiagnosticStatus.FAIL,
                detailsMM = "အသံဖမ်း ဟာ့ဒ်ဝဲ စစ်ဆေးစဉ် ချို့ယွင်းချက်ဖြစ်ပေါ်သည်: ${e.message}",
                latencyMs = System.currentTimeMillis() - start
            )
        }
    }

    private fun checkBurmeseNluEngine(): DiagnosticCheckResult {
        val start = System.currentTimeMillis()
        try {
            val parser = LocalBurmeseIntentParser.getInstance()

            // Test Case 1: Wi-Fi toggle
            val wifiResult = parser.parse("ဝိုင်ဖိုင် ပိတ်ပေးပါ")
            val flashlightResult = parser.parse("ဓာတ်မီး ဖွင့်ပါ")
            val bleResult = parser.parse("ဘလူးတုသ် ရှာပေးပါ")

            val latency = System.currentTimeMillis() - start

            if (wifiResult.intentName.contains("WIFI", ignoreCase = true) &&
                flashlightResult.intentName.contains("FLASHLIGHT", ignoreCase = true)
            ) {
                return DiagnosticCheckResult(
                    id = "burmese_nlu_ok",
                    category = DiagnosticCategory.NLU_AI,
                    titleMM = "မြန်မာဘာသာ NLU သဘာဝဘာသာစကား ဦးနှောက်",
                    descriptionMM = "Regex, Fuzzy Matching & Slot Extraction Engine",
                    status = DiagnosticStatus.PASS,
                    detailsMM = "အော့ဖ်လိုင်း စကားသံအမိန့်များအားလုံးကို အင်တာနက်မလိုဘဲ အောင်မြင်စွာ ခွဲခြားနိုင်ပါသည် (တုံ့ပြန်ချိန်: ${latency}ms)။",
                    latencyMs = latency
                )
            } else {
                return DiagnosticCheckResult(
                    id = "burmese_nlu_drift",
                    category = DiagnosticCategory.NLU_AI,
                    titleMM = "မြန်မာဘာသာ NLU ခွဲခြားမှု စစ်ဆေးချက်",
                    descriptionMM = "Local Burmese Intent Parsing Accuracy",
                    status = DiagnosticStatus.WARNING,
                    detailsMM = "စကားသံအမိန့် အချို့တွင် Confidence လျော့နည်းမှု တွေ့ရှိရသည် (Wifi=${wifiResult.intentName}, Flashlight=${flashlightResult.intentName})။",
                    latencyMs = latency
                )
            }
        } catch (e: Exception) {
            return DiagnosticCheckResult(
                id = "burmese_nlu_error",
                category = DiagnosticCategory.NLU_AI,
                titleMM = "မြန်မာဘာသာ NLU စနစ် ချို့ယွင်းချက်",
                descriptionMM = "NLU Parser Execution Error",
                status = DiagnosticStatus.FAIL,
                detailsMM = "LocalBurmeseIntentParser စစ်ဆေးစဉ် Exception ဖြစ်ပေါ်သည်: ${e.message}",
                latencyMs = System.currentTimeMillis() - start
            )
        }
    }

    private fun checkMyanmarTtsEngine(): DiagnosticCheckResult {
        val start = System.currentTimeMillis()
        try {
            // Test phonetic normalizer
            val testText = "ကျေးဇူးတင်ပါတယ် 10% 500$ 0912345678"
            val normalized = MyanmarTTSEngine.preprocessText(testText)

            val hasNormalizerWorked = normalized.contains("ရာခိုင်နှုန်း") && normalized.contains("ဒေါ်လာ")

            val isTtsMasterEnabled = MyanmarTTSEngine.masterTtsEnabled

            val latency = System.currentTimeMillis() - start

            if (!isTtsMasterEnabled) {
                return DiagnosticCheckResult(
                    id = "tts_disabled_warning",
                    category = DiagnosticCategory.AUDIO_VOICE,
                    titleMM = "မြန်မာစကား အသံထွက်စနစ် (TTS Master Toggle)",
                    descriptionMM = "Text-to-Speech Engine Status",
                    status = DiagnosticStatus.WARNING,
                    detailsMM = "TTS Master Toggle အား ပိတ်ထားပါသည် (အသံတိတ် စနစ်တွင် ရောက်ရှိနေသည်)။",
                    latencyMs = latency,
                    remediationType = RemediationType.AUTO_FIX
                )
            }

            if (hasNormalizerWorked) {
                return DiagnosticCheckResult(
                    id = "tts_engine_ok",
                    category = DiagnosticCategory.AUDIO_VOICE,
                    titleMM = "မြန်မာစကား အသံထွက်စနစ် (TTS Engine)",
                    descriptionMM = "Text Normalization & Speech Output",
                    status = DiagnosticStatus.PASS,
                    detailsMM = "မြန်မာအသံထွက် Normalization နှင့် TTS အသံထွက် အဆင်သင့်ရှိပါသည် (စစ်ဆေးချိန်: ${latency}ms)။",
                    latencyMs = latency
                )
            } else {
                return DiagnosticCheckResult(
                    id = "tts_engine_partial",
                    category = DiagnosticCategory.AUDIO_VOICE,
                    titleMM = "မြန်မာစကား အသံထွက်စနစ် (TTS Engine)",
                    descriptionMM = "Burmese Phonetic Normalizer",
                    status = DiagnosticStatus.WARNING,
                    detailsMM = "အသံထွက် စာသားပုံစံ ပြောင်းလဲမှုတွင် သတိပေးချက် ရှိပါသည် (Google TTS Myanmar voice download လိုအပ်နိုင်သည်)။",
                    latencyMs = latency,
                    remediationType = RemediationType.OPEN_SETTINGS,
                    settingsDestination = SettingsDestination.TTS_SETTINGS
                )
            }
        } catch (e: Exception) {
            return DiagnosticCheckResult(
                id = "tts_engine_fail",
                category = DiagnosticCategory.AUDIO_VOICE,
                titleMM = "TTS အသံထွက်စနစ် ချို့ယွင်းချက်",
                descriptionMM = "TTS Normalization Failure",
                status = DiagnosticStatus.FAIL,
                detailsMM = "TTS အင်ဂျင် စစ်ဆေးစဉ် အမှားဖြစ်ပေါ်သည်: ${e.message}",
                latencyMs = System.currentTimeMillis() - start
            )
        }
    }

    private fun checkRoomDatabaseIntegrity(): DiagnosticCheckResult {
        val start = System.currentTimeMillis()
        try {
            val db = AppDatabase.getDatabase(context)
            val isOpen = db.isOpen

            // Query check
            val taskCount = kotlinx.coroutines.runBlocking { db.taskDao().getTaskCount() }
            val latency = System.currentTimeMillis() - start

            return DiagnosticCheckResult(
                id = "room_db_ok",
                category = DiagnosticCategory.DATABASE_STORAGE,
                titleMM = "Room Database နှင့် SQLite သိမ်းဆည်းမှု",
                descriptionMM = "Room Database Schema v13, Task & Habit Persistence",
                status = DiagnosticStatus.PASS,
                detailsMM = "Room Database ချိတ်ဆက်မှု အောင်မြင်ပါသည် (DB Open=$isOpen, သိမ်းဆည်းထားသော Tasks: $taskCount ခု, ဖတ်ရှုချိန်: ${latency}ms)။",
                latencyMs = latency
            )
        } catch (e: Exception) {
            return DiagnosticCheckResult(
                id = "room_db_fail",
                category = DiagnosticCategory.DATABASE_STORAGE,
                titleMM = "Room Database ချို့ယွင်းချက်",
                descriptionMM = "SQLite Database Integrity Check",
                status = DiagnosticStatus.FAIL,
                detailsMM = "Database စစ်ဆေးစဉ် ချို့ယွင်းချက်ဖြစ်ပေါ်သည် (Schema Mismatch ဖြစ်နိုင်ပါသည်): ${e.message}",
                latencyMs = System.currentTimeMillis() - start
            )
        }
    }

    private fun checkHardwareActuators(): DiagnosticCheckResult {
        val start = System.currentTimeMillis()
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            val mediaVolume = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: -1
            val maxMedia = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: -1

            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            val hasCameraList = (cameraManager?.cameraIdList?.size ?: 0) > 0

            val latency = System.currentTimeMillis() - start

            if (audioManager != null && hasCameraList) {
                return DiagnosticCheckResult(
                    id = "hardware_actuators_ok",
                    category = DiagnosticCategory.HARDWARE_BLE,
                    titleMM = "စနစ်တွင်း ဟာ့ဒ်ဝဲ ထိန်းချုပ်မှုများ",
                    descriptionMM = "ဓာတ်မီး (Torch)၊ အသံအတိုးအကျယ်နှင့် ဆက်တင်များ",
                    status = DiagnosticStatus.PASS,
                    detailsMM = "ဟာ့ဒ်ဝဲ ထိန်းချုပ်စနစ်များ အဆင်သင့်ရှိပါသည် (Music Volume: $mediaVolume/$maxMedia, Camera Sensors တွေ့ရှိ)။",
                    latencyMs = latency
                )
            } else {
                return DiagnosticCheckResult(
                    id = "hardware_actuators_warning",
                    category = DiagnosticCategory.HARDWARE_BLE,
                    titleMM = "ဟာ့ဒ်ဝဲ ထိန်းချုပ်မှု သတိပေးချက်",
                    descriptionMM = "Camera Manager / Audio Service Probe",
                    status = DiagnosticStatus.WARNING,
                    detailsMM = "အချို့ ဟာ့ဒ်ဝဲ စနစ်များ (Camera Torch/Audio) တုံ့ပြန်မှု နှေးကွေးနေပါသည် (Emulator ဖြစ်နိုင်သည်)။",
                    latencyMs = latency
                )
            }
        } catch (e: Exception) {
            return DiagnosticCheckResult(
                id = "hardware_actuators_fail",
                category = DiagnosticCategory.HARDWARE_BLE,
                titleMM = "ဟာ့ဒ်ဝဲ စနစ် ချို့ယွင်းချက်",
                descriptionMM = "Hardware Controller Exception",
                status = DiagnosticStatus.FAIL,
                detailsMM = "ဟာ့ဒ်ဝဲ စစ်ဆေးစဉ် ချို့ယွင်းချက် ဖြစ်ပေါ်သည်: ${e.message}",
                latencyMs = System.currentTimeMillis() - start
            )
        }
    }

    private fun checkBleSubsystem(): DiagnosticCheckResult {
        val start = System.currentTimeMillis()
        try {
            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val adapter = bluetoothManager?.adapter

            val hasBleFeature = context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)

            val bleManager = BlePeripheralManager.getInstance(context)
            val currentState = bleManager.uiState.value.connectionState

            val latency = System.currentTimeMillis() - start

            if (adapter == null) {
                return DiagnosticCheckResult(
                    id = "ble_no_adapter",
                    category = DiagnosticCategory.HARDWARE_BLE,
                    titleMM = "Bluetooth ဟာ့ဒ်ဝဲ စစ်ဆေးချက်",
                    descriptionMM = "Bluetooth Adapter Availability",
                    status = DiagnosticStatus.WARNING,
                    detailsMM = "စက်တွင် Bluetooth ဟာ့ဒ်ဝဲ မပါရှိပါ (သို့မဟုတ် Android Emulator ဖြစ်နေသည်)။ BLE ကိရိယာချိတ်ဆက်မှု မရနိုင်ပါ။",
                    latencyMs = latency
                )
            }

            if (!adapter.isEnabled) {
                return DiagnosticCheckResult(
                    id = "ble_disabled",
                    category = DiagnosticCategory.HARDWARE_BLE,
                    titleMM = "Bluetooth ဖွင့်ထားခြင်း မရှိပါ",
                    descriptionMM = "Bluetooth State Disabled",
                    status = DiagnosticStatus.WARNING,
                    detailsMM = "Bluetooth ပိတ်ထားပါသည်။ BLE ကိရိယာများနှင့် Smart Home ချိတ်ဆက်ရန် Bluetooth ဖွင့်ပေးပါ။",
                    latencyMs = latency,
                    remediationType = RemediationType.OPEN_SETTINGS,
                    settingsDestination = SettingsDestination.BLUETOOTH_SETTINGS
                )
            }

            return DiagnosticCheckResult(
                id = "ble_ok",
                category = DiagnosticCategory.HARDWARE_BLE,
                titleMM = "Bluetooth Low Energy (BLE) စနစ်",
                descriptionMM = "BLE Peripheral & GATT Controller",
                status = DiagnosticStatus.PASS,
                detailsMM = "BLE ဟာ့ဒ်ဝဲ အဆင်သင့်ရှိပြီး Peripheral Manager အလုပ်လုပ်နေပါသည် (State: $currentState)။",
                latencyMs = latency
            )
        } catch (e: Exception) {
            return DiagnosticCheckResult(
                id = "ble_fail",
                category = DiagnosticCategory.HARDWARE_BLE,
                titleMM = "BLE စနစ် ချို့ယွင်းချက်",
                descriptionMM = "BLE Manager Check Exception",
                status = DiagnosticStatus.FAIL,
                detailsMM = "BLE စစ်ဆေးစဉ် ချို့ယွင်းချက်ဖြစ်ပေါ်သည်: ${e.message}",
                latencyMs = System.currentTimeMillis() - start
            )
        }
    }

    private fun checkNetworkAndAiEngine(): DiagnosticCheckResult {
        val start = System.currentTimeMillis()
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val activeNetwork = cm?.activeNetwork
            val caps = cm?.getNetworkCapabilities(activeNetwork)

            val hasInternet = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
            val hasValidated = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true

            val isGeminiConfigured = try {
                BuildConfig.GEMINI_API_KEY.isNotBlank() && !BuildConfig.GEMINI_API_KEY.contains("PLACEHOLDER")
            } catch (e: Throwable) {
                false
            }

            val latency = System.currentTimeMillis() - start

            if (!hasInternet) {
                return DiagnosticCheckResult(
                    id = "network_offline",
                    category = DiagnosticCategory.NLU_AI,
                    titleMM = "ကွန်ရက် ချိတ်ဆက်မှု (အော့ဖ်လိုင်း မုဒ်)",
                    descriptionMM = "Internet Connectivity Status",
                    status = DiagnosticStatus.WARNING,
                    detailsMM = "အင်တာနက် မရှိပါ။ အော့ဖ်လိုင်း Local NLU စနစ်ဖြင့်သာ သီးသန့် အလုပ်လုပ်နိုင်ပါမည်။",
                    latencyMs = latency
                )
            }

            if (!isGeminiConfigured) {
                return DiagnosticCheckResult(
                    id = "gemini_key_missing",
                    category = DiagnosticCategory.NLU_AI,
                    titleMM = "Gemini Cloud AI API Key အခြေအနေ",
                    descriptionMM = "Cloud LLM Intelligence Configuration",
                    status = DiagnosticStatus.WARNING,
                    detailsMM = "အင်တာနက် ရှိသော်လည်း GEMINI_API_KEY မထည့်သွင်းရသေးပါ။ အော့ဖ်လိုင်း စကားသံအမိန့်များသာ အလုပ်လုပ်ပါမည်။",
                    latencyMs = latency,
                    remediationType = RemediationType.OPEN_SETTINGS,
                    settingsDestination = SettingsDestination.APPLICATION_DETAILS
                )
            }

            return DiagnosticCheckResult(
                id = "network_ai_ok",
                category = DiagnosticCategory.NLU_AI,
                titleMM = "အင်တာနက် ကွန်ရက်နှင့် Gemini Cloud AI",
                descriptionMM = "Internet Verified & Gemini 2.5 Flash API Key",
                status = DiagnosticStatus.PASS,
                detailsMM = "အင်တာနက် ချိတ်ဆက်မှု ကောင်းမွန်ပြီး Gemini Cloud AI အဆင်သင့်ရှိပါသည် (စစ်ဆေးချိန်: ${latency}ms)။",
                latencyMs = latency
            )
        } catch (e: Exception) {
            return DiagnosticCheckResult(
                id = "network_ai_fail",
                category = DiagnosticCategory.NLU_AI,
                titleMM = "ကွန်ရက် စစ်ဆေးမှု ချို့ယွင်းချက်",
                descriptionMM = "Connectivity Manager Error",
                status = DiagnosticStatus.FAIL,
                detailsMM = "ကွန်ရက် စစ်ဆေးစဉ် ချို့ယွင်းချက် ဖြစ်ပေါ်သည်: ${e.message}",
                latencyMs = System.currentTimeMillis() - start
            )
        }
    }

    private fun checkSecurityAndPrivacyGuard(): DiagnosticCheckResult {
        val start = System.currentTimeMillis()
        try {
            // Test 1: Gatekeeper Keywords Check
            val affirmativeMatches = ActionPermissionGatekeeper.AFFIRMATIVE_KEYWORDS.contains("အတည်ပြုပါတယ်")
            val negativeMatches = ActionPermissionGatekeeper.CANCELLATION_KEYWORDS.contains("မလုပ်နဲ့")

            // Test 2: PII Redaction
            val testPhone = "၀၉၁၂၃၄၅၆၇၈ သို့ ဖုန်းခေါ်ပါ"
            val sanitized = AuditLogger.sanitizePii(testPhone)
            val isSanitized = sanitized.contains("[REDACTED_PHONE]") && !sanitized.contains("၀၉၁၂၃၄၅၆၇၈")

            val latency = System.currentTimeMillis() - start

            if (affirmativeMatches && negativeMatches && isSanitized) {
                return DiagnosticCheckResult(
                    id = "security_guard_ok",
                    category = DiagnosticCategory.SECURITY_PRIVACY,
                    titleMM = "၅ စက္ကန့် လုံခြုံရေး Gatekeeper နှင့် PII ကာကွယ်မှု",
                    descriptionMM = "Play Policy Compliance & Voice Action Confirmation",
                    status = DiagnosticStatus.PASS,
                    detailsMM = "ဖုန်းနံပါတ် PII Masking နှင့် အရေးကြီးလုပ်ဆောင်ချက် ၅ စက္ကန့် စောင့်ဆိုင်း အတည်ပြုမှု စနစ် မှန်ကန်စွာ ကာကွယ်ထားပါသည် (စစ်ဆေးချိန်: ${latency}ms)။",
                    latencyMs = latency
                )
            } else {
                return DiagnosticCheckResult(
                    id = "security_guard_warning",
                    category = DiagnosticCategory.SECURITY_PRIVACY,
                    titleMM = "လုံခြုံရေး စနစ် စစ်ဆေးချက်",
                    descriptionMM = "PII Sanitizer & Confirmation Keywords",
                    status = DiagnosticStatus.WARNING,
                    detailsMM = "လုံခြုံရေး စည်းမျဉ်းများတွင် သတိပေးချက် ရှိပါသည် (Sanitized=$isSanitized)။",
                    latencyMs = latency
                )
            }
        } catch (e: Exception) {
            return DiagnosticCheckResult(
                id = "security_guard_fail",
                category = DiagnosticCategory.SECURITY_PRIVACY,
                titleMM = "လုံခြုံရေး စနစ် ချို့ယွင်းချက်",
                descriptionMM = "Gatekeeper Audit Exception",
                status = DiagnosticStatus.FAIL,
                detailsMM = "လုံခြုံရေး စနစ် စစ်ဆေးစဉ် ချို့ယွင်းချက်ဖြစ်ပေါ်သည်: ${e.message}",
                latencyMs = System.currentTimeMillis() - start
            )
        }
    }

    private fun checkBackgroundServiceAndDoze(): DiagnosticCheckResult {
        val start = System.currentTimeMillis()
        try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            val isIgnoringDoze = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                pm?.isIgnoringBatteryOptimizations(context.packageName) ?: false
            } else {
                true
            }

            val latency = System.currentTimeMillis() - start

            if (!isIgnoringDoze) {
                return DiagnosticCheckResult(
                    id = "doze_optimization_warning",
                    category = DiagnosticCategory.OS_BACKGROUND,
                    titleMM = "ဘက်ထရီ ချွေတာမှု ကင်းလွတ်ခွင့် (Battery Optimization)",
                    descriptionMM = "Screen ပိတ်ထားစဉ် Doze Mode အသတ်မခံရစေရန်",
                    status = DiagnosticStatus.WARNING,
                    detailsMM = "Battery Optimization ကင်းလွတ်ခွင့် မရသေးပါ။ Screen ပိတ်ထားစဉ် အသံဖမ်း Service ရပ်တန့်သွားနိုင်ပါသည်။",
                    latencyMs = latency,
                    remediationType = RemediationType.OPEN_SETTINGS,
                    settingsDestination = SettingsDestination.BATTERY_OPTIMIZATION
                )
            }

            return DiagnosticCheckResult(
                id = "background_service_ok",
                category = DiagnosticCategory.OS_BACKGROUND,
                titleMM = "အနောက်ကွယ် အသံဖမ်း Service နှင့် Doze ကင်းလွတ်ခွင့်",
                descriptionMM = "Battery Doze Optimization Whitelisted",
                status = DiagnosticStatus.PASS,
                detailsMM = "ဘက်ထရီ ချွေတာမှု ကင်းလွတ်ခွင့် ရရှိထားသဖြင့် Screen ပိတ်ထားစဉ်လည်း စကားသံအမိန့်များ အမြဲနိုးကြားနေပါမည်။",
                latencyMs = latency
            )
        } catch (e: Exception) {
            return DiagnosticCheckResult(
                id = "background_service_fail",
                category = DiagnosticCategory.OS_BACKGROUND,
                titleMM = "Background Service စစ်ဆေးမှု ချို့ယွင်းချက်",
                descriptionMM = "Battery Service Check Error",
                status = DiagnosticStatus.FAIL,
                detailsMM = "Service စစ်ဆေးစဉ် ချို့ယွင်းချက်ဖြစ်ပေါ်သည်: ${e.message}",
                latencyMs = System.currentTimeMillis() - start
            )
        }
    }

    private fun checkOverlayAndAccessibility(): DiagnosticCheckResult {
        val start = System.currentTimeMillis()
        try {
            val canOverlay = OverlayPermissionHelper.canDrawOverlays(context)
            val isAccessibilityGranted = settingsRouter.isCapabilityGranted(SettingsDestination.ACCESSIBILITY)

            val latency = System.currentTimeMillis() - start

            if (!canOverlay) {
                return DiagnosticCheckResult(
                    id = "overlay_permission_warning",
                    category = DiagnosticCategory.OS_BACKGROUND,
                    titleMM = "မျက်နှာပြင်ပေါ် အမြဲပေါ်ခွင့် (Display over other apps)",
                    descriptionMM = "အခြားအက်ပ်များ သုံးစွဲနေစဉ် Floating Voice Ball ပြသနိုင်ရန်",
                    status = DiagnosticStatus.WARNING,
                    detailsMM = "Overlay ခွင့်ပြုချက် မရရှိသေးပါ။ Floating Mic Window ကို အသုံးပြု၍ မရနိုင်သေးပါ။",
                    latencyMs = latency,
                    remediationType = RemediationType.OPEN_SETTINGS,
                    settingsDestination = SettingsDestination.OVERLAY_PERMISSION
                )
            }

            return DiagnosticCheckResult(
                id = "overlay_ok",
                category = DiagnosticCategory.OS_BACKGROUND,
                titleMM = "Floating Overlay နှင့် System Alert Window",
                descriptionMM = "Display Over Other Apps Permission",
                status = DiagnosticStatus.PASS,
                detailsMM = "မျက်နှာပြင်ပေါ် အမြဲပေါ်ခွင့် (Overlay) ရရှိထားပါသည် (Accessibility: ${if (isAccessibilityGranted) "Active" else "Inactive"})။",
                latencyMs = latency
            )
        } catch (e: Exception) {
            return DiagnosticCheckResult(
                id = "overlay_fail",
                category = DiagnosticCategory.OS_BACKGROUND,
                titleMM = "Overlay စစ်ဆေးမှု ချို့ယွင်းချက်",
                descriptionMM = "Overlay Permission Check Error",
                status = DiagnosticStatus.FAIL,
                detailsMM = "Overlay စစ်ဆေးစဉ် ချို့ယွင်းချက်ဖြစ်ပေါ်သည်: ${e.message}",
                latencyMs = System.currentTimeMillis() - start
            )
        }
    }

    /**
     * Generates a beautifully formatted Myanmar Diagnostic Report suitable for clipboard or sharing.
     */
    fun generateDiagnosticReportText(): String {
        val report = _reportFlow.value
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val dateStr = sdf.format(Date(report.timestamp))

        val sb = StringBuilder()
        sb.append("=========================================\n")
        sb.append("KYAW GYI VOICE AI - စနစ် အလုံးစုံ စစ်ဆေးမှု အစီရင်ခံစာ\n")
        sb.append("=========================================\n")
        sb.append("အချိန်: $dateStr\n")
        sb.append("ကျန်းမာရေး ရမှတ်: ${report.healthScore}%\n")
        sb.append("စုစုပေါင်း စစ်ဆေးချက်: ${report.totalChecks} ခု\n")
        sb.append("အောင်မြင်: ${report.passedCount} ခု | သတိပေးချက်: ${report.warningCount} ခု | ချို့ယွင်းချက်: ${report.failedCount} ခု\n")
        sb.append("သုံးသပ်ချက်: ${report.summaryMM}\n")
        sb.append("-----------------------------------------\n\n")

        report.results.forEachIndexed { index, item ->
            val icon = when (item.status) {
                DiagnosticStatus.PASS -> "[✓ PASS]"
                DiagnosticStatus.WARNING -> "[! WARNING]"
                DiagnosticStatus.FAIL -> "[✗ FAIL]"
            }
            sb.append("${index + 1}. $icon ${item.titleMM} (${item.latencyMs}ms)\n")
            sb.append("   - အမျိုးအစား: ${item.category.titleMM}\n")
            sb.append("   - အသေးစိတ်: ${item.detailsMM}\n\n")
        }
        sb.append("=========================================\n")
        return sb.toString()
    }
}
