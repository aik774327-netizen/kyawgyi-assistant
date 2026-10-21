package com.example.pipeline

import android.content.Context
import android.util.Log
import com.example.actions.KyawGyiActionExecutor
import com.example.pipeline.contract.ActionStep
import com.example.pipeline.contract.RiskLevel
import com.example.pipeline.contract.UnifiedActionPlan
import com.example.pipeline.contract.UnifiedActionResult
import com.example.pipeline.verifier.PostExecutionVerifier
import com.example.pipeline.verifier.VerificationReport
import com.example.pipeline.verifier.VerificationStatus
import com.example.security.UnifiedSafetyValidator
import com.example.telemetry.TelemetryEventType
import com.example.telemetry.TelemetryManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Transactional Execution Outcome
 */
data class TransactionalExecutionResult(
    val isSuccess: Boolean,
    val isVerified: Boolean,
    val status: VerificationStatus,
    val actionPlan: UnifiedActionPlan,
    val stepResults: List<UnifiedActionResult>,
    val finalReport: VerificationReport,
    val executionTimeMs: Long
)

/**
 * ActionPlanExecutor (Stage 16 Production Hardening)
 *
 * Implements strict transactional pipeline execution:
 * PLAN -> VALIDATE -> PRECONDITION_CHECK -> EXECUTE -> VERIFY -> SUCCESS / FAILED / EXECUTED_UNVERIFIED
 */
class ActionPlanExecutor(
    private val context: Context,
    private val actionExecutor: KyawGyiActionExecutor = KyawGyiActionExecutor(context),
    private val postExecutionVerifier: PostExecutionVerifier = PostExecutionVerifier(context)
) {

    companion object {
        private const val TAG = "ActionPlanExecutor"
    }

    /**
     * Transactionally execute an ActionPlan with safety checks and post-verification
     */
    suspend fun executePlan(
        plan: UnifiedActionPlan,
        isUserConfirmed: Boolean = false,
        rawQuery: String = ""
    ): TransactionalExecutionResult = withContext(Dispatchers.Default) {
        val startTime = System.currentTimeMillis()
        val stepResults = mutableListOf<UnifiedActionResult>()

        for (step in plan.steps) {
            // 1. Safety Validation Gate
            val safety = UnifiedSafetyValidator.evaluate(
                actionType = step.actionType,
                target = step.target,
                confidence = plan.overallConfidence,
                parameters = step.parameters
            )

            if (safety.requiresUserConfirmation && !isUserConfirmed) {
                Log.w(TAG, "Step ${step.stepId} requires explicit user confirmation before execution")
                val unverifiedReport = VerificationReport(
                    status = VerificationStatus.PRECONDITION_FAILED,
                    isSuccess = false,
                    isVerified = false,
                    actionType = step.actionType,
                    target = step.target,
                    details = safety.confirmationPromptMM ?: "User confirmation required",
                    errorCode = "CONFIRMATION_REQUIRED"
                )
                return@withContext TransactionalExecutionResult(
                    isSuccess = false,
                    isVerified = false,
                    status = VerificationStatus.PRECONDITION_FAILED,
                    actionPlan = plan,
                    stepResults = stepResults,
                    finalReport = unverifiedReport,
                    executionTimeMs = System.currentTimeMillis() - startTime
                )
            }

            // 2. Precondition Check & Capture Pre-State
            val preState = postExecutionVerifier.capturePreState(step.actionType)

            // 3. Execution Dispatch
            TelemetryManager.recordEvent(
                eventType = TelemetryEventType.ACTION_STARTED,
                details = "action=${step.actionType}, target=${step.target}"
            )
            val stepStart = System.currentTimeMillis()
            val dispatchResult = dispatchStep(step, rawQuery)

            // 4. Post-Execution Verification
            val verificationReport = postExecutionVerifier.verify(
                actionType = step.actionType,
                target = step.target,
                parameters = step.parameters,
                initialSuccess = dispatchResult.success,
                preState = preState
            )

            val actionResult = UnifiedActionResult(
                success = verificationReport.isSuccess,
                verified = verificationReport.isVerified,
                actionType = step.actionType,
                target = step.target,
                parameters = step.parameters,
                reason = verificationReport.details,
                errorCode = verificationReport.errorCode,
                outputData = verificationReport.observedState,
                executionTimeMs = System.currentTimeMillis() - stepStart
            )
            stepResults.add(actionResult)

            TelemetryManager.recordEvent(
                eventType = if (verificationReport.isSuccess) TelemetryEventType.ACTION_COMPLETED else TelemetryEventType.ACTION_FAILED,
                details = "action=${step.actionType}, status=${verificationReport.status}, details=${verificationReport.details}"
            )

            if (!verificationReport.isSuccess) {
                Log.e(TAG, "Step ${step.stepId} failed: ${verificationReport.details}")
                return@withContext TransactionalExecutionResult(
                    isSuccess = false,
                    isVerified = false,
                    status = verificationReport.status,
                    actionPlan = plan,
                    stepResults = stepResults,
                    finalReport = verificationReport,
                    executionTimeMs = System.currentTimeMillis() - startTime
                )
            }
        }

        val lastReport = stepResults.lastOrNull()?.let {
            VerificationReport(
                status = if (it.verified) VerificationStatus.VERIFIED_SUCCESS else VerificationStatus.EXECUTED_UNVERIFIED,
                isSuccess = it.success,
                isVerified = it.verified,
                actionType = it.actionType,
                target = it.target,
                details = it.reason ?: "Plan executed"
            )
        } ?: VerificationReport(
            status = VerificationStatus.VERIFIED_SUCCESS,
            isSuccess = true,
            isVerified = true,
            actionType = plan.primaryIntent,
            target = plan.target,
            details = "No steps to execute"
        )

        return@withContext TransactionalExecutionResult(
            isSuccess = true,
            isVerified = stepResults.all { it.verified },
            status = lastReport.status,
            actionPlan = plan,
            stepResults = stepResults,
            finalReport = lastReport,
            executionTimeMs = System.currentTimeMillis() - startTime
        )
    }

    private fun dispatchStep(step: ActionStep, rawQuery: String): UnifiedActionResult {
        val start = System.currentTimeMillis()
        return try {
            when (step.actionType.uppercase()) {
                "OPEN_APP" -> {
                    val cmdResult = actionExecutor.openApplication(step.target ?: "", step.target ?: "")
                    UnifiedActionResult(
                        success = cmdResult.isSuccess,
                        verified = cmdResult.isSuccess,
                        actionType = step.actionType,
                        target = step.target,
                        parameters = step.parameters,
                        reason = cmdResult.userMessage,
                        executionTimeMs = System.currentTimeMillis() - start
                    )
                }
                "VOLUME_UP", "VOLUME_DOWN", "SET_VOLUME", "MUTE", "BRIGHTNESS_UP", "BRIGHTNESS_DOWN" -> {
                    val cmdResult = actionExecutor.executeSystemAction(rawQuery.ifBlank { step.actionType })
                    UnifiedActionResult(
                        success = cmdResult?.isSuccess ?: true,
                        verified = cmdResult?.isSuccess ?: true,
                        actionType = step.actionType,
                        target = step.target,
                        parameters = step.parameters,
                        reason = cmdResult?.userMessage ?: "Volume changed",
                        executionTimeMs = System.currentTimeMillis() - start
                    )
                }
                "FLASHLIGHT" -> {
                    val enable = step.parameters["enable"] as? Boolean ?: true
                    val cmdResult = actionExecutor.setFlashlight(enable)
                    UnifiedActionResult(
                        success = cmdResult.isSuccess,
                        verified = cmdResult.isSuccess,
                        actionType = step.actionType,
                        target = step.target,
                        parameters = step.parameters,
                        reason = cmdResult.userMessage,
                        executionTimeMs = System.currentTimeMillis() - start
                    )
                }
                "WIFI_TOGGLE" -> {
                    val enable = step.parameters["enable"] as? Boolean ?: true
                    val cmdResult = actionExecutor.setWifi(enable)
                    UnifiedActionResult(
                        success = cmdResult.isSuccess,
                        verified = cmdResult.isSuccess,
                        actionType = step.actionType,
                        target = step.target,
                        parameters = step.parameters,
                        reason = cmdResult.userMessage,
                        executionTimeMs = System.currentTimeMillis() - start
                    )
                }
                "BLUETOOTH_TOGGLE" -> {
                    val enable = step.parameters["enable"] as? Boolean ?: true
                    val cmdResult = actionExecutor.setBluetooth(enable)
                    UnifiedActionResult(
                        success = cmdResult.isSuccess,
                        verified = cmdResult.isSuccess,
                        actionType = step.actionType,
                        target = step.target,
                        parameters = step.parameters,
                        reason = cmdResult.userMessage,
                        executionTimeMs = System.currentTimeMillis() - start
                    )
                }
                "BACK" -> {
                    com.example.service.KyawGyiAccessibilityService.instance?.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
                    UnifiedActionResult(success = true, verified = true, actionType = step.actionType, target = step.target)
                }
                "HOME" -> {
                    com.example.service.KyawGyiAccessibilityService.instance?.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME)
                    UnifiedActionResult(success = true, verified = true, actionType = step.actionType, target = step.target)
                }
                "RECENTS" -> {
                    com.example.service.KyawGyiAccessibilityService.instance?.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_RECENTS)
                    UnifiedActionResult(success = true, verified = true, actionType = step.actionType, target = step.target)
                }
                "NOTIFICATIONS" -> {
                    com.example.service.KyawGyiAccessibilityService.instance?.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
                    UnifiedActionResult(success = true, verified = true, actionType = step.actionType, target = step.target)
                }
                "QUICK_SETTINGS" -> {
                    com.example.service.KyawGyiAccessibilityService.instance?.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
                    UnifiedActionResult(success = true, verified = true, actionType = step.actionType, target = step.target)
                }
                "OPEN_SETTINGS" -> {
                    val router = com.example.router.DirectSettingsRouter.getInstance(context)
                    val destName = step.parameters["settingsDestination"] as? String
                    val destination = destName?.let { name ->
                        com.example.router.SettingsDestination.entries.firstOrNull { it.name == name }
                    } ?: router.resolveSettingsDestination(step.target ?: rawQuery)
                        ?: com.example.router.SettingsDestination.APPLICATION_DETAILS
                    val report = router.openSettingsWithReport(destination)
                    UnifiedActionResult(
                        success = report.dispatchSucceeded,
                        verified = report.dispatchSucceeded,
                        actionType = step.actionType,
                        target = destination.titleMM,
                        parameters = step.parameters + mapOf(
                            "settingsDestination" to destination.name,
                            "resolvedIntentAction" to report.resolvedIntentAction
                        ),
                        reason = report.messageMM,
                        executionTimeMs = System.currentTimeMillis() - start
                    )
                }
                "TAKE_SCREENSHOT" -> {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                        com.example.service.KyawGyiAccessibilityService.instance?.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT)
                    }
                    UnifiedActionResult(success = true, verified = true, actionType = step.actionType, target = step.target)
                }
                "PLAY_MUSIC" -> {
                    val cmdResult = actionExecutor.playMedia(
                        query = step.target ?: "Myanmar song",
                        artist = step.parameters["artist"] as? String,
                        title = step.parameters["title"] as? String
                    )
                    UnifiedActionResult(success = cmdResult.isSuccess, verified = cmdResult.isSuccess, actionType = step.actionType, target = step.target)
                }
                "CALL_CONTACT", "CALL_PHONE" -> {
                    val targetContact = step.target ?: step.parameters["contactName"] as? String ?: rawQuery
                    val userConfirmed = step.parameters["userExplicitlyConfirmed"] == "true" || step.parameters["user_confirmed"] == "true"
                    val cmdResult = actionExecutor.callContact(targetContact, bypassConfirmation = userConfirmed)
                    UnifiedActionResult(
                        success = cmdResult.isSuccess,
                        verified = cmdResult.isSuccess && !cmdResult.parameters.containsKey("requiresConfirmation"),
                        actionType = step.actionType,
                        target = step.target,
                        parameters = cmdResult.parameters,
                        reason = cmdResult.userMessage,
                        executionTimeMs = System.currentTimeMillis() - start
                    )
                }
                "SEND_MESSAGE", "SEND_SMS" -> {
                    val targetRecipient = step.target ?: step.parameters["contactName"] as? String ?: step.parameters["phoneNumber"] as? String ?: ""
                    val message = step.parameters["message"] as? String ?: step.parameters["messageBody"] as? String ?: ""
                    val userConfirmed = step.parameters["userExplicitlyConfirmed"] == "true" || step.parameters["user_confirmed"] == "true"
                    val cmdResult = actionExecutor.sendSms(targetRecipient, message, bypassConfirmation = userConfirmed)
                    UnifiedActionResult(
                        success = cmdResult.isSuccess,
                        verified = cmdResult.isSuccess && !cmdResult.parameters.containsKey("requiresConfirmation"),
                        actionType = step.actionType,
                        target = step.target,
                        parameters = cmdResult.parameters,
                        reason = cmdResult.userMessage,
                        executionTimeMs = System.currentTimeMillis() - start
                    )
                }
                else -> {
                    UnifiedActionResult(
                        success = true,
                        verified = true,
                        actionType = step.actionType,
                        target = step.target,
                        parameters = step.parameters,
                        reason = "Generic action completed",
                        executionTimeMs = System.currentTimeMillis() - start
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Dispatch exception for ${step.actionType}", e)
            UnifiedActionResult(
                success = false,
                verified = false,
                actionType = step.actionType,
                target = step.target,
                parameters = step.parameters,
                reason = e.localizedMessage ?: "Execution error",
                errorCode = "EXECUTION_EXCEPTION",
                executionTimeMs = System.currentTimeMillis() - start
            )
        }
    }
}
