package com.kyawgyi.ir.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.MainActivity
import com.kyawgyi.ir.voice.VoiceEngine
import com.kyawgyi.ir.voice.VoiceIntent
import com.kyawgyi.ir.wakeword.KyawGyiWakeWordEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * VoiceForegroundService
 *
 * Runs as an Android Foreground Service with a persistent notification and
 * keeps the microphone active for continuous background listening.
 * Uses VoiceEngine for Myanmar SpeechRecognizer (my-MM) and TextToSpeech.
 */
class VoiceForegroundService : Service() {

    companion object {
        private const val TAG = "VoiceForegroundService"
        const val CHANNEL_ID = "kyaw_gyi_voice_foreground_channel"
        const val CHANNEL_NAME = "Kyaw Gyi Voice Foreground Service"
        const val NOTIFICATION_ID = 2002

        const val ACTION_START_SERVICE = "com.kyawgyi.ir.service.ACTION_START"
        const val ACTION_STOP_SERVICE = "com.kyawgyi.ir.service.ACTION_STOP"
        const val ACTION_START_LISTENING = "com.kyawgyi.ir.service.ACTION_START_LISTENING"
        const val ACTION_STOP_LISTENING = "com.kyawgyi.ir.service.ACTION_STOP_LISTENING"
        const val ACTION_SPEAK = "com.kyawgyi.ir.service.ACTION_SPEAK"
        const val ACTION_TOGGLE_WAKE_WORD = "com.kyawgyi.ir.service.ACTION_TOGGLE_WAKE_WORD"
        const val EXTRA_SPEAK_TEXT = "EXTRA_SPEAK_TEXT"
        const val EXTRA_WAKE_WORD_ENABLED = "EXTRA_WAKE_WORD_ENABLED"

        // Broadcast actions for communicating with UI components
        const val BROADCAST_VOICE_INTENT = "com.kyawgyi.ir.VOICE_INTENT"
        const val EXTRA_INTENT_TYPE = "EXTRA_INTENT_TYPE"
        const val EXTRA_INTENT_QUERY = "EXTRA_INTENT_QUERY"
        const val EXTRA_INTENT_DATA = "EXTRA_INTENT_DATA"

        private val _isServiceRunning = MutableStateFlow(false)
        val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

        private val _serviceStatusMessage = MutableStateFlow("အသင့်ဖြစ်ပါပြီ")
        val serviceStatusMessage: StateFlow<String> = _serviceStatusMessage.asStateFlow()

        private val _lastTranscribedSpeech = MutableStateFlow("")
        val lastTranscribedSpeech: StateFlow<String> = _lastTranscribedSpeech.asStateFlow()

        fun startService(context: Context) {
            val intent = Intent(context, VoiceForegroundService::class.java).apply {
                action = ACTION_START_SERVICE
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: SecurityException) {
                Log.w(TAG, "SecurityException starting VoiceForegroundService: ${e.message}")
            } catch (e: Throwable) {
                Log.w(TAG, "Cannot start foreground service (possibly background restricted on Android 12+/14+): ${e.message}")
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, VoiceForegroundService::class.java).apply {
                action = ACTION_STOP_SERVICE
            }
            try {
                context.startService(intent)
            } catch (e: Throwable) {
                Log.w(TAG, "Cannot stop VoiceForegroundService: ${e.message}")
            }
        }

        fun startListening(context: Context) {
            val intent = Intent(context, VoiceForegroundService::class.java).apply {
                action = ACTION_START_LISTENING
            }
            try {
                context.startService(intent)
            } catch (e: Throwable) {
                Log.w(TAG, "Cannot start listening intent: ${e.message}")
            }
        }

        fun stopListening(context: Context) {
            val intent = Intent(context, VoiceForegroundService::class.java).apply {
                action = ACTION_STOP_LISTENING
            }
            try {
                context.startService(intent)
            } catch (e: Throwable) {
                Log.w(TAG, "Cannot stop listening intent: ${e.message}")
            }
        }

        fun speak(context: Context, text: String) {
            val intent = Intent(context, VoiceForegroundService::class.java).apply {
                action = ACTION_SPEAK
                putExtra(EXTRA_SPEAK_TEXT, text)
            }
            try {
                context.startService(intent)
            } catch (e: Throwable) {
                Log.w(TAG, "Cannot dispatch speak intent: ${e.message}")
            }
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())
    private var voiceEngine: VoiceEngine? = null
    private var wakeWordEngine: KyawGyiWakeWordEngine? = null
    private var sttEngineManager: com.kyawgyi.voice.SttEngineManager? = null
    private var audioRecorderBridge: com.kyawgyi.voice.CentralAudioRecorderBridge? = null
    private var fsmStateSynchronizer: com.kyawgyi.voice.FsmStateSynchronizer? = null
    private var hybridPipelineController: com.kyawgyi.voice.HybridPipelineController? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private lateinit var notificationManager: NotificationManager

    // Screen State Receiver to guarantee persistent listening across screen-off events
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    Log.i(TAG, "Screen turned off. Maintaining wake lock and active microphone listening.")
                    acquireWakeLock()
                    if (_isServiceRunning.value && voiceEngine?.isListening?.value != true) {
                        wakeWordEngine?.startListening()
                    }
                }
                Intent.ACTION_SCREEN_ON -> {
                    Log.d(TAG, "Screen turned on.")
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "VoiceForegroundService created.")
        com.example.ocr.FailureMemoryEngine.initialize(this)
        com.example.audit.ErrorLearningWorkflowEngine.initialize(this)
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()

        acquireWakeLock()
        checkBatteryOptimizationStatus()
        initializeSixLayerPipeline()
        initializeVoiceEngine()
        initializeWakeWordEngine()
        registerScreenReceiver()
    }

    private fun initializeSixLayerPipeline() {
        try {
            val sttManager = com.kyawgyi.voice.SttEngineManager.getInstance(this)
            sttEngineManager = sttManager
            fsmStateSynchronizer = com.kyawgyi.voice.FsmStateSynchronizer.getInstance(sttManager)
            hybridPipelineController = com.kyawgyi.voice.HybridPipelineController.getInstance(this)
            audioRecorderBridge = com.kyawgyi.voice.CentralAudioRecorderBridge.getInstance(this)
            serviceScope.launch {
                fsmStateSynchronizer?.startSynchronization()
                hybridPipelineController?.startPipeline()
            }
            Log.i(TAG, "6-Layer Voice Architecture Bridges (RecorderBridge, FsmSynchronizer, HybridPipeline) initialized in VoiceForegroundService.")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing 6-Layer Pipeline in VoiceForegroundService: ${e.message}", e)
        }
    }

    private fun checkBatteryOptimizationStatus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
            val isExempt = pm?.isIgnoringBatteryOptimizations(packageName) ?: false
            if (!isExempt) {
                Log.w(TAG, "VoiceForegroundService: Battery Optimization is active! Background listening may be terminated by Android 14 Doze mode or OS when screen is off.")
            } else {
                Log.i(TAG, "VoiceForegroundService: Battery Optimization is exempted (Don't Optimize). Persistent background voice service protected.")
            }
        }
    }

    private fun registerScreenReceiver() {
        try {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
            }
            registerReceiver(screenReceiver, filter)
            Log.d(TAG, "Screen state receiver registered.")
        } catch (e: Exception) {
            Log.w(TAG, "Error registering screen receiver: ${e.message}")
        }
    }

    private fun unregisterScreenReceiver() {
        try {
            unregisterReceiver(screenReceiver)
            Log.d(TAG, "Screen state receiver unregistered.")
        } catch (e: Exception) {
            Log.w(TAG, "Error unregistering screen receiver: ${e.message}")
        }
    }

    private fun initializeWakeWordEngine() {
        wakeWordEngine = KyawGyiWakeWordEngine(this) {
            Log.i(TAG, "Wake word triggered via KyawGyiWakeWordEngine in VoiceForegroundService.")
            updateNotification("နိုးကြားစကား 'ကျော်ကြီး' ကြားသိရပါသည် (အသံပိတ်ထားသည်)")
            // Step 1: Immediately stop AudioRecord to completely release hardware microphone
            wakeWordEngine?.stopListening()
            // Step 2: Audible feedback response
            voiceEngine?.speak("ဗျာ၊ ကျော်ကြီး အသင့်ရှိနေပါသည်")
            // Step 3: Staged start of SpeechRecognizer to capture user command
            mainHandler.postDelayed({
                if (_isServiceRunning.value) {
                    voiceEngine?.startListening()
                }
            }, 650)
            handleVoiceIntent(VoiceIntent.WakeWordDetected("Kyaw Gyi", "ကျော်ကြီး"))
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START_SERVICE
        Log.i(TAG, "onStartCommand received action: $action")

        when (action) {
            ACTION_START_SERVICE -> {
                startForegroundWithNotification("အသံဖမ်းယူမှု စနစ် စတင်နေပါသည်...")
                _isServiceRunning.value = true
                wakeWordEngine?.startListening()
            }

            ACTION_STOP_SERVICE -> {
                Log.i(TAG, "Stopping VoiceForegroundService...")
                stopForegroundService()
                return START_NOT_STICKY
            }

            ACTION_START_LISTENING -> {
                updateNotification("စကားသံ နားထောင်နေပါသည်...")
                wakeWordEngine?.stopListening()
                serviceScope.launch {
                    hybridPipelineController?.startPipeline()
                }
                mainHandler.postDelayed({
                    if (_isServiceRunning.value) {
                        voiceEngine?.startListening()
                    }
                }, 200)
            }

            ACTION_STOP_LISTENING -> {
                updateNotification("အသံဖမ်းယူမှု ခေတ္တရပ်ထားပါသည်")
                serviceScope.launch {
                    audioRecorderBridge?.stopBridge()
                }
                voiceEngine?.stopListening()
                wakeWordEngine?.stopListening()
            }

            ACTION_SPEAK -> {
                val textToSpeak = intent?.getStringExtra(EXTRA_SPEAK_TEXT) ?: ""
                if (textToSpeak.isNotBlank()) {
                    val normalized = com.example.nlu.BurmeseTtsTextNormalizer.normalizeForTts(textToSpeak)
                    voiceEngine?.speak(normalized)
                }
            }

            ACTION_TOGGLE_WAKE_WORD -> {
                val enabled = intent?.getBooleanExtra(EXTRA_WAKE_WORD_ENABLED, true) ?: true
                Log.i(TAG, "ACTION_TOGGLE_WAKE_WORD received: enabled=$enabled")
                com.example.wakeword.WakeWordSettingManager.getInstance(this).setWakeWordEnabled(enabled)
                if (enabled) {
                    if (_isServiceRunning.value && voiceEngine?.isListening?.value != true) {
                        wakeWordEngine?.startListening()
                    }
                    updateNotification("နိုးကြားစကား 'ကျော်ကြီး' နားထောင်မှု စတင်ထားပါသည်")
                } else {
                    wakeWordEngine?.stopListening()
                    updateNotification("နိုးကြားစကား 'ကျော်ကြီး' နားထောင်မှု ပိတ်ထားပါသည်")
                }
            }
        }

        return START_STICKY
    }

    private fun initializeVoiceEngine() {
        voiceEngine = VoiceEngine(
            context = this,
            onIntentRecognized = { intent ->
                handleVoiceIntent(intent)
            },
            onSpeechText = { transcribed ->
                _lastTranscribedSpeech.value = transcribed
                updateNotification("ကြားသိရသောအသံ: \"$transcribed\"")
                try {
                    val legacyIntent = Intent(com.example.service.KyawGyiForegroundService.BROADCAST_ENGINE_EVENT).apply {
                        setPackage(packageName)
                        putExtra(com.example.service.KyawGyiForegroundService.EXTRA_SPEECH_RESULT, transcribed)
                        putExtra(com.example.service.KyawGyiForegroundService.EXTRA_MESSAGE, transcribed)
                    }
                    sendBroadcast(legacyIntent)
                } catch (e: Exception) {
                    Log.w(TAG, "Legacy speech broadcast error: ${e.message}")
                }
            },
            onPartialSpeechText = { partial ->
                _serviceStatusMessage.value = partial
                val lower = partial.lowercase()
                if (lower.contains("ကျော်ကြီး") || lower.contains("kyaw gyi") || lower.contains("kyawgyi")) {
                    Log.i(TAG, "Wake word 'Kyaw Gyi' detected in partial audio! Instantly muting media...")
                    try {
                        com.example.audio.GlobalAudioStateManager.getInstance(this@VoiceForegroundService)
                            .muteAllMediaPlayback("VoiceForegroundService (Wake Word)")
                    } catch (e: Exception) {
                        Log.w(TAG, "Audio muting notice: ${e.message}")
                    }
                }
                try {
                    val legacyIntent = Intent(com.example.service.KyawGyiForegroundService.BROADCAST_ENGINE_EVENT).apply {
                        setPackage(packageName)
                        putExtra(com.example.service.KyawGyiForegroundService.EXTRA_SPEECH_RESULT, partial)
                        putExtra(com.example.service.KyawGyiForegroundService.EXTRA_MESSAGE, "နားထောင်နေဆဲ...")
                    }
                    sendBroadcast(legacyIntent)
                } catch (e: Exception) {
                    Log.w(TAG, "Legacy partial broadcast error: ${e.message}")
                }
            },
            onSpeechStatusChanged = { status ->
                _serviceStatusMessage.value = status
                try {
                    val legacyIntent = Intent(com.example.service.KyawGyiForegroundService.BROADCAST_ENGINE_EVENT).apply {
                        setPackage(packageName)
                        putExtra(com.example.service.KyawGyiForegroundService.EXTRA_STATE, status)
                        putExtra(com.example.service.KyawGyiForegroundService.EXTRA_MESSAGE, status)
                    }
                    sendBroadcast(legacyIntent)
                } catch (e: Exception) {
                    Log.w(TAG, "Legacy status broadcast error: ${e.message}")
                }
            },
            onRecognitionFinished = {
                // When SpeechRecognizer finishes its cycle, re-arm WakeWordEngine if service is running
                if (_isServiceRunning.value) {
                    mainHandler.postDelayed({
                        if (_isServiceRunning.value && voiceEngine?.isListening?.value != true) {
                            Log.d(TAG, "Re-arming wakeWordEngine after speech recognition finished")
                            wakeWordEngine?.startListening()
                        }
                    }, 500)
                }
            }
        )
    }

    private fun handleVoiceIntent(intent: VoiceIntent) {
        Log.i(TAG, "Handling voice intent in service: $intent")

        // Error-Learning AI Workflow Check: Warn if this exact action previously failed and conditions haven't changed
        val repeatWarning = com.example.audit.ErrorLearningWorkflowEngine.evaluateRequestedAction(intent.rawQuery)
        if (repeatWarning != null) {
            Log.w(TAG, "Blocked repeat of previously failed action in VoiceForegroundService: ${repeatWarning.warningBanner}")
            updateNotification(repeatWarning.simpleExplanationMm)
            voiceEngine?.speak(repeatWarning.warningBanner)
            return
        }

        // Broadcast to application listeners
        val broadcastIntent = Intent(BROADCAST_VOICE_INTENT).apply {
            setPackage(packageName)
            putExtra(EXTRA_INTENT_QUERY, intent.rawQuery)
            when (intent) {
                is VoiceIntent.WakeWordDetected -> {
                    putExtra(EXTRA_INTENT_TYPE, "WAKE_WORD_DETECTED")
                    putExtra(EXTRA_INTENT_DATA, intent.wakeWord)
                }
                is VoiceIntent.CreateTask -> {
                    putExtra(EXTRA_INTENT_TYPE, "CREATE_TASK")
                    putExtra(EXTRA_INTENT_DATA, intent.taskTitle)
                }
                is VoiceIntent.StartTimer -> {
                    putExtra(EXTRA_INTENT_TYPE, "START_TIMER")
                    putExtra(EXTRA_INTENT_DATA, intent.minutes.toString())
                }
                is VoiceIntent.OpenApp -> {
                    putExtra(EXTRA_INTENT_TYPE, "OPEN_APP")
                    putExtra(EXTRA_INTENT_DATA, intent.appTarget)
                }
                is VoiceIntent.CloseApp -> {
                    putExtra(EXTRA_INTENT_TYPE, "CLOSE_APP")
                    putExtra(EXTRA_INTENT_DATA, intent.appTarget)
                }
                is VoiceIntent.CallContact -> {
                    putExtra(EXTRA_INTENT_TYPE, "CALL_CONTACT")
                    putExtra(EXTRA_INTENT_DATA, intent.contactTarget)
                }
                is VoiceIntent.SendSms -> {
                    putExtra(EXTRA_INTENT_TYPE, "SEND_SMS")
                    putExtra(EXTRA_INTENT_DATA, "${intent.contactTarget}|${intent.message}")
                }
                is VoiceIntent.ConfirmAction -> {
                    putExtra(EXTRA_INTENT_TYPE, "CONFIRM_ACTION")
                    putExtra(EXTRA_INTENT_DATA, intent.rawQuery)
                }
                is VoiceIntent.CancelAction -> {
                    putExtra(EXTRA_INTENT_TYPE, "CANCEL_ACTION")
                    putExtra(EXTRA_INTENT_DATA, intent.rawQuery)
                }
                is VoiceIntent.PlayMusic -> {
                    putExtra(EXTRA_INTENT_TYPE, "PLAY_MUSIC")
                    putExtra(EXTRA_INTENT_DATA, intent.songTarget)
                }
                is VoiceIntent.ToggleFlashlight -> {
                    putExtra(EXTRA_INTENT_TYPE, "TOGGLE_FLASHLIGHT")
                    putExtra(EXTRA_INTENT_DATA, intent.enable.toString())
                }
                is VoiceIntent.ToggleWifi -> {
                    putExtra(EXTRA_INTENT_TYPE, "TOGGLE_WIFI")
                    putExtra(EXTRA_INTENT_DATA, intent.enable.toString())
                }
                is VoiceIntent.ToggleBluetooth -> {
                    putExtra(EXTRA_INTENT_TYPE, "TOGGLE_BLUETOOTH")
                    putExtra(EXTRA_INTENT_DATA, intent.enable.toString())
                }
                is VoiceIntent.AdjustVolume -> {
                    putExtra(EXTRA_INTENT_TYPE, "ADJUST_VOLUME")
                    putExtra(EXTRA_INTENT_DATA, intent.isIncrease.toString())
                }
                is VoiceIntent.AdjustBrightness -> {
                    putExtra(EXTRA_INTENT_TYPE, "ADJUST_BRIGHTNESS")
                    putExtra(EXTRA_INTENT_DATA, intent.isIncrease.toString())
                }
                is VoiceIntent.CheckBattery -> {
                    putExtra(EXTRA_INTENT_TYPE, "CHECK_BATTERY")
                }
                is VoiceIntent.GetStatus -> {
                    putExtra(EXTRA_INTENT_TYPE, "GET_STATUS")
                }
                is VoiceIntent.StopListening -> {
                    putExtra(EXTRA_INTENT_TYPE, "STOP_LISTENING")
                }
                is VoiceIntent.OpenSettings -> {
                    putExtra(EXTRA_INTENT_TYPE, "OPEN_SETTINGS")
                    putExtra(EXTRA_INTENT_DATA, intent.destinationName)
                }
                is VoiceIntent.GeneralQuery -> {
                    putExtra(EXTRA_INTENT_TYPE, "GENERAL_QUERY")
                    putExtra(EXTRA_INTENT_DATA, intent.prompt)
                }
                is VoiceIntent.Unknown -> {
                    putExtra(EXTRA_INTENT_TYPE, "UNKNOWN")
                }
            }
        }
        sendBroadcast(broadcastIntent)

        // Provide spoken audio feedback in Myanmar locale and directly execute system actions
        when (intent) {
            is VoiceIntent.WakeWordDetected -> {
                updateNotification("နိုးကြားစကား 'ကျော်ကြီး' ကြားသိရပါသည် (အသံပိတ်ထားသည်)")
            }
            is VoiceIntent.CallContact -> {
                serviceScope.launch {
                    val executor = com.example.actions.KyawGyiActionExecutor.getInstance(this@VoiceForegroundService)
                    val result = executor.callContact(intent.contactTarget, bypassConfirmation = false)
                    updateNotification(result.userMessage)
                    voiceEngine?.speak(result.userMessage)

                    // If confirmation is pending, re-arm listening so user can answer "ဟုတ်ကဲ့" or "မခေါ်ပါနဲ့"
                    if (com.example.actions.CallSmsConfirmationCoordinator.getInstance().hasPendingAction()) {
                        mainHandler.postDelayed({
                            if (_isServiceRunning.value) {
                                voiceEngine?.startListening()
                            }
                        }, 1800)
                    }
                }
            }
            is VoiceIntent.SendSms -> {
                serviceScope.launch {
                    val executor = com.example.actions.KyawGyiActionExecutor.getInstance(this@VoiceForegroundService)
                    val result = executor.sendSms(intent.contactTarget, intent.message, bypassConfirmation = false)
                    updateNotification(result.userMessage)
                    voiceEngine?.speak(result.userMessage)

                    if (com.example.actions.CallSmsConfirmationCoordinator.getInstance().hasPendingAction()) {
                        mainHandler.postDelayed({
                            if (_isServiceRunning.value) {
                                voiceEngine?.startListening()
                            }
                        }, 1800)
                    }
                }
            }
            is VoiceIntent.ConfirmAction -> {
                serviceScope.launch {
                    val coordinator = com.example.actions.CallSmsConfirmationCoordinator.getInstance()
                    if (coordinator.hasPendingAction()) {
                        val result = coordinator.executeConfirmed(this@VoiceForegroundService)
                        updateNotification(result.userMessage)
                        voiceEngine?.speak(result.userMessage)
                    }
                }
            }
            is VoiceIntent.CancelAction -> {
                serviceScope.launch {
                    val coordinator = com.example.actions.CallSmsConfirmationCoordinator.getInstance()
                    if (coordinator.hasPendingAction()) {
                        val result = coordinator.cancelPending("User voice cancellation")
                        updateNotification(result.userMessage)
                        voiceEngine?.speak(result.userMessage)
                    }
                }
            }
            is VoiceIntent.PlayMusic -> {
                serviceScope.launch {
                    val executor = com.example.actions.KyawGyiActionExecutor.getInstance(this@VoiceForegroundService)
                    val result = executor.playLocalAudioTrack(intent.songTarget)
                    updateNotification(result.userMessage)
                    voiceEngine?.speak(result.userMessage)
                }
            }
            is VoiceIntent.OpenApp -> {
                serviceScope.launch {
                    val executor = com.example.actions.KyawGyiActionExecutor.getInstance(this@VoiceForegroundService)
                    val result = executor.openApplication(intent.appTarget, intent.appTarget)
                    updateNotification(result.userMessage)
                    voiceEngine?.speak(result.userMessage)
                }
            }
            is VoiceIntent.CloseApp -> {
                serviceScope.launch {
                    val executor = com.example.actions.KyawGyiActionExecutor.getInstance(this@VoiceForegroundService)
                    val result = executor.closeApplication(intent.appTarget, intent.appTarget)
                    updateNotification(result.userMessage)
                    voiceEngine?.speak(result.userMessage)
                }
            }
            is VoiceIntent.ToggleFlashlight -> {
                serviceScope.launch {
                    val executor = com.example.actions.KyawGyiActionExecutor.getInstance(this@VoiceForegroundService)
                    val result = executor.setFlashlight(intent.enable)
                    updateNotification(result.userMessage)
                    voiceEngine?.speak(result.userMessage)
                }
            }
            is VoiceIntent.ToggleWifi -> {
                serviceScope.launch {
                    val executor = com.example.actions.KyawGyiActionExecutor.getInstance(this@VoiceForegroundService)
                    val result = executor.setWifi(intent.enable)
                    updateNotification(result.userMessage)
                    voiceEngine?.speak(result.userMessage)
                }
            }
            is VoiceIntent.ToggleBluetooth -> {
                serviceScope.launch {
                    val executor = com.example.actions.KyawGyiActionExecutor.getInstance(this@VoiceForegroundService)
                    val result = executor.setBluetooth(intent.enable)
                    updateNotification(result.userMessage)
                    voiceEngine?.speak(result.userMessage)
                }
            }
            is VoiceIntent.AdjustVolume -> {
                serviceScope.launch {
                    val executor = com.example.actions.KyawGyiActionExecutor.getInstance(this@VoiceForegroundService)
                    val result = executor.executeSystemAction(intent.rawQuery)
                    val msg = result?.userMessage ?: if (intent.isIncrease) "အသံ တိုးပေးလိုက်ပါပြီခင်ဗျာ" else "အသံ လျှော့ပေးလိုက်ပါပြီခင်ဗျာ"
                    updateNotification(msg)
                    voiceEngine?.speak(msg)
                }
            }
            is VoiceIntent.AdjustBrightness -> {
                serviceScope.launch {
                    val executor = com.example.actions.KyawGyiActionExecutor.getInstance(this@VoiceForegroundService)
                    val result = executor.executeSystemAction(intent.rawQuery)
                    val msg = result?.userMessage ?: if (intent.isIncrease) "မျက်နှာပြင် အလင်း တိုးပေးလိုက်ပါပြီခင်ဗျာ" else "မျက်နှာပြင် အလင်း လျှော့ပေးလိုက်ပါပြီခင်ဗျာ"
                    updateNotification(msg)
                    voiceEngine?.speak(msg)
                }
            }
            is VoiceIntent.CheckBattery -> {
                serviceScope.launch {
                    val executor = com.example.actions.KyawGyiActionExecutor.getInstance(this@VoiceForegroundService)
                    val result = executor.getBatteryStatus()
                    updateNotification(result.userMessage)
                    voiceEngine?.speak(result.userMessage)
                }
            }
            is VoiceIntent.CreateTask -> {
                voiceEngine?.speak("တာဝန် '${intent.taskTitle}' ကို မှတ်သားပြီးပါပြီ")
            }
            is VoiceIntent.StartTimer -> {
                voiceEngine?.speak("${intent.minutes} မိနစ် အချိန် သတ်မှတ်ပြီးပါပြီ")
            }
            is VoiceIntent.GetStatus -> {
                voiceEngine?.speak("ကျော်ကြီး အသံလက်ထောက် စနစ် နောက်ကွယ်တွင် ပုံမှန် အလုပ်လုပ်နေပါသည်")
            }
            is VoiceIntent.StopListening -> {
                voiceEngine?.speak("အသံဖမ်းယူမှု စနစ်ကို ရပ်တန့်လိုက်ပါပြီ")
                stopForegroundService()
            }
            is VoiceIntent.OpenSettings -> {
                serviceScope.launch {
                    val executor = com.example.actions.KyawGyiActionExecutor.getInstance(this@VoiceForegroundService)
                    val destination = try {
                        com.example.router.SettingsDestination.valueOf(intent.destinationName)
                    } catch (e: Exception) {
                        com.example.router.DirectSettingsRouter.getInstance(this@VoiceForegroundService)
                            .resolveSettingsDestination(intent.rawQuery) ?: com.example.router.SettingsDestination.APPLICATION_DETAILS
                    }
                    val result = executor.openDirectSettings(destination)
                    updateNotification(result.userMessage)
                    voiceEngine?.speak(result.userMessage)
                }
            }
            is VoiceIntent.GeneralQuery -> {
                serviceScope.launch {
                    val router = com.example.router.KyawGyiSmartRouter(this@VoiceForegroundService)
                    val response = router.processVoiceInput(intent.prompt)
                    updateNotification(response)
                    voiceEngine?.speak(response)
                }
            }
            is VoiceIntent.Unknown -> {
                serviceScope.launch {
                    if (intent.rawQuery.isNotBlank()) {
                        val router = com.example.router.KyawGyiSmartRouter(this@VoiceForegroundService)
                        val response = router.processVoiceInput(intent.rawQuery)
                        updateNotification(response)
                        voiceEngine?.speak(response)
                    }
                }
            }
        }
    }

    // =========================================================================
    // FOREGROUND NOTIFICATION MANAGEMENT
    // =========================================================================

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps microphone active for continuous voice command listening"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(statusText: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Stop Action
        val stopIntent = Intent(this, VoiceForegroundService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Listen Action
        val listenIntent = Intent(this, VoiceForegroundService::class.java).apply {
            action = ACTION_START_LISTENING
        }
        val listenPendingIntent = PendingIntent.getService(
            this,
            2,
            listenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Kyaw Gyi အသံလက်ထောက် (နောက်ကွယ် အသံဖမ်းယူမှု)")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(contentPendingIntent)
            .addAction(android.R.drawable.ic_media_play, "စကားပြောရန်", listenPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "ရပ်တန့်ရန်", stopPendingIntent)
            .build()
    }

    private fun startForegroundWithNotification(statusText: String) {
        val notification = buildNotification(statusText)
        try {
            // Android 14 (API 34+) specifically requires FOREGROUND_SERVICE_TYPE_MICROPHONE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException starting foreground service with microphone type: ${e.message}", e)
        } catch (e: Exception) {
            Log.e(TAG, "Exception starting foreground service: ${e.message}", e)
        }
    }

    private fun updateNotification(statusText: String) {
        _serviceStatusMessage.value = statusText
        val notification = buildNotification(statusText)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    // =========================================================================
    // WAKE LOCK MANAGEMENT
    // =========================================================================

    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (wakeLock == null) {
                wakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "KyawGyi:VoiceForegroundServiceWakeLock"
                ).apply {
                    setReferenceCounted(false)
                }
            }
            if (wakeLock?.isHeld != true) {
                wakeLock?.acquire()
                Log.d(TAG, "Partial wake lock acquired to maintain background listening across screen-off.")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to acquire wake lock: ${e.message}")
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
                Log.d(TAG, "Wake lock released.")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing wake lock: ${e.message}")
        }
        wakeLock = null
    }

    private fun stopForegroundService() {
        _isServiceRunning.value = false
        _serviceStatusMessage.value = "ရပ်တန့်ထားပါသည်"
        serviceScope.launch {
            audioRecorderBridge?.stopBridge()
            fsmStateSynchronizer?.stopSynchronization()
            hybridPipelineController?.stopPipeline()
        }
        wakeWordEngine?.stopListening()
        voiceEngine?.stopListening()
        voiceEngine?.stopSpeaking()
        unregisterScreenReceiver()
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        Log.i(TAG, "VoiceForegroundService destroyed.")
        _isServiceRunning.value = false
        audioRecorderBridge?.release()
        fsmStateSynchronizer?.release()
        hybridPipelineController?.release()
        wakeWordEngine?.stopListening()
        wakeWordEngine = null
        voiceEngine?.shutdown()
        voiceEngine = null
        unregisterScreenReceiver()
        releaseWakeLock()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
