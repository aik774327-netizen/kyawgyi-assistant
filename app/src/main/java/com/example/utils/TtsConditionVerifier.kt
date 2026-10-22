package com.example.utils

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.example.audit.VerificationOutcome
import com.example.audit.VerificationResult
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * ISSUE-033 — REAL live verification for Burmese TTS availability.
 *
 * Replaces the fabricated constant-string "verifier" that previously claimed
 * a removed phonetic fallback was active. Verification is STAGED and every
 * stage is produced by an actual check against the device's TTS engine:
 *
 *   1. ENGINE_INIT        — a fresh [TextToSpeech] probe must initialize.
 *   2. VOICE_AVAILABLE    — the probe's enumerated voices are run through the
 *                           FIX-02 [MyanmarVoiceSelector]; only an INSTALLED
 *                           my-MM voice passes (downloadable-only does not).
 *   3. SPEAK_REQUEST_ACCEPTED — a real `speak()` call must return SUCCESS.
 *   4. SPEAK_COMPLETED / SPEAK_FAILED — the utterance listener must report
 *                           onDone / onError within a bounded wait.
 *
 * Even when stage 4 completes, the result states AUDIO_OUTPUT_NOT_VERIFIED:
 * an utterance-completion callback proves the engine finished processing the
 * utterance; it cannot prove sound was audible to a human listener.
 *
 * Threading: [verify] blocks (bounded) while probing — call it off the main
 * thread. The engine caches results briefly (see ErrorLearningWorkflowEngine)
 * so repeated guard checks do not re-probe audibly.
 */
interface TtsProbe {
    sealed interface InitOutcome {
        data class Ready(val enginePackage: String) : InitOutcome
        data class Failed(val reason: String) : InitOutcome
    }

    data class SpeakOutcome(
        val requestAccepted: Boolean,
        val completed: Boolean = false,
        val failedWithError: Int? = null,
        val timedOut: Boolean = false
    )

    fun initialize(): InitOutcome
    fun voiceCandidates(): List<TtsVoiceCandidate>
    fun speakProbe(selection: MyanmarVoiceSelection, candidates: List<TtsVoiceCandidate>): SpeakOutcome
    fun shutdown()
}

/** Production probe backed by a real, throwaway [TextToSpeech] instance. */
class AndroidTtsProbe(
    context: Context,
    private val enginePackage: String?
) : TtsProbe {

    private val appContext: Context = context.applicationContext
    private var tts: TextToSpeech? = null

    override fun initialize(): TtsProbe.InitOutcome {
        val latch = CountDownLatch(1)
        val statusRef = AtomicInteger(Int.MIN_VALUE)
        val engine = enginePackage?.takeIf { it.isNotBlank() }
        val instance = try {
            if (engine != null) {
                TextToSpeech(appContext, { status ->
                    statusRef.set(status)
                    latch.countDown()
                }, engine)
            } else {
                TextToSpeech(appContext, { status ->
                    statusRef.set(status)
                    latch.countDown()
                })
            }
        } catch (e: Exception) {
            return TtsProbe.InitOutcome.Failed("TextToSpeech construction threw ${e.javaClass.simpleName}: ${e.message}")
        }
        tts = instance
        val finished = try {
            latch.await(INIT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (e: InterruptedException) {
            false
        }
        if (!finished) {
            return TtsProbe.InitOutcome.Failed("TTS engine initialization timed out after ${INIT_TIMEOUT_MS}ms")
        }
        val status = statusRef.get()
        if (status != TextToSpeech.SUCCESS) {
            return TtsProbe.InitOutcome.Failed("TTS engine onInit returned status=$status")
        }
        val resolvedEngine = try {
            instance.defaultEngine ?: engine ?: "default"
        } catch (e: Exception) {
            engine ?: "default"
        }
        return TtsProbe.InitOutcome.Ready(resolvedEngine)
    }

    override fun voiceCandidates(): List<TtsVoiceCandidate> {
        val engine = tts ?: return emptyList()
        val voiceSet = try {
            engine.voices
        } catch (e: Exception) {
            Log.w(TAG, "Probe could not enumerate TTS voices: ${e.message}")
            null
        }
        if (voiceSet.isNullOrEmpty()) return emptyList()
        return voiceSet.map { voice ->
            TtsVoiceCandidate(
                localeTag = try {
                    voice.locale?.toLanguageTag() ?: ""
                } catch (e: Exception) {
                    ""
                },
                name = voice.name ?: "",
                dataInstalled = !(
                    voice.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) ?: false
                    )
            )
        }
    }

    override fun speakProbe(
        selection: MyanmarVoiceSelection,
        candidates: List<TtsVoiceCandidate>
    ): TtsProbe.SpeakOutcome {
        val engine = tts ?: return TtsProbe.SpeakOutcome(requestAccepted = false)
        val voiceList = try {
            engine.voices?.toList().orEmpty()
        } catch (e: Exception) {
            emptyList()
        }
        if (selection.selectedIndex in voiceList.indices) {
            try {
                engine.setVoice(voiceList[selection.selectedIndex])
            } catch (e: Exception) {
                Log.w(TAG, "Probe setVoice failed: ${e.message}")
            }
        } else {
            try {
                engine.setLanguage(MyanmarTTSVoicePackManager.BURMESE_LOCALE)
            } catch (e: Exception) {
                Log.w(TAG, "Probe setLanguage failed: ${e.message}")
            }
        }

        val doneLatch = CountDownLatch(1)
        val errorRef = AtomicReference<Int?>(null)
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) {
                doneLatch.countDown()
            }

            @Deprecated("Deprecated in API 21")
            override fun onError(utteranceId: String?) {
                errorRef.set(-1)
                doneLatch.countDown()
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                errorRef.set(errorCode)
                doneLatch.countDown()
            }
        })

        val rc = try {
            engine.speak(PROBE_TEXT, TextToSpeech.QUEUE_FLUSH, null, PROBE_UTTERANCE_ID)
        } catch (e: Exception) {
            Log.w(TAG, "Probe speak() threw: ${e.message}")
            TextToSpeech.ERROR
        }
        if (rc == TextToSpeech.ERROR) {
            return TtsProbe.SpeakOutcome(requestAccepted = false)
        }
        val finished = try {
            doneLatch.await(SPEAK_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (e: InterruptedException) {
            false
        }
        if (!finished) {
            return TtsProbe.SpeakOutcome(requestAccepted = true, timedOut = true)
        }
        val error = errorRef.get()
        return if (error != null) {
            TtsProbe.SpeakOutcome(requestAccepted = true, failedWithError = error)
        } else {
            TtsProbe.SpeakOutcome(requestAccepted = true, completed = true)
        }
    }

    override fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {
            Log.w(TAG, "Probe shutdown failed: ${e.message}")
        }
        tts = null
    }

    companion object {
        private const val TAG = "TtsConditionVerifier"
        const val PROBE_TEXT = "စစ်ဆေးနေပါသည်"
        const val PROBE_UTTERANCE_ID = "kyawgyi_tts_verify_probe"
        const val INIT_TIMEOUT_MS = 5_000L
        const val SPEAK_TIMEOUT_MS = 10_000L
    }
}

object TtsConditionVerifier {

    const val EVIDENCE_SOURCE = "TTS_LIVE_PROBE"

    /**
     * Runs the staged verification. [probe] is always shut down before return.
     */
    fun verify(
        errorId: String,
        commandKey: String,
        probe: TtsProbe,
        nowMs: Long = System.currentTimeMillis()
    ): VerificationResult {
        val stages = mutableListOf<String>()
        try {
            when (val init = probe.initialize()) {
                is TtsProbe.InitOutcome.Failed -> {
                    stages += "ENGINE_INIT_FAILED"
                    return result(
                        VerificationOutcome.FAILED, errorId, commandKey, nowMs,
                        "TTS engine initialization failed: ${init.reason}", stages
                    )
                }
                is TtsProbe.InitOutcome.Ready -> {
                    stages += "ENGINE_INIT_OK (${init.enginePackage})"
                }
            }

            val candidates = probe.voiceCandidates()
            val selection = MyanmarVoiceSelector.select(candidates)
            when (selection.choice) {
                MyanmarVoiceChoice.NO_MYANMAR_VOICE -> {
                    stages += "VOICE_AVAILABLE_FAILED (no my-MM voice enumerated)"
                    return result(
                        VerificationOutcome.BLOCKED, errorId, commandKey, nowMs,
                        "No Burmese (my-MM) voice exists on the active TTS engine; the voice resource is still missing.",
                        stages
                    )
                }
                MyanmarVoiceChoice.VOICE_DATA_MISSING -> {
                    stages += "VOICE_AVAILABLE_FAILED (my-MM voice present but data not installed)"
                    return result(
                        VerificationOutcome.BLOCKED, errorId, commandKey, nowMs,
                        "A Burmese (my-MM) voice exists but its data is not installed on this device; the voice resource is still missing.",
                        stages
                    )
                }
                MyanmarVoiceChoice.INSTALLED_VOICE -> {
                    stages += "VOICE_AVAILABLE (installed my-MM voice: ${candidates.getOrNull(selection.selectedIndex)?.name ?: "?"})"
                }
            }

            val speak = probe.speakProbe(selection, candidates)
            if (!speak.requestAccepted) {
                stages += "SPEAK_REQUEST_REJECTED"
                return result(
                    VerificationOutcome.FAILED, errorId, commandKey, nowMs,
                    "TTS engine rejected the probe speak() request.", stages
                )
            }
            stages += "SPEAK_REQUEST_ACCEPTED"
            return when {
                speak.failedWithError != null -> {
                    stages += "SPEAK_FAILED (utterance error=${speak.failedWithError})"
                    result(
                        VerificationOutcome.FAILED, errorId, commandKey, nowMs,
                        "Probe utterance failed with TTS error code ${speak.failedWithError}.", stages
                    )
                }
                speak.timedOut -> {
                    stages += "SPEAK_COMPLETION_UNVERIFIED (timeout)"
                    result(
                        VerificationOutcome.UNVERIFIED, errorId, commandKey, nowMs,
                        "Probe utterance was accepted but no completion/error callback arrived within the bounded wait; completion cannot be proven.",
                        stages
                    )
                }
                speak.completed -> {
                    stages += "SPEAK_COMPLETED"
                    result(
                        VerificationOutcome.VERIFIED, errorId, commandKey, nowMs,
                        "Installed my-MM voice spoke the probe utterance to completion (utterance callback). " +
                            "AUDIO_OUTPUT_NOT_VERIFIED: the completion callback proves the engine processed the utterance; audible sound output cannot be proven programmatically.",
                        stages
                    )
                }
                else -> {
                    stages += "SPEAK_COMPLETION_UNVERIFIED"
                    result(
                        VerificationOutcome.UNVERIFIED, errorId, commandKey, nowMs,
                        "Probe utterance outcome could not be determined.", stages
                    )
                }
            }
        } finally {
            try {
                probe.shutdown()
            } catch (e: Exception) {
                Log.w("TtsConditionVerifier", "Probe shutdown failed: ${e.message}")
            }
        }
    }

    private fun result(
        outcome: VerificationOutcome,
        errorId: String,
        commandKey: String,
        nowMs: Long,
        detail: String,
        stages: List<String>
    ): VerificationResult = VerificationResult(
        outcome = outcome,
        errorId = errorId,
        commandKey = commandKey,
        checkedAtMs = nowMs,
        evidenceSource = EVIDENCE_SOURCE,
        detail = detail,
        stages = stages.toList()
    )
}
