package com.example.audio

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AdaptiveAcousticSensingEngineTest {

    private lateinit var context: Context
    private lateinit var engine: AdaptiveAcousticSensingEngine

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        engine = AdaptiveAcousticSensingEngine.getInstance(context)
    }

    @Test
    fun powerProfiles_switchAndPersistCorrectly() {
        // Test Eco Saver
        engine.setPowerProfile(AdaptiveAcousticSensingEngine.PowerProfile.ECO_SAVER)
        assertEquals(AdaptiveAcousticSensingEngine.PowerProfile.ECO_SAVER, engine.metrics.value.powerProfile)
        assertEquals(75, engine.metrics.value.powerProfile.energySavings)

        // Test Ultra Sensitivity
        engine.setPowerProfile(AdaptiveAcousticSensingEngine.PowerProfile.ULTRA_SENSITIVITY)
        assertEquals(AdaptiveAcousticSensingEngine.PowerProfile.ULTRA_SENSITIVITY, engine.metrics.value.powerProfile)

        // Test Adaptive Balanced
        engine.setPowerProfile(AdaptiveAcousticSensingEngine.PowerProfile.ADAPTIVE_BALANCED)
        assertEquals(AdaptiveAcousticSensingEngine.PowerProfile.ADAPTIVE_BALANCED, engine.metrics.value.powerProfile)
    }

    @Test
    fun processBuffer_handlesSilenceWithoutCrashing() {
        engine.setPowerProfile(AdaptiveAcousticSensingEngine.PowerProfile.ECO_SAVER)
        val silentBuffer = ShortArray(1024) { 0 }

        val result = engine.processBuffer(silentBuffer, silentBuffer.size)
        assertNotNull(result)
        assertFalse("Silent buffer should not be classified as speech", result.isSpeech)
        assertTrue("Silent buffer in eco mode should be gated by power saver", result.isGatedByPowerSaver)
    }

    @Test
    fun processBuffer_detectsSyntheticVoiceBurst() {
        engine.setPowerProfile(AdaptiveAcousticSensingEngine.PowerProfile.ADAPTIVE_BALANCED)

        // Create synthetic sine wave around ~1 kHz (Burmese vocal formant range)
        val voiceBuffer = ShortArray(1024)
        for (i in voiceBuffer.indices) {
            val angle = 2.0 * Math.PI * 1000.0 * i / 16000.0
            voiceBuffer[i] = (16000.0 * Math.sin(angle)).toInt().toShort()
        }

        val result = engine.processBuffer(voiceBuffer, voiceBuffer.size)
        assertNotNull(result)
        assertTrue("RMS should be significant for loud voice buffer", result.rms > 1000)
        assertFalse("Should not be gated by power saver during loud signal", result.isGatedByPowerSaver)
    }

    @Test
    fun acousticEnvironment_classification() {
        // Feed quiet buffer
        engine.setPowerProfile(AdaptiveAcousticSensingEngine.PowerProfile.ADAPTIVE_BALANCED)
        val lowNoiseBuffer = ShortArray(512) { 50 }
        val result = engine.processBuffer(lowNoiseBuffer, lowNoiseBuffer.size)

        assertNotNull(result)
        assertNotNull(engine.metrics.value.acousticEnvironment)
    }
}
