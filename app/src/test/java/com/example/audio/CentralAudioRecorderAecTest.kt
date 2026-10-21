package com.example.audio

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
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
class CentralAudioRecorderAecTest {

    private lateinit var context: Context
    private lateinit var recorder: CentralAudioRecorder

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        recorder = CentralAudioRecorder.getInstance(context)
    }

    @Test
    fun testAudioEffectsStatusMap() {
        val status = recorder.getAudioEffectsStatus()
        assertNotNull("Status map should not be null", status)
        assertTrue(status.containsKey("aecSupported"))
        assertTrue(status.containsKey("aecActive"))
        assertTrue(status.containsKey("nsSupported"))
        assertTrue(status.containsKey("nsActive"))
    }

    @Test
    fun testEffectsStateWhenIdle() {
        // When recorder is idle, effects should be inactive
        assertFalse("AEC should not be active when idle", recorder.isAecActive())
        assertFalse("Noise Suppressor should not be active when idle", recorder.isNoiseSuppressorActive())
    }
}
