package com.kyawgyi.voice

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.GeminiFailureClassifier
import com.example.data.GeminiFailureClass
import com.example.data.GeminiHttpException
import com.example.engine.VoiceSessionArbiter
import com.example.engine.VoiceSessionOwner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * §7 Part B regression tests (t18_b_07..09) — SttEngineManager wiring
 * for the Gemini online engine, via the manager's fake-transcription
 * seam (no network, no key). Pins: engine selection is real (not the
 * former hard-coded single engine), an online transcript enters the
 * pipeline through the SAME onEngineTranscriptReceived entry the
 * system recognizer uses, and a classified online failure surfaces in
 * uiState AND fails over to SYSTEM_ON_DEVICE without wedging the
 * FIX-06 microphone arbitration.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GeminiOnlineSttManagerTest {

    private lateinit var context: Context
    private lateinit var audioCaptureModule: AudioCaptureModule
    private lateinit var manager: SttEngineManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        AudioFocusAndAecManager.getInstance(context).resetForTesting()
        VoiceSessionArbiter.resetForTesting()
        // Deterministic starting selection for every test.
        VoiceSettings.getInstance(context).preferredSttEngine = SttEngineType.SYSTEM_ON_DEVICE
        audioCaptureModule = AudioCaptureModule(context)
        manager = SttEngineManager(
            context = context,
            modelManager = ModelManager(context),
            audioCaptureModule = audioCaptureModule
        )
    }

    private fun awaitTrue(timeoutMs: Long = 5_000, condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return true
            Thread.sleep(50)
        }
        return condition()
    }

    // t18_b_07: selection actually switches the active engine and the
    // reported uiState type (both directions).
    @Test
    fun t18_b_07_selectionSwitchesActiveEngine() {
        manager.selectEngine(SttEngineType.GEMINI_ONLINE)
        assertEquals(SttEngineType.GEMINI_ONLINE, manager.uiState.value.activeEngineType)
        assertTrue(manager.activeEngine is GeminiOnlineSttEngine)

        manager.selectEngine(SttEngineType.SYSTEM_ON_DEVICE)
        assertEquals(SttEngineType.SYSTEM_ON_DEVICE, manager.uiState.value.activeEngineType)
        assertTrue(manager.activeEngine === manager.systemEngine)
    }

    // t18_b_08: an online transcript re-enters through the manager's
    // standard transcript entry (final transcript + utterance count).
    @Test
    fun t18_b_08_onlineTranscriptEntersPipeline() {
        manager.transcribeAudioOverrideForTesting = { _, _ -> "မင်္ဂလာပါ" }
        manager.selectEngine(SttEngineType.GEMINI_ONLINE)
        manager.startListening()
        assertEquals(VoiceSessionOwner.IR_SERVICE, VoiceSessionArbiter.currentOwner())

        manager.feedPcmChunk(ByteArray(1280) { 5 })
        manager.finalizeCurrentUtterance()

        assertTrue(
            "online transcript must reach uiState.finalTranscript",
            awaitTrue { manager.uiState.value.finalTranscript == "မင်္ဂလာပါ" }
        )
        assertEquals(1, manager.uiState.value.totalUtterancesProcessed)
        manager.stopListening()
    }

    // t18_b_09: a classified online failure is surfaced verbatim in
    // uiState.lastErrorMessage and fails over to the system engine;
    // the microphone arbitration is still released cleanly on stop.
    @Test
    fun t18_b_09_onlineFailureSurfacesAndFailsOver() {
        manager.transcribeAudioOverrideForTesting = { _, _ -> throw GeminiHttpException(500) }
        manager.selectEngine(SttEngineType.GEMINI_ONLINE)
        manager.startListening()
        manager.feedPcmChunk(ByteArray(1280) { 5 })
        manager.finalizeCurrentUtterance()

        assertTrue(
            "must fail over to SYSTEM_ON_DEVICE",
            awaitTrue { manager.uiState.value.activeEngineType == SttEngineType.SYSTEM_ON_DEVICE }
        )
        assertEquals(
            GeminiFailureClassifier.userMessageFor(GeminiFailureClass.SERVER, 500),
            manager.uiState.value.lastErrorMessage
        )
        assertTrue(manager.uiState.value.failoverSwitchCount >= 1)

        manager.stopListening()
        assertEquals(VoiceSessionOwner.NONE, VoiceSessionArbiter.currentOwner())
    }
}
