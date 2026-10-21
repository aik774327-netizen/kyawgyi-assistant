package com.example.engine

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * High-Performance, Zero-GC Circular Audio Ring Buffer Pool for sub-100ms latency speech processing.
 * Pre-allocates fixed ByteBuffers to avoid GC pauses during active continuous voice sessions.
 */
class AudioRingBufferPool(
    val sampleRate: Int = 16000,
    val channelConfig: Int = AudioFormat.CHANNEL_IN_MONO,
    val audioFormat: Int = AudioFormat.ENCODING_PCM_16BIT,
    val bufferCapacityFrames: Int = 16000 * 5 // 5 seconds buffer
) {
    private val bufferSizeBytes = bufferCapacityFrames * 2 // 16-bit PCM = 2 bytes/sample
    private val pcmCircularArray = ByteArray(bufferSizeBytes)

    @Volatile
    private var writeHead = 0

    @Volatile
    private var readHead = 0

    @Volatile
    private var availableBytes = 0

    private val isRecording = AtomicBoolean(false)
    private var audioRecord: AudioRecord? = null

    /**
     * Write PCM audio frame into the circular pool without heap allocation.
     */
    @Synchronized
    fun writePCM(data: ByteArray, offset: Int, length: Int) {
        for (i in 0 until length) {
            pcmCircularArray[writeHead] = data[offset + i]
            writeHead = (writeHead + 1) % bufferSizeBytes
            if (availableBytes < bufferSizeBytes) {
                availableBytes++
            } else {
                // Buffer overrun: advance read head
                readHead = (readHead + 1) % bufferSizeBytes
            }
        }
    }

    /**
     * Read buffered PCM frame without object instantiation.
     */
    @Synchronized
    fun readPCM(outBuffer: ByteArray, maxReadLength: Int): Int {
        val bytesToRead = minOf(maxReadLength, availableBytes)
        for (i in 0 until bytesToRead) {
            outBuffer[i] = pcmCircularArray[readHead]
            readHead = (readHead + 1) % bufferSizeBytes
        }
        availableBytes -= bytesToRead
        return bytesToRead
    }

    /**
     * Low-latency direct AudioRecord stream initializer.
     */
    @androidx.annotation.RequiresPermission(android.Manifest.permission.RECORD_AUDIO)
    suspend fun startDirectAudioStream(onFrameReady: (ByteArray, Int) -> Unit) = withContext(Dispatchers.IO) {
        val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        val chunkSize = minOf(1024, minBufferSize)
        val tempReadChunk = ByteArray(chunkSize)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                sampleRate,
                channelConfig,
                audioFormat,
                maxOf(minBufferSize, chunkSize * 4)
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                return@withContext
            }

            audioRecord?.startRecording()
            isRecording.set(true)

            while (isRecording.get()) {
                val readCount = audioRecord?.read(tempReadChunk, 0, tempReadChunk.size) ?: -1
                if (readCount > 0) {
                    writePCM(tempReadChunk, 0, readCount)
                    onFrameReady(tempReadChunk, readCount)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            stopAudioStream()
        }
    }

    fun stopAudioStream() {
        isRecording.set(false)
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (ignored: Exception) {}
        audioRecord = null
    }

    @Synchronized
    fun clearBuffer() {
        writeHead = 0
        readHead = 0
        availableBytes = 0
    }
}
