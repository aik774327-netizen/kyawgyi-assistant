package com.kyawgyi.voice

import android.content.Context
import android.util.Log
import com.example.actions.CommandResult
import com.example.actions.KyawGyiActionExecutor
import com.example.nlu.BurmeseNluPreprocessor
import com.example.nlu.KyawGyiIntent
import com.example.nlu.LocalBurmeseIntentParser
import com.example.security.ActionPermissionGatekeeper
import com.kyawgyi.assistant.state.AssistantState
import com.kyawgyi.assistant.state.AssistantStateStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * HybridPipelineController (Layer 2 ➔ Layer 6 ➔ Layer 4 Execution Pipeline)
 *
 * Central orchestrator connecting:
 * - Layer 2: SttEngineManager (Speech-to-Text transcriptions) & F5MyanmarTtsClient (Speech synthesis)
 * - Layer 6: BurmeseNluPreprocessor (Diacritic re-ordering & Syllable segmentation)
 * - Layer 2: LocalBurmeseIntentParser (Offline Intent & Slot classification)
 * - Layer 4: ActionPermissionGatekeeper (5-second Zero-Trust voice confirmation gate)
 * - Layer 4: KyawGyiActionExecutor (Hardware, Telephony, App Launch & System actions)
 * - Layer 3: AssistantStateStore (Central FSM life-cycle transitions)
 */
class HybridPipelineController(
    private val context: Context,
    private val sttEngineManager: SttEngineManager = SttEngineManager.getInstance(context),
    private val ttsClient: F5MyanmarTtsClient = F5MyanmarTtsClient(context),
    private val actionExecutor: KyawGyiActionExecutor = KyawGyiActionExecutor(context),
    private val gatekeeper: ActionPermissionGatekeeper = ActionPermissionGatekeeper.getInstance(),
    private val stateStore: AssistantStateStore = AssistantStateStore.getInstance(),
    private val intentParser: LocalBurmeseIntentParser = LocalBurmeseIntentParser.getInstance(),
    val wakeWordEngine: KyawGyiWakeWordEngine = KyawGyiWakeWordEngine.getInstance(context)
) {

    companion object {
        private const val TAG = "HybridPipelineController"

        @Volatile
        private var instance: HybridPipelineController? = null

        fun getInstance(context: Context): HybridPipelineController {
            return instance ?: synchronized(this) {
                instance ?: HybridPipelineController(context.applicationContext).also { instance = it }
            }
        }
    }

    private val pipelineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val pipelineMutex = Mutex()
    private val isPipelineRunning = AtomicBoolean(false)
    private var transcriptionCollectorJob: Job? = null

    init {
        com.example.audit.ErrorLearningWorkflowEngine.initialize(context)
    }

    /**
     * Starts listening to finalized speech recognition results from SttEngineManager.
     */
    suspend fun startPipeline() = pipelineMutex.withLock {
        if (isPipelineRunning.get()) {
            Log.d(TAG, "Hybrid pipeline is already running.")
            return@withLock
        }

        Log.i(TAG, "Starting HybridPipelineController...")

        transcriptionCollectorJob = pipelineScope.launch {
            sttEngineManager.transcriptionFlow.collect { (transcript, isFinal) ->
                if (!isFinal || transcript.isBlank()) return@collect

                Log.i(TAG, "New finalized STT utterance received: \"$transcript\"")
                processUtterance(transcript)
            }
        }

        isPipelineRunning.set(true)
        Log.i(TAG, "HybridPipelineController is active.")
    }

    /**
     * Stops the pipeline and cancels active transcription collectors.
     */
    suspend fun stopPipeline() = pipelineMutex.withLock {
        if (!isPipelineRunning.getAndSet(false)) return@withLock
        Log.i(TAG, "Stopping HybridPipelineController...")
        transcriptionCollectorJob?.cancel()
        transcriptionCollectorJob = null
    }

    /**
     * Core processing loop taking raw speech -> Preprocessing -> Gatekeeper -> Execution -> TTS.
     */
    suspend fun processUtterance(rawUtterance: String) = withContext(Dispatchers.Default) {
        // 1. If currently in AWAITING_CONFIRMATION, submit directly to Gatekeeper to resolve pending action
        if (stateStore.getCurrentState() == AssistantState.AWAITING_CONFIRMATION) {
            val gateResolved = gatekeeper.submitVoiceInput(rawUtterance)
            if (gateResolved) {
                Log.i(TAG, "Speech utterance resolved pending safety gate.")
                return@withContext
            }
        }

        // 2. Check Hands-Free Wake Word ("ကျော်ကြီး" / "ဟယ်လို ကျော်ကြီး")
        val wakeResult = wakeWordEngine.processTranscriptAndTrigger(rawUtterance)
        val effectiveInput = if (wakeResult.isDetected) {
            if (wakeResult.trailingCommand.isBlank()) {
                // Standalone wake word ("ဟယ်လို ကျော်ကြီး"): Earcon played, keep in LISTENING for next command
                Log.i(TAG, "Standalone wake word detected. Waiting for user command in LISTENING state.")
                stateStore.transitionTo(AssistantState.LISTENING)
                return@withContext
            }
            wakeResult.trailingCommand
        } else {
            rawUtterance
        }

        // 3. Layer 6 Myanmar Linguistic Preprocessing: Diacritic Re-ordering & Syllable Segmentation
        val preprocessedText = BurmeseNluPreprocessor.normalize(effectiveInput)
        Log.d(TAG, "Layer 6 Preprocessed: \"$rawUtterance\" -> \"$preprocessedText\"")

        // ISSUE-013 NEGATION VETO (Phase 1.5): a prohibitive negation
        // must never reach the intent parser or executor below.
        // (Replies to a pending gatekeeper action were already resolved
        // at step 1 — the gatekeeper owns cancellation matching for
        // staged actions — so this veto only sees fresh commands.)
        val negationResult = com.example.security.MyanmarNegationGuard.detectProhibitiveNegation(preprocessedText)
        if (negationResult is com.example.security.MyanmarNegationGuard.NegationResult.Negated) {
            Log.i(TAG, "Prohibitive negation vetoed (ISSUE-013); marker=${negationResult.matchedMarker}")
            stateStore.transitionTo(AssistantState.SPEAKING)
            ttsClient.synthesizeAndPlay(com.example.security.MyanmarNegationGuard.NO_ACTION_RESPONSE_MM) {
                stateStore.resetToIdle()
            }
            return@withContext
        }

        // Transition FSM to THINKING
        stateStore.transitionTo(AssistantState.THINKING)

        // 3. Layer 2 Offline Intent & Slot Parsing (Fast-Path Regex + Levenshtein Fuzzy Matching)
        val parsedIntent = intentParser.parse(preprocessedText)
        Log.i(TAG, "Parsed Local Intent: ${parsedIntent.intentName} (Confidence: ${parsedIntent.confidence})")

        // 4. Error-Learning AI Workflow Check: Warn if this exact action previously failed and conditions haven't changed
        val repeatWarning = com.example.audit.ErrorLearningWorkflowEngine.evaluateRequestedAction(
            commandKey = preprocessedText,
            context = context
        )
        if (repeatWarning != null) {
            Log.w(TAG, "Blocked repeat of previously failed action: ${repeatWarning.warningBanner}")
            stateStore.transitionTo(AssistantState.SPEAKING)
            ttsClient.synthesizeAndPlay(repeatWarning.warningBanner) {
                stateStore.resetToIdle()
            }
            return@withContext
        }

        // 5. Layer 4 Zero-Trust Safety Gatekeeper Check & Execution
        val actionResult = executeWithSafetyPolicy(parsedIntent, preprocessedText)

        // 6. Record Error Log if execution failed (Never pretend an unsuccessful action succeeded)
        if (!actionResult.isSuccess) {
            val errorEntry = com.example.audit.ErrorLearningWorkflowEngine.recordErrorLog(
                task = "Execute voice command: '$preprocessedText' (${parsedIntent.intentName})",
                commandKey = preprocessedText,
                expectedResult = "Action '${actionResult.action}' on target '${actionResult.target}' completes with isSuccess = true",
                actualResult = "Execution failed with message: ${actionResult.userMessage}",
                attemptedMethod = "${parsedIntent.intentName} via KyawGyiActionExecutor (${actionResult.action})",
                evidence = "CommandResult(isSuccess=false, action=${actionResult.action}, target=${actionResult.target}, errorCode=${actionResult.errorCode ?: actionResult.userMessage})"
            )
            com.example.audit.CommandFailureAnalyzer.recordFailure(
                context = context,
                commandText = preprocessedText,
                commandType = parsedIntent.intentName,
                rawDetail = errorEntry.toFormattedReport()
            )
        }

        // 7. Output Response to User via F5 Myanmar TTS (Layer 2)
        val speechResponse = actionResult.userMessage
        if (speechResponse.isNotBlank()) {
            stateStore.transitionTo(AssistantState.SPEAKING)

            Log.i(TAG, "Speaking response via F5 Myanmar TTS: \"$speechResponse\"")
            ttsClient.synthesizeAndPlay(speechResponse) {
                stateStore.resetToIdle()
            }
        } else {
            stateStore.resetToIdle()
        }
    }

    /**
     * Executes the parsed command through ActionPermissionGatekeeper's 5s safety confirmation policy.
     */
    private suspend fun executeWithSafetyPolicy(
        intent: LocalBurmeseIntentParser.ParsedLocalIntent,
        preprocessedText: String
    ): CommandResult = withContext(Dispatchers.IO) {
        val actionType = intent.intentName
        val slots = intent.slots

        when (intent.intent) {
            KyawGyiIntent.CALL -> {
                val rawContact = slots["contact_name"] ?: "မိတ်ဆွေ"
                val contactName = com.example.nlu.BurmeseNumberNormalizer.extractPhoneNumber(rawContact)
                    ?: com.example.nlu.BurmeseNumberNormalizer.normalizeSpokenDigits(rawContact)
                gatekeeper.executeWithSafetyGate(
                    actionType = "CALL",
                    target = contactName,
                    promptMM = "‘$contactName’ ထံသို့ ဖုန်းခေါ်ဆိုရန် အတည်ပြုပါသလား ခင်ဗျာ။",
                    onConfirmed = {
                        actionExecutor.callContact(contactName, bypassConfirmation = true)
                    }
                )
            }

            KyawGyiIntent.SEND_SMS -> {
                val rawRecipient = slots["target"] ?: "မိတ်ဆွေ"
                val recipient = com.example.nlu.BurmeseNumberNormalizer.extractPhoneNumber(rawRecipient)
                    ?: com.example.nlu.BurmeseNumberNormalizer.normalizeSpokenDigits(rawRecipient)
                val messageText = slots["message"] ?: ""
                gatekeeper.executeWithSafetyGate(
                    actionType = "SMS",
                    target = recipient,
                    promptMM = "‘$recipient’ ထံသို့ စာတို ပေးပို့ရန် သေချာပါသလား ခင်ဗျာ။",
                    onConfirmed = {
                        actionExecutor.sendSms(recipient, messageText, bypassConfirmation = true)
                    }
                )
            }

            KyawGyiIntent.OPEN_SETTINGS -> {
                val target = slots["target"] ?: ""
                val state = slots["state"] ?: "ON"
                val isEnable = state.equals("ON", ignoreCase = true)

                stateStore.transitionTo(AssistantState.EXECUTING)

                when (target) {
                    "WIFI" -> actionExecutor.setWifi(isEnable)
                    "BLUETOOTH" -> actionExecutor.setBluetooth(isEnable)
                    "FLASHLIGHT" -> actionExecutor.setFlashlight(isEnable)
                    "BLE" -> if (state == "DISCONNECT") actionExecutor.disconnectBleDevice() else actionExecutor.scanBleDevices()
                    else -> actionExecutor.executeSystemAction(preprocessedText)
                        ?: fallbackResult(preprocessedText)
                }
            }

            KyawGyiIntent.OPEN_APP -> {
                val appName = slots["app_name"] ?: "Application"
                stateStore.transitionTo(AssistantState.EXECUTING)
                actionExecutor.openApplication(appName, appName)
            }

            KyawGyiIntent.VOLUME_UP, KyawGyiIntent.VOLUME_DOWN, KyawGyiIntent.MUTE -> {
                stateStore.transitionTo(AssistantState.EXECUTING)
                actionExecutor.executeSystemAction(preprocessedText) ?: fallbackResult(preprocessedText)
            }

            KyawGyiIntent.PLAY_MUSIC -> {
                val song = slots["song_name"] ?: ""
                stateStore.transitionTo(AssistantState.EXECUTING)
                actionExecutor.playLocalAudioTrack(song)
            }

            KyawGyiIntent.SEARCH -> {
                val query = slots["query"] ?: preprocessedText
                stateStore.transitionTo(AssistantState.EXECUTING)
                actionExecutor.searchMedia(query)
            }

            KyawGyiIntent.OPEN_CAMERA -> {
                stateStore.transitionTo(AssistantState.EXECUTING)
                actionExecutor.openApplication("camera", "ကင်မရာ")
            }

            KyawGyiIntent.OPEN_PHOTOS -> {
                stateStore.transitionTo(AssistantState.EXECUTING)
                actionExecutor.openApplication("gallery", "ဓာတ်ပုံများ")
            }

            KyawGyiIntent.OPEN_MAPS -> {
                stateStore.transitionTo(AssistantState.EXECUTING)
                actionExecutor.openApplication("maps", "မြေပုံ")
            }

            KyawGyiIntent.OPEN_MESSAGES -> {
                stateStore.transitionTo(AssistantState.EXECUTING)
                actionExecutor.openApplication("messages", "မက်ဆေ့ခ်ျ")
            }

            KyawGyiIntent.OPEN_PHONE -> {
                stateStore.transitionTo(AssistantState.EXECUTING)
                actionExecutor.openApplication("dialer", "ဖုန်းခေါ်ဆိုမှု")
            }

            KyawGyiIntent.PAUSE_MEDIA, KyawGyiIntent.RESUME_MEDIA,
            KyawGyiIntent.NEXT_TRACK, KyawGyiIntent.PREVIOUS_TRACK,
            KyawGyiIntent.STOP -> {
                stateStore.transitionTo(AssistantState.EXECUTING)
                actionExecutor.executeSystemAction(preprocessedText)
                    ?: fallbackResult(preprocessedText)
            }

            KyawGyiIntent.CANCEL -> {
                stateStore.resetToIdle()
                CommandResult(
                    isSuccess = true,
                    action = "CANCEL",
                    target = "SESSION",
                    userMessage = "လုပ်ဆောင်ချက်ကို ရပ်ဆိုင်းလိုက်ပါပြီခင်ဗျာ။"
                )
            }

            else -> {
                stateStore.transitionTo(AssistantState.EXECUTING)
                actionExecutor.executeSystemAction(preprocessedText) ?: fallbackResult(preprocessedText)
            }
        }
    }

    private fun fallbackResult(query: String): CommandResult {
        return CommandResult(
            isSuccess = false,
            action = "UNKNOWN",
            target = query,
            errorCode = "INTENT_NOT_FOUND",
            userMessage = "ကြားရတဲ့ စကားလုံးအတွက် လုပ်ဆောင်ချက် မတွေ့ရှိသေးပါခင်ဗျာ။"
        )
    }

    fun isRunning(): Boolean = isPipelineRunning.get()

    fun release() {
        pipelineScope.launch {
            stopPipeline()
            pipelineScope.coroutineContext.cancelChildren()
        }
    }
}
