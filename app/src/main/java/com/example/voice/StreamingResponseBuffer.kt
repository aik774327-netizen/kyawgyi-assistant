package com.example.voice

import android.util.Log
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean

/**
 * StreamingResponseBuffer (Layer 2 & 6 - Resilient Audio/Text Streaming Buffer)
 *
 * Implements low-latency streaming pipeline between Gemini LLM / Sherpa-ONNX and TTS:
 * 1. Text Chunk Ingestion & Sentence Boundary Segmentation:
 *    - Ingests streaming delta text tokens from Gemini Online API.
 *    - Automatically slices text at natural Myanmar sentence/clause delimiters ("။", "၊", "\n").
 *    - Emits segmented clauses immediately into TTS synthesis queue for zero-wait speech playback.
 * 2. Audio PCM Ring Buffer:
 *    - Buffers synthesized PCM audio chunks with backpressure protection (DROP_OLDEST on extreme buffer overflow).
 * 3. Lifecycle Completion Handling:
 *    - Safely flushes remaining partial buffer on end-of-stream.
 */
class StreamingResponseBuffer(
    private val maxAudioBufferCapacity: Int = 128
) {

    companion object {
        private const val TAG = "StreamingResponseBuffer"

        // Natural Myanmar and Latin sentence/clause boundaries
        private val SENTENCE_DELIMITERS = Regex("([။၊\n.!?]+)")
    }

    private val mutex = Mutex()
    private val textAccumulator = StringBuilder()
    private val isStreamCompleted = AtomicBoolean(false)

    // Channel for ready-to-synthesize sentence segments
    private val sentenceChannel = Channel<String>(capacity = 32)
    val sentenceFlow: Flow<String> = sentenceChannel.receiveAsFlow()

    // SharedFlow for synthesized PCM audio streaming chunks
    private val _audioChunkFlow = MutableSharedFlow<ByteArray>(
        replay = 0,
        extraBufferCapacity = maxAudioBufferCapacity,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val audioChunkFlow: Flow<ByteArray> = _audioChunkFlow.asSharedFlow()

    /**
     * Ingests a streaming text delta token from Gemini or Cloud LLM.
     */
    suspend fun appendTextChunk(chunk: String) = mutex.withLock {
        if (isStreamCompleted.get() || chunk.isEmpty()) return@withLock

        textAccumulator.append(chunk)
        val currentText = textAccumulator.toString()

        val delimiterMatcher = SENTENCE_DELIMITERS.find(currentText)
        if (delimiterMatcher != null) {
            val splitIndex = delimiterMatcher.range.last + 1
            val completeSentence = currentText.substring(0, splitIndex).trim()
            val remaining = currentText.substring(splitIndex)

            textAccumulator.clear()
            textAccumulator.append(remaining)

            if (completeSentence.isNotEmpty()) {
                Log.d(TAG, "Segmented speech clause: [$completeSentence]")
                sentenceChannel.send(completeSentence)
            }
        }
    }

    /**
     * Ingests a synthesized audio PCM chunk for streaming playback.
     */
    suspend fun appendAudioChunk(pcmChunk: ByteArray) {
        if (pcmChunk.isNotEmpty()) {
            _audioChunkFlow.emit(pcmChunk)
        }
    }

    /**
     * Marks the streaming response as completed, flushing any remaining text.
     */
    suspend fun completeStream() = mutex.withLock {
        if (isStreamCompleted.getAndSet(true)) return@withLock

        val remainingText = textAccumulator.toString().trim()
        textAccumulator.clear()

        if (remainingText.isNotEmpty()) {
            Log.d(TAG, "Flushing final clause: [$remainingText]")
            sentenceChannel.send(remainingText)
        }

        sentenceChannel.close()
        Log.i(TAG, "StreamingResponseBuffer completed and closed.")
    }

    fun isCompleted(): Boolean = isStreamCompleted.get()

    /**
     * Resets the buffer for a new streaming query session.
     */
    @Synchronized
    fun reset() {
        textAccumulator.clear()
        isStreamCompleted.set(false)
    }
}
