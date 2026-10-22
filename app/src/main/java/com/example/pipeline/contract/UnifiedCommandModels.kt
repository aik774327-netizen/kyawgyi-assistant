package com.example.pipeline.contract

import com.example.semantic.model.ConfidenceLevel
import com.example.semantic.model.SemanticEntity
import com.example.semantic.model.SemanticIntent

/**
 * Risk Level Classification for Actions
 */
enum class RiskLevel {
    LOW,        // Read-only or harmless system actions (e.g., volume, time, app launch)
    MEDIUM,     // Minor state changes (e.g., toggle wifi, set alarm, create note)
    HIGH,       // Communication or modification (e.g., call, send message, edit file)
    CRITICAL    // Destructive or financial operations (e.g., delete, purchase, factory reset)
}

/**
 * Inbound Command Input Source
 */
enum class InputSource {
    VOICE,
    TYPED,
    ACCESSIBILITY,
    SHORTCUT,
    URL_WEBHOOK,
    SYSTEM_EVENT,
    TEST_HARNESS
}

/**
 * Command Execution Status
 */
enum class ExecutionStatus {
    IDLE,
    WAKE_DETECTED,
    LISTENING,
    TRANSCRIBING,
    UNDERSTANDING,
    PROCESSING,
    PLANNING,
    SAFETY_CHECK,
    AWAITING_CONFIRMATION,
    EXECUTING,
    VERIFYING,
    RESPONDING,
    SUCCESS,
    NOT_VERIFIABLE,
    UNKNOWN,
    SAFE_FAIL,
    FAILED,
    OFFLINE,
    PERMISSION_REQUIRED
}

/**
 * Single step inside an ActionPlan
 */
data class ActionStep(
    val stepId: String,
    val actionType: String,
    val target: String? = null,
    val parameters: Map<String, Any?> = emptyMap(),
    val riskLevel: RiskLevel = RiskLevel.LOW,
    val requiresConfirmation: Boolean = false,
    val confirmationPromptMM: String? = null
)

/**
 * Structured Plan produced by ActionPlanner
 */
data class UnifiedActionPlan(
    val planId: String,
    val primaryIntent: String,
    val target: String? = null,
    val steps: List<ActionStep>,
    val overallConfidence: Float,
    val requiresConfirmation: Boolean = false,
    val confirmationPromptMM: String? = null,
    val clarificationPromptMM: String? = null,
    val fallbackPlan: String? = null,
    val isMultiStep: Boolean = steps.size > 1
)

/**
 * Execution Result with Post-Execution Verification
 */
data class UnifiedActionResult(
    val success: Boolean,
    val verified: Boolean,
    val actionType: String,
    val target: String? = null,
    val parameters: Map<String, Any?> = emptyMap(),
    val reason: String? = null,
    val errorCode: String? = null,
    val outputData: Map<String, Any?> = emptyMap(),
    val executionTimeMs: Long = 0L,
    val timestamp: Long = System.currentTimeMillis(),
    // ISSUE-037: propagated from the executor's CommandResult — the ER entry
    // already minted for this failure event, if any.
    val errorLearningEntryId: String? = null
)

/**
 * Inbound Unified Command Request
 */
data class UnifiedCommandRequest(
    val requestId: String,
    val sessionId: String,
    val transcript: String,
    val rawInput: String = transcript,
    val source: InputSource = InputSource.VOICE,
    val normalizedText: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val isVoiceInput: Boolean = source == InputSource.VOICE,
    val isOfflineMode: Boolean = false,
    val metadata: Map<String, String> = emptyMap(),
    val attachedUri: String? = null,
    val attachedAssetId: Long? = null,
    val attachedAssetCategory: String? = null
)

/**
 * Outbound Canonical Command Result
 */
data class UnifiedCommandResult(
    val success: Boolean,
    val commandType: String,
    val target: String?,
    val responseText: String,
    val confidence: Float,
    val riskLevel: RiskLevel,
    val executionStatus: ExecutionStatus,
    val errorCode: String? = null,
    val telemetryId: String? = null,
    val requiresConfirmation: Boolean = false,
    val latencyMs: Long = 0L,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Outbound Unified Command Response
 */
data class UnifiedCommandResponse(
    val requestId: String,
    val sessionId: String,
    val originalTranscript: String,
    val normalizedText: String,
    val intent: String,
    val target: String?,
    val parameters: Map<String, Any?>,
    val confidence: Float,
    val confidenceLevel: ConfidenceLevel,
    val requiresConfirmation: Boolean,
    val actionPlan: UnifiedActionPlan,
    val executionResult: UnifiedActionResult?,
    val verificationResult: Boolean,
    val responseMyanmar: String,
    val isOfflineProcessed: Boolean,
    val latencyMs: Long,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toCanonicalResult(): UnifiedCommandResult {
        val outcomeTag = executionResult?.outputData?.get("verificationOutcome") as? String
        val status = when {
            requiresConfirmation -> ExecutionStatus.AWAITING_CONFIRMATION
            verificationResult -> ExecutionStatus.SUCCESS
            outcomeTag == "NOT_VERIFIABLE" || executionResult?.errorCode?.contains("NOT_VERIFIABLE") == true -> ExecutionStatus.NOT_VERIFIABLE
            outcomeTag == "UNKNOWN" -> ExecutionStatus.UNKNOWN
            outcomeTag == "SAFE_FAIL" || outcomeTag == "UNSUPPORTED" -> ExecutionStatus.SAFE_FAIL
            else -> ExecutionStatus.FAILED
        }
        return UnifiedCommandResult(
            success = verificationResult,
            commandType = intent,
            target = target,
            responseText = responseMyanmar,
            confidence = confidence,
            riskLevel = actionPlan.steps.firstOrNull()?.riskLevel ?: RiskLevel.LOW,
            executionStatus = status,
            errorCode = executionResult?.errorCode,
            telemetryId = requestId,
            requiresConfirmation = requiresConfirmation,
            latencyMs = latencyMs,
            timestamp = timestamp
        )
    }
}
