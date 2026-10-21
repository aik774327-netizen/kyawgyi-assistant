package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.pipeline.UnifiedIntelligencePipeline
import com.example.pipeline.contract.ExecutionStatus
import com.example.pipeline.contract.InputSource
import com.example.pipeline.contract.RiskLevel
import com.example.pipeline.contract.UnifiedCommandRequest
import com.example.pipeline.contract.UnifiedCommandResponse
import com.example.pipeline.contract.UnifiedCommandResult
import com.example.router.DirectSettingsRouter
import com.example.router.SettingsDestination
import com.example.telemetry.TelemetryEventType
import com.example.telemetry.TelemetryManager
import com.example.utils.NetworkMonitor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * UI State Model for Nexus / Kyaw Gyi Assistant
 */
sealed class NexusUiState(val statusMM: String, val executionStatus: ExecutionStatus) {
    data object Idle : NexusUiState("အသင့်ရှိပါသည်", ExecutionStatus.IDLE)
    data class Listening(val transcript: String = "") : NexusUiState("နားထောင်နေပါတယ်…", ExecutionStatus.LISTENING)
    data class Processing(val input: String) : NexusUiState("စဉ်းစားနေပါတယ်…", ExecutionStatus.PROCESSING)
    data class Planning(val planDetails: String) : NexusUiState("အစီအစဉ် ရေးဆွဲနေပါတယ်…", ExecutionStatus.PLANNING)
    data class AwaitingConfirmation(
        val promptMM: String,
        val target: String?,
        val commandType: String,
        val pendingRequestId: String
    ) : NexusUiState("အတည်ပြုပေးပါ။", ExecutionStatus.AWAITING_CONFIRMATION)
    data class Executing(val actionMM: String) : NexusUiState("လုပ်ဆောင်နေပါတယ်…", ExecutionStatus.EXECUTING)
    data class Success(val responseMM: String, val target: String?) : NexusUiState("လုပ်ဆောင်ပြီးပါပြီ။", ExecutionStatus.SUCCESS)
    data class Failed(val errorMM: String, val errorCode: String?) : NexusUiState("နားမလည်လိုက်ပါဘူး။", ExecutionStatus.FAILED)
    data class Offline(val messageMM: String = "အော့ဖ်လိုင်းအခြေအနေဖြစ်ပါသည်") : NexusUiState(messageMM, ExecutionStatus.OFFLINE)
    data class PermissionRequired(
        val destination: SettingsDestination,
        val guidanceMM: String
    ) : NexusUiState("ခွင့်ပြုချက် လိုအပ်ပါသည်", ExecutionStatus.PERMISSION_REQUIRED)
}

/**
 * NexusViewModel - Single Source of Truth UI ViewModel delegating all command
 * interpretation, reasoning, safety validation, and execution to UnifiedIntelligencePipeline.
 */
class NexusViewModel(application: Application) : AndroidViewModel(application) {

    private val pipeline = UnifiedIntelligencePipeline.getInstance(application)
    private val settingsRouter = DirectSettingsRouter(application)

    private val _uiState = MutableStateFlow<NexusUiState>(NexusUiState.Idle)
    val uiState: StateFlow<NexusUiState> = _uiState.asStateFlow()

    private val _lastResult = MutableStateFlow<UnifiedCommandResult?>(null)
    val lastResult: StateFlow<UnifiedCommandResult?> = _lastResult.asStateFlow()

    private val _commandHistory = MutableStateFlow<List<UnifiedCommandResult>>(emptyList())
    val commandHistory: StateFlow<List<UnifiedCommandResult>> = _commandHistory.asStateFlow()

    private var currentSessionId: String = UUID.randomUUID().toString()
    private var pendingResponse: UnifiedCommandResponse? = null

    /**
     * Submit user command (Voice or Typed) to UnifiedIntelligencePipeline
     */
    fun submitCommand(rawText: String, source: InputSource = InputSource.TYPED): kotlinx.coroutines.Job {
        if (rawText.isBlank()) return viewModelScope.launch {}

        return viewModelScope.launch(Dispatchers.Default) {
            _uiState.value = NexusUiState.Processing(rawText)

            val request = UnifiedCommandRequest(
                requestId = "req_${UUID.randomUUID()}",
                sessionId = currentSessionId,
                transcript = rawText,
                rawInput = rawText,
                source = source,
                isOfflineMode = !NetworkMonitor.isInternetAvailable(getApplication())
            )

            val response = pipeline.processCommand(request, speakResponse = true)
            val result = response.toCanonicalResult()
            _lastResult.value = result
            _commandHistory.value = (_commandHistory.value + result).takeLast(50)

            if (response.requiresConfirmation) {
                pendingResponse = response
                _uiState.value = NexusUiState.AwaitingConfirmation(
                    promptMM = response.responseMyanmar,
                    target = response.target,
                    commandType = response.intent,
                    pendingRequestId = response.requestId
                )
            } else if (response.confidence < 0.50f) {
                _uiState.value = NexusUiState.Failed(
                    errorMM = response.responseMyanmar,
                    errorCode = "LOW_CONFIDENCE"
                )
            } else if (result.success) {
                _uiState.value = NexusUiState.Success(
                    responseMM = response.responseMyanmar,
                    target = response.target
                )
            } else {
                _uiState.value = NexusUiState.Failed(
                    errorMM = response.responseMyanmar,
                    errorCode = result.errorCode
                )
            }
        }
    }

    /**
     * Confirm high-risk pending action
     */
    fun confirmPendingAction(): kotlinx.coroutines.Job {
        val pending = pendingResponse ?: return viewModelScope.launch {}
        return viewModelScope.launch(Dispatchers.Default) {
            _uiState.value = NexusUiState.Executing("လုပ်ဆောင်နေပါတယ်…")
            TelemetryManager.recordEvent(
                eventType = TelemetryEventType.CONFIRMATION_RECEIVED,
                sessionId = currentSessionId,
                details = "user_confirmed=true, intent=${pending.intent}"
            )

            // Submit explicit confirmation execution command
            val confirmRequest = UnifiedCommandRequest(
                requestId = "req_confirmed_${UUID.randomUUID()}",
                sessionId = currentSessionId,
                transcript = "${pending.intent} ${pending.target ?: ""}",
                source = InputSource.SYSTEM_EVENT
            )
            val confirmResponse = pipeline.processCommand(confirmRequest, speakResponse = true)
            val result = confirmResponse.toCanonicalResult()
            _lastResult.value = result
            pendingResponse = null

            _uiState.value = NexusUiState.Success(confirmResponse.responseMyanmar, confirmResponse.target)
        }
    }

    /**
     * Cancel pending action
     */
    fun cancelPendingAction() {
        TelemetryManager.recordEvent(
            eventType = TelemetryEventType.CONFIRMATION_RECEIVED,
            sessionId = currentSessionId,
            details = "user_confirmed=false, cancelled"
        )
        pendingResponse = null
        _uiState.value = NexusUiState.Idle
    }

    /**
     * Start microphone listening
     */
    fun startListening() {
        TelemetryManager.recordEvent(
            eventType = TelemetryEventType.MICROPHONE_STARTED,
            sessionId = currentSessionId,
            details = "startListening called"
        )
        _uiState.value = NexusUiState.Listening("")
    }

    /**
     * Stop microphone listening and submit transcript
     */
    fun stopListening(finalTranscript: String) {
        TelemetryManager.recordEvent(
            eventType = TelemetryEventType.MICROPHONE_STOPPED,
            sessionId = currentSessionId,
            details = "stopListening called, transcript_length=${finalTranscript.length}"
        )
        if (finalTranscript.isNotBlank()) {
            submitCommand(finalTranscript, source = InputSource.VOICE)
        } else {
            _uiState.value = NexusUiState.Idle
        }
    }

    /**
     * Route user to direct system settings screen for capability
     */
    fun navigateToDirectSettings(destination: SettingsDestination) {
        settingsRouter.openSettings(destination)
    }

    /**
     * Re-verify permission after returning from Settings
     */
    fun verifyPermissionState(destination: SettingsDestination): Boolean {
        val granted = settingsRouter.isCapabilityGranted(destination)
        if (granted && _uiState.value is NexusUiState.PermissionRequired) {
            _uiState.value = NexusUiState.Idle
        }
        return granted
    }
}
