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
    val primaryEngine: SttEngineType = SttEngineType.SYSTEM_ON_DEVICE,
    val secondaryEngine: SttEngineType = SttEngineType.SYSTEM_ON_DEVICE,
    val activeEngine: SttEngineType = SttEngineType.SYSTEM_ON_DEVICE,
    val failoverSwitchCount: Int = 0,
    val consecutiveEmptySpeechResults: Int = 0,
    val lastFailoverReason: String? = null,
    val lastFailoverTimestampMs: Long = 0L
)

/**
 * DualSttFailover (Requirements 12 — Automatic Dual-Engine STT Failover Coordinator)
 *
 * Tracks STT engine health/failure state. Only [SttEngineType.SYSTEM_ON_DEVICE]
 * exists now — the Sherpa-ONNX and Vosk tiers were heuristic fakes and were
 * removed in the 2026-10-22 fake-data removal pass, so "failover" resolves to
 * the system recognizer; the FailureMemory bookkeeping is retained.
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
        // SYSTEM_ON_DEVICE is the only engine type that exists.
        val orderedCandidates = listOf(SttEngineType.SYSTEM_ON_DEVICE)

        for (candidate in orderedCandidates) {
            val isInstalled = isSystemAvailable
            if (isInstalled && failureMemory.isEngineHealthy(candidate.name, nowMs)) {
                _state.update { it.copy(activeEngine = candidate) }
                return candidate
            }
        }

        if (failureMemory.isEngineHealthy(SttEngineType.SYSTEM_ON_DEVICE.name, nowMs)) {
            return SttEngineType.SYSTEM_ON_DEVICE
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
        // No other engine exists to fail over to; the system recognizer is
        // always the resolution. Failure bookkeeping above still records the
        // failure honestly.
        @Suppress("UNUSED_EXPRESSION")
        run { failedEngine; isSystemAvailable; nowMs }
        return SttEngineType.SYSTEM_ON_DEVICE
    }
}
