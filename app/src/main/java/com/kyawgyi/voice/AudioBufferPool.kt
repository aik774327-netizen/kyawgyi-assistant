package com.kyawgyi.voice

import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicLong

/**
 * Snapshot of [AudioBufferPool] memory reuse metrics for real-time diagnostics.
 */
data class AudioBufferPoolMetrics(
    val bytePoolHits: Long,
    val bytePoolMisses: Long,
    val floatPoolHits: Long,
    val floatPoolMisses: Long,
    val pooledByteBuffersCount: Int,
    val pooledFloatBuffersCount: Int
) {
    val hitRatioPercent: Float
        get() {
            val totalHits = bytePoolHits + floatPoolHits
            val totalReq = totalHits + bytePoolMisses + floatPoolMisses
            return if (totalReq > 0L) (totalHits.toFloat() * 100f) / totalReq.toFloat() else 100f
        }
}

/**
 * AudioBufferPool (Requirements 12 — Zero-Jitter PCM & Float Waveform Object Pool)
 *
 * Eliminates garbage collection (GC) allocation pressure during continuous 16kHz 16-bit
 * audio streaming (25–50 frames/sec) by pooling fixed-size [ByteArray] and [FloatArray]
 * frame buffers.
 */
class AudioBufferPool(
    val defaultByteBufferSize: Int = AudioCaptureModule.DEFAULT_FRAME_BYTES,
    val defaultFloatBufferSize: Int = AudioCaptureModule.DEFAULT_FRAME_BYTES / 2,
    private val maxPoolCapacity: Int = 32
) {

    private val byteQueue = ArrayDeque<ByteArray>(maxPoolCapacity)
    private val floatQueue = ArrayDeque<FloatArray>(maxPoolCapacity)

    private val byteHits = AtomicLong(0L)
    private val byteMisses = AtomicLong(0L)
    private val floatHits = AtomicLong(0L)
    private val floatMisses = AtomicLong(0L)

    init {
        // Pre-warm 8 buffers of each type to avoid cold-start allocations on speech onset
        repeat(8.coerceAtMost(maxPoolCapacity)) {
            byteQueue.addLast(ByteArray(defaultByteBufferSize))
            floatQueue.addLast(FloatArray(defaultFloatBufferSize))
        }
    }

    /**
     * Acquires a zero-initialized [ByteArray] of [size] from the pool, or allocates a new one on miss.
     */
    @Synchronized
    fun acquireByteBuffer(size: Int = defaultByteBufferSize): ByteArray {
        if (size == defaultByteBufferSize && byteQueue.isNotEmpty()) {
            byteHits.incrementAndGet()
            val buf = byteQueue.removeFirst()
            buf.fill(0)
            return buf
        }
        byteMisses.incrementAndGet()
        return ByteArray(size)
    }

    /**
     * Returns a [ByteArray] back to the pool for reuse on future audio frames.
     */
    @Synchronized
    fun releaseByteBuffer(buffer: ByteArray?) {
        if (buffer == null || buffer.size != defaultByteBufferSize) return
        if (byteQueue.size < maxPoolCapacity) {
            byteQueue.addLast(buffer)
        }
    }

    /**
     * Acquires a zero-initialized [FloatArray] of [size] from the pool, or allocates a new one on miss.
     */
    @Synchronized
    fun acquireFloatBuffer(size: Int = defaultFloatBufferSize): FloatArray {
        if (size == defaultFloatBufferSize && floatQueue.isNotEmpty()) {
            floatHits.incrementAndGet()
            val buf = floatQueue.removeFirst()
            buf.fill(0f)
            return buf
        }
        floatMisses.incrementAndGet()
        return FloatArray(size)
    }

    /**
     * Returns a [FloatArray] back to the pool for reuse on future audio frames.
     */
    @Synchronized
    fun releaseFloatBuffer(buffer: FloatArray?) {
        if (buffer == null || buffer.size != defaultFloatBufferSize) return
        if (floatQueue.size < maxPoolCapacity) {
            floatQueue.addLast(buffer)
        }
    }

    @Synchronized
    fun getMetrics(): AudioBufferPoolMetrics = AudioBufferPoolMetrics(
        bytePoolHits = byteHits.get(),
        bytePoolMisses = byteMisses.get(),
        floatPoolHits = floatHits.get(),
        floatPoolMisses = floatMisses.get(),
        pooledByteBuffersCount = byteQueue.size,
        pooledFloatBuffersCount = floatQueue.size
    )

    @Synchronized
    fun clear() {
        byteQueue.clear()
        floatQueue.clear()
    }
}
