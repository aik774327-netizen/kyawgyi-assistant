package com.example.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.test.core.app.ApplicationProvider
import com.example.data.FailureCategory
import com.example.ocr.FailureMemoryEngine
import com.example.ocr.MyanmarScriptDetector
import com.example.utils.MyanmarTTSVoicePackManager
import com.example.utils.VoicePackStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MyanmarTTSVoicePackManagerTest {

    private lateinit var context: Context
    private lateinit var voicePackManager: MyanmarTTSVoicePackManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        FailureMemoryEngine.clearAll()
        voicePackManager = MyanmarTTSVoicePackManager.getInstance(context)
    }

    @Test
    fun testBurmeseScriptDetection() {
        assertTrue(MyanmarScriptDetector.containsMyanmar("မင်္ဂလာပါခင်ဗျာ"))
        assertTrue(MyanmarScriptDetector.containsMyanmar("Hello မင်္ဂလာပါ"))
        assertFalse(MyanmarScriptDetector.containsMyanmar("Hello World 12345"))
        assertFalse(MyanmarScriptDetector.containsMyanmar(""))
        assertFalse(MyanmarScriptDetector.containsMyanmar(null))
    }

    @Test
    fun testVoicePackEvaluation_MissingData() {
        val status = voicePackManager.evaluateLanguageAvailability(
            langResult = TextToSpeech.LANG_MISSING_DATA,
            engine = "com.google.android.tts"
        )

        assertEquals(VoicePackStatus.VOICE_PACK_MISSING_DATA, status)
        assertFalse(voicePackManager.canSafelyVocalizeMyanmar())

        // Verify truthful diagnostic was recorded in FailureMemoryEngine
        assertTrue(FailureMemoryEngine.hasPreviousFailure("TTS_SYNTHESIS", "CHECK_VOICE_PACK"))
        val failure = FailureMemoryEngine.getLatestFailure("TTS_SYNTHESIS", "CHECK_VOICE_PACK")
        assertNotNull(failure)
        assertEquals(FailureCategory.RESOURCE, failure?.failureCategory)
        assertTrue(failure?.rootCauseMm?.contains("Google TTS") == true)
    }

    @Test
    fun testVoicePackEvaluation_EngineUnsupported() {
        val status = voicePackManager.evaluateLanguageAvailability(
            langResult = TextToSpeech.LANG_NOT_SUPPORTED,
            engine = "com.sec.android.app.samsungtts"
        )

        assertEquals(VoicePackStatus.ENGINE_UNSUPPORTED, status)
        assertFalse(voicePackManager.canSafelyVocalizeMyanmar())

        val failure = FailureMemoryEngine.getLatestFailure("TTS_SYNTHESIS", "CHECK_VOICE_PACK")
        assertNotNull(failure)
        assertEquals(FailureCategory.RESOURCE, failure?.failureCategory)
        assertTrue(failure?.rootCauseMm?.contains("မပံ့ပိုးပါ") == true)
    }

    @Test
    fun testVoicePackEvaluation_InstalledAndAvailable() {
        val status = voicePackManager.evaluateLanguageAvailability(
            langResult = TextToSpeech.LANG_COUNTRY_AVAILABLE,
            engine = "com.google.android.tts"
        )

        assertEquals(VoicePackStatus.VOICE_PACK_INSTALLED, status)
        assertTrue(voicePackManager.canSafelyVocalizeMyanmar())
    }

    @Test
    fun testPronunciationSafetyGate() {
        // Simulate missing Burmese voice data
        voicePackManager.evaluateLanguageAvailability(
            langResult = TextToSpeech.LANG_MISSING_DATA,
            engine = "com.google.android.tts"
        )

        // Myanmar script must be blocked from vocalizing to prevent garbled screeching
        assertFalse(voicePackManager.canSafelyVocalizeText("မင်္ဂလာပါ"))

        // Standard English text can still be vocalized by the engine
        assertTrue(voicePackManager.canSafelyVocalizeText("Ok, playing song on YouTube"))
    }

    @Test
    fun testOfflineSpeechRecovery_EmitsEventAndCompletesCallback() = runBlocking {
        voicePackManager.evaluateLanguageAvailability(
            langResult = TextToSpeech.LANG_MISSING_DATA,
            engine = "com.google.android.tts"
        )

        var callbackInvoked = false
        val testText = "မျက်နှာပြင်ပေါ်ရှိ စာသားကို ဖတ်ပြနေပါသည်"

        voicePackManager.handleMissingVoicePackFailure(context, testText) {
            callbackInvoked = true
        }

        // Run UI tasks on main looper
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        assertTrue(callbackInvoked)

        // Verify truthful diagnostic was recorded for SPEAK_BURMESE operation
        val lastFailure = FailureMemoryEngine.getLatestFailure("TTS_SYNTHESIS", "SPEAK_BURMESE")
        assertNotNull(lastFailure)
        assertEquals(FailureCategory.RESOURCE, lastFailure?.failureCategory)
        assertTrue(lastFailure?.rootCauseMm?.contains("Google TTS") == true || lastFailure?.rootCauseMm?.contains("မြန်မာ") == true)
    }

    @Test
    fun testLaunchVoicePackDownloadFlow() {
        val launched = voicePackManager.launchVoicePackDownload(context)
        assertTrue(launched)

        val shadowApp = Shadows.shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>())
        val nextIntent = shadowApp.nextStartedActivity
        assertNotNull(nextIntent)
        assertTrue(
            nextIntent.action == TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA ||
            nextIntent.action == "com.android.settings.TTS_SETTINGS" ||
            nextIntent.action == android.provider.Settings.ACTION_SETTINGS
        )
    }
}
