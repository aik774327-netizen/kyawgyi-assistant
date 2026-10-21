package com.kyawgyi.voice

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Snapshot of [DualSttFailover] routing and failover telemetry.
 */
data class DualSttFailoverState(
    val primaryEngine: SttEngineType = SttEngineType.SHERPA_ONNX,
    val secondaryEngine: SttEngineType = SttEngineType.VOSK,
    val activeEngine: SttEngineType = SttEngineType.SHERPA_ONNX,
    val failoverSwitchCount: Int = 0,
    val consecutiveEmptySpeechResults: Int = 0,
    val lastFailoverReason: String? = null,
    val lastFailoverTimestampMs: Long = 0L
)

/**
 * DualSttFailover (Requirements 12 — Automatic Dual-Engine STT Failover Coordinator)
 *
 * Coordinates seamless runtime failover across:
 * - Tier 1: [SttEngineType.SHERPA_ONNX] (Primary Offline Neural Zipformer)
 * - Tier 2: [SttEngineType.VOSK] (Secondary Offline Kaldi Recognizer)
 * - Tier 3: [SttEngineType.SYSTEM_ON_DEVICE] (OS SpeechRecognizer Fallback)
 *
 * Consults [FailureMemory] and monitors consecutive empty/failed recognitions during active
 * speech frames to switch engines without losing user utterances.
 */
class DualSttFailover(
    private val modelManager: ModelManager,
    val failureMemory: FailureMemory = FailureMemory(),
    private val maxConsecutiveEmptyUtterances: Int = 2
) {

    companion object {
        private const val TAG = "DualSttFailover"
    }

    private val _state = MutableStateFlow(DualSttFailoverState())
    val state: StateFlow<DualSttFailoverState> = _state.asStateFlow()

    /**
     * Resolves the best healthy STT engine given disk model availability and [FailureMemory] status.
     */
    @Synchronized
    fun resolveBestEngine(
        preferred: SttEngineType = _state.value.activeEngine,
        isSystemAvailable: Boolean = true,
        nowMs: Long = System.currentTimeMillis()
    ): SttEngineType {
        val orderedCandidates = buildList {
            add(preferred)
            if (preferred != SttEngineType.SHERPA_ONNX) add(SttEngineType.SHERPA_ONNX)
            if (preferred != SttEngineType.VOSK) add(SttEngineType.VOSK)
            if (preferred != SttEngineType.SYSTEM_ON_DEVICE) add(SttEngineType.SYSTEM_ON_DEVICE)
        }

        for (candidate in orderedCandidates) {
            val isInstalled = when (candidate) {
                SttEngineType.SHERPA_ONNX -> modelManager.isSherpaOnnxModelAvailable()
                SttEngineType.VOSK -> modelManager.isVoskModelAvailable()
                SttEngineType.SYSTEM_ON_DEVICE -> isSystemAvailable
            }
            if (isInstalled && failureMemory.isEngineHealthy(candidate.name, nowMs)) {
                _state.update { it.copy(activeEngine = candidate) }
                return candidate
            }
        }

        // If offline model binaries aren't installed yet, still allow preferred model engine in hybrid/stream mode
        // if it hasn't been quarantined by runtime failures.
        if (failureMemory.isEngineHealthy(preferred.name, nowMs)) {
            return preferred
        }

        val fallback = failureMemory.filterHealthyEngines(orderedCandidates, nowMs).first()
        _state.update { it.copy(activeEngine = fallback) }
        return fallback
    }

    /**
     * Records a successful utterance recognition and clears any consecutive empty-speech counter.
     */
    @Synchronized
    fun onUtteranceSucceeded(engine: SttEngineType) {
        failureMemory.recordSuccess(engine.name)
        _state.update { current ->
            current.copy(
                activeEngine = engine,
                consecutiveEmptySpeechResults = 0
            )
        }
    }

    /**
     * Records an engine error or timeout and returns the next healthy failover [SttEngineType].
     */
    @Synchronized
    fun onEngineFailure(
        failedEngine: SttEngineType,
        reason: String,
        isSystemAvailable: Boolean = true,
        nowMs: Long = System.currentTimeMillis()
    ): SttEngineType {
        failureMemory.recordFailure(failedEngine.name, reason, nowMs)
        val nextEngine = selectNextFallback(failedEngine, isSystemAvailable, nowMs)
        val switched = nextEngine != failedEngine
        Log.w(TAG, "STT Engine $failedEngine failed ($reason) -> Failover to $nextEngine")

        _state.update { current ->
            current.copy(
                activeEngine = nextEngine,
                failoverSwitchCount = if (switched) current.failoverSwitchCount + 1 else current.failoverSwitchCount,
                lastFailoverReason = "$failedEngine: $reason -> $nextEngine",
                lastFailoverTimestampMs = nowMs
            )
        }
        return nextEngine
    }

    /**
     * Called when VAD detected clear speech frames, but the active STT engine emitted a blank final transcript.
     */
    @Synchronized
    fun onEmptyTranscriptAfterSpeech(
        activeEngine: SttEngineType,
        isSystemAvailable: Boolean = true,
        nowMs: Long = System.currentTimeMillis()
    ): SttEngineType {
        val emptyCount = _state.value.consecutiveEmptySpeechResults + 1
        return if (emptyCount >= maxConsecutiveEmptyUtterances) {
            _state.update { it.copy(consecutiveEmptySpeechResults = 0) }
            onEngineFailure(
                failedEngine = activeEngine,
                reason = "Consecutive empty transcripts ($emptyCount) on active speech",
                isSystemAvailable = isSystemAvailable,
                nowMs = nowMs
            )
        } else {
            _state.update { it.copy(consecutiveEmptySpeechResults = emptyCount) }
            activeEngine
        }
    }

    private fun selectNextFallback(
        failedEngine: SttEngineType,
        isSystemAvailable: Boolean,
        nowMs: Long
    ): SttEngineType {
        val chain = when (failedEngine) {
            SttEngineType.SHERPA_ONNX -> listOf(SttEngineType.VOSK, SttEngineType.SYSTEM_ON_DEVICE)
            SttEngineType.VOSK -> listOf(SttEngineType.SHERPA_ONNX, SttEngineType.SYSTEM_ON_DEVICE)
            SttEngineType.SYSTEM_ON_DEVICE -> listOf(SttEngineType.SHERPA_ONNX, SttEngineType.VOSK)
        }
        for (candidate in chain) {
            if (!failureMemory.isEngineHealthy(candidate.name, nowMs)) continue
            val usable = when (candidate) {
                SttEngineType.SHERPA_ONNX -> modelManager.isSherpaOnnxModelAvailable()
                SttEngineType.VOSK -> modelManager.isVoskModelAvailable()
                SttEngineType.SYSTEM_ON_DEVICE -> isSystemAvailable
            }
            if (usable) return candidate
        }
        return failureMemory.filterHealthyEngines(chain, nowMs).firstOrNull() ?: SttEngineType.SYSTEM_ON_DEVICE
    }
}
