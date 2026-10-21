package com.kyawgyi.voice

import android.content.Context
import android.util.Log
import com.example.audio.WakeWordSoundCueManager
import com.example.nlu.BurmeseNluPreprocessor
import com.kyawgyi.assistant.state.AssistantState
import com.kyawgyi.assistant.state.AssistantStateStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/**
 * KyawGyiWakeWordEngine (Layer 1 & Layer 2 Offline Keyword Spotting - KWS — Item 25 Hardened)
 *
 * Hands-Free Wake Word detection engine for "ကျော်ကြီး" / "ဟယ်လို ကျော်ကြီး":
 * 1. Recognizes canonical and phonetic ASR variations of the wake phrase with explicit confidence scoring.
 * 2. Tracks reliability metrics: confidence, falseAcceptRejectedCount, falseRejectCount,
 *    cooldownRejectedCount, duplicateDetectionCount, ttsSelfTriggerBlockedCount, and noiseEnvironment.
 * 3. Blocks TTS self-triggering when AudioFocusAndAecManager reports active speech or echo hangover.
 * 4. Plays low-latency direct PCM earcon chime via WakeWordSoundCueManager upon verified detection.
 */
class KyawGyiWakeWordEngine(
    private val context: Context,
    private val soundCueManager: WakeWordSoundCueManager = WakeWordSoundCueManager.getInstance(context),
    private val stateStore: AssistantStateStore = AssistantStateStore.getInstance(),
    var cooldownMs: Long = 1500L
) {

    companion object {
        private const val TAG = "KyawGyiWakeWordEngine"
        const val MIN_WAKE_CONFIDENCE_THRESHOLD = 0.78f

        private val WAKE_PHRASES = listOf(
            "ဟယ်လို ကျော်ကြီး",
            "ဟယ်လိုကျော်ကြီး",
            "ဟေး ကျော်ကြီး",
            "ဟေးကျော်ကြီး",
            "အစ်ကို ကျော်ကြီး",
            "အစ်ကိုကျော်ကြီး",
            "ကိုကျော်ကြီး",
            "ကျော်ကြီးရေ",
            "ကျော်ကြီးခင်ဗျာ",
            "ကျော်ကြီးရှင့်",
            "ကျော်ကြီး",
            "ကျော်ကြီ",
            "ကျောကြီး",
            "ကြော်ကြီး",
            "ချော်ကြီး",
            "ဂျော်ကြီး",
            "hello kyaw gyi",
            "hello kyawgyi",
            "hey kyaw gyi",
            "hey kyawgyi",
            "kyaw gyi",
            "kyawgyi"
        )

        @Volatile
        private var instance: KyawGyiWakeWordEngine? = null

        fun getInstance(context: Context): KyawGyiWakeWordEngine {
            return instance ?: synchronized(this) {
                instance ?: KyawGyiWakeWordEngine(context.applicationContext).also { instance = it }
            }
        }
    }

    data class WakeDetectionResult(
        val isDetected: Boolean,
        val matchedWakePhrase: String = "",
        val trailingCommand: String = "",
        val confidence: Float = 0.0f,
        val blockedByTtsEcho: Boolean = false,
        val blockedByCooldown: Boolean = false
    )

    data class WakeWordReliabilityTelemetry(
        val totalEvaluations: Int,
        val validDetectionsCount: Int,
        val falseAcceptRejectedCount: Int,
        val cooldownRejectedCount: Int,
        val duplicateDetectionCount: Int,
        val ttsSelfTriggerBlockedCount: Int,
        val lastConfidence: Float,
        val noiseEnvironment: String
    )

    private val _isWakeListeningEnabled = MutableStateFlow(true)
    val isWakeListeningEnabled: StateFlow<Boolean> = _isWakeListeningEnabled.asStateFlow()

    @Volatile
    private var lastTriggerTimestamp: Long = 0L

    @Volatile
    private var lastConfidenceScore: Float = 0f

    @Volatile
    private var currentNoiseEnvironment: String = "QUIET_INDOOR"

    private val totalEvaluations = AtomicInteger(0)
    private val validDetections = AtomicInteger(0)
    private val falseAcceptRejections = AtomicInteger(0)
    private val cooldownRejections = AtomicInteger(0)
    private val duplicateDetections = AtomicInteger(0)
    private val ttsSelfTriggerBlocks = AtomicInteger(0)

    var onWakeWordDetected: ((WakeDetectionResult) -> Unit)? = null

    fun setWakeListeningEnabled(enabled: Boolean) {
        _isWakeListeningEnabled.value = enabled
        Log.i(TAG, "Hands-Free Wake Word Engine enabled: $enabled")
    }

    fun setNoiseEnvironmentProfile(profile: String) {
        currentNoiseEnvironment = profile
    }

    fun resetCooldown() {
        lastTriggerTimestamp = 0L
    }

    fun getReliabilityTelemetry(): WakeWordReliabilityTelemetry {
        return WakeWordReliabilityTelemetry(
            totalEvaluations = totalEvaluations.get(),
            validDetectionsCount = validDetections.get(),
            falseAcceptRejectedCount = falseAcceptRejections.get(),
            cooldownRejectedCount = cooldownRejections.get(),
            duplicateDetectionCount = duplicateDetections.get(),
            ttsSelfTriggerBlockedCount = ttsSelfTriggerBlocks.get(),
            lastConfidence = lastConfidenceScore,
            noiseEnvironment = currentNoiseEnvironment
        )
    }

    /**
     * Evaluates a raw or partial utterance for the "ကျော်ကြီး" wake word with confidence scoring.
     */
    fun evaluateUtterance(rawText: String): WakeDetectionResult {
        totalEvaluations.incrementAndGet()
        if (rawText.isBlank()) return WakeDetectionResult(false, confidence = 0.0f)

        val normalized = BurmeseNluPreprocessor.normalize(rawText)
        val lower = normalized.lowercase(Locale.ROOT).trim()

        // 1. Exact / prefix match against known canonical wake phrases (Confidence = 0.96 - 1.0)
        for (phrase in WAKE_PHRASES) {
            if (lower.startsWith(phrase)) {
                val remainder = normalized.substring(phrase.length)
                    .replaceFirst(Regex("^[\\s,၊။!?:]+"), "")
                    .trim()
                val conf = if (phrase.contains("ကျော်ကြီး") || phrase.contains("kyaw")) 0.98f else 0.90f
                lastConfidenceScore = conf
                return WakeDetectionResult(
                    isDetected = true,
                    matchedWakePhrase = phrase,
                    trailingCommand = remainder,
                    confidence = conf
                )
            }
        }

        // 2. Constrained phonetic match on the first token (Requires confidence >= MIN_WAKE_CONFIDENCE_THRESHOLD)
        val tokens = lower.split(Regex("\\s+"))
        if (tokens.isNotEmpty()) {
            val firstToken = tokens[0]
            val fuzzyConf = computeFuzzyWakeConfidence(firstToken)
            lastConfidenceScore = fuzzyConf
            if (fuzzyConf >= MIN_WAKE_CONFIDENCE_THRESHOLD) {
                val remainder = tokens.drop(1).joinToString(" ").trim()
                return WakeDetectionResult(
                    isDetected = true,
                    matchedWakePhrase = firstToken,
                    trailingCommand = remainder,
                    confidence = fuzzyConf
                )
            } else if (fuzzyConf > 0.40f) {
                falseAcceptRejections.incrementAndGet()
            }
        }

        return WakeDetectionResult(false, confidence = 0.0f)
    }

    /**
     * Processes an incoming transcript from STT:
     * - Blocks TTS self-trigger if AudioFocusAndAecManager reports active TTS or echo hangover
     * - Enforces cooldown & duplicate suppression
     * - Plays Earcon audio chime and transitions canonical FSM state
     */
    @Synchronized
    fun processTranscriptAndTrigger(
        transcript: String,
        playEarcon: Boolean = true
    ): WakeDetectionResult {
        if (!_isWakeListeningEnabled.value) {
            return WakeDetectionResult(false)
        }

        // Item 25: TTS Self-Trigger Prevention
        val aecManager = AudioFocusAndAecManager.getInstance(context)
        if (aecManager.isSpeaking || aecManager.isEchoHangoverActive) {
            ttsSelfTriggerBlocks.incrementAndGet()
            Log.w(TAG, "Blocked wake word evaluation during active TTS playback or echo hangover.")
            return WakeDetectionResult(
                isDetected = false,
                blockedByTtsEcho = true
            )
        }

        val detection = evaluateUtterance(transcript)
        if (!detection.isDetected) {
            return detection
        }

        val now = System.currentTimeMillis()
        if (cooldownMs > 0L && (now - lastTriggerTimestamp) < cooldownMs) {
            cooldownRejections.incrementAndGet()
            duplicateDetections.incrementAndGet()
            Log.d(TAG, "Wake word detected within cooldown window (${now - lastTriggerTimestamp}ms). Ignoring duplicate.")
            return detection.copy(blockedByCooldown = true)
        }

        lastTriggerTimestamp = now
        validDetections.incrementAndGet()
        Log.i(TAG, "Wake Word 'ကျော်ကြီး' DETECTED (conf=${detection.confidence})! Matched: \"${detection.matchedWakePhrase}\", Trailing: \"${detection.trailingCommand}\"")

        // Transition to WAKE_DETECTED first
        stateStore.transitionTo(AssistantState.WAKE_DETECTED)

        if (playEarcon) {
            soundCueManager.playWakeWordDetectedCue()
        }

        if (detection.trailingCommand.isBlank()) {
            stateStore.transitionTo(AssistantState.LISTENING)
        } else {
            stateStore.transitionTo(AssistantState.UNDERSTANDING)
        }

        onWakeWordDetected?.invoke(detection)
        return detection
    }

    private fun computeFuzzyWakeConfidence(token: String): Float {
        if (token.length < 4 || token.length > 14) return 0.0f
        val canonical = "ကျော်ကြီး"
        val dist = levenshteinDistance(token, canonical)
        return when {
            dist == 0 -> 1.0f
            dist == 1 -> 0.88f
            dist == 2 && (token.startsWith("ကျော") || token.startsWith("ကြော") || token.endsWith("ကြီး")) -> 0.80f
            dist <= 3 -> 0.55f // Below MIN_WAKE_CONFIDENCE_THRESHOLD (rejected as false accept)
            else -> 0.0f
        }
    }

    private fun levenshteinDistance(lhs: CharSequence, rhs: CharSequence): Int {
        val lhsLen = lhs.length
        val rhsLen = rhs.length
        var cost = IntArray(lhsLen + 1) { it }
        var newCost = IntArray(lhsLen + 1)

        for (i in 1..rhsLen) {
            newCost[0] = i
            for (j in 1..lhsLen) {
                val match = if (lhs[j - 1] == rhs[i - 1]) 0 else 1
                val costReplace = cost[j - 1] + match
                val costInsert = cost[j] + 1
                val costDelete = newCost[j - 1] + 1
                newCost[j] = minOf(costInsert, costDelete, costReplace)
            }
            val swap = cost
            cost = newCost
            newCost = swap
        }
        return cost[lhsLen]
    }
}
