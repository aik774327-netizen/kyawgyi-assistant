package com.example.audio

import android.content.Context
import android.media.AudioManager
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AudioManagerFocusListenerTest {

    private lateinit var context: Context
    private lateinit var focusListener: AudioManagerFocusListener

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        focusListener = AudioManagerFocusListener.getInstance(context)
    }

    @Test
    fun testTransientLoss_StateTransition() = runBlocking {
        // When AUDIOFOCUS_LOSS_TRANSIENT is received by listener
        focusListener.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
        assertEquals(
            AudioManagerFocusListener.FocusTransitionState.TRANSIENT_LOSS,
            focusListener.focusTransitionState.value
        )
    }

    @Test
    fun testTransientLossCanDuck_StateTransition() = runBlocking {
        focusListener.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK)
        assertEquals(
            AudioManagerFocusListener.FocusTransitionState.TRANSIENT_LOSS_CAN_DUCK,
            focusListener.focusTransitionState.value
        )
    }

    @Test
    fun testFocusGain_StateTransition() = runBlocking {
        focusListener.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
        focusListener.onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN)
        assertEquals(
            AudioManagerFocusListener.FocusTransitionState.GAINED,
            focusListener.focusTransitionState.value
        )
    }

    @Test
    fun testPermanentLoss_TransitionsState() = runBlocking {
        focusListener.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS)
        assertEquals(
            AudioManagerFocusListener.FocusTransitionState.PERMANENT_LOSS,
            focusListener.focusTransitionState.value
        )
    }
}
