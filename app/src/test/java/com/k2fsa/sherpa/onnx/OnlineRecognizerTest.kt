package com.k2fsa.sherpa.onnx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sin

class OnlineRecognizerTest {

    @Test
    fun testOnlineRecognizerAcousticDecoding() {
        val config = OnlineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = 16_000),
            enableEndpoint = true,
            rule3MinUtteranceLength = 0.22f,
            rule1MinTrailingSilence = 0.5f
        )
        val recognizer = OnlineRecognizer(config)
        val stream = recognizer.createStream()

        assertNotNull(stream)
        assertFalse("Stream should initially not be ready", recognizer.isReady(stream))

        // Synthesize simulated 3-syllable speech waveform (e.g. 16kHz audio with voiced pulses)
        val sampleRate = 16_000
        val totalSamples = (sampleRate * 0.8).toInt() // 800ms
        val speechWaveform = FloatArray(totalSamples)

        // Generate 3 distinct voiced bursts with small pauses
        for (i in 0 until totalSamples) {
            val t = i.toDouble() / sampleRate
            val isBurst = (t in 0.05..0.25) || (t in 0.35..0.55) || (t in 0.60..0.78)
            if (isBurst) {
                // Voiced speech: fundamental frequency 180Hz with harmonics
                speechWaveform[i] = (0.25 * sin(2.0 * Math.PI * 180.0 * t) +
                        0.10 * sin(2.0 * Math.PI * 360.0 * t)).toFloat()
            } else {
                speechWaveform[i] = 0.001f
            }
        }

        // Feed frames in chunks of 160 samples (10ms)
        val chunkSize = 160
        for (offset in 0 until totalSamples step chunkSize) {
            val length = minOf(chunkSize, totalSamples - offset)
            val chunk = speechWaveform.copyOfRange(offset, offset + length)
            stream.acceptWaveform(chunk, sampleRate)
            while (recognizer.isReady(stream)) {
                recognizer.decode(stream)
            }
        }

        val result = recognizer.getResult(stream)
        assertNotNull("Result should not be null", result)
        assertTrue("Decoded text should not be blank after processing speech", result.text.isNotBlank())
        assertEquals("Should decode 3-syllable Myanmar wake-word candidate", "ဟေး ကျော်ကြီး", result.text)

        // Test reset
        recognizer.reset(stream)
        val resetResult = recognizer.getResult(stream)
        assertEquals("Text should be empty after reset", "", resetResult.text)
    }
}
