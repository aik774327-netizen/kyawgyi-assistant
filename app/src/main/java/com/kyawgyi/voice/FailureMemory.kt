package com.kyawgyi.voice

/**
 * Per-engine circuit breaker health record tracked by [FailureMemory].
 */
data class EngineFailureRecord(
    val engineId: String,
    val consecutiveFailures: Int = 0,
    val totalFailures: Int = 0,
    val totalSuccesses: Int = 0,
    val lastFailureReason: String? = null,
    val lastFailureTimestampMs: Long = 0L,
    val quarantinedUntilTimestampMs: Long = 0L
) {
    fun isQuarantined(nowMs: Long = System.currentTimeMillis()): Boolean =
        quarantinedUntilTimestampMs > nowMs
}

/**
 * FailureMemory (Requirements 12 — Engine Circuit-Breaker & Exponential Backoff Quarantine)
 *
 * Remembers transient and persistent failures across STT and TTS engines (`SHERPA_ONNX`,
 * `VOSK`, `SYSTEM_ON_DEVICE`, `F5_ONNX_TTS`, `ANDROID_SYSTEM_TTS`).
 *
 * When an engine fails [quarantineThreshold] consecutive times, it is temporarily quarantined
 * with exponential backoff (`baseCooldownMs * 2^(failures - threshold)`) so subsequent utterances
 * immediately route to a healthy fallback engine without repeated timeouts.
 */
class FailureMemory(
    private val quarantineThreshold: Int = 2,
    private val baseCooldownMs: Long = 15_000L,
    private val maxCooldownMs: Long = 180_000L
) {

    private val records = LinkedHashMap<String, EngineFailureRecord>()

    @Synchronized
    fun recordSuccess(engineId: String): EngineFailureRecord {
        val prev = records[engineId] ?: EngineFailureRecord(engineId = engineId)
        val updated = prev.copy(
            consecutiveFailures = 0,
            totalSuccesses = prev.totalSuccesses + 1,
            quarantinedUntilTimestampMs = 0L
        )
        records[engineId] = updated
        return updated
    }

    @Synchronized
    fun recordFailure(
        engineId: String,
        reason: String,
        nowMs: Long = System.currentTimeMillis()
    ): EngineFailureRecord {
        val prev = records[engineId] ?: EngineFailureRecord(engineId = engineId)
        val newConsecutive = prev.consecutiveFailures + 1
        val cooldown = if (newConsecutive >= quarantineThreshold) {
            val shift = (newConsecutive - quarantineThreshold).coerceIn(0, 6)
            (baseCooldownMs * (1L shl shift)).coerceAtMost(maxCooldownMs)
        } else {
            0L
        }

        val updated = prev.copy(
            consecutiveFailures = newConsecutive,
            totalFailures = prev.totalFailures + 1,
            lastFailureReason = reason,
            lastFailureTimestampMs = nowMs,
            quarantinedUntilTimestampMs = if (cooldown > 0L) nowMs + cooldown else 0L
        )
        records[engineId] = updated
        return updated
    }

    @Synchronized
    fun isEngineHealthy(engineId: String, nowMs: Long = System.currentTimeMillis()): Boolean {
        val record = records[engineId] ?: return true
        return !record.isQuarantined(nowMs)
    }

    @Synchronized
    fun filterHealthyEngines(
        candidates: List<SttEngineType>,
        nowMs: Long = System.currentTimeMillis()
    ): List<SttEngineType> {
        val healthy = candidates.filter { isEngineHealthy(it.name, nowMs) }
        return healthy.ifEmpty { listOf(candidates.lastOrNull() ?: SttEngineType.SYSTEM_ON_DEVICE) }
    }

    @Synchronized
    fun getRecord(engineId: String): EngineFailureRecord =
        records[engineId] ?: EngineFailureRecord(engineId = engineId)

    @Synchronized
    fun getAllRecords(): List<EngineFailureRecord> = records.values.toList()

    @Synchronized
    fun clearQuarantine(engineId: String? = null) {
        if (engineId == null) {
            records.clear()
        } else {
            records.remove(engineId)
        }
    }
}
