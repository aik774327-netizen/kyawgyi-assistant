package com.kyawgyi.voice

import android.content.Context
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.example.actions.CommandResult
import com.example.actions.KyawGyiActionExecutor
import com.example.audit.CommandFailureAnalyzer
import com.example.audit.ErrorLearningWorkflowEngine
import com.example.nlu.BurmeseNluPreprocessor
import com.example.nlu.BurmeseNumberNormalizer
import com.example.nlu.BurmeseTtsTextNormalizer
import com.example.nlu.KyawGyiIntent
import com.example.nlu.LocalBurmeseIntentParser
import com.example.security.ActionPermissionGatekeeper
import com.kyawgyi.assistant.state.AssistantState
import com.kyawgyi.assistant.state.AssistantStateStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.PI
import kotlin.math.sin

/**
 * KyawGyiEndToEndVerificationTest
 *
 * Comprehensive End-to-End Integration & Verification Suite validating:
 * 1. Layer 1: KyawGyiVadDetector (VAD & Dynamic Endpointing) + AudioFocusAndAecManager (250ms Echo Hangover)
 * 2. Layer 1 & 2: KyawGyiWakeWordEngine ("ကျော်ကြီး" / "ဟယ်လို ကျော်ကြီး" KWS + Direct PCM Earcon)
 * 3. Layer 6: BurmeseNumberNormalizer, Code-Switching Phonetic Lexicon, BurmeseNluPreprocessor, BurmeseTtsTextNormalizer
 * 4. Layer 2 & 3 & 4: HybridPipelineController + LocalBurmeseIntentParser + AssistantStateStore FSM
 * 5. Cross-Layer AI Error-Learning & Failure Memory Workflow:
 *    - Automatic ER-001 recording on action failure + CommandFailureAnalyzer Room DB persistence
 *    - Mandatory 3-Factor Error Analysis (Technical Cause, Resource Limitation, Real-World Mismatch)
 *    - Failed-Method Memory blocking repeat execution with "This is a previously unsuccessful action."
 *    - Live Condition Verifier unblocking automatically when real OS/environment conditions resolve.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class KyawGyiEndToEndVerificationTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        ErrorLearningWorkflowEngine.initialize(context)
        ErrorLearningWorkflowEngine.clearAllForTesting()
        AssistantStateStore.getInstance().resetToIdle()
    }

    @Test(timeout = 20_000L)
    fun e2e_layer1ToLayer6_vadWakeWordCodeSwitchingNumberAndTtsNormalizer() {
        // ---------------------------------------------------------------------------------
        // 1. Layer 1: VAD & Dynamic Endpointing + AEC 250ms Echo Hangover
        // ---------------------------------------------------------------------------------
        var vadSpeechStarted = false
        var vadEndpointTriggered = false
        val vadDetector = KyawGyiVadDetector(
            speechSilenceThresholdMs = 200L,
            minSpeechDurationMs = 80L,
            maxUtteranceDurationMs = 5000L
        ).apply {
            onSpeechStarted = { vadSpeechStarted = true }
            onSpeechEndpoint = { vadEndpointTriggered = true }
        }

        vadDetector.startSession()

        // Generate 16kHz 16-bit Little-Endian PCM frame of 250Hz human-speech-band sine wave
        val voicePcm = ByteArray(1024)
        val shorts = ShortArray(512) { idx ->
            (sin(2.0 * PI * 250.0 * idx / 16000.0) * 8500).toInt().toShort()
        }
        ByteBuffer.wrap(voicePcm).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(shorts)

        vadDetector.processPcmChunk(voicePcm)
        Thread.sleep(110)
        vadDetector.processPcmChunk(voicePcm)
        assertTrue("VAD should detect speech onset", vadSpeechStarted)

        val silencePcm = ByteArray(1024)
        Thread.sleep(240)
        vadDetector.processPcmChunk(silencePcm)
        assertTrue("VAD should trigger dynamic silence endpoint", vadEndpointTriggered)

        // Verify AEC 250ms Echo Hangover Guard
        val aecManager = AudioFocusAndAecManager.getInstance(context)
        aecManager.setEchoHangoverDurationMs(250L)
        aecManager.onTtsPlaybackStarted()
        assertTrue(aecManager.isSpeaking)
        aecManager.onTtsPlaybackStopped()
        assertFalse(aecManager.isSpeaking)
        assertTrue("250ms post-TTS echo hangover should be active immediately after stop", aecManager.isEchoHangoverActive)

        // Wait for the 250ms echo hangover window to complete before normal microphone wake-word capture
        Thread.sleep(270)
        assertFalse("Echo hangover should expire after 250ms window", aecManager.isEchoHangoverActive)

        // ---------------------------------------------------------------------------------
        // 2. Layer 1 & 2: Hands-Free Wake Word Engine ("ဟယ်လို ကျော်ကြီး")
        // ---------------------------------------------------------------------------------
        val wakeEngine = KyawGyiWakeWordEngine(context = context, cooldownMs = 0L)
        val rawWakeCommand = "ဟယ်လို ကျော်ကြီး သုည ကိုး ခုနစ် ရှစ် ကိုး ငါး သုံး နှစ် တစ် သုည ကို ဖုန်းခေါ်ပေးပါ"
        val wakeDetection = wakeEngine.processTranscriptAndTrigger(rawWakeCommand, playEarcon = true)
        assertTrue("Wake word should be detected", wakeDetection.isDetected)
        assertTrue(wakeDetection.trailingCommand.contains("0978953210"))

        // ---------------------------------------------------------------------------------
        // 3. Layer 6: Spoken Number Normalizer & Code-Switching Phonetic Lexicon
        // ---------------------------------------------------------------------------------
        val normalizedCallCommand = BurmeseNluPreprocessor.normalize(wakeDetection.trailingCommand)
        assertEquals("0978953210", BurmeseNumberNormalizer.extractPhoneNumber(normalizedCallCommand))

        val codeSwitchedText = BurmeseNluPreprocessor.normalize("ယူကျု့ ဖွင့်ပေးပါ")
        assertEquals("ယူကျု ဖွင့်ပေးပါ", codeSwitchedText)

        // ---------------------------------------------------------------------------------
        // 4. Layer 2: LocalBurmeseIntentParser Verification
        // ---------------------------------------------------------------------------------
        val parser = LocalBurmeseIntentParser.getInstance()
        val parsedCall = parser.parse(normalizedCallCommand)
        assertEquals(KyawGyiIntent.CALL, parsedCall.intent)

        val parsedYoutube = parser.parse(codeSwitchedText)
        assertEquals(KyawGyiIntent.OPEN_APP, parsedYoutube.intent)
        assertEquals("YouTube", parsedYoutube.slots["app_name"])

        // ---------------------------------------------------------------------------------
        // 5. Layer 2 & 6: Myanmar TTS Text Normalizer & G2P Expansion
        // ---------------------------------------------------------------------------------
        val ttsExpanded = BurmeseTtsTextNormalizer.normalizeForTts("ဘက်ထရီ ၈၅% ကျန်ပါသေးတယ်၊ နေ့လည် ၁၂:၃၀ မှာ 15,000 Ks လွှဲရန်ရှိသည်")
        assertTrue("Percentage should expand to ရာခိုင်နှုန်း", ttsExpanded.contains("ရှစ်ဆယ့်ငါး ရာခိုင်နှုန်း"))
        assertTrue("Time 12:30 should expand to Burmese spoken time", ttsExpanded.contains("ဆယ့်နှစ်") && ttsExpanded.contains("နာရီ"))
        assertTrue("Currency 15,000 Ks should expand to တစ်သောင်းငါးထောင် ကျပ်", ttsExpanded.contains("တစ်သောင်းငါးထောင် ကျပ်"))
    }

    @Test
    fun e2e_pipelineFailure_recordsErrorLog_blocksRepeat_andUnblocksOnLiveConditionChange() = runTest {
        // Ensure Accessibility Service is initially OFF in Android Settings
        Settings.Secure.putString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ""
        )

        val executorCallCount = AtomicInteger(0)
        val spokenTtsOutputs = mutableListOf<String>()

        // Custom KyawGyiActionExecutor that checks real Accessibility setting for Wi-Fi toggle
        val controlledExecutor = object : KyawGyiActionExecutor(context) {
            override fun setWifi(enable: Boolean): CommandResult {
                executorCallCount.incrementAndGet()
                val enabledServices = Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                ) ?: ""
                return if (enabledServices.contains(context.packageName)) {
                    CommandResult(
                        isSuccess = true,
                        action = "SET_WIFI",
                        target = "WIFI",
                        userMessage = "ဝိုင်ဖိုင်ကို အောင်မြင်စွာ ဖွင့်ပေးလိုက်ပါပြီခင်ဗျာ။"
                    )
                } else {
                    CommandResult(
                        isSuccess = false,
                        action = "SET_WIFI",
                        target = "WIFI",
                        userMessage = "Accessibility service permission denied in Settings",
                        errorCode = "SecurityException: Accessibility service not enabled"
                    )
                }
            }
        }

        // Custom F5MyanmarTtsClient that captures spoken responses deterministically
        val capturingTtsClient = object : F5MyanmarTtsClient(context = context) {
            override suspend fun synthesizeAndPlay(text: String, onPlaybackFinished: (() -> Unit)?) {
                spokenTtsOutputs.add(text)
                onPlaybackFinished?.invoke()
            }
        }

        val wakeEngine = KyawGyiWakeWordEngine(context = context, cooldownMs = 0L)
        val controller = HybridPipelineController(
            context = context,
            sttEngineManager = SttEngineManager.getInstance(context),
            ttsClient = capturingTtsClient,
            actionExecutor = controlledExecutor,
            gatekeeper = ActionPermissionGatekeeper.getInstance(),
            stateStore = AssistantStateStore.getInstance(),
            intentParser = LocalBurmeseIntentParser.getInstance(),
            wakeWordEngine = wakeEngine
        )

        // ---------------------------------------------------------------------------------
        // STEP 1: First execution fails because Accessibility permission is disabled
        // ---------------------------------------------------------------------------------
        controller.processUtterance("ကျော်ကြီး ဝိုင်ဖိုင် ဖွင့်ပေးပါ")

        assertEquals("ActionExecutor should be invoked once on initial attempt", 1, executorCallCount.get())

        // Verify Error-Learning Engine recorded ER-001 with 3-Factor Analysis
        val logs = ErrorLearningWorkflowEngine.getAllErrorLogs()
        assertEquals("One Error Log (ER-001) must be recorded", 1, logs.size)
        val er001 = logs.first()
        assertEquals("ER-001", er001.errorId)
        assertEquals(
            ErrorLearningWorkflowEngine.ErrorTaxonomy.ACCESS_PERMISSION_ERROR,
            er001.errorType
        )
        assertTrue(er001.threeFactorAnalysis.technicalCause.isNotBlank())
        assertTrue(er001.threeFactorAnalysis.resourceLimitation.isNotBlank())
        assertTrue(er001.threeFactorAnalysis.realWorldMismatch.isNotBlank())

        // Verify CommandFailureAnalyzer also stored the failure in Room DB
        val dbWarning = CommandFailureAnalyzer.checkPreviousFailure(context, "ဝိုင်ဖိုင် ဖွင့်ပေးပါ")
        assertNotNull("CommandFailureAnalyzer should persist failure in Room DB", dbWarning)

        // ---------------------------------------------------------------------------------
        // STEP 2: Repeating the same command while condition is unchanged MUST BE BLOCKED
        // ---------------------------------------------------------------------------------
        spokenTtsOutputs.clear()
        controller.processUtterance("ဝိုင်ဖိုင် ဖွင့်ပေးပါ")

        assertEquals(
            "Failed method must NOT be re-executed when environment conditions have not changed",
            1,
            executorCallCount.get()
        )
        assertTrue(
            "Pipeline must warn with 'This is a previously unsuccessful action.'",
            spokenTtsOutputs.any { it.startsWith("This is a previously unsuccessful action.") }
        )

        // ---------------------------------------------------------------------------------
        // STEP 3: Real-world condition changes (User enables Accessibility Service)
        // Live Condition Verifier detects the change and automatically unblocks execution!
        // ---------------------------------------------------------------------------------
        Settings.Secure.putString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            "${context.packageName}/.service.KyawGyiAccessibilityService"
        )

        spokenTtsOutputs.clear()
        controller.processUtterance("ဝိုင်ဖိုင် ဖွင့်ပေးပါ")

        assertEquals(
            "Live Condition Verifier should unblock and execute the command now that Accessibility is enabled",
            2,
            executorCallCount.get()
        )
        assertTrue(
            "Success TTS message should be spoken",
            spokenTtsOutputs.any { it.contains("အောင်မြင်စွာ ဖွင့်ပေးလိုက်ပါပြီ") }
        )
        assertNull(
            "Command should no longer be blocked in Failed-Method Memory",
            ErrorLearningWorkflowEngine.evaluateRequestedAction(
                commandKey = "ဝိုင်ဖိုင် ဖွင့်ပေးပါ",
                context = context
            )
        )
        assertEquals(AssistantState.IDLE, AssistantStateStore.getInstance().getCurrentState())
    }

    @Test
    fun e2e_codec2ErrorClassification_andPersistentMemoryReload() {
        val entry = ErrorLearningWorkflowEngine.recordErrorLog(
            task = "Initialize Wake Word Earcon Cue",
            commandKey = "soundpool_load_wav_cue",
            expectedResult = "Play wake word earcon without native CCodec error",
            actualResult = "E/io.aiangel.core: Failed to query component interface for required system resources: 6",
            attemptedMethod = "SoundPool.load(cueFile.absolutePath, 1)",
            evidence = "09-26 19:35:18.644 12509 E/io.aiangel.core: Failed to query component interface for required system resources: 6"
        )

        assertEquals(
            ErrorLearningWorkflowEngine.ErrorTaxonomy.ENVIRONMENT_MISMATCH_ERROR,
            entry.errorType
        )
        assertTrue(entry.threeFactorAnalysis.technicalCause.contains("C2ResourcesNeededTuning"))
        assertTrue(entry.threeFactorAnalysis.realWorldMismatch.contains("AudioTrack"))

        // Verify persistence across engine re-initialization
        ErrorLearningWorkflowEngine.initialize(context)
        val warning = ErrorLearningWorkflowEngine.evaluateRequestedAction(commandKey = "soundpool_load_wav_cue")
        assertNotNull(warning)
        assertTrue(warning!!.warningBanner.startsWith("This is a previously unsuccessful action."))
    }
}
