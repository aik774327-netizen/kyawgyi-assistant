package com.kyawgyi.voice

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * PreRollPcmRingBuffer (Requirements 9 — Circular Pre-Roll Audio Lookback Buffer)
 *
 * Maintains a thread-safe circular ring buffer of recent 16kHz 16-bit Little-Endian Mono PCM audio
 * (default 500ms = 8,000 samples = 16,000 bytes) captured BEFORE Wake-Word or VAD speech onset triggers.
 *
 * When VAD transitions from SILENCE -> SPEECH, [drainPreRollBytes] or [drainAndPrepend] flushes
 * the lookback frames so the initial Burmese consonant/syllable (e.g., "ဖုန်း...", "ကေဘီဇက်...")
 * is never clipped.
 */
class PreRollPcmRingBuffer(
    val sampleRateHz: Int = 16000,
    val preRollDurationMs: Int = 500
) {
    val capacityBytes: Int = ((sampleRateHz * preRollDurationMs) / 1000) * 2

    private val ring = ByteArray(capacityBytes)
    private var writePos = 0
    private var storedBytes = 0
    private var totalFramesWritten = 0L

    /**
     * Writes a 16-bit PCM chunk into the circular pre-roll buffer.
     * Ensures 2-byte sample alignment.
     */
    @Synchronized
    fun write(pcmBytes: ByteArray) {
        val alignedLen = pcmBytes.size and 0x7FFFFFFE // keep even byte count (16-bit alignment)
        if (alignedLen <= 0) return

        if (alignedLen >= capacityBytes) {
            // Input chunk is larger than the entire ring buffer: keep only the tail capacityBytes
            val startOffset = alignedLen - capacityBytes
            System.arraycopy(pcmBytes, startOffset, ring, 0, capacityBytes)
            writePos = 0
            storedBytes = capacityBytes
            totalFramesWritten++
            return
        }

        val firstCopy = minOf(alignedLen, capacityBytes - writePos)
        System.arraycopy(pcmBytes, 0, ring, writePos, firstCopy)
        val remaining = alignedLen - firstCopy
        if (remaining > 0) {
            System.arraycopy(pcmBytes, firstCopy, ring, 0, remaining)
            writePos = remaining
        } else {
            writePos = (writePos + firstCopy) % capacityBytes
        }

        storedBytes = minOf(capacityBytes, storedBytes + alignedLen)
        totalFramesWritten++
    }

    /**
     * Returns a chronological copy of the buffered pre-roll PCM bytes without clearing the ring.
     */
    @Synchronized
    fun snapshotPreRollBytes(): ByteArray {
        if (storedBytes == 0) return ByteArray(0)
        val out = ByteArray(storedBytes)
        if (storedBytes < capacityBytes) {
            System.arraycopy(ring, 0, out, 0, storedBytes)
        } else {
            val tailLen = capacityBytes - writePos
            System.arraycopy(ring, writePos, out, 0, tailLen)
            if (writePos > 0) {
                System.arraycopy(ring, 0, out, tailLen, writePos)
            }
        }
        return out
    }

    /**
     * Drains and clears the pre-roll buffer, returning the chronological 16-bit PCM bytes.
     */
    @Synchronized
    fun drainPreRollBytes(): ByteArray {
        val out = snapshotPreRollBytes()
        clear()
        return out
    }

    /**
     * Drains the pre-roll buffer and prepends it to [onsetFramePcm] in a single contiguous PCM array.
     */
    @Synchronized
    fun drainAndPrepend(onsetFramePcm: ByteArray): ByteArray {
        val preRoll = drainPreRollBytes()
        if (preRoll.isEmpty()) return onsetFramePcm
        val combined = ByteArray(preRoll.size + onsetFramePcm.size)
        System.arraycopy(preRoll, 0, combined, 0, preRoll.size)
        System.arraycopy(onsetFramePcm, 0, combined, preRoll.size, onsetFramePcm.size)
        return combined
    }

    /**
     * Returns the buffered pre-roll audio as normalized [-1.0f, 1.0f] floats for Sherpa-ONNX.
     */
    @Synchronized
    fun snapshotPreRollFloats(): FloatArray {
        val bytes = snapshotPreRollBytes()
        val samples = bytes.size / 2
        if (samples == 0) return FloatArray(0)
        val shorts = ShortArray(samples)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)
        return FloatArray(samples) { i -> (shorts[i] / 32768.0f).coerceIn(-1.0f, 1.0f) }
    }

    @Synchronized
    fun getBufferedDurationMs(): Int {
        val samples = storedBytes / 2
        return (samples * 1000) / sampleRateHz
    }

    @Synchronized
    fun getStoredBytesCount(): Int = storedBytes

    @Synchronized
    fun clear() {
        writePos = 0
        storedBytes = 0
    }
}
