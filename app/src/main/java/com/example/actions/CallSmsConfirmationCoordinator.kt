package com.example.actions

import android.content.Context
import android.util.Log
import com.example.discovery.DeviceContactResolver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * CallSmsConfirmationCoordinator enforces Google Play & Android OS Security compliance
 * for sensitive native phone calls and SMS communications.
 *
 * It prevents silent background dialing or SMS dispatching by requiring an explicit
 * user consent / confirmation step:
 * Step 1: When user requests a call or SMS, prompts user:
 *         e.g., "‘[နာမည်]’ သို့ ဖုန်းခေါ်မည် သေချာပါသလားခင်ဗျာ။ (ဟုတ်ကဲ့ / ခေါ်ပါ သို့မဟုတ် မခေါ်ပါနဲ့ ဟု ပြောပါ)"
 * Step 2: Waits for user affirmative confirmation ("ဟုတ်ကဲ့", "ခေါ်ပါ", "သေချာပါတယ်", "yes", etc.)
 *         or cancellation ("မခေါ်ပါနဲ့", "မလုပ်နဲ့", "cancel", "no", etc.).
 * Step 3: Only upon explicit confirmation does it dispatch the Android Intent (ACTION_CALL / ACTION_DIAL)
 *         or SMS via System SmsManager / ACTION_SENDTO.
 */
class CallSmsConfirmationCoordinator private constructor() {

    companion object {
        private const val TAG = "CallSmsConfirmCoordinator"
        private const val CONFIRMATION_TIMEOUT_MS = 5_000L // 5 seconds strict safety timeout

        @Volatile
        private var instance: CallSmsConfirmationCoordinator? = null

        fun getInstance(): CallSmsConfirmationCoordinator {
            return instance ?: synchronized(this) {
                instance ?: CallSmsConfirmationCoordinator().also { instance = it }
            }
        }
    }

    sealed class PendingCommunication(
        val timestamp: Long,
        val target: String,
        val displayName: String,
        val phoneNumber: String?
    ) {
        class Call(
            target: String,
            displayName: String,
            phoneNumber: String?,
            timestamp: Long = System.currentTimeMillis()
        ) : PendingCommunication(timestamp, target, displayName, phoneNumber)

        class Sms(
            target: String,
            displayName: String,
            phoneNumber: String?,
            val message: String,
            timestamp: Long = System.currentTimeMillis()
        ) : PendingCommunication(timestamp, target, displayName, phoneNumber)
    }

    private val _pendingState = MutableStateFlow<PendingCommunication?>(null)
    val pendingState: StateFlow<PendingCommunication?> = _pendingState.asStateFlow()

    @Volatile
    private var pendingAction: PendingCommunication? = null

    // Affirmative confirmation keywords (Myanmar & English)
    private val confirmationKeywords = listOf(
        "အတည်ပြုပါတယ်", "အတည်ပြုတယ်", "အတည်ပြု", "လုပ်ပါ", "လုပ်မယ်", "လုပ်လိုက်ပါ", "လုပ်ပေးပါ",
        "ဟုတ်ကဲ့", "ဟုတ်တယ်", "ဟုတ်", "သေချာပါတယ်", "သေချာတယ်", "သေချာ",
        "ခေါ်ပါ", "ခေါ်မယ်", "ခေါ်လိုက်ပါ", "ခေါ်ပေးပါ", "ခေါ်",
        "ဆက်ပါ", "ဆက်မယ်", "ဆက်လိုက်ပါ", "ဆက်ပေးပါ", "ဆက်",
        "ပို့ပါ", "ပို့မယ်", "ပို့လိုက်ပါ", "ပို့ပေးပါ", "ပို့",
        "yes", "confirm", "confirmed", "sure", "ok", "okay", "call", "send", "dial", "proceed"
    )

    // Negative cancellation keywords (Myanmar & English)
    private val cancellationKeywords = listOf(
        "မခေါ်ပါနဲ့", "မခေါ်နဲ့", "မခေါ်တော့ဘူး", "မခေါ်တော့ပါဘူး", "မခေါ်ဘူး",
        "မဆက်ပါနဲ့", "မဆက်နဲ့", "မဆက်တော့ဘူး",
        "မပို့ပါနဲ့", "မပို့နဲ့", "မပို့တော့ဘူး",
        "မလုပ်နဲ့", "မလုပ်ပါနဲ့", "မလုပ်တော့ဘူး", "မလုပ်တော့ပါ",
        "တော်ပြီ", "ရပ်ပါ", "ရပ်", "ပယ်ဖျက်", "ဖျက်ပါ", "ဖျက်မယ်",
        "no", "cancel", "stop", "don't", "dont", "abort", "nevermind", "never mind"
    )

    @Synchronized
    fun getPendingAction(): PendingCommunication? {
        val current = pendingAction ?: return null
        val now = System.currentTimeMillis()
        if (now - current.timestamp > CONFIRMATION_TIMEOUT_MS) {
            Log.d(TAG, "Pending communication timed out after ${CONFIRMATION_TIMEOUT_MS}ms. Resetting FSM to IDLE.")
            pendingAction = null
            _pendingState.value = null
            com.kyawgyi.assistant.state.AssistantStateStore.getInstance().resetToIdle()
            return null
        }
        return current
    }

    @Synchronized
    fun hasPendingAction(): Boolean {
        return getPendingAction() != null
    }

    @Synchronized
    fun clearPending() {
        pendingAction = null
        _pendingState.value = null
    }

    /**
     * Checks if given speech text expresses affirmative confirmation.
     *
     * ISSUE-029 (Phase 1.5): cancellation takes precedence — a reply
     * that matches any cancellation form (e.g. the mixed reply
     * "ဟုတ်ကဲ့ မခေါ်ပါနဲ့", "yes, but don't call") is NEVER a
     * confirmation, even though it also contains an affirmative
     * keyword. Call/SMS may only execute on an unambiguous affirmative.
     */
    fun isConfirmation(text: String): Boolean {
        if (isCancellation(text)) return false
        val clean = text.trim().lowercase()
        return confirmationKeywords.any { keyword ->
            clean == keyword || clean.contains(keyword)
        }
    }

    /**
     * Checks if given speech text expresses cancellation.
     */
    fun isCancellation(text: String): Boolean {
        val clean = text.trim().lowercase()
        return cancellationKeywords.any { keyword ->
            clean == keyword || clean.contains(keyword)
        }
    }

    /**
     * Stages a call for explicit confirmation before triggering Android Intent.
     */
    @Synchronized
    fun stageCall(target: String, resolvedName: String, phoneNumber: String?): String {
        val action = PendingCommunication.Call(
            target = target,
            displayName = resolvedName,
            phoneNumber = phoneNumber
        )
        pendingAction = action
        _pendingState.value = action
        val phoneDisplay = if (!phoneNumber.isNullOrBlank() && phoneNumber != resolvedName) " ($phoneNumber)" else ""
        val prompt = "‘$resolvedName’$phoneDisplay သို့ ဖုန်းခေါ်မည် သေချာပါသလားခင်ဗျာ။ (အတည်ပြုပါတယ် / လုပ်ပါ ဟု ၅ စက္ကန့်အတွင်း ပြောပါ)"
        Log.i(TAG, "Staged CALL for user confirmation: $resolvedName ($phoneNumber)")
        com.kyawgyi.assistant.state.AssistantStateStore.getInstance().transitionTo(com.kyawgyi.assistant.state.AssistantState.AWAITING_CONFIRMATION)
        return prompt
    }

    /**
     * Stages an SMS for explicit confirmation before sending.
     */
    @Synchronized
    fun stageSms(target: String, resolvedName: String, phoneNumber: String?, message: String): String {
        val action = PendingCommunication.Sms(
            target = target,
            displayName = resolvedName,
            phoneNumber = phoneNumber,
            message = message
        )
        pendingAction = action
        _pendingState.value = action
        val phoneDisplay = if (!phoneNumber.isNullOrBlank() && phoneNumber != resolvedName) " ($phoneNumber)" else ""
        val prompt = "‘$resolvedName’$phoneDisplay ထံသို့ \"$message\" မက်ဆေ့ခ်ျ ပို့မည် သေချာပါသလားခင်ဗျာ။ (အတည်ပြုပါတယ် / ပို့ပါ ဟု ၅ စက္ကန့်အတွင်း ပြောပါ)"
        Log.i(TAG, "Staged SMS for user confirmation: $resolvedName ($phoneNumber)")
        com.kyawgyi.assistant.state.AssistantStateStore.getInstance().transitionTo(com.kyawgyi.assistant.state.AssistantState.AWAITING_CONFIRMATION)
        return prompt
    }

    /**
     * Executes the pending call or SMS upon verified user confirmation.
     */
    @Synchronized
    fun executeConfirmed(context: Context): CommandResult {
        val pending = getPendingAction()
        if (pending == null) {
            return CommandResult(
                isSuccess = false,
                action = "COMMUNICATION_CONFIRM",
                target = "",
                errorCode = "NO_PENDING_ACTION",
                userMessage = "အတည်ပြုရန် ဖုန်းခေါ်ဆိုမှု သို့မဟုတ် မက်ဆေ့ခ်ျ မရှိတော့ပါခင်ဗျာ။"
            )
        }
        pendingAction = null // clear pending
        _pendingState.value = null
        com.kyawgyi.assistant.state.AssistantStateStore.getInstance().transitionTo(com.kyawgyi.assistant.state.AssistantState.EXECUTING)

        val resolver = DeviceContactResolver.getInstance(context)
        return when (pending) {
            is PendingCommunication.Call -> {
                val phone = pending.phoneNumber ?: pending.target
                val outcome = resolver.makePhoneCall(phone, pending.displayName)
                CommandResult(
                    isSuccess = outcome.isSuccess,
                    action = "CALL_PHONE",
                    target = pending.displayName,
                    parameters = mapOf(
                        "contactName" to pending.displayName,
                        "phoneNumber" to phone,
                        "isDirectCall" to outcome.isDirectCall.toString(),
                        "userExplicitlyConfirmed" to "true"
                    ),
                    executionStarted = outcome.isSuccess,
                    executionConfirmed = true,
                    userMessage = outcome.userMessageMM
                )
            }
            is PendingCommunication.Sms -> {
                val phone = pending.phoneNumber ?: pending.target
                val outcome = resolver.sendSmsOrOpenComposer(phone, pending.message, pending.displayName)
                CommandResult(
                    isSuccess = outcome.isSuccess,
                    action = "SEND_MESSAGE",
                    target = pending.displayName,
                    parameters = mapOf(
                        "contactName" to pending.displayName,
                        "phoneNumber" to phone,
                        "message" to pending.message,
                        "isDirectSend" to outcome.isDirectSend.toString(),
                        "userExplicitlyConfirmed" to "true"
                    ),
                    executionStarted = outcome.isSuccess,
                    executionConfirmed = true,
                    userMessage = outcome.userMessageMM
                )
            }
        }
    }

    /**
     * Cancels the pending call or SMS upon user request.
     */
    @Synchronized
    fun cancelPending(reason: String? = null): CommandResult {
        val pending = pendingAction
        pendingAction = null
        _pendingState.value = null
        com.kyawgyi.assistant.state.AssistantStateStore.getInstance().resetToIdle()
        val msg = when (pending) {
            is PendingCommunication.Call -> "‘${pending.displayName}’ သို့ ဖုန်းခေါ်ဆိုမှုကို ပယ်ဖျက်လိုက်ပါပြီခင်ဗျာ။"
            is PendingCommunication.Sms -> "‘${pending.displayName}’ ထံ မက်ဆေ့ခ်ျ ပို့ဆောင်မှုကို ပယ်ဖျက်လိုက်ပါပြီခင်ဗျာ။"
            null -> "လုပ်ဆောင်ချက်ကို ပယ်ဖျက်လိုက်ပါပြီခင်ဗျာ။"
        }
        Log.i(TAG, "Cancelled pending communication. Reason: $reason")
        return CommandResult(
            isSuccess = true,
            action = "CANCEL_COMMUNICATION",
            target = pending?.displayName ?: "",
            executionStarted = false,
            executionConfirmed = false,
            userMessage = msg
        )
    }
}
