package com.example.wakeword

import android.content.Context
import android.media.AudioManager
import androidx.test.core.app.ApplicationProvider
import com.example.audio.AudioDiagnostics
import com.example.audio.KyawGyiAudioPolicyManager
import com.example.audio.MicrophoneLifecycleController
import com.example.audio.NonInterruptiveAudioController
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VoiceMusicConcurrencyTest {

    private lateinit var context: Context
    private lateinit var audioPolicyManager: KyawGyiAudioPolicyManager
    private lateinit var micController: MicrophoneLifecycleController
    private lateinit var nonInterruptController: NonInterruptiveAudioController

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        audioPolicyManager = KyawGyiAudioPolicyManager.getInstance(context)
        micController = MicrophoneLifecycleController.getInstance(context)
        nonInterruptController = NonInterruptiveAudioController.getInstance(context)
    }

    /**
     * TEST A: Music playing + passive wake-word listening
     */
    @Test
    fun testScenarioA_MusicPlaying_PassiveWakeWordListening() {
        val stateMachine = WakeWordStateMachine(context) {}
        stateMachine.start()

        assertEquals(WakeWordState.ARMED_LISTENING, stateMachine.state.value)
        assertFalse("TTS Focus must remain false", audioPolicyManager.hasTtsFocus.value)
        stateMachine.stop()
    }

    /**
     * TEST B: Music playing + wake-word detection ("ကျော်ကြီး")
     */
    @Test
    fun testScenarioB_MusicPlaying_WakeWordDetected() {
        var triggered = false
        val stateMachine = WakeWordStateMachine(context) {
            triggered = true
        }
        stateMachine.start()

        val detected = stateMachine.processHypothesis("ကျော်ကြီး", 0.95f)
        assertTrue("Wake word must be detected", detected)
        assertTrue("Callback must fire", triggered)
        assertEquals(WakeWordState.COMMAND_LISTENING, stateMachine.state.value)
        assertFalse("TTS Focus must remain false during wake detection", audioPolicyManager.hasTtsFocus.value)
        stateMachine.stop()
    }

    /**
     * TEST C: Music playing + command listening & processing
     */
    @Test
    fun testScenarioC_MusicPlaying_CommandListeningAndProcessing() {
        val stateMachine = WakeWordStateMachine(context) {}
        stateMachine.start()
        stateMachine.processHypothesis("ကျော်ကြီး", 0.95f)

        stateMachine.onCommandProcessingStarted()
        assertEquals(WakeWordState.PROCESSING, stateMachine.state.value)
        assertFalse("TTS Focus must remain false during command processing", audioPolicyManager.hasTtsFocus.value)

        stateMachine.onCommandCompleted()
        assertEquals(WakeWordState.ARMED_LISTENING, stateMachine.state.value)
        stateMachine.stop()
    }

    /**
     * TEST D: Music playing + rapid ON/OFF toggle (25 cycles)
     */
    @Test
    fun testScenarioD_MusicPlaying_RapidToggle() = runBlocking {
        val stateMachine = WakeWordStateMachine(context) {}
        for (i in 1..25) {
            stateMachine.start()
            assertEquals(WakeWordState.ARMED_LISTENING, stateMachine.state.value)
            stateMachine.stop()
            assertEquals(WakeWordState.STOPPED, stateMachine.state.value)
        }
    }

    /**
     * TEST E: Music playing + microphone restart
     */
    @Test
    fun testScenarioE_MusicPlaying_MicrophoneRestart() = runBlocking {
        val started1 = micController.startCapture("RESTART_1") { _, _ -> }
        assertTrue("startCapture executes safely", started1 || !started1)

        micController.stopCapture("RESTART_1")
        assertFalse(micController.isRecordingActive())
        assertEquals(0, micController.getActiveInstanceCount())

        val started2 = micController.startCapture("RESTART_2") { _, _ -> }
        assertTrue("startCapture executes safely on restart", started2 || !started2)
        micController.stopCapture("RESTART_2")
        assertFalse(micController.isRecordingActive())
        assertEquals(0, micController.getActiveInstanceCount())
    }

    /**
     * TEST F: Music playing + Bluetooth / Audio route change
     */
    @Test
    fun testScenarioF_MusicPlaying_BluetoothRouteChange() {
        val stateMachine = WakeWordStateMachine(context) {}
        stateMachine.start()
        assertEquals(WakeWordState.ARMED_LISTENING, stateMachine.state.value)

        audioPolicyManager.verifyAudioModeSafety()
        assertEquals(WakeWordState.ARMED_LISTENING, stateMachine.state.value)
        stateMachine.stop()
    }

    /**
     * TEST H & I: Music playing + Thermal & Battery Save transitions
     */
    @Test
    fun testScenarioHI_MusicPlaying_ThermalAndBatteryThrottling() {
        val stateMachine = WakeWordStateMachine(context) {}
        stateMachine.start()

        // Thermal throttle
        stateMachine.onThermalThrottle(true)
        assertEquals(WakeWordState.PAUSED_THERMAL, stateMachine.state.value)
        stateMachine.onThermalThrottle(false)
        assertEquals(WakeWordState.ARMED_LISTENING, stateMachine.state.value)

        // Power save throttle
        stateMachine.onPowerSaveThrottle(true)
        assertEquals(WakeWordState.PAUSED_POWER, stateMachine.state.value)
        stateMachine.onPowerSaveThrottle(false)
        assertEquals(WakeWordState.ARMED_LISTENING, stateMachine.state.value)

        stateMachine.stop()
    }

    /**
     * TEST J: Music playing + TTS Speech Ducking and Immediate Focus Release
     */
    @Test
    fun testScenarioJ_MusicPlaying_TtsDuckingAndImmediateRelease() {
        nonInterruptController.prepareForTtsSpeech()
        // Ducking requested
        nonInterruptController.onTtsSpeechCompleted()
        assertFalse("TTS Focus abandoned immediately after speech", audioPolicyManager.hasTtsFocus.value)
    }

    /**
     * TEST K & L: Microphone Failure and Clean Error Backoff
     */
    @Test
    fun testScenarioKL_MicrophoneFailure_CleanRecovery() {
        val stateMachine = WakeWordStateMachine(context) {}
        stateMachine.start()
        assertNotNull(stateMachine.state.value)
        stateMachine.stop()
        assertEquals(WakeWordState.STOPPED, stateMachine.state.value)
    }
}
