package com.kyawgyi.voice

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Snapshot of [StreamingTtsQueue] pipelined sentence playback state.
 */
data class StreamingTtsQueueState(
    val isSpeaking: Boolean = false,
    val totalClausesInTurn: Int = 0,
    val completedClausesInTurn: Int = 0,
    val currentClauseText: String = "",
    val currentContour: MyanmarIntonationContour = MyanmarIntonationContour.DECLARATIVE,
    val firstClauseLatencyMs: Long = 0L
)

/**
 * StreamingTtsQueue (Additional 12 — Zero-Latency Sentence-Pipelined TTS Audio Queue)
 *
 * Covers Item #10 of the Production Burmese Voice AI Checklist:
 * Instead of waiting for an entire multi-sentence paragraph to finish synthesizing before
 * playing audio, [StreamingTtsQueue] splits the Burmese response along `၊` and `။` via
 * [TtsProsodyPreprocessor], speaks the first clause immediately, and pipelines subsequent
 * clauses with natural Burmese breath pauses (`pauseAfterMs`) and instant barge-in cancellation.
 */
class StreamingTtsQueue(
    private val prosodyPreprocessor: TtsProsodyPreprocessor = TtsProsodyPreprocessor(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {

    private val _state = MutableStateFlow(StreamingTtsQueueState())
    val state: StateFlow<StreamingTtsQueueState> = _state.asStateFlow()

    private var activeQueueJob: Job? = null

    /**
     * Splits [fullBurmeseResponse] into clauses and streams each clause sequentially through
     * [clauseSpeaker] with prosody-planned inter-clause pauses.
     */
    suspend fun speakStreaming(
        fullBurmeseResponse: String,
        clauseSpeaker: suspend (PlannedMyanmarPhrase) -> Unit
    ): Int {
        val startNs = System.nanoTime()
        val clauses = prosodyPreprocessor.splitIntoStreamingClauses(fullBurmeseResponse)
        if (clauses.isEmpty()) return 0

        _state.update {
            StreamingTtsQueueState(
                isSpeaking = true,
                totalClausesInTurn = clauses.size,
                completedClausesInTurn = 0,
                currentClauseText = clauses.first().text,
                currentContour = clauses.first().contour,
                firstClauseLatencyMs = ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(1L)
            )
        }

        var spokenCount = 0
        try {
            for ((index, phrase) in clauses.withIndex()) {
                _state.update { prev ->
                    prev.copy(
                        isSpeaking = true,
                        currentClauseText = phrase.text,
                        currentContour = phrase.contour
                    )
                }
                clauseSpeaker(phrase)
                spokenCount++
                _state.update { prev ->
                    prev.copy(completedClausesInTurn = spokenCount)
                }
                if (index < clauses.lastIndex && phrase.pauseAfterMs > 0) {
                    delay(phrase.pauseAfterMs.coerceAtMost(80).toLong())
                }
            }
        } finally {
            _state.update { prev ->
                prev.copy(
                    isSpeaking = false,
                    currentClauseText = ""
                )
            }
        }
        return spokenCount
    }

    /**
     * Enqueues asynchronous streaming playback on [scope] and cancels any prior speech turn.
     */
    fun enqueueStreaming(
        fullBurmeseResponse: String,
        clauseSpeaker: suspend (PlannedMyanmarPhrase) -> Unit
    ) {
        activeQueueJob?.cancel()
        activeQueueJob = scope.launch {
            if (!isActive) return@launch
            speakStreaming(fullBurmeseResponse, clauseSpeaker)
        }
    }

    /**
     * Immediately halts queued clauses (e.g., on user Barge-In or Stop button).
     */
    fun cancelAll() {
        activeQueueJob?.cancel()
        activeQueueJob = null
        _state.update { prev ->
            prev.copy(isSpeaking = false, currentClauseText = "")
        }
    }
}
