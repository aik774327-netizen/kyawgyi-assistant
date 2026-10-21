package com.kyawgyi.voice

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
class KyawGyiAdvancedAudioFeaturesTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun testAudioClippingLimiterSoftKneeCompression() {
        val limiter = AudioClippingLimiter(kneeThreshold = 0.80f, hardLimit = 0.98f)

        // Generate a saturated 16kHz sine wave that exceeds 32000
        val sampleCount = 640 // 40ms
        val pcm = ByteArray(sampleCount * 2)
        for (i in 0 until sampleCount) {
            val offset = i * 2
            // High amplitude sample hitting 32500 (clipping territory)
            val sample = if (i % 2 == 0) 32600 else -32600
            pcm[offset] = (sample and 0xFF).toByte()
            pcm[offset + 1] = ((sample shr 8) and 0xFF).toByte()
        }

        val metrics = limiter.processPcm16LeInPlace(pcm)
        assertTrue(metrics.wasLimited)
        assertTrue(metrics.clippedSamplesCount > 0)
        assertTrue(metrics.peakAmplitudeRatio in 0.80f..1.0f)

        // Verify that after limiting, no sample exceeds 98% of 32767 (~32111)
        for (i in 0 until sampleCount) {
            val offset = i * 2
            val low = pcm[offset].toInt() and 0xFF
            val high = pcm[offset + 1].toInt()
            val limitedSample = kotlin.math.abs((high shl 8) or low)
            assertTrue("Sample $limitedSample must not exceed hard limit", limitedSample <= 32115)
        }
    }

    @Test
    fun testTwoStageWakeWordDetectorEnergyFilter() {
        val detector = TwoStageWakeWordDetector(context, energyThresholdRms = 800.0)

        // 1. Silent frame should be dropped in Stage 1 without waking neural model
        val silentPcm = ByteArray(1280) // Silence (0 RMS)
        val resultSilence = detector.processPcmFrame(silentPcm)
        assertFalse(resultSilence.isConfirmed)
        assertFalse(resultSilence.isStage1Candidate)
        assertEquals(1L, detector.metrics.value.stage1DroppedFrames)
        assertEquals(0L, detector.metrics.value.stage2EvaluatedFrames)
        assertTrue(detector.metrics.value.batterySavingRatioPercent > 90f)

        // 2. Loud frame should pass Stage 1 and proceed to Stage 2
        val loudPcm = ByteArray(1280)
        for (i in 0 until 640) {
            val offset = i * 2
            val sample = (3000 * kotlin.math.sin(2.0 * kotlin.math.PI * 400.0 * i / 16000.0)).toInt()
            loudPcm[offset] = (sample and 0xFF).toByte()
            loudPcm[offset + 1] = ((sample shr 8) and 0xFF).toByte()
        }
        val resultCandidate = detector.processPcmFrame(loudPcm)
        assertTrue(resultCandidate.isStage1Candidate)
        assertEquals(1L, detector.metrics.value.stage2EvaluatedFrames)

        // 3. Spoken wake phrase triggers confirmed wake word
        val confirmedResult = detector.processPcmFrame(loudPcm, transcriptHint = "ဟေး ကျော်ကြီး")
        assertTrue(confirmedResult.isConfirmed)
        assertTrue(confirmedResult.matchedKeyword.contains("ကျော်ကြီး"))
        assertTrue(detector.metrics.value.wakeWordTriggersCount >= 1L)
    }

    @Test
    fun testScreenContextHotwordManagerDomainSwitching() {
        val hotwordBiasing = HotwordBiasing()
        val screenManager = ScreenContextHotwordManager(hotwordBiasing)

        // Default domain is HOME
        assertEquals(AppScreenDomain.HOME, screenManager.currentDomain.value)

        // Navigate to SETTINGS screen
        screenManager.onRouteChanged("settings_screen")
        assertEquals(AppScreenDomain.SETTINGS, screenManager.currentDomain.value)
        val settingsHotwords = hotwordBiasing.getAllEntries().map { it.phrase }
        assertTrue(settingsHotwords.contains("ဝိုင်ဖိုင်"))
        assertTrue(settingsHotwords.contains("ဘလူးတုသ်"))

        // Navigate to FINTECH screen
        screenManager.onRouteChanged("wallet_dashboard")
        assertEquals(AppScreenDomain.FINTECH, screenManager.currentDomain.value)
        val fintechHotwords = hotwordBiasing.getAllEntries().map { it.phrase }
        assertTrue(fintechHotwords.contains("ကေဘီဇက်ပေး"))
        assertTrue(fintechHotwords.contains("ဝေ့ဗ်ပေး"))

        // Navigate to COMMUNICATION screen
        screenManager.onRouteChanged("call_contacts")
        assertEquals(AppScreenDomain.COMMUNICATION, screenManager.currentDomain.value)
        val commHotwords = hotwordBiasing.getAllEntries().map { it.phrase }
        assertTrue(commHotwords.contains("ဖုန်းခေါ်ပေးပါ"))
    }

    @Test
    fun testAudioTrackComfortPaddingSmoothRamps() {
        val padding = AudioTrackComfortPadding(sampleRateHz = 16000, fadeInMs = 10, fadeOutMs = 15)

        val sampleCount = 640 // 40ms @ 16kHz
        val pcm = ByteArray(sampleCount * 2) { 0 }
        // Fill with constant high sample
        for (i in 0 until sampleCount) {
            val offset = i * 2
            val sample = 20000
            pcm[offset] = (sample and 0xFF).toByte()
            pcm[offset + 1] = ((sample shr 8) and 0xFF).toByte()
        }

        // Apply fade-in
        padding.applyFadeIn(pcm)
        // First sample should be close to 0
        val firstSample = ((pcm[1].toInt() shl 8) or (pcm[0].toInt() and 0xFF))
        assertTrue("First sample after fade-in should be attenuated: $firstSample", kotlin.math.abs(firstSample) < 500)

        // Apply fade-out
        padding.applyFadeOut(pcm)
        // Last sample should be attenuated
        val lastOffset = (sampleCount - 1) * 2
        val lastSample = ((pcm[lastOffset + 1].toInt() shl 8) or (pcm[lastOffset].toInt() and 0xFF))
        assertTrue("Last sample after fade-out should be attenuated: $lastSample", kotlin.math.abs(lastSample) < 2000)

        // Generate comfort dither
        val dither = padding.generateComfortDitherFrame(durationMs = 20)
        assertTrue(dither.isNotEmpty())
    }
}
