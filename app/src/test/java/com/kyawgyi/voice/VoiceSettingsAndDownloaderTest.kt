package com.kyawgyi.voice

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VoiceSettingsAndDownloaderTest {

    private lateinit var context: Context
    private lateinit var voiceSettings: VoiceSettings
    private lateinit var modelDownloader: ModelDownloader

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        voiceSettings = VoiceSettings.getInstance(context)
        modelDownloader = ModelDownloader.getInstance(context)
    }

    @Test
    fun testVoiceSettingsPersistenceAndDefaults() {
        voiceSettings.resetToDefaults()

        assertEquals(SttEngineType.SYSTEM_ON_DEVICE, voiceSettings.preferredSttEngine)
        assertEquals(1.0f, voiceSettings.ttsSpeechRate, 0.01f)
        assertEquals(1.0f, voiceSettings.ttsPitch, 0.01f)
        assertTrue(voiceSettings.autoDownloadModels)
        assertTrue(voiceSettings.isAecHangoverEnabled)
        assertTrue(voiceSettings.useLocalTtsFallback)

        // Mutate and check update
        voiceSettings.preferredSttEngine = SttEngineType.SHERPA_ONNX
        assertEquals(SttEngineType.SHERPA_ONNX, voiceSettings.preferredSttEngine)

        voiceSettings.ttsSpeechRate = 1.25f
        assertEquals(1.25f, voiceSettings.ttsSpeechRate, 0.01f)

        voiceSettings.ttsPitch = 0.9f
        assertEquals(0.9f, voiceSettings.ttsPitch, 0.01f)

        voiceSettings.autoDownloadModels = false
        assertFalse(voiceSettings.autoDownloadModels)
    }

    @Test
    fun testModelDownloaderInitialStateAndPaths() {
        assertNotNull(modelDownloader)
        assertFalse("Downloader should initially not be busy", modelDownloader.isBusy())
        assertEquals(ModelDownloader.DownloadState.Idle, modelDownloader.downloadState.value)

        val modelDir = File(context.filesDir, "models")
        assertTrue("Models directory should exist or be creatable", modelDir.exists() || modelDir.mkdirs())
    }
}
