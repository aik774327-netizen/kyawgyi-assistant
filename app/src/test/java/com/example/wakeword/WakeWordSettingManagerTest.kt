package com.example.wakeword

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class WakeWordSettingManagerTest {

    private lateinit var context: Context
    private lateinit var wakeWordSettingManager: WakeWordSettingManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        wakeWordSettingManager = WakeWordSettingManager.getInstance(context)
    }

    @Test
    fun testDefaultWakeWordStateIsEnabled() {
        wakeWordSettingManager.setWakeWordEnabled(true)
        assertTrue(wakeWordSettingManager.isEnabled())
        assertTrue(wakeWordSettingManager.isWakeWordEnabled.value)
    }

    @Test
    fun testDisableWakeWordState() {
        wakeWordSettingManager.setWakeWordEnabled(false)
        assertFalse(wakeWordSettingManager.isEnabled())
        assertFalse(wakeWordSettingManager.isWakeWordEnabled.value)
    }

    @Test
    fun testToggleHighSensitivity() {
        wakeWordSettingManager.setHighSensitivity(true)
        assertTrue(wakeWordSettingManager.sensitivityHigh.value)

        wakeWordSettingManager.setHighSensitivity(false)
        assertFalse(wakeWordSettingManager.sensitivityHigh.value)
    }
}
