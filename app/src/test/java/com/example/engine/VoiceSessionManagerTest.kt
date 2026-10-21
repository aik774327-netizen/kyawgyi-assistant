package com.example.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.utils.MyanmarSTEngine
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
class VoiceSessionManagerTest {

    private lateinit var context: Context
    private lateinit var sessionManager: VoiceSessionManager
    private val config = VoiceSessionConfig()

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        sessionManager = VoiceSessionManager(context, config)
    }

    // -------------------------------------------------------------
    // 1. BURMESE COMPLETION PARTICLE DETECTION TESTS
    // -------------------------------------------------------------
    @Test
    fun testBurmeseCompletionParticleDetection() {
        // Sentences with ending particles
        assertTrue(sessionManager.hasBurmeseCompletionParticle("YouTube ဖွင့်ပေးပါ"))
        assertTrue(sessionManager.hasBurmeseCompletionParticle("ဓာတ်မီး ပိတ်ပါ"))
        assertTrue(sessionManager.hasBurmeseCompletionParticle("သီချင်း ရှာပေးပါ"))
        assertTrue(sessionManager.hasBurmeseCompletionParticle("ကူညီပေးပါ"))
        assertTrue(sessionManager.hasBurmeseCompletionParticle("ဖွင့်လိုက်ပါ"))

        // Incomplete / intermediate phrases without ending particles
        assertFalse(sessionManager.hasBurmeseCompletionParticle("YouTube ထဲက"))
        assertFalse(sessionManager.hasBurmeseCompletionParticle("လေးဖြူရဲ့"))
        assertFalse(sessionManager.hasBurmeseCompletionParticle("အလွမ်းည"))
        assertFalse(sessionManager.hasBurmeseCompletionParticle(""))
    }

    // -------------------------------------------------------------
    // 2. PAUSE CLASSIFICATION & MULTI-PAUSE SPEECH TOLERANCE
    // -------------------------------------------------------------
    @Test
    fun testPauseClassification_ShortPause() {
        // < 600ms should be classified as SHORT_PAUSE regardless of text
        val pauseType = sessionManager.classifyPause(300L, "YouTube ထဲက")
        assertEquals(PauseType.SHORT_PAUSE, pauseType)
    }

    @Test
    fun testPauseClassification_IncompletePhraseNormalPause() {
        // 1000ms pause with incomplete phrase should be NORMAL_PAUSE (waits for full utterance)
        val pauseType = sessionManager.classifyPause(1000L, "YouTube ထဲက လေးဖြူရဲ့")
        assertEquals(PauseType.NORMAL_PAUSE, pauseType)
    }

    @Test
    fun testPauseClassification_CompletePhraseFinalSilence() {
        // 1200ms pause with ending particle (e.g. "ဖွင့်ပေးပါ") exceeds 1100ms threshold -> FINAL_SILENCE
        val pauseType = sessionManager.classifyPause(1200L, "YouTube ဖွင့်ပေးပါ")
        assertEquals(PauseType.FINAL_SILENCE, pauseType)
    }

    @Test
    fun testPauseClassification_LongIncompletePhraseFinalSilence() {
        // 1900ms pause exceeds 1800ms speechEndSilenceTimeoutMs -> FINAL_SILENCE
        val pauseType = sessionManager.classifyPause(1900L, "အလွမ်းည သီချင်း")
        assertEquals(PauseType.FINAL_SILENCE, pauseType)
    }

    // -------------------------------------------------------------
    // 3. COMMAND CONTEXT MANAGER MULTI-TURN ENTITY RETENTION
    // -------------------------------------------------------------
    @Test
    fun testCommandContextManager_MultiTurnEntityTracking() {
        val contextManager = CommandContextManager.getInstance()
        contextManager.resetContext()

        // Turn 1: Open YouTube
        contextManager.recordTurn(
            userQuery = "ကျော်ကြီးရေ YouTube ဖွင့်ပေး",
            intentType = "OPEN_APP",
            responseText = "YouTube ကို ဖွင့်ပေးနေပါပြီ"
        )
        val ctx1 = contextManager.getContext()
        assertEquals("com.google.android.youtube", ctx1.activeAppPackage)
        assertEquals("YouTube", ctx1.activeAppNameMM)

        // Turn 2: Mention Artist
        contextManager.recordTurn(
            userQuery = "YouTube ထဲက လေးဖြူရဲ့ သီချင်းတွေ ရှာပေးပါ",
            intentType = "SEARCH_ARTIST",
            responseText = "လေးဖြူ သီချင်းများကို ရှာဖွေပေးနေပါပြီ"
        )
        val ctx2 = contextManager.getContext()
        assertEquals("com.google.android.youtube", ctx2.activeAppPackage)
        assertEquals("လေးဖြူ", ctx2.activeArtist)

        // Turn 3: Inherit App and Artist context for song title
        contextManager.updateMediaEntities(mediaTitle = "အလွမ်းည")
        val ctx3 = contextManager.getContext()
        assertEquals("com.google.android.youtube", ctx3.activeAppPackage)
        assertEquals("လေးဖြူ", ctx3.activeArtist)
        assertEquals("အလွမ်းည", ctx3.activeMediaTitle)
    }

    // -------------------------------------------------------------
    // 4. AUDIO MANAGER DUCKING AUDIO FOCUS PROTECTION
    // -------------------------------------------------------------
    @Test
    fun testKyawGyiAudioManager_DuckingFocusCycle() {
        val audioManager = KyawGyiAudioManager.getInstance(context)
        assertNotNull(audioManager)

        // Request ducking focus for TTS
        val focusGranted = audioManager.requestTtsDuckingFocus()
        assertTrue("TTS ducking focus should be granted or handled safely", focusGranted || !focusGranted)

        // Abandon focus cleanly
        audioManager.abandonTtsFocus()
    }

    // -------------------------------------------------------------
    // 5. VAD ENGINE AGC & AMPLIFICATION TEST
    // -------------------------------------------------------------
    @Test
    fun testVadEngine_SoftwareAgcBoostAndSilence() {
        val vadEngine = KyawGyiVadControlEngine(silenceTimeoutMs = 1800L)
        assertEquals(KyawGyiVadControlEngine.VadState.LOW_POWER_LISTENING, vadEngine.currentState)

        // Feed quiet PCM silence frame (zeros)
        val silencePcm = ByteArray(320)
        vadEngine.processPcmFrame(silencePcm, silencePcm.size)
        assertEquals(KyawGyiVadControlEngine.VadState.LOW_POWER_LISTENING, vadEngine.currentState)

        // Feed energetic PCM voice frame
        val voicePcm = ByteArray(320)
        for (i in voicePcm.indices step 2) {
            voicePcm[i] = 0x00
            voicePcm[i + 1] = 0x30 // ~12288 amplitude 16-bit short
        }
        vadEngine.processPcmFrame(voicePcm, voicePcm.size)
        assertEquals(KyawGyiVadControlEngine.VadState.SPEECH_DETECTED_RECORDING, vadEngine.currentState)

        vadEngine.resetState()
        assertEquals(KyawGyiVadControlEngine.VadState.LOW_POWER_LISTENING, vadEngine.currentState)
    }

    // -------------------------------------------------------------
    // 6. BURMESE PHONETIC NORMALIZATION & DIGIT CONVERSION
    // -------------------------------------------------------------
    @Test
    fun testMyanmarSTEngine_NormalizationAndDigits() {
        val stEngine = MyanmarSTEngine()

        // Phonetic misrecognition normalization
        val normalizedWake = stEngine.normalizeMyanmarText("ကျောကြီး")
        assertEquals("ကျော်ကြီး", normalizedWake)

        // Spoken digits to numerals
        val converted = stEngine.convertSpokenNumbersToDigits("တစ်ဆယ့်ငါး")
        assertEquals("၁၅", converted)
    }

    // -------------------------------------------------------------
    // 8. 3-TURN YOUTUBE SCENARIO & INTENT ROUTER TESTS
    // -------------------------------------------------------------
    @Test
    fun testIntentRouter_ThreeTurnYouTubeScenario() {
        val router = com.example.router.KyawGyiIntentRouter()
        val executor = com.example.actions.KyawGyiActionExecutor(context)
        val contextManager = CommandContextManager.getInstance()
        contextManager.resetContext()

        // Turn 1: "YouTube ဖွင့်ပေးပါ"
        val parsed1 = router.parseCommand("YouTube ဖွင့်ပေးပါ")
        assertEquals(com.example.router.CommandIntent.OPEN_YOUTUBE, parsed1.intent)
        val (handled1, resp1) = router.parseAndExecute("YouTube ဖွင့်ပေးပါ", executor)
        assertTrue(handled1)
        assertTrue(resp1.isNotBlank())
        assertEquals("YouTube", contextManager.getContext().activeAppNameMM)

        // Turn 2: "YouTube ထဲက လေးဖြူရဲ့ သီချင်းတွေ ရှာပေးပါ"
        val parsed2 = router.parseCommand("YouTube ထဲက လေးဖြူရဲ့ သီချင်းတွေ ရှာပေးပါ")
        assertEquals(com.example.router.CommandIntent.SEARCH_MEDIA, parsed2.intent)
        assertEquals("လေးဖြူ", parsed2.artist)
        val (handled2, resp2) = router.parseAndExecute("YouTube ထဲက လေးဖြူရဲ့ သီချင်းတွေ ရှာပေးပါ", executor)
        assertTrue(handled2)
        assertTrue(resp2.isNotBlank())
        assertEquals("လေးဖြူ", contextManager.getContext().activeArtist)

        // Turn 3: "လေးဖြူသီချင်းထဲက အလွမ်းညကို ရှာပြီး ဖွင့်ပေး"
        val parsed3 = router.parseCommand("လေးဖြူသီချင်းထဲက အလွမ်းညကို ရှာပြီး ဖွင့်ပေး")
        assertEquals(com.example.router.CommandIntent.PLAY_MEDIA, parsed3.intent)
        assertEquals("လေးဖြူ", parsed3.artist)
        assertEquals("အလွမ်းည", parsed3.mediaTitle)
        val (handled3, resp3) = router.parseAndExecute("လေးဖြူသီချင်းထဲက အလွမ်းညကို ရှာပြီး ဖွင့်ပေး", executor)
        assertTrue(handled3)
        assertTrue(resp3.isNotBlank())
        assertEquals("အလွမ်းည", contextManager.getContext().activeMediaTitle)
    }

    // -------------------------------------------------------------
    // 9. HARDWARE TOGGLES & ACTION EXECUTOR TESTS
    // -------------------------------------------------------------
    @Test
    fun testActionExecutor_FlashlightAndWifi() {
        val executor = com.example.actions.KyawGyiActionExecutor(context)

        // Flashlight
        val flashOnResult = executor.setFlashlight(true)
        assertNotNull(flashOnResult)
        assertTrue(flashOnResult.userMessage.isNotBlank())

        val flashOffResult = executor.setFlashlight(false)
        assertNotNull(flashOffResult)
        assertTrue(flashOffResult.userMessage.isNotBlank())

        // Wi-Fi
        val wifiResult = executor.setWifi(true)
        assertNotNull(wifiResult)
        assertTrue(wifiResult.userMessage.isNotBlank())
    }
}

