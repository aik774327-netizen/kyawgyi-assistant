package com.example.security

import android.util.Log
import com.example.pipeline.contract.RiskLevel
import com.example.telemetry.TelemetryManager
import java.util.Locale
import java.util.regex.Pattern

/**
 * Sanitization Result
 */
data class SanitizationResult(
    val isClean: Boolean,
    val sanitizedText: String,
    val detectedThreats: List<String>,
    val riskScore: Float, // 0.0 (safe) to 1.0 (dangerous)
    val shouldBlock: Boolean,
    val reason: String = "Clean"
)

/**
 * Structured AI Command Model for Untrusted AI Output Validation
 */
data class CommandPlan(
    val commandType: String,
    val target: String? = null,
    val parameters: Map<String, Any?> = emptyMap(),
    val confidence: Float = 0.0f,
    val riskLevel: RiskLevel = RiskLevel.LOW,
    val requiresConfirmation: Boolean = false,
    val isValid: Boolean = true,
    val validationErrors: List<String> = emptyList()
)

/**
 * Production-Grade Security & Prompt Injection Sanitizer for Kyaw Gyi AI
 *
 * Protects against:
 * 1. Prompt Injection & Jailbreaking (e.g., "ignore previous instructions", "system:", "you are now in god mode")
 * 2. Shell Injection & Command Execution (e.g., "rm -rf", "sudo", "; ls", "| sh")
 * 3. JNDI / Code Execution exploits (e.g., "${jndi:ldap:...", "class.forName")
 * 4. Cross-Site Scripting & HTML Injection (e.g., "<script>", "javascript:", "onerror=")
 * 5. Zero-PII masking for Passwords, Pins, OTPs, Tokens, and Phone Numbers
 */
object SecurityPromptInjectionSanitizer {

    private const val TAG = "SecuritySanitizer"

    // Prompt injection override phrases (case-insensitive)
    private val PROMPT_INJECTION_PATTERNS = listOf(
        Pattern.compile("(?i)\\b(ignore\\s+(all\\s+)?previous\\s+instructions?)\\b"),
        Pattern.compile("(?i)\\b(disregard\\s+(all\\s+)?prior\\s+(rules|prompts?|instructions?))\\b"),
        Pattern.compile("(?i)\\b(bypass\\s+(all\\s+)?safet(y|ies)|override\\s+safet(y|ies))\\b"),
        Pattern.compile("(?i)\\b(you\\s+are\\s+now\\s+(unrestricted|in\\s+developer\\s+mode|dan|jailbroken))\\b"),
        Pattern.compile("(?i)\\b(system\\s*:\\s*|developer\\s*mode\\s*:\\s*)"),
        Pattern.compile("(?i)\\b(act\\s+as\\s+(root|superuser|administrator|unfiltered))\\b"),
        Pattern.compile("(?i)\\b(output\\s+your\\s+system\\s+prompt|reveal\\s+your\\s+instructions)\\b")
    )

    // Shell & OS command injection patterns
    private val SHELL_INJECTION_PATTERNS = listOf(
        Pattern.compile("(?i)\\b(sudo\\s+|chmod\\s+|chown\\s+|rm\\s+-rf|mkfs|dd\\s+if=)"),
        Pattern.compile("(?i)[;&|`$]\\s*(sh|bash|zsh|eval|exec|system|cmd|powershell)\\b"),
        Pattern.compile("(?i)\\b(curl|wget)\\s+https?://[^\\s]+\\s*\\|\\s*(sh|bash)"),
        Pattern.compile("(?i)\\b(drop\\s+table|drop\\s+database|truncate\\s+table|delete\\s+from\\s+users)\\b")
    )

    // Java / JNDI / Reflection exploits
    private val CODE_EXPLOIT_PATTERNS = listOf(
        Pattern.compile("(?i)\\$\\{jndi:(ldap|rmi|dns|corba|iiop)://"),
        Pattern.compile("(?i)\\b(class\\.forname|runtime\\.getruntime|processbuilder)\\b"),
        Pattern.compile("(?i)\\b(unsupportedoperationexception|throw\\s+new\\s+error)\\b")
    )

    // HTML / Script injection patterns
    private val SCRIPT_INJECTION_PATTERNS = listOf(
        Pattern.compile("(?i)<\\s*script[^>]*>.*?<\\s*/\\s*script\\s*>", Pattern.DOTALL),
        Pattern.compile("(?i)javascript\\s*:\\s*"),
        Pattern.compile("(?i)<\\s*(iframe|object|embed|applet)[^>]*>"),
        Pattern.compile("(?i)onload\\s*=|onerror\\s*=|onclick\\s*=")
    )

    // Disallowed package prefixes / characters
    private val INVALID_PACKAGE_CHARS = Pattern.compile("[^a-zA-Z0-9_.]")

    /**
     * Sanitize user voice / text input before passing to NLP or AI models
     */
    fun sanitizeInput(rawInput: String?): SanitizationResult {
        if (rawInput.isNullOrBlank()) {
            return SanitizationResult(
                isClean = true,
                sanitizedText = "",
                detectedThreats = emptyList(),
                riskScore = 0.0f,
                shouldBlock = false
            )
        }

        val threats = mutableListOf<String>()
        var riskScore = 0.0f
        var sanitized = rawInput.trim()

        // 1. Check Prompt Injection
        for (pattern in PROMPT_INJECTION_PATTERNS) {
            if (pattern.matcher(sanitized).find()) {
                threats.add("PROMPT_INJECTION")
                riskScore = maxOf(riskScore, 0.9f)
                sanitized = pattern.matcher(sanitized).replaceAll("[REMOVED_INJECTION]")
            }
        }

        // 2. Check Shell Injection
        for (pattern in SHELL_INJECTION_PATTERNS) {
            if (pattern.matcher(sanitized).find()) {
                threats.add("SHELL_INJECTION")
                riskScore = maxOf(riskScore, 0.95f)
                sanitized = pattern.matcher(sanitized).replaceAll("[REMOVED_SHELL_CMD]")
            }
        }

        // 3. Check JNDI / Code Exploits
        for (pattern in CODE_EXPLOIT_PATTERNS) {
            if (pattern.matcher(sanitized).find()) {
                threats.add("CODE_EXPLOIT_PAYLOAD")
                riskScore = maxOf(riskScore, 1.0f)
                sanitized = pattern.matcher(sanitized).replaceAll("[REMOVED_PAYLOAD]")
            }
        }

        // 4. Check HTML / Scripts
        for (pattern in SCRIPT_INJECTION_PATTERNS) {
            if (pattern.matcher(sanitized).find()) {
                threats.add("SCRIPT_INJECTION")
                riskScore = maxOf(riskScore, 0.85f)
                sanitized = pattern.matcher(sanitized).replaceAll("[REMOVED_TAG]")
            }
        }

        // 5. Redact PII (Passwords, OTPs, Card Numbers, Phone Numbers) using standardized TelemetryManager
        sanitized = TelemetryManager.redactPii(sanitized)

        val shouldBlock = riskScore >= 0.80f
        val isClean = threats.isEmpty()

        if (!isClean) {
            Log.w(TAG, "Input sanitization flagged threats: $threats, riskScore: $riskScore, blocked: $shouldBlock")
        }

        return SanitizationResult(
            isClean = isClean,
            sanitizedText = sanitized,
            detectedThreats = threats,
            riskScore = riskScore,
            shouldBlock = shouldBlock,
            reason = if (isClean) "Clean input" else "Threats detected: ${threats.joinToString(", ")}"
        )
    }

    /**
     * Strict Typed Validation of AI / LLM Structured Command Output
     * Ensures untrusted AI outputs are strictly constrained before execution planning.
     */
    fun validateAiCommandPlan(
        rawCommandType: String,
        rawTarget: String?,
        rawParameters: Map<String, Any?> = emptyMap(),
        reportedConfidence: Float = 0.0f
    ): CommandPlan {
        val validationErrors = mutableListOf<String>()

        // 1. Sanitize command type
        val commandTypeClean = rawCommandType.trim().uppercase(Locale.ROOT)
        val validCommands = setOf(
            "OPEN_APP", "LAUNCH_APP", "CLOSE_APP", "VOLUME_UP", "VOLUME_DOWN", "SET_VOLUME",
            "MUTE", "UNMUTE", "BRIGHTNESS_UP", "BRIGHTNESS_DOWN", "SET_BRIGHTNESS", "FLASHLIGHT",
            "WIFI_TOGGLE", "BLUETOOTH_TOGGLE", "CALL", "SEND_MESSAGE", "SEND_SMS", "ALARM",
            "TIMER", "TAKE_SCREENSHOT", "BACK", "HOME", "RECENTS", "NOTIFICATIONS", "QUICK_SETTINGS",
            "PLAY_MUSIC", "PAUSE_MUSIC", "NEXT_TRACK", "PREV_TRACK", "SEARCH_WEB", "OPEN_URL",
            "TIME_QUERY", "DATE_QUERY", "WEATHER_QUERY", "SYSTEM_INFO", "DELETE_ITEM", "PAYMENT", "CUSTOM"
        )

        if (!validCommands.contains(commandTypeClean)) {
            validationErrors.add("Unknown commandType: $rawCommandType")
        }

        // 2. Validate and sanitize target
        var targetClean = rawTarget?.trim()
        if (targetClean != null) {
            val targetSanitization = sanitizeInput(targetClean)
            if (!targetSanitization.isClean) {
                validationErrors.add("Malicious payload inside target: ${targetSanitization.detectedThreats}")
                targetClean = targetSanitization.sanitizedText
            }

            // If target represents a package name (contains dot), validate package format
            if (commandTypeClean == "OPEN_APP" && targetClean.contains(".")) {
                if (INVALID_PACKAGE_CHARS.matcher(targetClean).find() || targetClean.length > 256) {
                    validationErrors.add("Invalid package identifier characters in target: $targetClean")
                }
            }
        }

        // 3. Validate parameters map
        val sanitizedParams = mutableMapOf<String, Any?>()
        for ((key, value) in rawParameters) {
            val keySanitized = key.trim().filter { it.isLetterOrDigit() || it == '_' }
            if (value is String) {
                val paramSanitize = sanitizeInput(value)
                if (paramSanitize.shouldBlock) {
                    validationErrors.add("Malicious parameter value for key '$key': ${paramSanitize.detectedThreats}")
                }
                sanitizedParams[keySanitized] = paramSanitize.sanitizedText
            } else {
                sanitizedParams[keySanitized] = value
            }
        }

        // 4. Determine intrinsic risk level
        val riskLevel = when (commandTypeClean) {
            "PAYMENT", "DELETE_ITEM" -> RiskLevel.CRITICAL
            "CALL", "SEND_MESSAGE", "SEND_SMS" -> RiskLevel.HIGH
            "WIFI_TOGGLE", "BLUETOOTH_TOGGLE", "SET_VOLUME", "FLASHLIGHT" -> RiskLevel.MEDIUM
            else -> RiskLevel.LOW
        }

        // 5. Enforce confirmation policy for High/Critical risks (AI confidence cannot bypass safety)
        val requiresConfirmation = (riskLevel == RiskLevel.HIGH || riskLevel == RiskLevel.CRITICAL)

        val boundedConfidence = reportedConfidence.coerceIn(0.0f, 1.0f)
        val isValid = validationErrors.isEmpty()

        return CommandPlan(
            commandType = commandTypeClean,
            target = targetClean,
            parameters = sanitizedParams,
            confidence = boundedConfidence,
            riskLevel = riskLevel,
            requiresConfirmation = requiresConfirmation,
            isValid = isValid,
            validationErrors = validationErrors
        )
    }
}
