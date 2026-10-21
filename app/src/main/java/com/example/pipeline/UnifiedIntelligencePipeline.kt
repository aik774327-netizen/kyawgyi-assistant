package com.example.pipeline

import android.content.Context
import android.util.Log
import com.example.actions.ActionRegistry
import com.example.actions.KyawGyiActionExecutor
import com.example.audit.AuditLogger
import com.example.audit.ResolutionPath
import com.example.data.remote.GeminiRepository
import com.example.pipeline.contract.ActionStep
import com.example.pipeline.contract.RiskLevel
import com.example.pipeline.contract.UnifiedActionPlan
import com.example.pipeline.contract.UnifiedActionResult
import com.example.pipeline.contract.UnifiedCommandRequest
import com.example.pipeline.contract.UnifiedCommandResponse
import com.example.pipeline.memory.StructuredMemoryStore
import com.example.pipeline.response.ResponseGenerator
import com.example.pipeline.verifier.PostExecutionVerifier
import com.example.pipeline.verifier.ResultVerifier
import com.example.security.SecurityPromptInjectionSanitizer
import com.example.security.UnifiedSafetyValidator
import com.example.semantic.ActionSafetyValidator
import com.example.semantic.MyanmarSemanticEngine
import com.example.semantic.model.ConfidenceLevel
import com.example.semantic.model.ConversationContext
import com.example.semantic.model.SemanticIntent
import com.example.service.KyawGyiAccessibilityService
import com.example.utils.MyanmarTTSEngine
import com.example.utils.NetworkMonitor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Unified Intelligence Pipeline for Myanmar Intelligent Voice Assistant (Stage 10)
 * 
 * Pipeline Flow:
 * VoiceInput / Transcript
 *   ↓
 * TranscriptNormalizer
 *   ↓
 * MyanmarSemanticAnalyzer (with Phonetic Normalization & Slang Handling)
 *   ↓
 * ContextResolver & ReferenceResolver (with Structured Memory Architecture)
 *   ↓
 * IntentClassifier
 *   ↓
 * ActionPlanner (Explicit ActionPlan Generation)
 *   ↓
 * SafetyValidator & ActionRegistry Guardrails
 *   ↓
 * ActionExecutor (Android System & Accessibility Capabilities)
 *   ↓
 * ResultVerifier (Post-Execution State Verification)
 *   ↓
 * ResponseGenerator (Natural Myanmar-First Voice Feedback)
 *   ↓
 * MyanmarTTS Engine
 */
class UnifiedIntelligencePipeline(
    private val context: Context,
    val memoryStore: StructuredMemoryStore = StructuredMemoryStore(),
    private val geminiApiKey: String = ""
) {

    companion object {
        private const val TAG = "UnifiedPipeline"

        @Volatile
        private var instance: UnifiedIntelligencePipeline? = null

        fun getInstance(context: Context, geminiApiKey: String = ""): UnifiedIntelligencePipeline {
            return instance ?: synchronized(this) {
                instance ?: UnifiedIntelligencePipeline(context.applicationContext, StructuredMemoryStore(), geminiApiKey).also {
                    instance = it
                }
            }
        }
    }

    private val semanticEngine = MyanmarSemanticEngine()
    private val actionExecutor = KyawGyiActionExecutor(context)
    private val resultVerifier = ResultVerifier(context)
    private val responseGenerator = ResponseGenerator()
    private val ttsEngine = MyanmarTTSEngine(context)
    private val safetyValidator = ActionSafetyValidator()
    private val geminiRepository = if (geminiApiKey.isNotBlank()) GeminiRepository(geminiApiKey) else null

    /**
     * Process an inbound natural voice or text command end-to-end
     */
    suspend fun processCommand(
        request: UnifiedCommandRequest,
        speakResponse: Boolean = true
    ): UnifiedCommandResponse = withContext(Dispatchers.Default) {
        val startTime = System.currentTimeMillis()
        val rawInput = request.transcript.trim()

        if (rawInput.isBlank()) {
            return@withContext createFallbackResponse(
                request = request,
                reason = "Input transcript is empty",
                responseMM = "ခိုင်းစေလိုသည်များကို ပြောပြနိုင်ပါသည်ခင်ဗျာ။",
                startTime = startTime
            )
        }

        // Step 0: Security & Prompt Injection Sanitization
        val sanitization = SecurityPromptInjectionSanitizer.sanitizeInput(rawInput)
        if (sanitization.shouldBlock) {
            val blockedResponse = "လုံခြုံရေးအရ ဤတောင်းဆိုချက်ကို ဆောင်ရွက်ပေးနိုင်ခြင်း မရှိပါခင်ဗျာ။"
            if (speakResponse) ttsEngine.speak(blockedResponse)
            com.example.telemetry.TelemetryManager.recordEvent(
                eventType = com.example.telemetry.TelemetryEventType.PIPELINE_ERROR,
                sessionId = request.sessionId,
                details = "Blocked security violation: ${sanitization.reason}"
            )
            return@withContext createFallbackResponse(
                request = request,
                reason = "Security threat blocked: ${sanitization.reason}",
                responseMM = blockedResponse,
                startTime = startTime
            )
        }

        // Step 0.5: Record Command Received Telemetry (Zero-PII)
        com.example.telemetry.TelemetryManager.recordEvent(
            eventType = com.example.telemetry.TelemetryEventType.COMMAND_RECEIVED,
            sessionId = request.sessionId,
            details = sanitization.sanitizedText,
            metadata = mapOf("source" to request.source.name, "requestId" to request.requestId)
        )

        // -------------------------------------------------------------
        // Step 1: Text Normalization & Slang Handling
        // -------------------------------------------------------------
        val normStart = System.currentTimeMillis()
        val normalized = semanticEngine.textNormalizer.normalize(sanitization.sanitizedText)
        val cleanedText = normalized.cleanedText
        com.example.telemetry.TelemetryManager.recordEvent(
            eventType = com.example.telemetry.TelemetryEventType.NORMALIZATION_COMPLETED,
            sessionId = request.sessionId,
            details = cleanedText,
            latencyMs = System.currentTimeMillis() - normStart
        )

        // Check for Previous Failure History (အမှားမှသင်ယူနိုင်သော AI စနစ် - အချက် ၂)
        val previousFailureWarning = com.example.audit.CommandFailureAnalyzer.checkPreviousFailure(context, cleanedText)

        // -------------------------------------------------------------
        // Step 2: Retrieve Context from Structured Memory
        // -------------------------------------------------------------
        val refStart = System.currentTimeMillis()
        val activeContextMemory = memoryStore.getActiveContext()
        val semanticContext = ConversationContext(
            activeAppNameMM = activeContextMemory.getValidLastApp(),
            activeAppPackage = activeContextMemory.getValidLastPackage(),
            activeArtist = activeContextMemory.getValidLastArtist(),
            activeSongTitle = activeContextMemory.getValidLastMedia(),
            activeContact = activeContextMemory.getValidLastContact(),
            lastIntent = activeContextMemory.getValidLastIntent()?.let { runCatching { SemanticIntent.valueOf(it) }.getOrNull() } ?: SemanticIntent.UNKNOWN
        )
        com.example.telemetry.TelemetryManager.recordEvent(
            eventType = com.example.telemetry.TelemetryEventType.REFERENCE_RESOLVED,
            sessionId = request.sessionId,
            details = "resolvedApp=${semanticContext.activeAppNameMM}, resolvedContact=${semanticContext.activeContact}",
            latencyMs = System.currentTimeMillis() - refStart
        )

        // -------------------------------------------------------------
        // Step 3: Semantic Analysis, Intent & Context Resolution
        // -------------------------------------------------------------
        val classStart = System.currentTimeMillis()
        val semanticResult = semanticEngine.process(cleanedText, overrideContext = semanticContext)
        val initialConfidence = semanticResult.confidence
        var finalIntent = semanticResult.intent.name
        var finalTarget = semanticResult.actionPlan.target
        var parameters: MutableMap<String, Any?> = semanticResult.actionPlan.parameters.toMutableMap()
        var isOfflineProcessed = true

        com.example.telemetry.TelemetryManager.recordEvent(
            eventType = com.example.telemetry.TelemetryEventType.INTENT_CLASSIFIED,
            sessionId = request.sessionId,
            details = "intent=${semanticResult.intent.name}, target=$finalTarget, conf=${semanticResult.confidence}",
            latencyMs = System.currentTimeMillis() - classStart
        )

        // -------------------------------------------------------------
        // Step 4: Confidence & Cloud Fallback Strategy (Items 05 & 12 Hardened)
        // Enforces CloudFallbackPolicy (SENSITIVE_DATA_BLOCKED / OFFLINE_ONLY)
        // and GeminiTrustBoundaryValidator (JSON Schema + Intent Allowlist + Sanitization)
        // -------------------------------------------------------------
        val cloudPolicy = com.kyawgyi.pipeline.GeminiTrustBoundaryValidator.evaluateCloudFallbackEligibility(
            rawTranscript = cleanedText,
            isOfflineModeRequested = request.isOfflineMode,
            userApprovedCloud = geminiRepository != null
        )
        if (initialConfidence < 0.75f &&
            cloudPolicy == com.kyawgyi.pipeline.CloudFallbackPolicy.CLOUD_ALLOWED &&
            NetworkMonitor.isInternetAvailable(context) &&
            geminiRepository != null
        ) {
            try {
                val safePromptText = com.kyawgyi.pipeline.SensitiveDataRedactor.redactForAuditLog(cleanedText)
                val cloudPrompt = "Return ONLY a valid JSON object {\"intent\":\"...\",\"target\":\"...\",\"confidence\":0.9} for Myanmar command: '$safePromptText'"
                val cloudResponse = geminiRepository.queryGeminiAI(cloudPrompt)
                val validatedProposal = com.kyawgyi.pipeline.GeminiTrustBoundaryValidator.validateGeminiJsonOutput(cloudResponse)
                if (validatedProposal.isValid && validatedProposal.approvedIntent != null) {
                    finalIntent = validatedProposal.approvedIntent
                    if (!validatedProposal.sanitizedTarget.isNullOrBlank()) {
                        finalTarget = validatedProposal.sanitizedTarget
                    }
                    parameters.putAll(validatedProposal.sanitizedParameters)
                    isOfflineProcessed = false
                    Log.d(TAG, "Validated Gemini proposal accepted by Trust Boundary: intent=$finalIntent, target=$finalTarget")
                } else {
                    Log.w(TAG, "Gemini output rejected by Trust Boundary: ${validatedProposal.rejectionReason}")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Cloud reasoning failed, continuing with offline best-effort", e)
            }
        }

        // Map SemanticIntent to ActionRegistry ActionType
        val directSettingsMatch = com.example.router.DirectSettingsRouter.getInstance(context).resolveSettingsDestination(cleanedText)
        val registryActionType = if (request.attachedUri != null) {
            "INGEST_FILE"
        } else if (cleanedText.contains("ဖိုင်ရှာ") || cleanedText.contains("ရှာဖွေ") && (cleanedText.contains("ဖိုင်") || cleanedText.contains("သီချင်း") || cleanedText.contains("စာရွက်"))) {
            "SEARCH_FILES"
        } else if (directSettingsMatch != null && (semanticResult.intent == SemanticIntent.OPEN_SETTINGS || cleanedText.contains("ဆက်တင်") || cleanedText.contains("setting") || cleanedText.contains("ခွင့်ပြုချက်") || cleanedText.contains("permission"))) {
            parameters["settingsDestination"] = directSettingsMatch.name
            if (finalTarget.isNullOrBlank()) {
                finalTarget = directSettingsMatch.titleMM
            }
            "OPEN_SETTINGS"
        } else {
            mapIntentToActionType(semanticResult.intent, finalTarget)
        }
        if (request.attachedUri != null) {
            parameters["fileUri"] = request.attachedUri
            parameters["mimeType"] = request.metadata["mimeType"]
            parameters["category"] = request.attachedAssetCategory
            if (finalTarget.isNullOrBlank()) {
                finalTarget = request.metadata["fileName"] ?: "Attached File"
            }
        }
        val actionDef = ActionRegistry.getAction(registryActionType) ?: ActionRegistry.getAction("CUSTOM")!!

        // -------------------------------------------------------------
        // Step 5: Action Planning (Explicit Structured Plan)
        // -------------------------------------------------------------
        val step = ActionStep(
            stepId = "step_1",
            actionType = registryActionType,
            target = finalTarget,
            parameters = parameters,
            riskLevel = actionDef.riskLevel,
            requiresConfirmation = actionDef.defaultRequiresConfirmation || (actionDef.riskLevel == RiskLevel.CRITICAL),
            confirmationPromptMM = actionDef.confirmationPromptTemplateMM?.replace("{target}", finalTarget ?: "")
        )

        val planStart = System.currentTimeMillis()
        val actionPlan = UnifiedActionPlan(
            planId = "plan_${UUID.randomUUID()}",
            primaryIntent = registryActionType,
            target = finalTarget,
            steps = listOf(step),
            overallConfidence = semanticResult.confidence,
            requiresConfirmation = step.requiresConfirmation,
            confirmationPromptMM = step.confirmationPromptMM,
            clarificationPromptMM = semanticResult.actionPlan.clarificationPrompt
        )
        com.example.telemetry.TelemetryManager.recordEvent(
            eventType = com.example.telemetry.TelemetryEventType.PLAN_CREATED,
            sessionId = request.sessionId,
            details = "planId=${actionPlan.planId}, intent=$registryActionType, steps=${actionPlan.steps.size}",
            latencyMs = System.currentTimeMillis() - planStart
        )

        // -------------------------------------------------------------
        // Step 6: Safety Validation & Hardened Guardrails
        // -------------------------------------------------------------
        val safetyStart = System.currentTimeMillis()
        val unifiedSafety = UnifiedSafetyValidator.evaluate(
            actionType = registryActionType,
            target = finalTarget,
            confidence = semanticResult.confidence,
            parameters = parameters
        )
        val safetyResult = safetyValidator.validate(
            intent = semanticResult.intent,
            target = finalTarget,
            confidenceLevel = semanticResult.confidenceLevel,
            parameters = parameters
        )
        val requiresExplicitConfirmation = unifiedSafety.requiresUserConfirmation || safetyResult.requiresConfirmation || actionPlan.requiresConfirmation
        val confirmationPrompt = unifiedSafety.confirmationPromptMM
            ?: safetyResult.confirmationPromptMM
            ?: actionPlan.confirmationPromptMM
            ?: responseGenerator.generateConfirmationPrompt(registryActionType, finalTarget, parameters)

        com.example.telemetry.TelemetryManager.recordEvent(
            eventType = com.example.telemetry.TelemetryEventType.SAFETY_CHECKED,
            sessionId = request.sessionId,
            details = "requiresConfirmation=$requiresExplicitConfirmation, risk=${unifiedSafety.riskLevel}",
            latencyMs = System.currentTimeMillis() - safetyStart
        )

        // If confidence is too low (< 0.50), or rejected by safety gate
        if (!unifiedSafety.isPermitted || semanticResult.confidence < 0.50f) {
            val repeatResponse = unifiedSafety.rejectionReasonMM
                ?: responseGenerator.generateUnknownOrRepeatResponse(ConfidenceLevel.ASK_USER_TO_REPEAT)
            if (speakResponse) ttsEngine.speak(repeatResponse)
            return@withContext UnifiedCommandResponse(
                requestId = request.requestId,
                sessionId = request.sessionId,
                originalTranscript = rawInput,
                normalizedText = cleanedText,
                intent = registryActionType,
                target = finalTarget,
                parameters = parameters,
                confidence = semanticResult.confidence,
                confidenceLevel = ConfidenceLevel.ASK_USER_TO_REPEAT,
                requiresConfirmation = false,
                actionPlan = actionPlan,
                executionResult = null,
                verificationResult = false,
                responseMyanmar = repeatResponse,
                isOfflineProcessed = isOfflineProcessed,
                latencyMs = System.currentTimeMillis() - startTime
            )
        }

        // If action requires confirmation
        if (requiresExplicitConfirmation) {
            val confirmPrompt = confirmationPrompt

            if (speakResponse) ttsEngine.speak(confirmPrompt)

            com.example.telemetry.TelemetryManager.recordEvent(
                eventType = com.example.telemetry.TelemetryEventType.CONFIRMATION_REQUESTED,
                sessionId = request.sessionId,
                details = "target=$finalTarget, prompt=$confirmPrompt"
            )

            // Update session state for pending confirmation
            val session = memoryStore.getOrCreateSession(request.sessionId)
            session.pendingIntent = registryActionType
            session.pendingTarget = finalTarget
            session.awaitingConfirmation = true
            session.confirmationPayload = parameters

            return@withContext UnifiedCommandResponse(
                requestId = request.requestId,
                sessionId = request.sessionId,
                originalTranscript = rawInput,
                normalizedText = cleanedText,
                intent = registryActionType,
                target = finalTarget,
                parameters = parameters,
                confidence = semanticResult.confidence,
                confidenceLevel = semanticResult.confidenceLevel,
                requiresConfirmation = true,
                actionPlan = actionPlan,
                executionResult = null,
                verificationResult = false,
                responseMyanmar = confirmPrompt,
                isOfflineProcessed = isOfflineProcessed,
                latencyMs = System.currentTimeMillis() - startTime
            )
        }

        // -------------------------------------------------------------
        // Step 6.5: Error-Learning Failed-Method Memory & Live Condition Verifier Guard
        // -------------------------------------------------------------
        val errorLearningWarning = com.example.audit.ErrorLearningWorkflowEngine.evaluateRequestedAction(
            commandKey = cleanedText,
            context = context
        )
        if (errorLearningWarning != null) {
            val blockedMsg = "${errorLearningWarning.warningBanner} ${errorLearningWarning.simpleExplanationMm}"
            if (speakResponse) {
                ttsEngine.speak(blockedMsg)
            }
            return@withContext UnifiedCommandResponse(
                requestId = request.requestId,
                sessionId = request.sessionId,
                originalTranscript = rawInput,
                normalizedText = cleanedText,
                intent = registryActionType,
                target = finalTarget,
                parameters = parameters,
                confidence = semanticResult.confidence,
                confidenceLevel = semanticResult.confidenceLevel,
                requiresConfirmation = false,
                actionPlan = actionPlan,
                executionResult = UnifiedActionResult(
                    success = false,
                    verified = false,
                    actionType = registryActionType,
                    target = finalTarget,
                    parameters = parameters,
                    reason = blockedMsg,
                    errorCode = errorLearningWarning.previousErrorLog.errorId
                ),
                verificationResult = false,
                responseMyanmar = blockedMsg,
                isOfflineProcessed = isOfflineProcessed,
                latencyMs = System.currentTimeMillis() - startTime
            )
        }

        // -------------------------------------------------------------
        // Step 6.8: Idempotency Guard (Item 18 — Prevent duplicate SMS/Call/Action)
        // -------------------------------------------------------------
        val idempotencyKey = com.kyawgyi.pipeline.CommandIdempotencyManager.computeIdempotencyKey(
            intent = registryActionType,
            target = finalTarget,
            normalizedTranscript = cleanedText
        )
        val isHighRiskDuplicateCheck = registryActionType in setOf("CALL", "SEND_MESSAGE", "SEND_SMS", "DELETE")
        if (isHighRiskDuplicateCheck && !com.kyawgyi.pipeline.CommandIdempotencyManager.checkAndRegisterExecution(idempotencyKey)) {
            val dedupMsg = "တူညီသော လုပ်ဆောင်ချက် ($registryActionType) ကို ယခုလေးတင် ဆောင်ရွက်ထားပြီးဖြစ်သဖြင့် ထပ်မံမပေးပို့စေရန် ကာကွယ်ထားပါသည် (SAFE_FAIL_DUPLICATE)။"
            return@withContext UnifiedCommandResponse(
                requestId = request.requestId,
                sessionId = request.sessionId,
                originalTranscript = rawInput,
                normalizedText = cleanedText,
                intent = registryActionType,
                target = finalTarget,
                parameters = parameters,
                confidence = semanticResult.confidence,
                confidenceLevel = semanticResult.confidenceLevel,
                requiresConfirmation = false,
                actionPlan = actionPlan,
                executionResult = UnifiedActionResult(
                    success = false,
                    verified = false,
                    actionType = registryActionType,
                    target = finalTarget,
                    parameters = parameters,
                    reason = dedupMsg,
                    errorCode = "IDEMPOTENCY_DUPLICATE_BLOCKED",
                    outputData = mapOf("verificationOutcome" to "SAFE_FAIL")
                ),
                verificationResult = false,
                responseMyanmar = dedupMsg,
                isOfflineProcessed = isOfflineProcessed,
                latencyMs = System.currentTimeMillis() - startTime
            )
        }

        // -------------------------------------------------------------
        // Step 7: Action Execution via Android Capabilities
        // -------------------------------------------------------------
        com.example.telemetry.TelemetryManager.recordEvent(
            eventType = com.example.telemetry.TelemetryEventType.ACTION_STARTED,
            sessionId = request.sessionId,
            details = "action=$registryActionType, target=$finalTarget"
        )
        val executionResult = executeAction(registryActionType, finalTarget, parameters, cleanedText)

        // -------------------------------------------------------------
        // Step 8: Result Verification
        // -------------------------------------------------------------
        val verifiedResult = resultVerifier.verify(
            actionType = registryActionType,
            target = finalTarget,
            parameters = parameters,
            initialSuccess = executionResult.success
        )

        com.example.telemetry.TelemetryManager.recordEvent(
            eventType = if (verifiedResult.verified) com.example.telemetry.TelemetryEventType.ACTION_COMPLETED else com.example.telemetry.TelemetryEventType.ACTION_FAILED,
            sessionId = request.sessionId,
            details = "action=$registryActionType, verified=${verifiedResult.verified}, reason=${verifiedResult.reason}"
        )

        // -------------------------------------------------------------
        // Step 9: Response Generation & TTS
        // -------------------------------------------------------------
        val baseResponseMM = if (verifiedResult.verified) {
            responseGenerator.generateSuccessResponse(registryActionType, finalTarget, parameters)
        } else {
            responseGenerator.generateFailureResponse(
                actionType = registryActionType,
                target = finalTarget,
                reason = verifiedResult.reason,
                errorCode = verifiedResult.errorCode
            )
        }

        // အမှားအလိုက် အစားထိုးလမ်းကြောင်း တွက်ချက်ခြင်း (အချက် ၃)
        val alternativeFlow = if (!verifiedResult.verified) {
            val failureCat = com.example.audit.CommandFailureAnalyzer.classifyFailure(
                commandText = rawInput,
                commandType = registryActionType,
                rawDetail = (verifiedResult.reason ?: "") + " " + (verifiedResult.errorCode ?: "")
            ).category
            com.example.fallback.AlternativeFlowManager.resolveFlow(
                context = context,
                commandText = rawInput,
                commandType = registryActionType,
                target = finalTarget,
                failureCategory = failureCat,
                failureDetail = (verifiedResult.reason ?: "") + " " + (verifiedResult.errorCode ?: "")
            )
        } else null

        // အမှားတစ်ခုတူ ထပ်မံတောင်းဆိုလာပါက သတိပေးချက်နှင့် ရှင်းပြချက် ပေးခြင်း (အချက် ၂ နှင့် အချက် ၃)
        val responseMM = if (!verifiedResult.verified && previousFailureWarning != null) {
            previousFailureWarning.warningSpeechMm
        } else if (!verifiedResult.verified && alternativeFlow != null) {
            "$baseResponseMM အစားထိုးအကြံပြုချက်: ${alternativeFlow.actionGuidanceMm}"
        } else {
            baseResponseMM
        }

        // Web Fallback အလိုအလျောက် ဆောင်ရွက်ပေးခြင်း (အက်ပ်မရှိပါက Web မှတစ်ဆင့် အစားထိုးဖွင့်ပေးခြင်း)
        if (!verifiedResult.verified && alternativeFlow != null && alternativeFlow.remediationType == com.example.fallback.RemediationType.WEB_FALLBACK) {
            com.example.fallback.AlternativeFlowManager.executeRemediation(context, alternativeFlow)
        }

        if (speakResponse) {
            ttsEngine.speak(responseMM)
        }

        // -------------------------------------------------------------
        // Step 10: Update Multi-Tier Memory Architecture
        // -------------------------------------------------------------
        memoryStore.recordTurn(
            StructuredMemoryStore.ConversationTurn(
                turnId = "turn_${System.currentTimeMillis()}",
                userQuery = rawInput,
                normalizedQuery = cleanedText,
                assistantResponse = responseMM,
                intent = registryActionType,
                target = finalTarget
            )
        )

        memoryStore.updateContext(
            app = if (registryActionType == "OPEN_APP") finalTarget else null,
            artist = if (registryActionType == "PLAY_MUSIC") parameters["artist"] as? String else null,
            media = if (registryActionType == "PLAY_MUSIC") finalTarget else null,
            contact = if (registryActionType == "CALL" || registryActionType == "SEND_MESSAGE") finalTarget else null,
            command = rawInput,
            intent = registryActionType,
            action = registryActionType
        )

        memoryStore.recordAction(verifiedResult)

        if (!verifiedResult.verified) {
            memoryStore.recordError(registryActionType, finalTarget, verifiedResult.reason ?: "Unknown error")
            // Error Log မှတ်သားခြင်းနှင့် နည်းပညာ/အရင်းအမြစ်/လက်တွေ့မကိုက်ညီမှု ခွဲခြမ်းစိတ်ဖြာခြင်း
            com.example.audit.CommandFailureAnalyzer.recordFailure(
                context = context,
                commandText = rawInput,
                commandType = registryActionType,
                rawDetail = verifiedResult.reason ?: verifiedResult.errorCode,
                isRepeated = previousFailureWarning != null
            )
            com.example.audit.ErrorLearningWorkflowEngine.recordErrorLog(
                task = "$registryActionType (${finalTarget ?: cleanedText})",
                commandKey = cleanedText,
                expectedResult = "$registryActionType အောင်မြင်စွာ ဆောင်ရွက်ရန်",
                actualResult = verifiedResult.reason ?: "Action verification failed",
                attemptedMethod = "UnifiedIntelligencePipeline.executeAction($registryActionType, target=$finalTarget)",
                evidence = (verifiedResult.reason ?: "") + " " + (verifiedResult.errorCode ?: ""),
                recoveryAttempt = alternativeFlow?.actionGuidanceMm ?: "Provided fallback guidance",
                recoveryResult = if (alternativeFlow != null) "FALLBACK_GUIDANCE_PROVIDED" else "NOT_EXECUTED"
            )
        } else {
            com.example.audit.ErrorLearningWorkflowEngine.clearFailedMethodWithVerifiedEvidence(
                commandKey = cleanedText,
                verificationEvidence = "Verified execution succeeded for $registryActionType on ${finalTarget ?: "system"}"
            )
            try {
                val db = com.example.data.AppDatabase.getDatabase(context)
                db.commandLogDao().insertCommandLog(
                    com.example.data.CommandLogEntity(
                        commandText = rawInput,
                        commandType = registryActionType,
                        status = "SUCCESS",
                        actionDetail = "Executed successfully on ${finalTarget ?: "system"}",
                        timestamp = System.currentTimeMillis()
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to record success command log", e)
            }
        }

        // Observability Audit Log
        val totalLatency = System.currentTimeMillis() - startTime
        AuditLogger.log(
            rawQuery = rawInput,
            normalizedQuery = cleanedText,
            resolutionPath = if (isOfflineProcessed) ResolutionPath.EXACT_FASTPATH else ResolutionPath.ONLINE_GEMINI_FALLBACK,
            targetAction = registryActionType,
            confidence = semanticResult.confidence,
            isSuccess = verifiedResult.verified,
            details = "verified=${verifiedResult.verified}, reason=${verifiedResult.reason}",
            executionTimeMs = totalLatency
        )

        return@withContext UnifiedCommandResponse(
            requestId = request.requestId,
            sessionId = request.sessionId,
            originalTranscript = rawInput,
            normalizedText = cleanedText,
            intent = registryActionType,
            target = finalTarget,
            parameters = parameters,
            confidence = semanticResult.confidence,
            confidenceLevel = semanticResult.confidenceLevel,
            requiresConfirmation = false,
            actionPlan = actionPlan,
            executionResult = verifiedResult,
            verificationResult = verifiedResult.verified,
            responseMyanmar = responseMM,
            isOfflineProcessed = isOfflineProcessed,
            latencyMs = totalLatency
        )
    }

    /**
     * Map SemanticIntent to Centralized ActionRegistry type
     */
    private fun mapIntentToActionType(intent: SemanticIntent, target: String?): String {
        return when (intent) {
            SemanticIntent.OPEN_APP, SemanticIntent.REOPEN_APP, SemanticIntent.OPEN_CAMERA, SemanticIntent.OPEN_PHOTOS, SemanticIntent.OPEN_MAPS -> "OPEN_APP"
            SemanticIntent.CALL_CONTACT -> "CALL"
            SemanticIntent.SEND_SMS -> "SEND_MESSAGE"
            SemanticIntent.VOLUME_UP -> "VOLUME_UP"
            SemanticIntent.VOLUME_DOWN -> "VOLUME_DOWN"
            SemanticIntent.TOGGLE_FLASHLIGHT -> "FLASHLIGHT"
            SemanticIntent.TOGGLE_WIFI -> "WIFI_TOGGLE"
            SemanticIntent.TOGGLE_BLUETOOTH -> "BLUETOOTH_TOGGLE"
            SemanticIntent.SET_TIMER -> "SET_TIMER"
            SemanticIntent.SET_ALARM -> "SET_ALARM"
            SemanticIntent.PLAY_MUSIC -> "PLAY_MUSIC"
            SemanticIntent.NEXT_TRACK -> "NEXT_TRACK"
            SemanticIntent.PREVIOUS_TRACK -> "PREVIOUS_TRACK"
            SemanticIntent.PAUSE_MEDIA -> "PAUSE_MEDIA"
            SemanticIntent.RESUME_MEDIA -> "RESUME_MEDIA"
            SemanticIntent.NAVIGATE_BACK -> "BACK"
            SemanticIntent.NAVIGATE_HOME -> "HOME"
            SemanticIntent.SHOW_RECENTS -> "RECENTS"
            SemanticIntent.READ_SCREEN -> "READ_SCREEN"
            SemanticIntent.OPEN_SETTINGS -> "OPEN_SETTINGS"
            SemanticIntent.DELETE_ITEM, SemanticIntent.UNINSTALL_APP -> "DELETE"
            else -> "CUSTOM"
        }
    }

    /**
     * Internal Action Dispatcher
     */
    private fun executeAction(
        actionType: String,
        target: String?,
        parameters: Map<String, Any?>,
        rawText: String
    ): UnifiedActionResult {
        val startTime = System.currentTimeMillis()
        return try {
            when (actionType.uppercase()) {
                "OPEN_APP" -> {
                    val cmdResult = actionExecutor.openApplication(target ?: "", target ?: "")
                    UnifiedActionResult(
                        success = cmdResult.isSuccess,
                        verified = cmdResult.isSuccess,
                        actionType = actionType,
                        target = target,
                        parameters = parameters,
                        reason = cmdResult.userMessage,
                        executionTimeMs = System.currentTimeMillis() - startTime
                    )
                }
                "VOLUME_UP", "VOLUME_DOWN", "SET_VOLUME", "MUTE", "BRIGHTNESS_UP", "BRIGHTNESS_DOWN" -> {
                    val cmdResult = actionExecutor.executeSystemAction(rawText)
                    UnifiedActionResult(
                        success = cmdResult?.isSuccess ?: true,
                        verified = cmdResult?.isSuccess ?: true,
                        actionType = actionType,
                        target = target,
                        parameters = parameters,
                        reason = cmdResult?.userMessage ?: "Volume changed",
                        executionTimeMs = System.currentTimeMillis() - startTime
                    )
                }
                "FLASHLIGHT" -> {
                    val enable = parameters["enable"] as? Boolean ?: true
                    val cmdResult = actionExecutor.setFlashlight(enable)
                    UnifiedActionResult(
                        success = cmdResult.isSuccess,
                        verified = cmdResult.isSuccess,
                        actionType = actionType,
                        target = target,
                        parameters = parameters,
                        reason = cmdResult.userMessage,
                        executionTimeMs = System.currentTimeMillis() - startTime
                    )
                }
                "WIFI_TOGGLE" -> {
                    val enable = parameters["enable"] as? Boolean ?: true
                    val cmdResult = actionExecutor.setWifi(enable)
                    UnifiedActionResult(
                        success = cmdResult.isSuccess,
                        verified = cmdResult.isSuccess,
                        actionType = actionType,
                        target = target,
                        parameters = parameters,
                        reason = cmdResult.userMessage,
                        executionTimeMs = System.currentTimeMillis() - startTime
                    )
                }
                "BLUETOOTH_TOGGLE" -> {
                    val enable = parameters["enable"] as? Boolean ?: true
                    val cmdResult = actionExecutor.setBluetooth(enable)
                    UnifiedActionResult(
                        success = cmdResult.isSuccess,
                        verified = cmdResult.isSuccess,
                        actionType = actionType,
                        target = target,
                        parameters = parameters,
                        reason = cmdResult.userMessage,
                        executionTimeMs = System.currentTimeMillis() - startTime
                    )
                }
                "BACK" -> {
                    KyawGyiAccessibilityService.instance?.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
                    UnifiedActionResult(success = true, verified = true, actionType = actionType, target = target)
                }
                "HOME" -> {
                    KyawGyiAccessibilityService.instance?.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME)
                    UnifiedActionResult(success = true, verified = true, actionType = actionType, target = target)
                }
                "RECENTS" -> {
                    KyawGyiAccessibilityService.instance?.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_RECENTS)
                    UnifiedActionResult(success = true, verified = true, actionType = actionType, target = target)
                }
                "NOTIFICATIONS" -> {
                    KyawGyiAccessibilityService.instance?.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
                    UnifiedActionResult(success = true, verified = true, actionType = actionType, target = target)
                }
                "QUICK_SETTINGS" -> {
                    KyawGyiAccessibilityService.instance?.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
                    UnifiedActionResult(success = true, verified = true, actionType = actionType, target = target)
                }
                "OPEN_SETTINGS" -> {
                    val router = com.example.router.DirectSettingsRouter.getInstance(context)
                    val destName = parameters["settingsDestination"] as? String
                    val destination = destName?.let { name ->
                        com.example.router.SettingsDestination.entries.firstOrNull { it.name == name }
                    } ?: router.resolveSettingsDestination(target ?: rawText)
                        ?: com.example.router.SettingsDestination.APPLICATION_DETAILS
                    val report = router.openSettingsWithReport(destination)
                    UnifiedActionResult(
                        success = report.dispatchSucceeded,
                        verified = report.dispatchSucceeded,
                        actionType = actionType,
                        target = destination.titleMM,
                        parameters = parameters + mapOf(
                            "settingsDestination" to destination.name,
                            "resolvedIntentAction" to report.resolvedIntentAction,
                            "usedPackageUriDeepLink" to report.usedPackageUriDeepLink
                        ),
                        reason = report.messageMM,
                        executionTimeMs = System.currentTimeMillis() - startTime
                    )
                }
                "TAKE_SCREENSHOT" -> {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                        KyawGyiAccessibilityService.instance?.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT)
                    }
                    UnifiedActionResult(success = true, verified = true, actionType = actionType, target = target)
                }
                "READ_SCREEN" -> {
                    val screenText = com.example.service.KyawGyiVoiceSession.getLatestScreenText().ifEmpty {
                        KyawGyiAccessibilityService.instance?.extractCurrentScreenText() ?: ""
                    }
                    val msg = if (screenText.isBlank()) {
                        "မျက်နှာပြင်ပေါ်တွင် ဖတ်ပြစရာ စာသား မတွေ့ရှိပါခင်ဗျာ"
                    } else {
                        "မျက်နှာပြင်ပေါ်ရှိ စာသားများမှာ အောက်ပါအတိုင်း ဖြစ်ပါသည်ခင်ဗျာ။ $screenText"
                    }
                    UnifiedActionResult(
                        success = true,
                        verified = true,
                        actionType = actionType,
                        target = target,
                        parameters = mapOf("screenText" to screenText),
                        reason = msg,
                        executionTimeMs = System.currentTimeMillis() - startTime
                    )
                }
                "PLAY_MUSIC" -> {
                    val query = target ?: parameters["title"] as? String ?: rawText
                    val cmdResult = actionExecutor.playLocalAudioTrack(query)
                    UnifiedActionResult(
                        success = cmdResult.isSuccess,
                        verified = cmdResult.isSuccess,
                        actionType = actionType,
                        target = target,
                        parameters = cmdResult.parameters,
                        reason = cmdResult.userMessage,
                        executionTimeMs = System.currentTimeMillis() - startTime
                    )
                }
                "CALL", "CALL_CONTACT", "CALL_PHONE" -> {
                    val targetContact = target ?: parameters["contactName"] as? String ?: rawText
                    val cmdResult = actionExecutor.callContact(targetContact)
                    UnifiedActionResult(
                        success = cmdResult.isSuccess,
                        verified = cmdResult.isSuccess,
                        actionType = actionType,
                        target = target,
                        parameters = cmdResult.parameters,
                        reason = cmdResult.userMessage,
                        executionTimeMs = System.currentTimeMillis() - startTime
                    )
                }
                "SEND_MESSAGE", "SEND_SMS" -> {
                    val targetRecipient = target ?: parameters["contactName"] as? String ?: parameters["phoneNumber"] as? String ?: ""
                    val message = parameters["message"] as? String ?: parameters["messageBody"] as? String ?: ""
                    val cmdResult = actionExecutor.sendSms(targetRecipient, message)
                    UnifiedActionResult(
                        success = cmdResult.isSuccess,
                        verified = cmdResult.isSuccess,
                        actionType = actionType,
                        target = target,
                        parameters = cmdResult.parameters,
                        reason = cmdResult.userMessage,
                        executionTimeMs = System.currentTimeMillis() - startTime
                    )
                }
                "INGEST_FILE" -> {
                    val uriStr = parameters["fileUri"] as? String
                    if (uriStr != null) {
                        val ingestionMgr = com.example.ingestion.UniversalDataIngestionManager(context)
                        val ingResult = kotlinx.coroutines.runBlocking {
                            ingestionMgr.ingestUri(android.net.Uri.parse(uriStr))
                        }
                        UnifiedActionResult(
                            success = ingResult.isSuccess,
                            verified = ingResult.isSuccess,
                            actionType = actionType,
                            target = ingResult.asset?.fileName ?: target,
                            parameters = parameters,
                            reason = ingResult.messageMM,
                            executionTimeMs = System.currentTimeMillis() - startTime
                        )
                    } else {
                        UnifiedActionResult(
                            success = false,
                            verified = false,
                            actionType = actionType,
                            target = target,
                            parameters = parameters,
                            reason = "ဖိုင်လိပ်စာ (URI) မတွေ့ရှိပါခင်ဗျာ",
                            executionTimeMs = System.currentTimeMillis() - startTime
                        )
                    }
                }
                "SEARCH_FILES" -> {
                    val searchEngine = com.example.ingestion.LocalAssetSearchIndex(context)
                    val q = target ?: rawText.replace("ဖိုင်ရှာ", "").replace("ရှာဖွေ", "").trim()
                    val results = kotlinx.coroutines.runBlocking {
                        searchEngine.search(q)
                    }
                    val foundMsg = if (results.isNotEmpty()) {
                        "ဖိုင် ${results.size} ခု တွေ့ရှိပါသည်: " + results.take(3).joinToString(", ") { it.fileName }
                    } else {
                        "‘$q’ နှင့် ကိုက်ညီသော ဖိုင်/စာရွက်စာတမ်း မတွေ့ရှိပါခင်ဗျာ"
                    }
                    UnifiedActionResult(
                        success = true,
                        verified = true,
                        actionType = actionType,
                        target = q,
                        parameters = mapOf("resultCount" to results.size),
                        reason = foundMsg,
                        executionTimeMs = System.currentTimeMillis() - startTime
                    )
                }
                else -> {
                    UnifiedActionResult(
                        success = true,
                        verified = true,
                        actionType = actionType,
                        target = target,
                        parameters = parameters,
                        reason = "Generic action completed",
                        executionTimeMs = System.currentTimeMillis() - startTime
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Action execution error", e)
            UnifiedActionResult(
                success = false,
                verified = false,
                actionType = actionType,
                target = target,
                parameters = parameters,
                reason = e.localizedMessage ?: "Execution exception",
                executionTimeMs = System.currentTimeMillis() - startTime
            )
        }
    }

    private fun createFallbackResponse(
        request: UnifiedCommandRequest,
        reason: String,
        responseMM: String,
        startTime: Long
    ): UnifiedCommandResponse {
        return UnifiedCommandResponse(
            requestId = request.requestId,
            sessionId = request.sessionId,
            originalTranscript = request.transcript,
            normalizedText = request.normalizedText,
            intent = "UNKNOWN",
            target = null,
            parameters = emptyMap(),
            confidence = 0.0f,
            confidenceLevel = ConfidenceLevel.ASK_USER_TO_REPEAT,
            requiresConfirmation = false,
            actionPlan = UnifiedActionPlan(
                planId = "plan_fallback",
                primaryIntent = "UNKNOWN",
                target = null,
                steps = emptyList(),
                overallConfidence = 0.0f
            ),
            executionResult = null,
            verificationResult = false,
            responseMyanmar = responseMM,
            isOfflineProcessed = true,
            latencyMs = System.currentTimeMillis() - startTime
        )
    }
}
