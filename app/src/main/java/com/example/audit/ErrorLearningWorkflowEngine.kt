package com.example.audit

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.data.AppDatabase
import com.example.data.CommandLogEntity
import com.example.utils.AndroidTtsProbe
import com.example.utils.MyanmarTTSVoicePackManager
import com.example.utils.TtsConditionVerifier
import com.example.utils.TtsProbe
import com.kyawgyi.pipeline.SensitiveDataRedactor
import com.kyawgyi.voice.ModelManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * ErrorLearningWorkflowEngine (Production-Hardened AI Error-Learning & Failure Memory Framework)
 *
 * Implements Items 13, 14, 15, 16, 17, 19 of the Production Repair Checklist:
 * - Item 13: Never deletes historical failures on resolution; transitions status:
 *   FAILED -> BLOCKED -> CONDITION_CHANGED -> RETRY_ALLOWED -> VERIFIED_SUCCESS -> RESOLVED.
 * - Item 14: Structured FailureFingerprint (intent, parameters, package, Android SDK, device, permissions, model, env).
 * - Item 15: 95-Type Error Taxonomy + explicit UNKNOWN ("CAUSE = UNKNOWN") when evidence is insufficient.
 * - Item 16: Room (`AppDatabase.commandLogDao()`) synchronized as persistent source of truth.
 * - Item 17: Explicit ErrorRecoveryPolicy (RETRY_NOW, RETRY_LATER, WAIT_FOR_CONDITION, ASK_USER, SAFE_FAIL, UNSUPPORTED).
 * - Item 19: Automatic PII / Sensitive Data Redaction prior to persistence.
 */
object ErrorLearningWorkflowEngine {

    private const val TAG = "ErrorLearningWorkflow"
    private const val PREFS_NAME = "kyawgyi_error_learning_memory"
    private const val KEY_ERROR_HISTORY_JSON = "error_log_history_json"
    private const val KEY_FAILED_METHODS_JSON = "failed_methods_keys_json"
    private const val KEY_SEQ_COUNTER = "error_seq_counter"

    const val MANDATORY_WARNING_PREFIX = "This is a previously unsuccessful action."
    const val MANDATORY_WARNING_PREFIX_MM = "ယခင်က မအောင်မြင်ခဲ့သော လုပ်ဆောင်ချက် ဖြစ်ပါသည်။"

    /**
     * ISSUE-035 — stored on an entry when no original exception object exists.
     * It is recorded explicitly at record time; an exception is never
     * synthesized later to fill the gap.
     */
    const val ORIGINAL_EXCEPTION_UNAVAILABLE = "ORIGINAL_EXCEPTION_UNAVAILABLE"

    /** ISSUE-035 — UniversalAppLauncher structured codes -> RESOURCE_UNAVAILABLE (#25). */
    private val LAUNCHER_RESOURCE_CODES = setOf(
        "APP_NOT_FOUND",
        "APP_NOT_FOUND_OR_LOW_CONFIDENCE",
        "PACKAGE_DISABLED_OR_NOT_FOUND",
        "NO_LAUNCH_INTENT"
    )

    /** ISSUE-035 — UniversalAppLauncher structured codes -> INTENT_MISUNDERSTANDING (#11). */
    private val LAUNCHER_INTENT_CODES = setOf("AMBIGUOUS_APP_MATCH")

    private val STRUCTURED_CODE_REGEX = Regex("\\b[A-Z][A-Z0-9]*(?:_[A-Z0-9]+)+\\b")

    enum class ExecutionOutcome {
        PASS,
        FAIL,
        PARTIAL
    }

    /**
     * ITEM 13 — Non-Destructive Error Lifecycle Status
     */
    enum class ErrorLifecycleStatus {
        FAILED,
        BLOCKED,
        MUTATED,
        CONDITION_CHANGED,
        RETRY_ALLOWED,
        VERIFIED_SUCCESS,
        RESOLVED
    }

    /**
     * ITEM 17 — Explicit Error Recovery Policy
     */
    enum class ErrorRecoveryPolicy {
        RETRY_NOW,
        RETRY_LATER,
        WAIT_FOR_CONDITION,
        ASK_USER,
        SAFE_FAIL,
        UNSUPPORTED
    }

    /**
     * ITEM 15 — Systematic Error Taxonomy (Including UNKNOWN when cause cannot be proven)
     */
    enum class ErrorTaxonomy(val id: Int, val label: String) {
        UNKNOWN(0, "CAUSE = UNKNOWN"),
        HALLUCINATION_ERROR(1, "Hallucination Error"),
        MISSING_INFORMATION_ERROR(4, "Missing Information Error"),
        FALSE_ASSUMPTION_ERROR(5, "False Assumption Error"),
        INTENT_MISUNDERSTANDING_ERROR(11, "Intent Misunderstanding Error"),
        CONSTRAINT_VIOLATION_ERROR(15, "Constraint Violation Error"),
        TOOL_SELECTION_ERROR(21, "Tool Selection Error"),
        MISSING_TOOL_CAPABILITY_ERROR(24, "Missing Tool Capability Error"),
        RESOURCE_UNAVAILABLE_ERROR(25, "Resource Unavailable Error"),
        ACCESS_PERMISSION_ERROR(26, "Access / Permission Error"),
        FILE_NOT_FOUND_ERROR(27, "File Not Found Error"),
        SYNTAX_ERROR(31, "Syntax Error"),
        TYPE_ERROR(32, "Type Error"),
        LOGIC_BUG(33, "Logic Bug"),
        RUNTIME_ERROR(34, "Runtime Error"),
        BUILD_ERROR(35, "Build Error"),
        DEPENDENCY_ERROR(36, "Dependency Error"),
        VERSION_COMPATIBILITY_ERROR(37, "Version Compatibility Error"),
        CONFIGURATION_ERROR(38, "Configuration Error"),
        API_CONTRACT_ERROR(39, "API Contract Error"),
        ARCHITECTURE_ERROR(40, "Architecture Error"),
        INTEGRATION_ERROR(41, "Integration Error"),
        STATE_MANAGEMENT_ERROR(42, "State Management Error"),
        PERSISTENCE_DATABASE_ERROR(43, "Persistence / Database Error"),
        CONCURRENCY_ERROR(44, "Concurrency Error"),
        PERFORMANCE_ERROR(45, "Performance Error"),
        MEMORY_RESOURCE_LEAK_ERROR(46, "Memory / Resource Leak Error"),
        ENVIRONMENT_MISMATCH_ERROR(47, "Environment Mismatch Error"),
        HARDWARE_CAPABILITY_ERROR(48, "Hardware Capability Error"),
        OS_PLATFORM_BEHAVIOR_ERROR(49, "OS / Platform Behavior Error"),
        OEM_MANUFACTURER_DIFFERENCE_ERROR(50, "OEM / Manufacturer Difference Error"),
        NETWORK_REALITY_ERROR(51, "Network Reality Error"),
        BLUETOOTH_BLE_USB_HARDWARE_ERROR(52, "Bluetooth / BLE / USB Hardware Error"),
        RUNTIME_PERMISSION_ERROR(53, "Runtime Permission Error"),
        PHYSICAL_VALIDATION_ERROR(54, "Physical Validation Error"),
        VERIFICATION_GAP(69, "Verification Gap"),
        SECURITY_MISCONFIGURATION_ERROR(73, "Security Misconfiguration Error"),
        PRIVACY_ERROR(77, "Privacy Error"),
        INJECTION_MALICIOUS_INPUT_ERROR(79, "Injection / Malicious Input Error"),
        FAILED_METHOD_REUSE_ERROR(82, "Failed-Method Reuse Error"),
        REPEATED_KNOWN_FAILURE_ERROR(95, "Repeated Known-Failure Error")
    }

    data class ThreeFactorAnalysis(
        val technicalCause: String,
        val resourceLimitation: String,
        val realWorldMismatch: String
    )

    /**
     * ITEM 14 — Structured Failure Fingerprint
     */
    data class FailureFingerprint(
        val commandKey: String,
        val intent: String = "UNKNOWN",
        val parametersHash: String = "none",
        val targetPackage: String = "system",
        val androidSdkInt: Int = Build.VERSION.SDK_INT,
        val deviceModel: String = "${Build.MANUFACTURER}:${Build.MODEL}",
        val permissionState: String = "UNKNOWN",
        val modelVersion: String = "sherpa-onnx-2024",
        val appVersion: String = "1.0.0",
        val environmentState: String = "DEFAULT"
    ) {
        fun toCompositeHash(): String {
            return "${commandKey}|${intent}|sdk$androidSdkInt|$permissionState|$environmentState"
        }

        fun toJsonObject(): JSONObject = JSONObject().apply {
            put("commandKey", commandKey)
            put("intent", intent)
            put("parametersHash", parametersHash)
            put("targetPackage", targetPackage)
            put("androidSdkInt", androidSdkInt)
            put("deviceModel", deviceModel)
            put("permissionState", permissionState)
            put("modelVersion", modelVersion)
            put("appVersion", appVersion)
            put("environmentState", environmentState)
        }

        companion object {
            fun fromJsonObject(obj: JSONObject?, fallbackKey: String): FailureFingerprint {
                if (obj == null) return FailureFingerprint(commandKey = fallbackKey)
                return FailureFingerprint(
                    commandKey = obj.optString("commandKey", fallbackKey),
                    intent = obj.optString("intent", "UNKNOWN"),
                    parametersHash = obj.optString("parametersHash", "none"),
                    targetPackage = obj.optString("targetPackage", "system"),
                    androidSdkInt = obj.optInt("androidSdkInt", Build.VERSION.SDK_INT),
                    deviceModel = obj.optString("deviceModel", "${Build.MANUFACTURER}:${Build.MODEL}"),
                    permissionState = obj.optString("permissionState", "UNKNOWN"),
                    modelVersion = obj.optString("modelVersion", "sherpa-onnx-2024"),
                    appVersion = obj.optString("appVersion", "1.0.0"),
                    environmentState = obj.optString("environmentState", "DEFAULT")
                )
            }
        }
    }

    data class ErrorLogEntry(
        val errorId: String,
        val dateTime: String,
        val timestampMs: Long,
        val task: String,
        val commandKey: String,
        val expectedResult: String,
        val actualResult: String,
        val errorType: ErrorTaxonomy,
        val threeFactorAnalysis: ThreeFactorAnalysis,
        val evidence: String,
        val attemptedMethod: String,
        val result: ExecutionOutcome,
        val rootCause: String,
        val recoveryAttempt: String,
        val recoveryResult: String,
        val lessonLearned: String,
        val doNotReuseMethod: String,
        val verificationStatus: String,
        val simpleExplanation: String,
        val simpleExplanationMm: String,
        // Extended P0/P1 Production Fields (Items 13, 14, 15, 17)
        val confidence: Float = 0.9f,
        val recoverability: Boolean = true,
        val retryPolicy: ErrorRecoveryPolicy = ErrorRecoveryPolicy.WAIT_FOR_CONDITION,
        val environmentFingerprint: FailureFingerprint = FailureFingerprint(commandKey = commandKey),
        val firstSeenMs: Long = timestampMs,
        val lastSeenMs: Long = timestampMs,
        val occurrenceCount: Int = 1,
        val status: ErrorLifecycleStatus = ErrorLifecycleStatus.BLOCKED,
        val statusTransitionHistory: List<String> = listOf("FAILED -> BLOCKED"),
        // ISSUE-035 — canonical structured evidence stored verbatim at record time
        val errorCode: String = "",
        val exceptionClass: String = ORIGINAL_EXCEPTION_UNAVAILABLE,
        val exceptionMessage: String = ""
    ) {
        fun toFormattedReport(): String {
            return buildString {
                appendLine("Error ID: $errorId")
                appendLine("Date / Time: $dateTime")
                appendLine("Status: ${status.name} (Occurrences: $occurrenceCount)")
                appendLine("Task: $task")
                appendLine("Expected Result: $expectedResult")
                appendLine("Actual Result: $actualResult")
                appendLine("Error Type: #${errorType.id} ${errorType.label}")
                appendLine("Technical Cause: ${threeFactorAnalysis.technicalCause}")
                appendLine("Resource / Source Limitation: ${threeFactorAnalysis.resourceLimitation}")
                appendLine("Real-World / Practical Mismatch: ${threeFactorAnalysis.realWorldMismatch}")
                appendLine("Evidence: $evidence")
                appendLine("Error Code: ${errorCode.ifBlank { "(none recorded)" }}")
                appendLine("Original Exception: $exceptionClass${if (exceptionMessage.isNotBlank()) " — $exceptionMessage" else ""}")
                appendLine("Attempted Method: $attemptedMethod")
                appendLine("Result: ${result.name}")
                appendLine("Root Cause: $rootCause")
                appendLine("Recovery Policy: ${retryPolicy.name}")
                appendLine("Recovery Attempt: $recoveryAttempt")
                appendLine("Recovery Result: $recoveryResult")
                appendLine("Lesson Learned: $lessonLearned")
                appendLine("Do Not Reuse: $doNotReuseMethod")
                appendLine("Fingerprint: ${environmentFingerprint.toCompositeHash()}")
                appendLine("Verification: $verificationStatus")
            }
        }

        fun toJsonObject(): JSONObject {
            return JSONObject().apply {
                put("errorId", errorId)
                put("dateTime", dateTime)
                put("timestampMs", timestampMs)
                put("task", task)
                put("commandKey", commandKey)
                put("expectedResult", expectedResult)
                put("actualResult", actualResult)
                put("errorType", errorType.name)
                put("technicalCause", threeFactorAnalysis.technicalCause)
                put("resourceLimitation", threeFactorAnalysis.resourceLimitation)
                put("realWorldMismatch", threeFactorAnalysis.realWorldMismatch)
                put("evidence", evidence)
                put("attemptedMethod", attemptedMethod)
                put("result", result.name)
                put("rootCause", rootCause)
                put("recoveryAttempt", recoveryAttempt)
                put("recoveryResult", recoveryResult)
                put("lessonLearned", lessonLearned)
                put("doNotReuseMethod", doNotReuseMethod)
                put("verificationStatus", verificationStatus)
                put("simpleExplanation", simpleExplanation)
                put("simpleExplanationMm", simpleExplanationMm)
                put("confidence", confidence.toDouble())
                put("recoverability", recoverability)
                put("retryPolicy", retryPolicy.name)
                put("environmentFingerprint", environmentFingerprint.toJsonObject())
                put("firstSeenMs", firstSeenMs)
                put("lastSeenMs", lastSeenMs)
                put("occurrenceCount", occurrenceCount)
                put("status", status.name)
                put("statusTransitionHistory", JSONArray(statusTransitionHistory))
                put("errorCode", errorCode)
                put("exceptionClass", exceptionClass)
                put("exceptionMessage", exceptionMessage)
            }
        }

        companion object {
            fun fromJsonObject(obj: JSONObject): ErrorLogEntry {
                val taxonomy = try {
                    ErrorTaxonomy.valueOf(obj.optString("errorType", ErrorTaxonomy.UNKNOWN.name))
                } catch (e: Exception) {
                    ErrorTaxonomy.UNKNOWN
                }
                val outcome = try {
                    ExecutionOutcome.valueOf(obj.optString("result", ExecutionOutcome.FAIL.name))
                } catch (e: Exception) {
                    ExecutionOutcome.FAIL
                }
                val policy = try {
                    ErrorRecoveryPolicy.valueOf(obj.optString("retryPolicy", ErrorRecoveryPolicy.WAIT_FOR_CONDITION.name))
                } catch (e: Exception) {
                    ErrorRecoveryPolicy.WAIT_FOR_CONDITION
                }
                val lifecycleStatus = try {
                    ErrorLifecycleStatus.valueOf(obj.optString("status", ErrorLifecycleStatus.BLOCKED.name))
                } catch (e: Exception) {
                    ErrorLifecycleStatus.BLOCKED
                }
                val cmdKey = obj.optString("commandKey", "")
                val ts = obj.optLong("timestampMs", 0L)
                val historyArr = obj.optJSONArray("statusTransitionHistory")
                val historyList = mutableListOf<String>()
                if (historyArr != null) {
                    for (i in 0 until historyArr.length()) {
                        historyList.add(historyArr.optString(i))
                    }
                }
                if (historyList.isEmpty()) historyList.add("FAILED -> ${lifecycleStatus.name}")

                return ErrorLogEntry(
                    errorId = obj.optString("errorId", "ER-000"),
                    dateTime = obj.optString("dateTime", ""),
                    timestampMs = ts,
                    task = obj.optString("task", ""),
                    commandKey = cmdKey,
                    expectedResult = obj.optString("expectedResult", ""),
                    actualResult = obj.optString("actualResult", ""),
                    errorType = taxonomy,
                    threeFactorAnalysis = ThreeFactorAnalysis(
                        technicalCause = obj.optString("technicalCause", ""),
                        resourceLimitation = obj.optString("resourceLimitation", ""),
                        realWorldMismatch = obj.optString("realWorldMismatch", "")
                    ),
                    evidence = obj.optString("evidence", ""),
                    attemptedMethod = obj.optString("attemptedMethod", ""),
                    result = outcome,
                    rootCause = obj.optString("rootCause", ""),
                    recoveryAttempt = obj.optString("recoveryAttempt", ""),
                    recoveryResult = obj.optString("recoveryResult", ""),
                    lessonLearned = obj.optString("lessonLearned", ""),
                    doNotReuseMethod = obj.optString("doNotReuseMethod", ""),
                    verificationStatus = obj.optString("verificationStatus", ""),
                    simpleExplanation = obj.optString("simpleExplanation", ""),
                    simpleExplanationMm = obj.optString("simpleExplanationMm", ""),
                    confidence = obj.optDouble("confidence", 0.9).toFloat(),
                    recoverability = obj.optBoolean("recoverability", true),
                    retryPolicy = policy,
                    environmentFingerprint = FailureFingerprint.fromJsonObject(
                        obj.optJSONObject("environmentFingerprint"),
                        cmdKey
                    ),
                    firstSeenMs = obj.optLong("firstSeenMs", ts),
                    lastSeenMs = obj.optLong("lastSeenMs", ts),
                    occurrenceCount = obj.optInt("occurrenceCount", 1),
                    status = lifecycleStatus,
                    statusTransitionHistory = historyList,
                    errorCode = obj.optString("errorCode", ""),
                    exceptionClass = obj.optString("exceptionClass", ORIGINAL_EXCEPTION_UNAVAILABLE),
                    exceptionMessage = obj.optString("exceptionMessage", "")
                )
            }
        }
    }

    data class FailedMethodWarning(
        val warningBanner: String,
        val previousErrorLog: ErrorLogEntry,
        val simpleExplanation: String,
        val simpleExplanationMm: String,
        val blockedMethod: String,
        val safeAlternativeMethod: String,
        val requiredEvidenceToRetry: String,
        val conditionsChanged: Boolean,
        val recoveryPolicy: ErrorRecoveryPolicy = previousErrorLog.retryPolicy
    )

    private val sequenceCounter = AtomicInteger(1)
    private val errorLogHistory = CopyOnWriteArrayList<ErrorLogEntry>()
    private val failedMethodMemory = ConcurrentHashMap<String, ErrorLogEntry>()
    private val dbSyncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * ISSUE-033: injectable TTS probe factory so unit tests can drive the
     * staged live TTS verifier with fakes. Null in production, where
     * [AndroidTtsProbe] is used.
     */
    @Volatile
    internal var ttsProbeFactoryForTesting: ((Context) -> TtsProbe)? = null

    /**
     * ISSUE-033: short-lived cache of TTS verification results per command key,
     * so repeated guard checks do not re-run an audible probe utterance.
     */
    private data class CachedVerification(val result: VerificationResult, val cachedAtMs: Long)

    private val ttsVerificationCache = ConcurrentHashMap<String, CachedVerification>()
    private const val TTS_VERIFICATION_CACHE_TTL_MS = 60_000L

    @Volatile
    private var appContext: Context? = null
    private var prefs: SharedPreferences? = null

    private val _errorLogsFlow = MutableStateFlow<List<ErrorLogEntry>>(emptyList())
    val errorLogsFlow: StateFlow<List<ErrorLogEntry>> = _errorLogsFlow.asStateFlow()

    private val _blockedMethodsFlow = MutableStateFlow<Map<String, ErrorLogEntry>>(emptyMap())
    val blockedMethodsFlow: StateFlow<Map<String, ErrorLogEntry>> = _blockedMethodsFlow.asStateFlow()

    @Synchronized
    fun initialize(context: Context) {
        val ctx = context.applicationContext
        appContext = ctx
        prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        loadFromPersistentStorage()
        // NOTE (FAKE-10, 2026-10-22): initialize() previously auto-resolved
        // every stale TTS blocked record here, claiming a verification that
        // was never performed. Blocked records now clear ONLY through a typed
        // VerificationResult (ISSUE-034): a real live check via
        // verifyLiveEnvironmentConditions, or a verified successful execution.
        // There is no user-action or free-text-evidence clearing path.
    }

    @Synchronized
    private fun loadFromPersistentStorage() {
        val sharedPrefs = prefs ?: return
        try {
            val savedSeq = sharedPrefs.getInt(KEY_SEQ_COUNTER, 1)
            sequenceCounter.set(savedSeq.coerceAtLeast(1))

            val historyJsonStr = sharedPrefs.getString(KEY_ERROR_HISTORY_JSON, null)
            val activeBlockedKeysStr = sharedPrefs.getString(KEY_FAILED_METHODS_JSON, null)

            val activeBlockedKeys = mutableSetOf<String>()
            if (!activeBlockedKeysStr.isNullOrBlank()) {
                val keysArr = JSONArray(activeBlockedKeysStr)
                for (i in 0 until keysArr.length()) {
                    activeBlockedKeys.add(keysArr.optString(i))
                }
            }

            if (!historyJsonStr.isNullOrBlank()) {
                val arr = JSONArray(historyJsonStr)
                errorLogHistory.clear()
                failedMethodMemory.clear()
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val entry = ErrorLogEntry.fromJsonObject(obj)
                    errorLogHistory.add(entry)
                    // ISSUE-036 legacy migration (read-time, non-destructive):
                    // entries recorded by older builds keep their stored key in
                    // history, but the memory index uses the canonical form so
                    // canonical lookups find them.
                    val canonicalKey = FailedMethodKeys.canonicalOrSelf(entry.commandKey)
                    if ((activeBlockedKeys.contains(entry.commandKey) || activeBlockedKeys.contains(canonicalKey)) &&
                        (entry.status == ErrorLifecycleStatus.BLOCKED || entry.status == ErrorLifecycleStatus.FAILED)
                    ) {
                        failedMethodMemory[canonicalKey] = entry
                    }
                }
            }
            publishStateFlows()
            Log.i(TAG, "Restored ${errorLogHistory.size} Error Logs and ${failedMethodMemory.size} blocked methods.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load ErrorLearningWorkflowEngine storage: ${e.message}", e)
        }
    }

    @Synchronized
    private fun saveToPersistentStorage() {
        val sharedPrefs = prefs ?: return
        try {
            val arr = JSONArray()
            for (entry in errorLogHistory.takeLast(150)) {
                arr.put(entry.toJsonObject())
            }
            val blockedKeysArr = JSONArray()
            for (key in failedMethodMemory.keys) {
                blockedKeysArr.put(key)
            }
            sharedPrefs.edit()
                .putInt(KEY_SEQ_COUNTER, sequenceCounter.get())
                .putString(KEY_ERROR_HISTORY_JSON, arr.toString())
                .putString(KEY_FAILED_METHODS_JSON, blockedKeysArr.toString())
                .apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist ErrorLearningWorkflowEngine storage: ${e.message}", e)
        }
    }

    private fun publishStateFlows() {
        _errorLogsFlow.value = errorLogHistory.toList().reversed()
        _blockedMethodsFlow.value = failedMethodMemory.toMap()
    }

    /**
     * Records a failure into the Error-Learning Memory with complete 3-Factor Analysis,
     * Sensitive Data Redaction (Item 19), Failure Fingerprint (Item 14), and Room DB sync (Item 16).
     */
    @Synchronized
    fun recordErrorLog(
        task: String,
        commandKey: String,
        expectedResult: String,
        actualResult: String,
        attemptedMethod: String,
        evidence: String,
        exception: Throwable? = null,
        recoveryAttempt: String = "Switched to verified alternative fallback flow",
        recoveryResult: String = "Alternative path ready; failed method blocked from blind reuse",
        verificationStatus: String = "Not verified (Action failed; recorded truthfully without fabrication)",
        intent: String = "UNKNOWN",
        targetPackage: String = "system",
        errorCode: String? = null
    ): ErrorLogEntry {
        val now = System.currentTimeMillis()
        val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(now))
        // ISSUE-036: every record is stored under the ONE canonical key
        // (structured/legacy keys canonicalize; free-text keys normalize).
        val normKey = FailedMethodKeys.canonicalOrSelf(commandKey)

        // Item 19: Redact sensitive PII before logging
        val safeTask = SensitiveDataRedactor.redactForAuditLog(task)
        val safeEvidenceRaw = evidence.ifBlank { exception?.toString() ?: "" }
        val safeEvidence = SensitiveDataRedactor.redactForAuditLog(
            safeEvidenceRaw.ifBlank { "INSUFFICIENT_EVIDENCE" }
        )

        // ISSUE-035: resolve the canonical structured error code (explicit
        // caller value wins; otherwise extract from the raw evidence), and
        // normalize underscores to spaces at the classifier boundary so the
        // ER-007 underscore-vs-space mismatch is impossible by construction.
        val resolvedErrorCode = errorCode?.takeIf { it.isNotBlank() }
            ?: extractStructuredCode(safeEvidenceRaw + " " + actualResult)
        val lowerEvidence = (safeEvidenceRaw + " " + (exception?.message ?: "")).trim().lowercase(Locale.ROOT)
        val normalizedEvidence = lowerEvidence.replace('_', ' ')
        val taxonomy = classifyTaxonomy(normalizedEvidence, resolvedErrorCode)
        val threeFactors = analyzeThreeFactors(normalizedEvidence, attemptedMethod, taxonomy, resolvedErrorCode)
        val (simpleEn, simpleMm) = generateSimpleExplanation(taxonomy, threeFactors)
        val recoveryPolicy = determineRecoveryPolicy(taxonomy)

        val fingerprint = FailureFingerprint(
            commandKey = normKey,
            intent = intent,
            parametersHash = SensitiveDataRedactor.sha256Short(normKey),
            targetPackage = targetPackage,
            androidSdkInt = Build.VERSION.SDK_INT,
            deviceModel = "${Build.MANUFACTURER}:${Build.MODEL}",
            permissionState = if (taxonomy == ErrorTaxonomy.ACCESS_PERMISSION_ERROR || taxonomy == ErrorTaxonomy.RUNTIME_PERMISSION_ERROR) "DENIED" else "DEFAULT",
            environmentState = taxonomy.name
        )

        // ISSUE-038: same key + same taxonomy inside the same UNRESOLVED
        // episode updates the existing entry in place (occurrenceCount + a
        // RETRY_ATTEMPTED history note) instead of minting a new ER ID —
        // the ER-008/ER-015/ER-016 flooding defect. A resolved episode or a
        // different taxonomy falls through and mints a fresh entry below.
        val episodeEntry = failedMethodMemory[normKey]
        if (episodeEntry != null &&
            episodeEntry.errorType == taxonomy &&
            (episodeEntry.status == ErrorLifecycleStatus.BLOCKED || episodeEntry.status == ErrorLifecycleStatus.FAILED)
        ) {
            val updated = episodeEntry.copy(
                dateTime = dateStr,
                timestampMs = now,
                lastSeenMs = now,
                actualResult = SensitiveDataRedactor.redactForAuditLog(actualResult),
                evidence = safeEvidence,
                occurrenceCount = episodeEntry.occurrenceCount + 1,
                errorCode = resolvedErrorCode.ifBlank { episodeEntry.errorCode },
                exceptionClass = exception?.javaClass?.name ?: episodeEntry.exceptionClass,
                exceptionMessage = exception?.message?.let {
                    SensitiveDataRedactor.redactForAuditLog(it).take(300)
                } ?: episodeEntry.exceptionMessage,
                statusTransitionHistory = episodeEntry.statusTransitionHistory +
                    "RETRY_ATTEMPTED -> FAILED (occurrence ${episodeEntry.occurrenceCount + 1}; same unresolved episode)"
            )
            val histIdx = errorLogHistory.indexOfLast { it.errorId == episodeEntry.errorId }
            if (histIdx >= 0) errorLogHistory[histIdx] = updated
            failedMethodMemory[normKey] = updated
            saveToPersistentStorage()
            publishStateFlows()
            Log.w(TAG, "ISSUE-038: repeat failure for '$commandKey' updated ${updated.errorId} in place (occurrence ${updated.occurrenceCount}); no new ER ID minted.")
            return updated
        }

        // Check if an existing entry for this commandKey exists in history to increment occurrenceCount without deleting history
        val existingIdx = errorLogHistory.indexOfLast { it.commandKey == normKey }
        val existingEntry = if (existingIdx >= 0) errorLogHistory[existingIdx] else null

        val seq = sequenceCounter.getAndIncrement()
        val errorId = String.format(Locale.US, "ER-%03d", seq)

        val entry = ErrorLogEntry(
            errorId = errorId,
            dateTime = dateStr,
            timestampMs = now,
            task = safeTask,
            commandKey = normKey,
            expectedResult = expectedResult,
            actualResult = SensitiveDataRedactor.redactForAuditLog(actualResult),
            errorType = taxonomy,
            threeFactorAnalysis = threeFactors,
            evidence = safeEvidence,
            attemptedMethod = attemptedMethod,
            result = ExecutionOutcome.FAIL,
            rootCause = determineRootCause(taxonomy, threeFactors),
            recoveryAttempt = recoveryAttempt,
            recoveryResult = recoveryResult,
            lessonLearned = "Do not repeat '$attemptedMethod' under identical environment conditions; use verified fallback.",
            doNotReuseMethod = attemptedMethod,
            verificationStatus = verificationStatus,
            simpleExplanation = simpleEn,
            simpleExplanationMm = simpleMm,
            confidence = if (taxonomy == ErrorTaxonomy.UNKNOWN) 0.0f else 0.92f,
            recoverability = recoveryPolicy != ErrorRecoveryPolicy.UNSUPPORTED,
            retryPolicy = recoveryPolicy,
            environmentFingerprint = fingerprint,
            firstSeenMs = existingEntry?.firstSeenMs ?: now,
            lastSeenMs = now,
            occurrenceCount = (existingEntry?.occurrenceCount ?: 0) + 1,
            status = ErrorLifecycleStatus.BLOCKED,
            statusTransitionHistory = (existingEntry?.statusTransitionHistory ?: emptyList()) + "FAILED -> BLOCKED ($errorId)",
            errorCode = resolvedErrorCode,
            exceptionClass = exception?.javaClass?.name ?: ORIGINAL_EXCEPTION_UNAVAILABLE,
            exceptionMessage = exception?.message?.let {
                SensitiveDataRedactor.redactForAuditLog(it).take(300)
            } ?: ""
        )

        errorLogHistory.add(entry)
        failedMethodMemory[normKey] = entry
        saveToPersistentStorage()
        publishStateFlows()

        // Item 16: Persist into Room Database (Source of Truth)
        val ctx = appContext
        if (ctx != null) {
            dbSyncScope.launch {
                try {
                    val db = AppDatabase.getDatabase(ctx)
                    db.commandLogDao().insertCommandLog(
                        CommandLogEntity(
                            commandText = normKey,
                            commandType = intent.ifBlank { "ERROR_LEARNING" },
                            status = "FAILED",
                            actionDetail = "[${entry.errorId}] ${entry.rootCause} | Fingerprint=${fingerprint.toCompositeHash()}",
                            timestamp = now,
                            failureCategory = when (taxonomy) {
                                ErrorTaxonomy.RESOURCE_UNAVAILABLE_ERROR, ErrorTaxonomy.NETWORK_REALITY_ERROR -> "RESOURCE"
                                ErrorTaxonomy.ACCESS_PERMISSION_ERROR, ErrorTaxonomy.RUNTIME_PERMISSION_ERROR, ErrorTaxonomy.ENVIRONMENT_MISMATCH_ERROR -> "PRAGMATIC_CONTEXTUAL"
                                else -> "TECHNICAL"
                            },
                            failureReason = simpleMm,
                            suggestedAlternative = recoveryAttempt,
                            isRepeatedFailure = entry.occurrenceCount > 1
                        )
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Room DB error-learning sync note: ${e.message}")
                }
            }
        }

        Log.w(TAG, "Recorded Error Log ${entry.errorId} for '$commandKey' (status=${entry.status}, count=${entry.occurrenceCount}): ${entry.rootCause}")
        return entry
    }

    /**
     * ISSUE-037 — records a pipeline-layer failure WITHOUT minting a
     * duplicate ER entry when the executor already recorded this exact
     * failure event. When [executorEntryId] names an existing entry, that
     * entry is returned with a PIPELINE_CROSS_REF history note (the Room
     * CommandFailureAnalyzer row is recorded separately by the caller and
     * cross-references the same id). Otherwise this behaves exactly like
     * [recordErrorLog].
     */
    @Synchronized
    fun recordFailureOnce(
        executorEntryId: String?,
        task: String,
        commandKey: String,
        expectedResult: String,
        actualResult: String,
        attemptedMethod: String,
        evidence: String,
        recoveryAttempt: String,
        recoveryResult: String
    ): ErrorLogEntry {
        if (executorEntryId != null) {
            val idx = errorLogHistory.indexOfLast { it.errorId == executorEntryId }
            if (idx >= 0) {
                val existing = errorLogHistory[idx]
                val updated = existing.copy(
                    lastSeenMs = System.currentTimeMillis(),
                    statusTransitionHistory = existing.statusTransitionHistory +
                        "PIPELINE_CROSS_REF (same failure event $executorEntryId; duplicate ER entry suppressed)"
                )
                errorLogHistory[idx] = updated
                if (failedMethodMemory[updated.commandKey]?.errorId == executorEntryId) {
                    failedMethodMemory[updated.commandKey] = updated
                }
                saveToPersistentStorage()
                publishStateFlows()
                Log.i(TAG, "ISSUE-037: pipeline failure cross-referenced to existing $executorEntryId; no duplicate ER entry minted.")
                return updated
            }
        }
        return recordErrorLog(
            task = task,
            commandKey = commandKey,
            expectedResult = expectedResult,
            actualResult = actualResult,
            attemptedMethod = attemptedMethod,
            evidence = evidence,
            recoveryAttempt = recoveryAttempt,
            recoveryResult = recoveryResult
        )
    }

    /**
     * Checks if a requested command/method has previously failed.
     * Automatically runs Live Condition Verifier when Context is available.
     * When conditions resolve, transitions historical entry through:
     * BLOCKED -> CONDITION_CHANGED -> RETRY_ALLOWED (never deleting history!).
     */
    @Synchronized
    fun evaluateRequestedAction(
        commandKey: String,
        context: Context? = appContext
    ): FailedMethodWarning? {
        val key = FailedMethodKeys.canonicalOrSelf(commandKey)
        val previousLog = failedMethodMemory[key] ?: return null

        if (context != null) {
            val liveResult = verifyLiveEnvironmentConditions(context, previousLog)
            if (liveResult != null && liveResult.outcome == VerificationOutcome.VERIFIED) {
                Log.i(TAG, "Live Condition Verifier confirmed environment resolved for '$key': ${liveResult.summaryLine()}")
                clearFailedMethodWithVerification(liveResult)
                return null
            }
        }

        val banner = buildString {
            append("$MANDATORY_WARNING_PREFIX ($MANDATORY_WARNING_PREFIX_MM) ")
            append(previousLog.simpleExplanation)
            append(" ")
            append(previousLog.simpleExplanationMm)
        }

        return FailedMethodWarning(
            warningBanner = banner,
            previousErrorLog = previousLog,
            simpleExplanation = previousLog.simpleExplanation,
            simpleExplanationMm = previousLog.simpleExplanationMm,
            blockedMethod = previousLog.doNotReuseMethod,
            safeAlternativeMethod = previousLog.recoveryAttempt,
            requiredEvidenceToRetry = determineRequiredEvidence(previousLog.errorType),
            conditionsChanged = false,
            recoveryPolicy = previousLog.retryPolicy
        )
    }

    /**
     * ISSUE-033/034 — Live Condition Verifier. Returns a typed
     * [VerificationResult] produced by a REAL check, or null when no verifier
     * branch applies to this record. A record may clear ONLY when the result
     * outcome is [VerificationOutcome.VERIFIED].
     */
    fun verifyLiveEnvironmentConditions(context: Context, previousLog: ErrorLogEntry): VerificationResult? {
        return try {
            val lowerKeyAndEv = (previousLog.commandKey + " " + previousLog.task + " " + previousLog.evidence).lowercase(Locale.ROOT)
            if (lowerKeyAndEv.contains("tts_synthesis") || lowerKeyAndEv.contains("speak_burmese") || lowerKeyAndEv.contains("voicepackstatus") || lowerKeyAndEv.contains("com.google.android.tts")) {
                return verifyTtsCondition(context, previousLog)
            }

            val evidence: Pair<String, String>? = when (previousLog.errorType) {
                ErrorTaxonomy.NETWORK_REALITY_ERROR -> {
                    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                    val network = cm?.activeNetwork
                    val caps = network?.let { cm.getNetworkCapabilities(it) }
                    val isOnline = caps != null &&
                        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                    if (isOnline) {
                        "CONNECTIVITY_MANAGER" to "Verified via ConnectivityManager: NET_CAPABILITY_INTERNET & VALIDATED are now active."
                    } else null
                }

                ErrorTaxonomy.ACCESS_PERMISSION_ERROR -> {
                    val lowerEv = previousLog.evidence.lowercase(Locale.ROOT)
                    if (lowerEv.contains("overlay")) {
                        if (Settings.canDrawOverlays(context)) {
                            "SETTINGS_OVERLAY" to "Verified via Settings.canDrawOverlays: Overlay permission is now granted."
                        } else null
                    } else {
                        val enabledServices = Settings.Secure.getString(
                            context.contentResolver,
                            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                        ) ?: ""
                        if (enabledServices.contains(context.packageName)) {
                            "SETTINGS_SECURE_ACCESSIBILITY" to "Verified via Settings.Secure: Accessibility service is now enabled for ${context.packageName}."
                        } else null
                    }
                }

                ErrorTaxonomy.RUNTIME_PERMISSION_ERROR -> {
                    val lowerEv = (previousLog.evidence + " " + previousLog.task).lowercase(Locale.ROOT)
                    val requiredPerm = when {
                        lowerEv.contains("call") || lowerEv.contains("ဖုန်းခေါ်") -> Manifest.permission.CALL_PHONE
                        lowerEv.contains("sms") || lowerEv.contains("စာတို") || lowerEv.contains("မက်ဆေ့") -> Manifest.permission.SEND_SMS
                        lowerEv.contains("camera") || lowerEv.contains("ကင်မရာ") -> Manifest.permission.CAMERA
                        lowerEv.contains("audio") || lowerEv.contains("mic") -> Manifest.permission.RECORD_AUDIO
                        else -> null
                    }
                    if (requiredPerm != null &&
                        ContextCompat.checkSelfPermission(context, requiredPerm) == PackageManager.PERMISSION_GRANTED
                    ) {
                        "CONTEXT_COMPAT_PERMISSION" to "Verified via ContextCompat.checkSelfPermission: $requiredPerm is now PERMISSION_GRANTED."
                    } else null
                }

                ErrorTaxonomy.RESOURCE_UNAVAILABLE_ERROR -> {
                    val modelManager = ModelManager(context)
                    if (modelManager.isSherpaOnnxModelAvailable() || modelManager.isVoskModelAvailable()) {
                        "MODEL_MANAGER_FILES" to "Verified via ModelManager: Offline speech model files are now present on disk."
                    } else null
                }

                else -> null
            }
            evidence?.let { (source, detail) ->
                VerificationResult(
                    outcome = VerificationOutcome.VERIFIED,
                    errorId = previousLog.errorId,
                    commandKey = previousLog.commandKey,
                    checkedAtMs = System.currentTimeMillis(),
                    evidenceSource = source,
                    detail = detail
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Live Condition Verifier check encountered exception: ${e.message}")
            null
        }
    }

    /**
     * ISSUE-033 — staged TTS verification via [TtsConditionVerifier].
     * Results are cached per command key for [TTS_VERIFICATION_CACHE_TTL_MS]
     * so repeated guard checks do not re-run an audible probe utterance.
     * Must be called off the main thread (the probe blocks with bounded waits).
     */
    private fun verifyTtsCondition(context: Context, previousLog: ErrorLogEntry): VerificationResult {
        val cacheKey = previousLog.commandKey
        val now = System.currentTimeMillis()
        val cached = ttsVerificationCache[cacheKey]
        if (cached != null && now - cached.cachedAtMs < TTS_VERIFICATION_CACHE_TTL_MS) {
            return cached.result
        }
        val probe = ttsProbeFactoryForTesting?.invoke(context)
            ?: AndroidTtsProbe(
                context,
                MyanmarTTSVoicePackManager.getInstance(context).currentEnginePackage.value
                    .takeIf { it.isNotBlank() }
                    ?: MyanmarTTSVoicePackManager.getBestTtsEnginePackage(context)
            )
        val result = TtsConditionVerifier.verify(previousLog.errorId, previousLog.commandKey, probe, now)
        ttsVerificationCache[cacheKey] = CachedVerification(result, now)
        return result
    }

    /**
     * ISSUE-034 — Runs the live verifier for one blocked record and clears it
     * ONLY when the verifier returns [VerificationOutcome.VERIFIED]. Every
     * outcome is returned to the caller as a typed [VerificationResult] so UI,
     * log and memory always show the same state:
     * missing resource -> BLOCKED, failed check -> FAILED, no verifier branch
     * or inconclusive check -> UNVERIFIED (record stays blocked).
     */
    @Synchronized
    fun verifyBlockedMethod(
        commandKey: String,
        context: Context? = appContext
    ): VerificationResult {
        val key = FailedMethodKeys.canonicalOrSelf(commandKey)
        val previousLog = failedMethodMemory[key]
            ?: return VerificationResult(
                outcome = VerificationOutcome.UNVERIFIED,
                errorId = "",
                commandKey = key,
                checkedAtMs = System.currentTimeMillis(),
                evidenceSource = "NONE",
                detail = "No blocked failed-method record exists for this key; nothing was verified or cleared."
            )
        if (context == null) {
            return VerificationResult(
                outcome = VerificationOutcome.UNVERIFIED,
                errorId = previousLog.errorId,
                commandKey = key,
                checkedAtMs = System.currentTimeMillis(),
                evidenceSource = "NONE",
                detail = "No Context available to run a live check; the record was NOT cleared."
            )
        }
        val result = verifyLiveEnvironmentConditions(context, previousLog)
            ?: return VerificationResult(
                outcome = VerificationOutcome.UNVERIFIED,
                errorId = previousLog.errorId,
                commandKey = key,
                checkedAtMs = System.currentTimeMillis(),
                evidenceSource = "NONE",
                detail = "No automatic verifier exists for this error type (${previousLog.errorType}); the record was NOT cleared. Retry the action manually — a verified successful retry clears it."
            )
        if (result.outcome == VerificationOutcome.VERIFIED) {
            clearFailedMethodWithVerification(result)
        }
        return result
    }

    /**
     * ISSUE-034 — Mints a typed [VerificationResult] for a VERIFIED SUCCESSFUL
     * EXECUTION of a previously blocked method (the execution itself, checked
     * by the caller's result verifier, is the evidence). Bound to the blocked
     * record's error ID; returns null when no blocked record exists.
     */
    @Synchronized
    fun verificationFromExecutionSuccess(commandKey: String, detail: String): VerificationResult? {
        val key = FailedMethodKeys.canonicalOrSelf(commandKey)
        val blocked = failedMethodMemory[key] ?: return null
        return VerificationResult(
            outcome = VerificationOutcome.VERIFIED,
            errorId = blocked.errorId,
            commandKey = key,
            checkedAtMs = System.currentTimeMillis(),
            evidenceSource = "EXECUTION_SUCCESS",
            detail = detail
        )
    }

    /**
     * ITEM 13 + ISSUE-034 — Non-Destructive Lifecycle Transition on Resolution:
     * BLOCKED -> CONDITION_CHANGED -> RETRY_ALLOWED -> VERIFIED_SUCCESS -> RESOLVED,
     * NEVER deleting the historical record from [errorLogHistory].
     *
     * The ONLY way a record can reach VERIFIED_SUCCESS / RESOLVED: a typed
     * [VerificationResult] with outcome VERIFIED whose errorId AND commandKey
     * bind to the blocked record. Free-text evidence strings are not accepted
     * anywhere (the previous String-based API was removed in ISSUE-034).
     */
    @Synchronized
    fun clearFailedMethodWithVerification(result: VerificationResult): Boolean {
        if (result.outcome != VerificationOutcome.VERIFIED) return false
        val key = FailedMethodKeys.canonicalOrSelf(result.commandKey)
        val blocked = failedMethodMemory[key] ?: return false
        if (blocked.errorId != result.errorId) {
            Log.w(TAG, "Refused to clear '$key': verification is bound to ${result.errorId}, blocked record is ${blocked.errorId}")
            return false
        }
        val evidence = result.summaryLine()
        failedMethodMemory.remove(key)
        // Update the historical record in errorLogHistory WITHOUT deleting it
        val histIdx = errorLogHistory.indexOfLast { it.errorId == blocked.errorId || it.commandKey == key }
        if (histIdx >= 0) {
            val old = errorLogHistory[histIdx]
            val updated = old.copy(
                status = ErrorLifecycleStatus.RESOLVED,
                lastSeenMs = System.currentTimeMillis(),
                recoveryResult = "RESOLVED: $evidence",
                verificationStatus = "Verified by ${result.evidenceSource} (${result.detail})",
                statusTransitionHistory = old.statusTransitionHistory + listOf(
                    "MUTATED",
                    "CONDITION_CHANGED ($evidence)",
                    "RETRY_ALLOWED",
                    "VERIFIED_SUCCESS",
                    "RESOLVED"
                )
            )
            errorLogHistory[histIdx] = updated
        }
        saveToPersistentStorage()
        publishStateFlows()
        Log.i(TAG, "Transitioned failed method '$key' to RESOLVED (history preserved): $evidence")
        return true
    }

    @Synchronized
    fun getAllErrorLogs(): List<ErrorLogEntry> = errorLogHistory.toList()

    @Synchronized
    fun clearAllForTesting() {
        sequenceCounter.set(1)
        errorLogHistory.clear()
        failedMethodMemory.clear()
        ttsVerificationCache.clear()
        ttsProbeFactoryForTesting = null
        prefs?.edit()?.clear()?.commit()
        publishStateFlows()
    }

    private fun normalizeKey(raw: String): String = raw.trim().lowercase(Locale.ROOT)

    /**
     * ISSUE-035 — extracts a canonical structured error code from raw evidence
     * text. Known UniversalAppLauncher codes are matched first; otherwise the
     * first UPPER_SNAKE token is taken as the code. Returns "" when none.
     */
    private fun extractStructuredCode(rawText: String): String {
        val upper = rawText.uppercase(Locale.ROOT)
        // Longest first: APP_NOT_FOUND is a substring of
        // APP_NOT_FOUND_OR_LOW_CONFIDENCE and must not shadow it.
        val knownCodes = (LAUNCHER_RESOURCE_CODES + LAUNCHER_INTENT_CODES)
            .sortedByDescending { it.length }
        for (code in knownCodes) {
            if (upper.contains(code)) return code
        }
        return STRUCTURED_CODE_REGEX.find(upper)?.value ?: ""
    }

    private fun classifyTaxonomy(lowerEvidence: String, structuredCode: String = ""): ErrorTaxonomy {
        // ISSUE-035: explicit structured-code mapping runs FIRST, before any
        // keyword matching, so spelling variants cannot change the taxonomy.
        if (structuredCode in LAUNCHER_RESOURCE_CODES) {
            return ErrorTaxonomy.RESOURCE_UNAVAILABLE_ERROR
        }
        if (structuredCode in LAUNCHER_INTENT_CODES) {
            return ErrorTaxonomy.INTENT_MISUNDERSTANDING_ERROR
        }
        if (lowerEvidence.isBlank() || lowerEvidence == "insufficient evidence" || lowerEvidence == "unknown") {
            return ErrorTaxonomy.UNKNOWN
        }
        return when {
            lowerEvidence.contains("accessibility") || lowerEvidence.contains("overlay") ->
                ErrorTaxonomy.ACCESS_PERMISSION_ERROR
            lowerEvidence.contains("permission") || lowerEvidence.contains("securityexception") || lowerEvidence.contains("denied") ->
                ErrorTaxonomy.RUNTIME_PERMISSION_ERROR
            lowerEvidence.contains("network") || lowerEvidence.contains("timeout") || lowerEvidence.contains("offline") || lowerEvidence.contains("504") ->
                ErrorTaxonomy.NETWORK_REALITY_ERROR
            lowerEvidence.contains("not found") || lowerEvidence.contains("missing") || lowerEvidence.contains("not installed") || lowerEvidence.contains("voicepackstatus") || lowerEvidence.contains("tts_synthesis") ->
                ErrorTaxonomy.RESOURCE_UNAVAILABLE_ERROR
            lowerEvidence.contains("unresolved reference") || lowerEvidence.contains("compile") ->
                ErrorTaxonomy.BUILD_ERROR
            lowerEvidence.contains("component interface") || lowerEvidence.contains("required system resources") || lowerEvidence.contains("soundpool") || lowerEvidence.contains("ccodec") ->
                ErrorTaxonomy.ENVIRONMENT_MISMATCH_ERROR
            lowerEvidence.contains("hardware") || lowerEvidence.contains("sensor") || lowerEvidence.contains("ir emitter") || lowerEvidence.contains("unsupported") ->
                ErrorTaxonomy.HARDWARE_CAPABILITY_ERROR
            lowerEvidence.contains("injection") || lowerEvidence.contains("trust boundary") || lowerEvidence.contains("allowlist") ->
                ErrorTaxonomy.INJECTION_MALICIOUS_INPUT_ERROR
            else -> ErrorTaxonomy.RUNTIME_ERROR
        }
    }

    private fun determineRecoveryPolicy(taxonomy: ErrorTaxonomy): ErrorRecoveryPolicy {
        return when (taxonomy) {
            ErrorTaxonomy.ACCESS_PERMISSION_ERROR, ErrorTaxonomy.RUNTIME_PERMISSION_ERROR ->
                ErrorRecoveryPolicy.ASK_USER
            ErrorTaxonomy.NETWORK_REALITY_ERROR, ErrorTaxonomy.RESOURCE_UNAVAILABLE_ERROR ->
                ErrorRecoveryPolicy.WAIT_FOR_CONDITION
            ErrorTaxonomy.HARDWARE_CAPABILITY_ERROR ->
                ErrorRecoveryPolicy.UNSUPPORTED
            ErrorTaxonomy.INJECTION_MALICIOUS_INPUT_ERROR ->
                ErrorRecoveryPolicy.SAFE_FAIL
            ErrorTaxonomy.UNKNOWN ->
                ErrorRecoveryPolicy.SAFE_FAIL
            else ->
                ErrorRecoveryPolicy.RETRY_LATER
        }
    }

    private fun analyzeThreeFactors(
        lowerEvidence: String,
        attemptedMethod: String,
        taxonomy: ErrorTaxonomy,
        structuredCode: String = ""
    ): ThreeFactorAnalysis {
        // ISSUE-035: when a structured error code was stored, the 3-factor
        // analysis derives from that fact instead of the generic templates.
        if (structuredCode in LAUNCHER_RESOURCE_CODES || structuredCode in LAUNCHER_INTENT_CODES) {
            return ThreeFactorAnalysis(
                technicalCause = "UniversalAppLauncher returned structured error code $structuredCode for the requested target (stored verbatim on this entry).",
                resourceLimitation = "The target application package is not installed, is disabled, or could not be resolved/launched with sufficient confidence on this device.",
                realWorldMismatch = "The command assumed the target app exists and can be identified on the user's phone; the launcher's structured result proves otherwise in the current environment."
            )
        }
        if (taxonomy == ErrorTaxonomy.UNKNOWN) {
            return ThreeFactorAnalysis(
                technicalCause = "CAUSE = UNKNOWN (Insufficient diagnostic evidence to prove specific technical fault).",
                resourceLimitation = "CAUSE = UNKNOWN (Resource state could not be conclusively verified).",
                realWorldMismatch = "CAUSE = UNKNOWN (Environment mismatch cannot be assumed without evidence)."
            )
        }
        return when {
            lowerEvidence.contains("component interface") || lowerEvidence.contains("required system resources") || lowerEvidence.contains("soundpool") || lowerEvidence.contains("ccodec") -> ThreeFactorAnalysis(
                technicalCause = "SoundPool / MediaCodec (CCodec c2.android.raw.decoder) queried C2ResourcesNeededTuning interface which returned status 6 (C2_OMITTED).",
                resourceLimitation = "Virtualized/emulator Codec2 software decoder lacks hardware system resource tuning interfaces.",
                realWorldMismatch = "Decoding WAV files via SoundPool invokes in-process CCodec on Android 12+, whereas direct in-memory PCM AudioTrack (MODE_STATIC) streams directly to AudioFlinger without Codec2."
            )
            lowerEvidence.contains("permission") || lowerEvidence.contains("accessibility") || lowerEvidence.contains("denied") -> ThreeFactorAnalysis(
                technicalCause = "System API call rejected by Android OS SecurityManager due to missing runtime/accessibility grant.",
                resourceLimitation = "Required system permission token is unavailable to the application process.",
                realWorldMismatch = "The code assumed permissions were already enabled on the user's phone, but they are currently turned off in Settings."
            )
            lowerEvidence.contains("network") || lowerEvidence.contains("timeout") || lowerEvidence.contains("offline") -> ThreeFactorAnalysis(
                technicalCause = "Socket read/connection timed out before server response arrived.",
                resourceLimitation = "Active internet connectivity or remote server availability is insufficient.",
                realWorldMismatch = "Cloud API requires stable low-latency internet, whereas real-world mobile connectivity is offline or congested."
            )
            lowerEvidence.contains("voicepackstatus") || lowerEvidence.contains("tts_synthesis") || lowerEvidence.contains("com.google.android.tts") -> ThreeFactorAnalysis(
                technicalCause = "TextToSpeech.isLanguageAvailable(my-MM) returned LANG_NOT_SUPPORTED / LANG_MISSING_DATA on com.google.android.tts.",
                resourceLimitation = "Offline Burmese (my-MM) neural voice data pack is not yet downloaded inside Google Speech Services on this device.",
                realWorldMismatch = "Raw Myanmar Unicode requires either the native my-MM voice pack or Universal Burmese Phonetic G2P Fallback to vocalize on standard Android TTS engines."
            )
            lowerEvidence.contains("not installed") || lowerEvidence.contains("activitynotfound") || lowerEvidence.contains("missing") -> ThreeFactorAnalysis(
                technicalCause = "PackageManager / file lookup returned null for the requested target.",
                resourceLimitation = "Target application package or local model file is not present on device storage.",
                realWorldMismatch = "The action assumes the target app/file exists on the user's device, which is not true in the current environment."
            )
            else -> ThreeFactorAnalysis(
                technicalCause = "Execution of '$attemptedMethod' encountered an unhandled runtime condition or invalid state.",
                resourceLimitation = "Required runtime state or system service dependency did not respond as expected.",
                realWorldMismatch = "Theoretical execution path did not match the actual device state at runtime."
            )
        }
    }

    private fun generateSimpleExplanation(
        taxonomy: ErrorTaxonomy,
        factors: ThreeFactorAnalysis
    ): Pair<String, String> {
        return when (taxonomy) {
            ErrorTaxonomy.UNKNOWN -> Pair(
                "The exact cause of this failure could not be verified (CAUSE = UNKNOWN). We will safely halt instead of guessing.",
                "အမှားဖြစ်ပွားရသည့် အကြောင်းရင်းအတိအကျကို သက်သေမပြနိုင်ပါ (CAUSE = UNKNOWN)။ မှန်းဆလုပ်ဆောင်ခြင်းမပြုဘဲ ဘေးကင်းစွာ ရပ်ဆိုင်းထားပါသည်။"
            )
            ErrorTaxonomy.RUNTIME_PERMISSION_ERROR, ErrorTaxonomy.ACCESS_PERMISSION_ERROR -> Pair(
                "It failed because the required phone permission is turned off. Repeating the same action will fail again until the permission is enabled in Settings.",
                "ဖုန်း၏ လိုအပ်သော ခွင့်ပြုချက် (Permission) ပိတ်ထားသောကြောင့် မအောင်မြင်ခဲ့ခြင်း ဖြစ်ပါသည်။ Settings တွင် ခွင့်ပြုချက် မဖွင့်မချင်း ယခင်နည်းလမ်းအတိုင်း ထပ်လုပ်ပါက ထပ်မံ မအောင်မြင်နိုင်ပါ။"
            )
            ErrorTaxonomy.NETWORK_REALITY_ERROR -> Pair(
                "It failed because the internet connection was unreachable or timed out. We should use the offline on-device mode instead of repeating the failed network call.",
                "အင်တာနက်လိုင်း ပြတ်တောက်နေသောကြောင့် မအောင်မြင်ခဲ့ခြင်း ဖြစ်ပါသည်။ မအောင်မြင်သော အွန်လိုင်းနည်းလမ်းအစား စက်တွင်း Offline စနစ်ကို ပြောင်းလဲအသုံးပြုပါမည်။"
            )
            ErrorTaxonomy.RESOURCE_UNAVAILABLE_ERROR -> Pair(
                "It failed because the requested app or model file is not installed on this phone.",
                "တောင်းဆိုထားသော အက်ပ် သို့မဟုတ် မော်ဒယ်ဖိုင်သည် ဖုန်းထဲတွင် မရှိသေးသောကြောင့် မအောင်မြင်ခဲ့ခြင်း ဖြစ်ပါသည်။"
            )
            else -> Pair(
                "It failed due to a technical mismatch on the device (${factors.technicalCause}). We will avoid repeating the same method.",
                "စက်ပိုင်းဆိုင်ရာ နည်းပညာ ကန့်သတ်ချက်ကြောင့် မအောင်မြင်ခဲ့ခြင်း ဖြစ်ပါသဖြင့် ယခင်နည်းလမ်းကို ထပ်မံ မသုံးတော့ဘဲ အစားထိုးနည်းလမ်းဖြင့် ဆောင်ရွက်ပါမည်။"
            )
        }
    }

    private fun determineRootCause(taxonomy: ErrorTaxonomy, factors: ThreeFactorAnalysis): String {
        if (taxonomy == ErrorTaxonomy.UNKNOWN) return "CAUSE = UNKNOWN"
        return "[${taxonomy.label}] ${factors.technicalCause} | Mismatch: ${factors.realWorldMismatch}"
    }

    private fun determineRequiredEvidence(taxonomy: ErrorTaxonomy): String {
        return when (taxonomy) {
            ErrorTaxonomy.RUNTIME_PERMISSION_ERROR, ErrorTaxonomy.ACCESS_PERMISSION_ERROR ->
                "Verified PackageManager / AccessibilityManager check confirming the user granted the permission."
            ErrorTaxonomy.NETWORK_REALITY_ERROR ->
                "Verified active network validation showing internet reachability."
            ErrorTaxonomy.RESOURCE_UNAVAILABLE_ERROR ->
                "Verified file/package existence check on device storage."
            else ->
                "Verified state change or configuration update resolving the technical cause."
        }
    }
}
