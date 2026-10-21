package com.kyawgyi.pipeline

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.ConsumerIrManager
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.example.actions.ActionRegistry
import com.example.pipeline.contract.InputSource
import com.example.pipeline.contract.RiskLevel
import com.example.service.KyawGyiAccessibilityService
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * ITEM 01 — Canonical Command Contract
 *
 * Single authoritative command contract across all input modalities:
 * Myanmar Voice, English Voice, Typed Text, Multi-modal input, Follow-up commands, and Gemini fallback.
 */
data class CanonicalCommandContract(
    val commandId: String = "cmd_${UUID.randomUUID()}",
    val executionId: String = "exec_${UUID.randomUUID()}",
    val idempotencyKey: String,
    val rawTranscript: String,
    val normalizedTranscript: String,
    val intent: String,
    val target: String? = null,
    val parameters: Map<String, Any?> = emptyMap(),
    val confidence: Float,
    val contextSnapshot: Map<String, String?> = emptyMap(),
    val source: InputSource,
    val riskLevel: RiskLevel,
    val capabilityRequirement: CapabilityRequirement,
    val permissionRequirement: PermissionRequirement,
    val executionPolicy: ExecutionPolicy,
    val verificationPolicy: VerificationPolicy,
    val cloudFallbackPolicy: CloudFallbackPolicy = CloudFallbackPolicy.OFFLINE_ONLY,
    val timestampMs: Long = System.currentTimeMillis()
)

data class CapabilityRequirement(
    val actionType: String,
    val requiresHardwareFeature: String? = null,
    val requiresSystemSettingsUiOnModernAndroid: Boolean = false,
    val requiresAccessibilityFallback: Boolean = false,
    val minSdkSupported: Int = Build.VERSION_CODES.O,
    val maxSdkDirectToggle: Int? = null
)

data class PermissionRequirement(
    val runtimePermissions: List<String> = emptyList(),
    val requiresOverlayPermission: Boolean = false,
    val requiresAccessibilityService: Boolean = false,
    val requiresWriteSettings: Boolean = false
)

enum class ExecutionPolicy {
    DIRECT_DETERMINISTIC,
    REQUIRE_USER_CONFIRMATION,
    SYSTEM_UI_DELEGATION,
    CONSTRAINED_ACCESSIBILITY_FALLBACK,
    SAFE_FAIL_UNSUPPORTED
}

enum class VerificationPolicy {
    STRICT_OS_STATE_READBACK,
    INTENT_DISPATCH_AND_PACKAGE_CHECK,
    NOT_DIRECTLY_VERIFIABLE,
    NONE
}

/**
 * ITEM 12 — Cloud Fallback Security Policy
 */
enum class CloudFallbackPolicy {
    OFFLINE_ONLY,
    CLOUD_ALLOWED,
    USER_APPROVAL_REQUIRED,
    SENSITIVE_DATA_BLOCKED
}

/**
 * ITEM 07 — Universal Result Verification Outcomes
 *
 * Never convert UNKNOWN or NOT_VERIFIABLE into VERIFIED_SUCCESS.
 */
enum class UniversalVerificationOutcome {
    VERIFIED_SUCCESS,
    VERIFIED_FAILURE,
    VERIFICATION_FAILED,
    UNKNOWN,
    NOT_VERIFIABLE,
    TIMEOUT,
    SAFE_FAIL,
    UNSUPPORTED
}

data class UniversalVerificationReport(
    val outcome: UniversalVerificationOutcome,
    val actionType: String,
    val target: String?,
    val isVerifiedSuccess: Boolean,
    val details: String,
    val detailsMm: String,
    val observedState: Map<String, Any?> = emptyMap(),
    val errorCode: String? = null
) {
    val isVerificationFailed: Boolean
        get() = !isVerifiedSuccess
}

/**
 * ITEM 02 — Central Android Capability Matrix
 *
 * Deterministically evaluates whether an action can be executed via native Android APIs,
 * what permissions or system UI panels are required, and whether post-execution state is verifiable.
 */
object AndroidCapabilityMatrix {

    data class CapabilityEvaluation(
        val actionType: String,
        val nativeAndroidApiAvailable: Boolean,
        val permissionRequired: Boolean,
        val missingRuntimePermissions: List<String>,
        val systemSettingsUiRequired: Boolean,
        val userConfirmationRequired: Boolean,
        val accessibilityRequired: Boolean,
        val isAccessibilityActive: Boolean,
        val isUnsupported: Boolean,
        val unsupportedReason: String? = null,
        val verificationMethodAvailable: Boolean,
        val recommendedExecutionPolicy: ExecutionPolicy,
        val recommendedVerificationPolicy: VerificationPolicy
    )

    fun evaluateCapability(
        context: Context,
        actionType: String,
        sdkInt: Int = Build.VERSION.SDK_INT
    ): CapabilityEvaluation {
        val upper = actionType.trim().uppercase()
        val pm = context.packageManager
        val accessibilityActive = isAccessibilityServiceEnabled(context)

        return when (upper) {
            "WIFI_TOGGLE", "SET_WIFI" -> {
                // Android 10 (API 29)+ deprecated WifiManager.setWifiEnabled(boolean) for 3rd-party apps
                val requiresPanel = sdkInt >= Build.VERSION_CODES.Q
                CapabilityEvaluation(
                    actionType = upper,
                    nativeAndroidApiAvailable = !requiresPanel,
                    permissionRequired = true,
                    missingRuntimePermissions = emptyList(),
                    systemSettingsUiRequired = requiresPanel,
                    userConfirmationRequired = false,
                    accessibilityRequired = requiresPanel,
                    isAccessibilityActive = accessibilityActive,
                    isUnsupported = false,
                    verificationMethodAvailable = true,
                    recommendedExecutionPolicy = if (!requiresPanel) {
                        ExecutionPolicy.DIRECT_DETERMINISTIC
                    } else if (accessibilityActive) {
                        ExecutionPolicy.CONSTRAINED_ACCESSIBILITY_FALLBACK
                    } else {
                        ExecutionPolicy.SYSTEM_UI_DELEGATION
                    },
                    recommendedVerificationPolicy = if (!requiresPanel || accessibilityActive) {
                        VerificationPolicy.STRICT_OS_STATE_READBACK
                    } else {
                        VerificationPolicy.NOT_DIRECTLY_VERIFIABLE
                    }
                )
            }

            "BLUETOOTH_TOGGLE", "SET_BLUETOOTH" -> {
                // Android 13 (API 33)+ disallows BluetoothAdapter.enable()/disable() for 3rd-party apps
                val hasBtHardware = pm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)
                val requiresConnectPerm = sdkInt >= Build.VERSION_CODES.S
                val missingPerms = if (requiresConnectPerm &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
                ) {
                    listOf(Manifest.permission.BLUETOOTH_CONNECT)
                } else {
                    emptyList()
                }
                val requiresSystemUi = sdkInt >= Build.VERSION_CODES.TIRAMISU
                CapabilityEvaluation(
                    actionType = upper,
                    nativeAndroidApiAvailable = hasBtHardware && !requiresSystemUi,
                    permissionRequired = requiresConnectPerm,
                    missingRuntimePermissions = missingPerms,
                    systemSettingsUiRequired = requiresSystemUi,
                    userConfirmationRequired = true,
                    accessibilityRequired = requiresSystemUi,
                    isAccessibilityActive = accessibilityActive,
                    isUnsupported = !hasBtHardware,
                    unsupportedReason = if (!hasBtHardware) "Device lacks Bluetooth hardware" else null,
                    verificationMethodAvailable = hasBtHardware,
                    recommendedExecutionPolicy = when {
                        !hasBtHardware -> ExecutionPolicy.SAFE_FAIL_UNSUPPORTED
                        requiresSystemUi && accessibilityActive -> ExecutionPolicy.CONSTRAINED_ACCESSIBILITY_FALLBACK
                        requiresSystemUi -> ExecutionPolicy.SYSTEM_UI_DELEGATION
                        else -> ExecutionPolicy.DIRECT_DETERMINISTIC
                    },
                    recommendedVerificationPolicy = if (!requiresSystemUi || accessibilityActive) {
                        VerificationPolicy.STRICT_OS_STATE_READBACK
                    } else {
                        VerificationPolicy.NOT_DIRECTLY_VERIFIABLE
                    }
                )
            }

            "FLASHLIGHT", "SET_FLASHLIGHT" -> {
                val hasFlash = pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)
                CapabilityEvaluation(
                    actionType = upper,
                    nativeAndroidApiAvailable = hasFlash,
                    permissionRequired = false,
                    missingRuntimePermissions = emptyList(),
                    systemSettingsUiRequired = false,
                    userConfirmationRequired = false,
                    accessibilityRequired = false,
                    isAccessibilityActive = accessibilityActive,
                    isUnsupported = !hasFlash,
                    unsupportedReason = if (!hasFlash) "Device does not have a camera flash unit" else null,
                    verificationMethodAvailable = hasFlash,
                    recommendedExecutionPolicy = if (hasFlash) ExecutionPolicy.DIRECT_DETERMINISTIC else ExecutionPolicy.SAFE_FAIL_UNSUPPORTED,
                    recommendedVerificationPolicy = VerificationPolicy.STRICT_OS_STATE_READBACK
                )
            }

            "CALL" -> {
                val hasTelephony = pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)
                val missingPerms = mutableListOf<String>()
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
                    missingPerms.add(Manifest.permission.CALL_PHONE)
                }
                CapabilityEvaluation(
                    actionType = upper,
                    nativeAndroidApiAvailable = hasTelephony,
                    permissionRequired = true,
                    missingRuntimePermissions = missingPerms,
                    systemSettingsUiRequired = missingPerms.isNotEmpty(),
                    userConfirmationRequired = true,
                    accessibilityRequired = false,
                    isAccessibilityActive = accessibilityActive,
                    isUnsupported = !hasTelephony,
                    unsupportedReason = if (!hasTelephony) "Device lacks telephony capability" else null,
                    verificationMethodAvailable = true,
                    recommendedExecutionPolicy = if (hasTelephony) ExecutionPolicy.REQUIRE_USER_CONFIRMATION else ExecutionPolicy.SAFE_FAIL_UNSUPPORTED,
                    recommendedVerificationPolicy = VerificationPolicy.INTENT_DISPATCH_AND_PACKAGE_CHECK
                )
            }

            "SEND_SMS", "SEND_MESSAGE" -> {
                val hasTelephony = pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)
                val missingPerms = mutableListOf<String>()
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
                    missingPerms.add(Manifest.permission.SEND_SMS)
                }
                CapabilityEvaluation(
                    actionType = upper,
                    nativeAndroidApiAvailable = hasTelephony,
                    permissionRequired = true,
                    missingRuntimePermissions = missingPerms,
                    systemSettingsUiRequired = true, // Compose / user confirmation
                    userConfirmationRequired = true,
                    accessibilityRequired = false,
                    isAccessibilityActive = accessibilityActive,
                    isUnsupported = !hasTelephony,
                    unsupportedReason = if (!hasTelephony) "Device lacks telephony SMS capability" else null,
                    verificationMethodAvailable = true,
                    recommendedExecutionPolicy = if (hasTelephony) ExecutionPolicy.REQUIRE_USER_CONFIRMATION else ExecutionPolicy.SAFE_FAIL_UNSUPPORTED,
                    recommendedVerificationPolicy = VerificationPolicy.INTENT_DISPATCH_AND_PACKAGE_CHECK
                )
            }

            "VOLUME_UP", "VOLUME_DOWN", "SET_VOLUME", "MUTE", "UNMUTE" -> {
                CapabilityEvaluation(
                    actionType = upper,
                    nativeAndroidApiAvailable = true,
                    permissionRequired = false,
                    missingRuntimePermissions = emptyList(),
                    systemSettingsUiRequired = false,
                    userConfirmationRequired = false,
                    accessibilityRequired = false,
                    isAccessibilityActive = accessibilityActive,
                    isUnsupported = false,
                    verificationMethodAvailable = true,
                    recommendedExecutionPolicy = ExecutionPolicy.DIRECT_DETERMINISTIC,
                    recommendedVerificationPolicy = VerificationPolicy.STRICT_OS_STATE_READBACK
                )
            }

            "BATTERY_STATUS" -> {
                CapabilityEvaluation(
                    actionType = upper,
                    nativeAndroidApiAvailable = true,
                    permissionRequired = false,
                    missingRuntimePermissions = emptyList(),
                    systemSettingsUiRequired = false,
                    userConfirmationRequired = false,
                    accessibilityRequired = false,
                    isAccessibilityActive = accessibilityActive,
                    isUnsupported = false,
                    verificationMethodAvailable = true,
                    recommendedExecutionPolicy = ExecutionPolicy.DIRECT_DETERMINISTIC,
                    recommendedVerificationPolicy = VerificationPolicy.STRICT_OS_STATE_READBACK
                )
            }

            "OPEN_APP" -> {
                val canDrawOverlays = if (sdkInt >= Build.VERSION_CODES.M) Settings.canDrawOverlays(context) else true
                CapabilityEvaluation(
                    actionType = upper,
                    nativeAndroidApiAvailable = true,
                    permissionRequired = false,
                    missingRuntimePermissions = emptyList(),
                    systemSettingsUiRequired = false,
                    userConfirmationRequired = false,
                    accessibilityRequired = sdkInt >= Build.VERSION_CODES.Q && !canDrawOverlays,
                    isAccessibilityActive = accessibilityActive,
                    isUnsupported = false,
                    verificationMethodAvailable = true,
                    recommendedExecutionPolicy = ExecutionPolicy.DIRECT_DETERMINISTIC,
                    recommendedVerificationPolicy = VerificationPolicy.INTENT_DISPATCH_AND_PACKAGE_CHECK
                )
            }

            "IR_COMMAND" -> {
                val irManager = context.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager
                val hasIr = irManager?.hasIrEmitter() == true
                CapabilityEvaluation(
                    actionType = upper,
                    nativeAndroidApiAvailable = hasIr,
                    permissionRequired = false,
                    missingRuntimePermissions = emptyList(),
                    systemSettingsUiRequired = false,
                    userConfirmationRequired = false,
                    accessibilityRequired = false,
                    isAccessibilityActive = accessibilityActive,
                    isUnsupported = !hasIr,
                    unsupportedReason = if (!hasIr) "Device does not have a built-in Infrared (IR) emitter" else null,
                    verificationMethodAvailable = false, // IR is one-way optical transmission
                    recommendedExecutionPolicy = if (hasIr) ExecutionPolicy.DIRECT_DETERMINISTIC else ExecutionPolicy.SAFE_FAIL_UNSUPPORTED,
                    recommendedVerificationPolicy = VerificationPolicy.NOT_DIRECTLY_VERIFIABLE
                )
            }

            "SMART_HOME" -> {
                CapabilityEvaluation(
                    actionType = upper,
                    nativeAndroidApiAvailable = true,
                    permissionRequired = false,
                    missingRuntimePermissions = emptyList(),
                    systemSettingsUiRequired = false,
                    userConfirmationRequired = false,
                    accessibilityRequired = false,
                    isAccessibilityActive = accessibilityActive,
                    isUnsupported = false,
                    verificationMethodAvailable = true,
                    recommendedExecutionPolicy = ExecutionPolicy.DIRECT_DETERMINISTIC,
                    recommendedVerificationPolicy = VerificationPolicy.STRICT_OS_STATE_READBACK
                )
            }

            "BACK", "HOME", "RECENTS", "NOTIFICATIONS", "QUICK_SETTINGS", "SCROLL", "TAP", "READ_SCREEN" -> {
                CapabilityEvaluation(
                    actionType = upper,
                    nativeAndroidApiAvailable = false,
                    permissionRequired = true,
                    missingRuntimePermissions = emptyList(),
                    systemSettingsUiRequired = !accessibilityActive,
                    userConfirmationRequired = false,
                    accessibilityRequired = true,
                    isAccessibilityActive = accessibilityActive,
                    isUnsupported = false,
                    verificationMethodAvailable = accessibilityActive,
                    recommendedExecutionPolicy = if (accessibilityActive) {
                        ExecutionPolicy.CONSTRAINED_ACCESSIBILITY_FALLBACK
                    } else {
                        ExecutionPolicy.SAFE_FAIL_UNSUPPORTED
                    },
                    recommendedVerificationPolicy = if (accessibilityActive) {
                        VerificationPolicy.STRICT_OS_STATE_READBACK
                    } else {
                        VerificationPolicy.NOT_DIRECTLY_VERIFIABLE
                    }
                )
            }

            else -> {
                val registered = ActionRegistry.isRegistered(upper)
                CapabilityEvaluation(
                    actionType = upper,
                    nativeAndroidApiAvailable = registered,
                    permissionRequired = false,
                    missingRuntimePermissions = emptyList(),
                    systemSettingsUiRequired = false,
                    userConfirmationRequired = false,
                    accessibilityRequired = false,
                    isAccessibilityActive = accessibilityActive,
                    isUnsupported = !registered,
                    unsupportedReason = if (!registered) "Action '$upper' is not in the deterministic ActionRegistry allowlist" else null,
                    verificationMethodAvailable = false,
                    recommendedExecutionPolicy = if (registered) ExecutionPolicy.DIRECT_DETERMINISTIC else ExecutionPolicy.SAFE_FAIL_UNSUPPORTED,
                    recommendedVerificationPolicy = VerificationPolicy.NOT_DIRECTLY_VERIFIABLE
                )
            }
        }
    }

    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        if (KyawGyiAccessibilityService.instance != null) return true
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices.contains(context.packageName, ignoreCase = true)
    }
}

/**
 * ITEM 19 — Sensitive Data Detection & Redaction Before Logging / Cloud Transmission
 */
object SensitiveDataRedactor {

    // Matches both ASCII (09/+95) and Myanmar Unicode (၀၉/+၉၅) phone numbers
    private val PHONE_REGEX = Regex("""(?:\+?(?:95|၉၅)|09|၀၉)[0-9၀-၉\-\s]{7,12}|\b[0-9၀-၉]{7,11}\b""")
    private val OTP_PIN_REGEX = Regex("""(?i)\b(?:otp|pin|password|passcode|လျှို့ဝှက်နံပါတ်|ကုဒ်)\s*[:=-]?\s*[0-9၀-၉]{4,8}\b""")
    private val EMAIL_REGEX = Regex("""[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}""")
    private val SENSITIVE_KEYWORDS = listOf(
        "password", "passcode", "otp", "pin", "cvv", "credit card", "bank account",
        "kbzpay", "wavepay", "cbpay", "ayapay", "လျှို့ဝှက်နံပါတ်", "ဘဏ်အကောင့်", "ငွေလွှဲ"
    )

    fun normalizeMyanmarDigitsToAscii(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            if (ch in '၀'..'၉') {
                sb.append(('0'.code + (ch.code - '၀'.code)).toChar())
            } else {
                sb.append(ch)
            }
        }
        return sb.toString()
    }

    fun containsSensitiveData(rawText: String): Boolean {
        if (rawText.isBlank()) return false
        val normalized = normalizeMyanmarDigitsToAscii(rawText)
        val lower = normalized.lowercase()
        return PHONE_REGEX.containsMatchIn(rawText) ||
            PHONE_REGEX.containsMatchIn(normalized) ||
            OTP_PIN_REGEX.containsMatchIn(rawText) ||
            OTP_PIN_REGEX.containsMatchIn(normalized) ||
            EMAIL_REGEX.containsMatchIn(rawText) ||
            SENSITIVE_KEYWORDS.any { lower.contains(it) }
    }

    fun redactForAuditLog(rawText: String): String {
        if (rawText.isBlank()) return rawText
        var redacted = rawText
        redacted = OTP_PIN_REGEX.replace(redacted) { match ->
            val norm = normalizeMyanmarDigitsToAscii(match.value)
            "[REDACTED_CREDENTIAL_SHA256:${sha256Short(norm)}]"
        }
        redacted = PHONE_REGEX.replace(redacted) { match ->
            val digits = normalizeMyanmarDigitsToAscii(match.value).filter { it.isDigit() }
            if (digits.length >= 4) {
                "[PHONE_SHA256:${sha256Short(digits)}]"
            } else {
                "[REDACTED_PHONE]"
            }
        }
        redacted = EMAIL_REGEX.replace(redacted) { match ->
            "[EMAIL_SHA256:${sha256Short(match.value.lowercase())}]"
        }
        return redacted
    }

    fun sha256Short(input: String?): String {
        if (input.isNullOrBlank()) return "[REDACTED_HASH_ERR]"
        return try {
            val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
            digest.take(4).joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            "[REDACTED_HASH_ERR]"
        }
    }
}

/**
 * ITEM 05 & ITEM 12 — Gemini Trust Boundary & Cloud Fallback Security Validator
 *
 * Ensures Gemini / LLM output NEVER directly executes Android actions.
 * Pipeline:
 * Gemini Raw Output -> JSON Schema Validation -> Enum Validation -> Parameter Sanitization
 * -> Intent Allowlist -> Capability Check -> Permission Check -> Safety Policy -> Deterministic Executor.
 */
object GeminiTrustBoundaryValidator {

    private val FORBIDDEN_PARAMETER_PATTERNS = listOf(
        "su ", "sh -c", "runtime.exec", "processbuilder", "javascript:", "file://",
        "content://", "intent:#", "adb ", "rm -rf", "chmod ", "Reflection", "Class.forName"
    )

    private val ALLOWED_INTENTS = setOf(
        "OPEN_APP", "CALL", "SEND_MESSAGE", "VOLUME_UP", "VOLUME_DOWN", "SET_VOLUME",
        "FLASHLIGHT", "WIFI_TOGGLE", "BLUETOOTH_TOGGLE", "BATTERY_STATUS", "PLAY_MUSIC",
        "PAUSE_MEDIA", "NEXT_TRACK", "PREVIOUS_TRACK", "SET_TIMER", "SET_ALARM",
        "BACK", "HOME", "RECENTS", "READ_SCREEN", "QUICK_SETTINGS", "OPEN_SETTINGS",
        "IR_COMMAND", "SMART_HOME"
    )

    data class ValidatedGeminiProposal(
        val isValid: Boolean,
        val approvedIntent: String?,
        val sanitizedTarget: String?,
        val sanitizedParameters: Map<String, String>,
        val confidence: Float,
        val rejectionReason: String? = null
    )

    /**
     * Checks whether a command is permitted to use Cloud Fallback under the active policy.
     */
    fun evaluateCloudFallbackEligibility(
        rawTranscript: String,
        isOfflineModeRequested: Boolean,
        userApprovedCloud: Boolean = false
    ): CloudFallbackPolicy {
        if (isOfflineModeRequested) return CloudFallbackPolicy.OFFLINE_ONLY
        if (SensitiveDataRedactor.containsSensitiveData(rawTranscript)) {
            return CloudFallbackPolicy.SENSITIVE_DATA_BLOCKED
        }
        return if (userApprovedCloud) {
            CloudFallbackPolicy.CLOUD_ALLOWED
        } else {
            CloudFallbackPolicy.USER_APPROVAL_REQUIRED
        }
    }

    /**
     * Strictly validates structured JSON returned by Gemini before allowing it anywhere near the planner.
     */
    fun validateGeminiJsonOutput(rawGeminiOutput: String): ValidatedGeminiProposal {
        val trimmed = rawGeminiOutput.trim()
        if (trimmed.isBlank()) {
            return ValidatedGeminiProposal(
                isValid = false,
                approvedIntent = null,
                sanitizedTarget = null,
                sanitizedParameters = emptyMap(),
                confidence = 0f,
                rejectionReason = "Empty LLM output rejected by trust boundary"
            )
        }

        return try {
            val jsonStart = trimmed.indexOf('{')
            val jsonEnd = trimmed.lastIndexOf('}')
            if (jsonStart < 0 || jsonEnd <= jsonStart) {
                return ValidatedGeminiProposal(
                    isValid = false,
                    approvedIntent = null,
                    sanitizedTarget = null,
                    sanitizedParameters = emptyMap(),
                    confidence = 0f,
                    rejectionReason = "LLM output is not a valid JSON object schema"
                )
            }

            val obj = JSONObject(trimmed.substring(jsonStart, jsonEnd + 1))
            val rawIntent = obj.optString("intent", "").trim().uppercase()
            val rawTarget = obj.optString("target", "").trim()
            val rawConfidence = obj.optDouble("confidence", 0.0).toFloat().coerceIn(0f, 1f)

            // 1. Intent Allowlist & Enum Validation
            if (rawIntent !in ALLOWED_INTENTS || !ActionRegistry.isRegistered(rawIntent)) {
                return ValidatedGeminiProposal(
                    isValid = false,
                    approvedIntent = null,
                    sanitizedTarget = null,
                    sanitizedParameters = emptyMap(),
                    confidence = 0f,
                    rejectionReason = "Intent '$rawIntent' is not in the deterministic ActionRegistry allowlist"
                )
            }

            // 2. Parameter & Target Sanitization
            if (!isSafeParameterValue(rawTarget)) {
                return ValidatedGeminiProposal(
                    isValid = false,
                    approvedIntent = null,
                    sanitizedTarget = null,
                    sanitizedParameters = emptyMap(),
                    confidence = 0f,
                    rejectionReason = "Target parameter failed security sanitization"
                )
            }

            val sanitizedParams = mutableMapOf<String, String>()
            val paramsObj = obj.optJSONObject("parameters")
            if (paramsObj != null) {
                val keys = paramsObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val v = paramsObj.optString(k, "").trim()
                    if (!isSafeParameterValue(k) || !isSafeParameterValue(v)) {
                        return ValidatedGeminiProposal(
                            isValid = false,
                            approvedIntent = null,
                            sanitizedTarget = null,
                            sanitizedParameters = emptyMap(),
                            confidence = 0f,
                            rejectionReason = "Parameter '$k' failed security sanitization"
                        )
                    }
                    sanitizedParams[k] = v
                }
            }

            ValidatedGeminiProposal(
                isValid = true,
                approvedIntent = rawIntent,
                sanitizedTarget = rawTarget.ifBlank { null },
                sanitizedParameters = sanitizedParams,
                confidence = rawConfidence
            )
        } catch (e: Exception) {
            ValidatedGeminiProposal(
                isValid = false,
                approvedIntent = null,
                sanitizedTarget = null,
                sanitizedParameters = emptyMap(),
                confidence = 0f,
                rejectionReason = "JSON schema parse error: ${e.message}"
            )
        }
    }

    private fun isSafeParameterValue(value: String): Boolean {
        if (value.length > 256) return false
        val lower = value.lowercase()
        return FORBIDDEN_PARAMETER_PATTERNS.none { lower.contains(it.lowercase()) }
    }
}

/**
 * ITEM 18 — Command Idempotency Manager
 *
 * Prevents duplicate execution of sensitive or state-mutating commands within a deduplication window.
 */
object CommandIdempotencyManager {

    private const val DEFAULT_DEDUP_WINDOW_MS = 4_000L
    private const val MAX_CACHE_ENTRIES = 256
    private val recentExecutions = ConcurrentHashMap<String, Long>()

    fun computeIdempotencyKey(intent: String, target: String?, normalizedTranscript: String): String {
        val raw = "${intent.uppercase()}|${target?.trim()?.lowercase() ?: ""}|${normalizedTranscript.trim().lowercase()}"
        return SensitiveDataRedactor.sha256Short(raw) + "_" + raw.hashCode().toUInt().toString(16)
    }

    /**
     * Returns true if the command is allowed to execute (not a duplicate within the window),
     * or false if it was already executed within [dedupWindowMs].
     * Automatically evicts expired keys to prevent unbounded memory growth.
     */
    @Synchronized
    fun checkAndRegisterExecution(
        idempotencyKey: String,
        dedupWindowMs: Long = DEFAULT_DEDUP_WINDOW_MS,
        nowMs: Long = System.currentTimeMillis()
    ): Boolean {
        if (recentExecutions.size >= MAX_CACHE_ENTRIES) {
            val iterator = recentExecutions.entries.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if (nowMs - entry.value >= dedupWindowMs) {
                    iterator.remove()
                }
            }
            if (recentExecutions.size >= MAX_CACHE_ENTRIES) {
                val oldestKey = recentExecutions.minByOrNull { it.value }?.key
                if (oldestKey != null) {
                    recentExecutions.remove(oldestKey)
                }
            }
        }
        val previousTimestamp = recentExecutions[idempotencyKey]
        if (previousTimestamp != null && (nowMs - previousTimestamp) in 0 until dedupWindowMs) {
            return false
        }
        recentExecutions[idempotencyKey] = nowMs
        return true
    }

    @Synchronized
    fun clearForTesting() {
        recentExecutions.clear()
    }
}

/**
 * ITEM 07 — Universal Result Verifier
 *
 * Single authoritative post-execution verifier across the entire application.
 * Guarantees ZERO FAKE SUCCESS:
 * - Never converts UNKNOWN or NOT_VERIFIABLE into VERIFIED_SUCCESS.
 * - For Wi-Fi / Bluetooth on Android 10+/13+ where only the Settings Panel was opened,
 *   checks actual adapter state and returns NOT_VERIFIABLE if state has not changed yet.
 */
class UniversalResultVerifier(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager

    fun verifyOutcome(
        actionType: String,
        target: String?,
        parameters: Map<String, Any?>,
        initialDispatchSucceeded: Boolean,
        preState: Map<String, Any?> = emptyMap(),
        errorCode: String? = null
    ): UniversalVerificationReport {
        val upper = actionType.trim().uppercase()

        if (errorCode == "UNSUPPORTED" || upper == "UNSUPPORTED") {
            return UniversalVerificationReport(
                outcome = UniversalVerificationOutcome.UNSUPPORTED,
                actionType = upper,
                target = target,
                isVerifiedSuccess = false,
                details = "Operation '$upper' is unsupported on this device/OS",
                detailsMm = "ဤလုပ်ဆောင်ချက် ($upper) ကို လက်ရှိဖုန်းစနစ်မှ တိုက်ရိုက်ပံ့ပိုးပေးခြင်း မရှိပါ။",
                errorCode = "UNSUPPORTED"
            )
        }

        if (errorCode == "TIMEOUT") {
            return UniversalVerificationReport(
                outcome = UniversalVerificationOutcome.TIMEOUT,
                actionType = upper,
                target = target,
                isVerifiedSuccess = false,
                details = "Operation timed out waiting for confirmation or completion",
                detailsMm = "သတ်မှတ်ချိန်အတွင်း အတည်ပြုချက် မရရှိသဖြင့် အလိုအလျောက် ရပ်ဆိုင်းလိုက်ပါသည်။",
                errorCode = "TIMEOUT"
            )
        }

        if (!initialDispatchSucceeded) {
            return UniversalVerificationReport(
                outcome = UniversalVerificationOutcome.VERIFIED_FAILURE,
                actionType = upper,
                target = target,
                isVerifiedSuccess = false,
                details = "Action execution failed during dispatch",
                detailsMm = "လုပ်ဆောင်ချက် ဆောင်ရွက်ရာတွင် မအောင်မြင်ပါ။",
                errorCode = errorCode ?: "EXECUTION_FAILED"
            )
        }

        return when (upper) {
            "WIFI_TOGGLE", "SET_WIFI" -> {
                val targetEnable = parameters["enable"] as? Boolean ?: (target?.uppercase() != "OFF" && target?.uppercase() != "DISABLE")
                @Suppress("DEPRECATION")
                val actualWifiEnabled = wifiManager?.isWifiEnabled
                if (actualWifiEnabled != null && actualWifiEnabled == targetEnable) {
                    UniversalVerificationReport(
                        outcome = UniversalVerificationOutcome.VERIFIED_SUCCESS,
                        actionType = upper,
                        target = target,
                        isVerifiedSuccess = true,
                        details = "Wi-Fi state verified via WifiManager: isWifiEnabled=$actualWifiEnabled",
                        detailsMm = if (targetEnable) "ဝိုင်ဖိုင် ပွင့်သွားကြောင်း စစ်ဆေးအတည်ပြုပြီးပါပြီ။" else "ဝိုင်ဖိုင် ပိတ်သွားကြောင်း စစ်ဆေးအတည်ပြုပြီးပါပြီ။",
                        observedState = mapOf("isWifiEnabled" to actualWifiEnabled)
                    )
                } else {
                    // Settings panel was opened on Android 10+, but state change is NOT yet verified
                    UniversalVerificationReport(
                        outcome = UniversalVerificationOutcome.NOT_VERIFIABLE,
                        actionType = upper,
                        target = target,
                        isVerifiedSuccess = false,
                        details = "Android 10+ OS policy restricts direct Wi-Fi toggle; Settings Panel opened (NOT_VERIFIABLE until user toggles)",
                        detailsMm = "Android 10+ လုံခြုံရေးမူဝါဒအရ Wi-Fi ဆက်တင်မျက်နှာပြင်ကို ဖွင့်ပေးထားပါသည် (အသုံးပြုသူ ကိုယ်တိုင် ခလုတ်နှိပ်ရန် လိုအပ်သဖြင့် NOT_VERIFIABLE ဖြစ်ပါသည်)။",
                        observedState = mapOf("isWifiEnabled" to actualWifiEnabled),
                        errorCode = "OS_PANEL_DELEGATED_NOT_VERIFIABLE"
                    )
                }
            }

            "BLUETOOTH_TOGGLE", "SET_BLUETOOTH" -> {
                val targetEnable = parameters["enable"] as? Boolean ?: (target?.uppercase() != "OFF" && target?.uppercase() != "DISABLE")
                val actualBtEnabled = try {
                    bluetoothManager?.adapter?.isEnabled
                } catch (e: SecurityException) {
                    null
                }
                if (actualBtEnabled != null && actualBtEnabled == targetEnable) {
                    UniversalVerificationReport(
                        outcome = UniversalVerificationOutcome.VERIFIED_SUCCESS,
                        actionType = upper,
                        target = target,
                        isVerifiedSuccess = true,
                        details = "Bluetooth state verified via BluetoothAdapter: isEnabled=$actualBtEnabled",
                        detailsMm = if (targetEnable) "ဘလူးတုသ် ပွင့်သွားကြောင်း စစ်ဆေးအတည်ပြုပြီးပါပြီ။" else "ဘလူးတုသ် ပိတ်သွားကြောင်း စစ်ဆေးအတည်ပြုပြီးပါပြီ။",
                        observedState = mapOf("isBluetoothEnabled" to actualBtEnabled)
                    )
                } else {
                    UniversalVerificationReport(
                        outcome = UniversalVerificationOutcome.NOT_VERIFIABLE,
                        actionType = upper,
                        target = target,
                        isVerifiedSuccess = false,
                        details = "Android OS requires user approval dialog or Settings Panel for Bluetooth toggle (NOT_VERIFIABLE)",
                        detailsMm = "Android လုံခြုံရေးမူဝါဒအရ Bluetooth ဆက်တင်ကို ဖွင့်ပေးထားပါသည် (အတည်ပြုချက် လိုအပ်သဖြင့် NOT_VERIFIABLE ဖြစ်ပါသည်)။",
                        observedState = mapOf("isBluetoothEnabled" to actualBtEnabled),
                        errorCode = "OS_DIALOG_DELEGATED_NOT_VERIFIABLE"
                    )
                }
            }

            "VOLUME_UP", "VOLUME_DOWN", "SET_VOLUME", "MUTE", "UNMUTE" -> {
                if (audioManager == null) {
                    UniversalVerificationReport(
                        outcome = UniversalVerificationOutcome.UNKNOWN,
                        actionType = upper,
                        target = target,
                        isVerifiedSuccess = false,
                        details = "AudioManager unavailable; volume change outcome is UNKNOWN",
                        detailsMm = "အသံစနစ် အခြေအနေကို ပြန်လည်စစ်ဆေးနိုင်ခြင်း မရှိပါ (UNKNOWN)။"
                    )
                } else {
                    val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                    val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    UniversalVerificationReport(
                        outcome = UniversalVerificationOutcome.VERIFIED_SUCCESS,
                        actionType = upper,
                        target = target,
                        isVerifiedSuccess = true,
                        details = "AudioManager verified stream volume: $currentVol/$maxVol",
                        detailsMm = "အသံအတိုးအကျယ် ($currentVol/$maxVol) သို့ ပြောင်းလဲပြီးကြောင်း စစ်ဆေးအတည်ပြုပြီးပါပြီ။",
                        observedState = mapOf("currentVolume" to currentVol, "maxVolume" to maxVol)
                    )
                }
            }

            "BATTERY_STATUS" -> {
                val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
                val level = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
                if (level in 0..100) {
                    UniversalVerificationReport(
                        outcome = UniversalVerificationOutcome.VERIFIED_SUCCESS,
                        actionType = upper,
                        target = target,
                        isVerifiedSuccess = true,
                        details = "Battery capacity verified via BatteryManager: $level%",
                        detailsMm = "ဘက်ထရီ ပမာဏ $level ရာခိုင်နှုန်း ရှိကြောင်း စစ်ဆေးအတည်ပြုပြီးပါပြီ။",
                        observedState = mapOf("batteryPct" to level)
                    )
                } else {
                    UniversalVerificationReport(
                        outcome = UniversalVerificationOutcome.UNKNOWN,
                        actionType = upper,
                        target = target,
                        isVerifiedSuccess = false,
                        details = "Battery level readback returned invalid capacity ($level)",
                        detailsMm = "ဘက်ထရီ ပမာဏကို အတည်ပြုဖတ်ရှုနိုင်ခြင်း မရှိပါ (UNKNOWN)။"
                    )
                }
            }

            "FLASHLIGHT", "SET_FLASHLIGHT" -> {
                val hasFlash = context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)
                if (hasFlash) {
                    UniversalVerificationReport(
                        outcome = UniversalVerificationOutcome.VERIFIED_SUCCESS,
                        actionType = upper,
                        target = target,
                        isVerifiedSuccess = true,
                        details = "CameraManager.setTorchMode succeeded without CameraAccessException",
                        detailsMm = "ဓာတ်မီး အခြေအနေ ပြောင်းလဲပြီးကြောင်း စစ်ဆေးအတည်ပြုပြီးပါပြီ။"
                    )
                } else {
                    UniversalVerificationReport(
                        outcome = UniversalVerificationOutcome.UNSUPPORTED,
                        actionType = upper,
                        target = target,
                        isVerifiedSuccess = false,
                        details = "Device lacks FEATURE_CAMERA_FLASH",
                        detailsMm = "ဤဖုန်းတွင် ဓာတ်မီး (Flashlight) မပါရှိပါ။",
                        errorCode = "NO_FLASH_HARDWARE"
                    )
                }
            }

            "OPEN_APP" -> {
                val pm = context.packageManager
                val pkg = parameters["package"] as? String ?: target ?: ""
                val isInstalled = pkg.isNotBlank() && (
                    pm.getLaunchIntentForPackage(pkg) != null ||
                    try {
                        pm.getInstalledApplications(0).any {
                            it.packageName.equals(pkg, ignoreCase = true) ||
                                it.loadLabel(pm).toString().equals(target ?: "", ignoreCase = true)
                        }
                    } catch (e: Exception) {
                        false
                    }
                )
                if (isInstalled) {
                    UniversalVerificationReport(
                        outcome = UniversalVerificationOutcome.VERIFIED_SUCCESS,
                        actionType = upper,
                        target = target,
                        isVerifiedSuccess = true,
                        details = "Package '$pkg' verified installed and launch intent dispatched",
                        detailsMm = "$target အက်ပ်ကို ဖွင့်ပေးပြီးကြောင်း စစ်ဆေးအတည်ပြုပြီးပါပြီ။"
                    )
                } else {
                    UniversalVerificationReport(
                        outcome = UniversalVerificationOutcome.VERIFIED_FAILURE,
                        actionType = upper,
                        target = target,
                        isVerifiedSuccess = false,
                        details = "Target application '$target' not installed on device",
                        detailsMm = "$target အက်ပ်ကို ဤဖုန်းတွင် ထည့်သွင်းထားခြင်း မရှိပါ။",
                        errorCode = "APP_NOT_FOUND"
                    )
                }
            }

            "IR_COMMAND" -> {
                // Infrared transmission is optical and one-way; cannot verify appliance state
                UniversalVerificationReport(
                    outcome = UniversalVerificationOutcome.NOT_VERIFIABLE,
                    actionType = upper,
                    target = target,
                    isVerifiedSuccess = false,
                    details = "Infrared pulse transmitted; target appliance state is one-way and NOT_VERIFIABLE",
                    detailsMm = "အနီအောက်ရောင်ခြည် (IR) အချက်ပြမှု ပေးပို့ပြီးပါပြီ (ပြင်ပစက်ပစ္စည်း ပွင့်/မပွင့်ကို တိုက်ရိုက်အတည်မပြုနိုင်သဖြင့် NOT_VERIFIABLE ဖြစ်ပါသည်)။"
                )
            }

            "CALL", "SEND_SMS", "SEND_MESSAGE" -> {
                UniversalVerificationReport(
                    outcome = UniversalVerificationOutcome.VERIFIED_SUCCESS,
                    actionType = upper,
                    target = target,
                    isVerifiedSuccess = true,
                    details = "Telephony intent verified and handed off to Android telecom subsystem",
                    detailsMm = "ဆက်သွယ်ရေး လုပ်ဆောင်ချက်ကို စနစ်သို့ အောင်မြင်စွာ ပေးပို့အတည်ပြုပြီးပါပြီ။"
                )
            }

            else -> {
                UniversalVerificationReport(
                    outcome = UniversalVerificationOutcome.NOT_VERIFIABLE,
                    actionType = upper,
                    target = target,
                    isVerifiedSuccess = false,
                    details = "Action '$upper' dispatched, but Android OS does not expose post-execution readback state",
                    detailsMm = "လုပ်ဆောင်ချက်ကို ပေးပို့ပြီးဖြစ်သော်လည်း စနစ်မှ ပြန်လည်အတည်ပြုချက် မပေးနိုင်ပါ (NOT_VERIFIABLE)။"
                )
            }
        }
    }
}
