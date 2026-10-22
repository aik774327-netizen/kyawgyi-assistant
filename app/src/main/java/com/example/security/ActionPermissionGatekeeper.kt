package com.example.security

import android.util.Log
import com.example.actions.CommandResult
import com.example.audit.AuditLogger
import com.example.audit.ResolutionPath
import com.kyawgyi.assistant.state.AssistantState
import com.kyawgyi.assistant.state.AssistantStateStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean

/**
 * ActionPermissionGatekeeper (Layer 4 - Security Interceptor & Safety Timeout)
 *
 * Implements Google Play & Android OS Zero-Trust Security Policies:
 * 1. High-Risk Action Interception:
 *    - Intercepts sensitive or destructive actions (Direct Phone Calls, Outgoing SMS, Factory Reset, Financial Transfers).
 * 2. Strict 5-Second Voice Confirmation Timeout:
 *    - Prompts user in Myanmar language for affirmative voice confirmation ("အတည်ပြုပါတယ်" / "လုပ်ပါ").
 *    - Coroutine waits for exactly 5,000 milliseconds.
 * 3. Deterministic Auto-Cancellation & FSM Reset:
 *    - If confirmation is received within 5 seconds, transitions FSM to EXECUTING and dispatches action.
 *    - If 5 seconds elapse without affirmative voice input (TIMEOUT), automatically cancels the operation
 *      and resets the FSM StateStore back to IDLE.
 *    - If user explicitly rejects ("မလုပ်နဲ့" / "မခေါ်ပါနဲ့"), cancels immediately and resets to IDLE.
 */
class ActionPermissionGatekeeper(
    private val stateStore: AssistantStateStore = AssistantStateStore.getInstance()
) {

    companion object {
        private const val TAG = "ActionGatekeeper"
        const val SAFETY_CONFIRMATION_TIMEOUT_MS = 5000L // Strict 5-second safety timeout

        @Volatile
        private var instance: ActionPermissionGatekeeper? = null

        fun getInstance(stateStore: AssistantStateStore = AssistantStateStore.getInstance()): ActionPermissionGatekeeper {
            return instance ?: synchronized(this) {
                instance ?: ActionPermissionGatekeeper(stateStore).also { instance = it }
            }
        }

        // Myanmar & English Affirmative Confirmation Keywords
        val AFFIRMATIVE_KEYWORDS = listOf(
            "အတည်ပြုပါတယ်", "အတည်ပြုတယ်", "အတည်ပြု",
            "လုပ်ပါ", "လုပ်မယ်", "လုပ်လိုက်ပါ", "လုပ်ပေးပါ",
            "ဟုတ်ကဲ့", "ဟုတ်တယ်", "ဟုတ်", "သေချာပါတယ်", "သေချာတယ်",
            "ခေါ်ပါ", "ခေါ်မယ်", "ခေါ်လိုက်ပါ", "ခေါ်ပေးပါ",
            "ပို့ပါ", "ပို့မယ်", "ပို့လိုက်ပါ", "ပို့ပေးပါ",
            "ဆက်ပါ", "ဆက်မယ်", "ဆက်လိုက်ပါ",
            "yes", "confirm", "proceed", "call", "send", "okay", "ok"
        )

        // Myanmar & English Rejection/Cancellation Keywords
        val CANCELLATION_KEYWORDS = listOf(
            "မလုပ်နဲ့", "မလုပ်ပါနဲ့", "မလုပ်တော့ဘူး", "မလုပ်တော့ပါ",
            "မခေါ်နဲ့", "မခေါ်ပါနဲ့", "မခေါ်တော့ဘူး", "မခေါ်တော့ပါဘူး",
            "မပို့နဲ့", "မပို့ပါနဲ့", "မပို့တော့ဘူး",
            "မဆက်နဲ့", "မဆက်ပါနဲ့", "မဆက်တော့ဘူး",
            "တော်ပြီ", "ရပ်ပါ", "ရပ်", "ဖျက်ပါ", "ပယ်ဖျက်",
            "no", "cancel", "stop", "abort", "don't", "dont"
        )
    }

    data class PendingAction(
        val actionId: String,
        val actionType: String,
        val target: String,
        val promptMM: String,
        val startTimeMs: Long = System.currentTimeMillis()
    )

    sealed class GatekeeperDecision {
        data class Approved(val action: PendingAction) : GatekeeperDecision()
        data class TimeoutCancelled(val action: PendingAction, val timeoutMs: Long) : GatekeeperDecision()
        data class UserCancelled(val action: PendingAction, val utterance: String) : GatekeeperDecision()
        data class Bypassed(val actionType: String) : GatekeeperDecision()
    }

    private val mutex = Mutex()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _pendingActionState = MutableStateFlow<PendingAction?>(null)
    val pendingActionState: StateFlow<PendingAction?> = _pendingActionState.asStateFlow()

    @Volatile
    private var activeDeferred: CompletableDeferred<Boolean>? = null
    private val isWaitingForConfirmation = AtomicBoolean(false)

    /**
     * Determines whether an action qualifies as High-Risk requiring safety gating.
     */
    fun isHighRisk(actionType: String, target: String): Boolean {
        val type = actionType.uppercase()
        val tgt = target.lowercase()

        // 1. Phone calls & SMS communication
        if (type in listOf("CALL", "DIRECT_CALL", "ACTION_CALL", "SMS", "SEND_SMS", "ACTION_SENDTO") ||
            tgt.contains("call") || tgt.contains("phone") || tgt.contains("sms") || tgt.contains("message")
        ) {
            return true
        }

        // 2. Destructive or irreversible actions
        if (tgt.contains("wipe") || tgt.contains("format") || tgt.contains("reset") ||
            tgt.contains("ဖျက်ချ") || tgt.contains("စက်ပြန်စတင်")
        ) {
            return true
        }

        // 3. Financial or critical risk
        if (tgt.contains("pay") || tgt.contains("transfer") || tgt.contains("money") ||
            tgt.contains("ငွေလွှဲ") || tgt.contains("ငွေပေး")
        ) {
            return true
        }

        return false
    }

    /**
     * Executes a system action with safety gating.
     * If High-Risk, intercepts, prompts user, and waits up to 5 seconds for voice confirmation.
     * Automatically cancels and resets FSM to IDLE if 5 seconds elapse without confirmation.
     */
    suspend fun executeWithSafetyGate(
        actionType: String,
        target: String,
        promptMM: String,
        onConfirmed: suspend () -> CommandResult
    ): CommandResult {
        if (!isHighRisk(actionType, target)) {
            Log.d(TAG, "Action [$actionType -> $target] is SAFE. Bypassing Gatekeeper.")
            stateStore.transitionTo(AssistantState.EXECUTING)
            return onConfirmed()
        }

        Log.w(TAG, "High-Risk action detected [$actionType -> $target]. Engaging 5-second Safety Gate...")
        val action = PendingAction(
            actionId = "${actionType}_${System.currentTimeMillis()}",
            actionType = actionType,
            target = target,
            promptMM = promptMM
        )

        val deferred = CompletableDeferred<Boolean>()
        mutex.withLock {
            _pendingActionState.value = action
            activeDeferred = deferred
            isWaitingForConfirmation.set(true)
            stateStore.transitionTo(AssistantState.AWAITING_CONFIRMATION)
        }

        // Enforce strict 5-second coroutine timeout
        val confirmationReceived: Boolean? = withTimeoutOrNull(SAFETY_CONFIRMATION_TIMEOUT_MS) {
            deferred.await()
        }

        // Clean up pending state
        mutex.withLock {
            _pendingActionState.value = null
            activeDeferred = null
            isWaitingForConfirmation.set(false)
        }

        return when (confirmationReceived) {
            true -> {
                Log.i(TAG, "Voice confirmation GRANTED within 5s for [${action.actionType}]. Executing...")
                stateStore.transitionTo(AssistantState.EXECUTING)
                AuditLogger.log(
                    rawQuery = "CONFIRMED",
                    normalizedQuery = "CONFIRMED",
                    resolutionPath = ResolutionPath.EXACT_FASTPATH,
                    targetAction = action.actionType,
                    confidence = 1.0f,
                    isSuccess = true,
                    details = "Safety confirmation approved within 5s"
                )
                onConfirmed()
            }
            false -> {
                Log.w(TAG, "User explicitly REJECTED confirmation for [${action.actionType}]. Resetting to IDLE.")
                stateStore.resetToIdle()
                AuditLogger.log(
                    rawQuery = "REJECTED",
                    normalizedQuery = "REJECTED",
                    resolutionPath = ResolutionPath.BLOCKED_BY_RISK_POLICY,
                    targetAction = action.actionType,
                    confidence = 1.0f,
                    isSuccess = false,
                    details = "Safety confirmation cancelled by user speech"
                )
                CommandResult(
                    isSuccess = false,
                    action = action.actionType,
                    target = action.target,
                    errorCode = "USER_REJECTED",
                    userMessage = "လုပ်ဆောင်ချက်ကို ပယ်ဖျက်လိုက်ပါပြီခင်ဗျာ။"
                )
            }
            null -> {
                // 5-second timeout expired!
                Log.e(TAG, "Strict 5-Second Confirmation TIMEOUT expired for [${action.actionType}]. Auto-cancelling and resetting FSM to IDLE.")
                stateStore.resetToIdle()
                AuditLogger.log(
                    rawQuery = "TIMEOUT",
                    normalizedQuery = "TIMEOUT_EXPIRED",
                    resolutionPath = ResolutionPath.BLOCKED_BY_RISK_POLICY,
                    targetAction = action.actionType,
                    confidence = 1.0f,
                    isSuccess = false,
                    details = "Strict 5-second confirmation timeout expired. Auto-cancelled."
                )
                CommandResult(
                    isSuccess = false,
                    action = action.actionType,
                    target = action.target,
                    errorCode = "CONFIRMATION_TIMEOUT",
                    userMessage = "၅ စက္ကန့်အတွင်း အတည်ပြုချက် မရရှိသဖြင့် ‘${action.target}’ လုပ်ဆောင်ချက်ကို အလိုအလျောက် ပယ်ဖျက်လိုက်ပါပြီခင်ဗျာ။"
                )
            }
        }
    }

    /**
     * Submits incoming voice transcription to resolve any active safety gate.
     * @return true if input matched affirmative or cancellation and resolved the gate; false otherwise.
     */
    fun submitVoiceInput(transcript: String): Boolean {
        if (!isWaitingForConfirmation.get()) {
            return false
        }

        val clean = transcript.trim().lowercase()
        val deferred = activeDeferred ?: return false

        // ISSUE-015: cancellation is checked BEFORE affirmative.
        // Every Burmese prohibitive cancellation of the form "မ<verb>ပါနဲ့"
        // contains the affirmative keyword "<verb>ပါ" as a substring
        // ("ခေါ်ပါ" ⊂ "မခေါ်ပါနဲ့", "ပို့ပါ" ⊂ "မပို့ပါနဲ့", "လုပ်ပါ" ⊂ "မလုပ်ပါနဲ့"),
        // so affirmative-first substring matching resolved an explicit
        // cancellation as a CONFIRMATION and executed the staged action.
        // Cancellation takes precedence — the same rule ISSUE-029 applied
        // to CallSmsConfirmationCoordinator.
        // Check negative cancellation ("မလုပ်နဲ့", "မခေါ်ပါနဲ့", etc.)
        val isCancellation = CANCELLATION_KEYWORDS.any { clean == it || clean.contains(it) }
        if (isCancellation) {
            Log.i(TAG, "Voice input matched cancellation: [$clean]")
            return deferred.complete(false)
        }

        // Check affirmative confirmation ("အတည်ပြုပါတယ်", "လုပ်ပါ", etc.)
        val isAffirmative = AFFIRMATIVE_KEYWORDS.any { clean == it || clean.contains(it) }
        if (isAffirmative) {
            Log.i(TAG, "Voice input matched affirmative confirmation: [$clean]")
            return deferred.complete(true)
        }

        Log.d(TAG, "Voice input did not resolve safety confirmation: [$clean]")
        return false
    }

    /**
     * Explicitly cancels any active pending safety gate programmatically or via UI button.
     */
    fun cancelActiveGate(reason: String = "USER_DISMISSED"): Boolean {
        if (!isWaitingForConfirmation.get()) return false
        val deferred = activeDeferred ?: return false
        Log.i(TAG, "Explicitly cancelling active safety gate: $reason")
        return deferred.complete(false)
    }

    fun isAwaitingConfirmation(): Boolean = isWaitingForConfirmation.get()
}
