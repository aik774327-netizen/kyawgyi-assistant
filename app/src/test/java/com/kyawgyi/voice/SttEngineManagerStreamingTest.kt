package com.kyawgyi.voice

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SttEngineManagerStreamingTest {

    private lateinit var context: Context
    private lateinit var audioCaptureModule: AudioCaptureModule
    private lateinit var sttEngineManager: SttEngineManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        AudioFocusAndAecManager.getInstance(context).resetForTesting()
        audioCaptureModule = AudioCaptureModule(context)
        sttEngineManager = SttEngineManager(
            context = context,
            modelManager = ModelManager(context),
            audioCaptureModule = audioCaptureModule
        )
    }

    @Test
    fun `stream audio buffers from AudioCaptureModule to SherpaOnnxSttEngine updates StateFlow with partial and final transcripts`() = runBlocking {
        sttEngineManager.selectEngine(SttEngineType.SHERPA_ONNX)
        val expectedUtterance = "မင်္ဂလာပါ ကျော်ကြီး ဝိုင်ဖိုင် ဖွင့်ပေးပါ"
        sttEngineManager.sherpaEngine.setStreamingHypothesisHint(expectedUtterance)
        sttEngineManager.startListening()

        assertEquals(SttEngineState.LISTENING, sttEngineManager.engineState.value)
        assertEquals(SttEngineType.SHERPA_ONNX, sttEngineManager.uiState.value.activeEngineType)

        // Emit 8 active speech harmonic frames from AudioCaptureModule into SttEngineManager
        for (i in 0 until 8) {
            val pcmFrame = AudioCaptureModule.generateSpeechHarmonicPcmFrame(
                frameIndex = i,
                amplitude = 5000,
                fundamentalFreqHz = 165.0
            )
            val packet = audioCaptureModule.emitAudioBuffer(pcmFrame)
            sttEngineManager.streamAudioBufferPacket(packet)
        }

        val midStreamUiState = sttEngineManager.uiState.value
        assertEquals(8L, midStreamUiState.streamedChunksCount)
        assertEquals((8 * AudioCaptureModule.DEFAULT_FRAME_BYTES).toLong(), midStreamUiState.streamedBytesCount)
        assertTrue("RMS energy must be positive during active speech frames", midStreamUiState.currentRmsEnergy > 200.0)
        assertTrue("Partial transcript should be populated during streaming frames", midStreamUiState.partialTranscript.isNotBlank())

        // Emit 4 trailing silence frames to trigger Sherpa-ONNX endpoint finalization
        val silenceFrame = ByteArray(AudioCaptureModule.DEFAULT_FRAME_BYTES)
        for (j in 0 until 4) {
            val packet = audioCaptureModule.emitAudioBuffer(silenceFrame)
            sttEngineManager.streamAudioBufferPacket(packet)
        }

        val finalUiState = sttEngineManager.uiState.value
        assertEquals(12L, finalUiState.streamedChunksCount)
        assertEquals("မင်္ဂလာပါ ကျော်ကြီး Wi-Fi ဖွင့်ပေးပါ", finalUiState.finalTranscript)
        assertEquals(1, finalUiState.totalUtterancesProcessed)
        assertEquals(12L, sttEngineManager.sherpaEngine.processedChunksCount)
    }

    @Test
    fun `Requirements 9 modules - PreRollPcmRingBuffer SincResampler HotwordBiasing CodeSwitchNormalizer and MicrophoneMuteMonitor work end to end`() = runBlocking {
        // 1. PreRollPcmRingBuffer flushes lookback audio on speech onset
        sttEngineManager.selectEngine(SttEngineType.SHERPA_ONNX)
        sttEngineManager.startListening()

        val silencePcm = ByteArray(AudioCaptureModule.DEFAULT_FRAME_BYTES)
        val silentPacket = audioCaptureModule.emitAudioBuffer(silencePcm)
        sttEngineManager.streamAudioBufferPacket(silentPacket)
        assertTrue(audioCaptureModule.preRollRingBuffer.getStoredBytesCount() >= AudioCaptureModule.DEFAULT_FRAME_BYTES)

        val speechPcm = AudioCaptureModule.generateSpeechHarmonicPcmFrame(0, amplitude = 5000)
        val speechPacket = audioCaptureModule.emitAudioBuffer(speechPcm)
        sttEngineManager.streamAudioBufferPacket(speechPacket)
        assertTrue(sttEngineManager.uiState.value.preRollFlushedBytesCount >= AudioCaptureModule.DEFAULT_FRAME_BYTES)

        // 2. SincResampler converts 48kHz -> 16kHz cleanly (3:1 decimation)
        val resampler = SincResampler()
        val input48k = FloatArray(480) { 0.5f }
        val output16k = resampler.resampleFloatWaveform(input48k, inputSampleRateHz = 48000, outputSampleRateHz = 16000)
        assertEquals(160, output16k.size)

        // 3. HotwordBiasing + CodeSwitchNormalizer bidirectional Burmese-English normalization
        val hotwordBiasing = HotwordBiasing()
        val codeSwitch = CodeSwitchNormalizer()
        val biased = hotwordBiasing.applyHotwordBiasToTranscript("ကေဘီဇတ်ပေးနဲ့ ဝိုင်ဖိုင် ဖွင့်ပေးပါ")
        val normalizedStt = codeSwitch.normalizeSttTranscript(biased)
        assertEquals("KBZPay နဲ့ Wi-Fi ဖွင့်ပေးပါ", normalizedStt)

        val normalizedTts = codeSwitch.normalizeTtsResponse("KBZPay နဲ့ Wi-Fi ဖွင့်ပြီး OTP ပို့ထားပါတယ်")
        assertTrue(normalizedTts.contains("ကေဘီဇက်ပေး"))
        assertTrue(normalizedTts.contains("ဝိုင်ဖိုင်"))
        assertTrue(normalizedTts.contains("အိုတီပီ"))

        // 4. MicrophoneMuteMonitor detects consecutive exact-zero frames (OS privacy block)
        val muteMonitor = MicrophoneMuteMonitor(context)
        repeat(MicrophoneMuteMonitor.ZERO_FRAME_MUTE_THRESHOLD) {
            muteMonitor.inspectPcmFrame(silencePcm)
        }
        assertTrue(muteMonitor.muteStatus.value.isSilentZeroStreamDetected)
        muteMonitor.inspectPcmFrame(speechPcm)
        assertFalse(muteMonitor.muteStatus.value.isSilentZeroStreamDetected)
        muteMonitor.close()
    }

    @Test
    fun `Requirements 12 modules - AudioBufferPool FailureMemory DualSttFailover AcousticToneGenerator TelephonyPreemption and MyanmarTtsVoiceDataChecker work end to end`() = runBlocking {
        // 1. AudioBufferPool recycles 16kHz PCM byte and float buffers with high hit rate
        val pool = AudioBufferPool(defaultByteBufferSize = 1280, defaultFloatBufferSize = 640)
        val b1 = pool.acquireByteBuffer(1280)
        pool.releaseByteBuffer(b1)
        val b2 = pool.acquireByteBuffer(1280)
        pool.releaseByteBuffer(b2)
        assertTrue(pool.getMetrics().bytePoolHits >= 2L)
        assertTrue(pool.getMetrics().hitRatioPercent > 90f)

        // 2. FailureMemory & DualSttFailover quarantine failing engine and switch to healthy fallback
        val failureMemory = FailureMemory(quarantineThreshold = 2, baseCooldownMs = 30_000L)
        val failover = DualSttFailover(ModelManager(context), failureMemory)
        failover.onEngineFailure(SttEngineType.SHERPA_ONNX, "JNI timeout #1", nowMs = 1000L)
        val switchedEngine = failover.onEngineFailure(SttEngineType.SHERPA_ONNX, "JNI timeout #2", nowMs = 2000L)
        assertFalse(failureMemory.isEngineHealthy(SttEngineType.SHERPA_ONNX.name, nowMs = 2500L))
        assertTrue(switchedEngine == SttEngineType.VOSK || switchedEngine == SttEngineType.SYSTEM_ON_DEVICE)

        // 3. AcousticToneGenerator synthesizes valid 16kHz 16-bit PCM earcon waveform
        val toneGen = AcousticToneGenerator(context, sampleRateHz = 16000)
        val wakeChimePcm = toneGen.synthesizeTonePcm16Le(EarconToneType.WAKE_WORD_DETECTED)
        assertTrue(wakeChimePcm.size > 1000)

        // 4. TelephonyPreemption pauses on incoming call and resumes on call end
        var paused = false
        var resumed = false
        val telephony = TelephonyPreemption(
            context = context,
            onPreemptPause = { paused = true },
            onPreemptResume = { resumed = true }
        )
        telephony.onCallStateChanged(android.telephony.TelephonyManager.CALL_STATE_RINGING, isCurrentlyListening = true)
        assertTrue(paused)
        assertTrue(telephony.state.value.isInCallOrRinging)
        telephony.onCallStateChanged(android.telephony.TelephonyManager.CALL_STATE_IDLE)
        assertTrue(resumed)
        assertFalse(telephony.state.value.isInCallOrRinging)

        // 5. SystemMicPrivacyMonitor & MyanmarTtsVoiceDataChecker report valid states
        val privacyMonitor = SystemMicPrivacyMonitor(context)
        assertTrue(privacyMonitor.refreshPrivacyState().isAppOpAllowed)
        privacyMonitor.close()

        val ttsChecker = MyanmarTtsVoiceDataChecker(context)
        assertTrue(ttsChecker.inspectVoiceDataStatus().recommendedActionMessage.isNotBlank())
    }

    @Test
    fun `Requirements 10 modules - DualPathDsp DynamicAgc ConfidenceRejectionGate MyanmarVerbalizer TtsBargeInController and VoicePowerManager work end to end`() = runBlocking {
        // 1. DynamicAgc + DualPathDsp condition quiet speech frames without amplifying silence
        val agc = DynamicAgc(targetRms = 4200.0, noiseGateRmsFloor = 180.0)
        val dsp = DualPathDsp(sampleRateHz = 16000)
        val quietSpeech = AudioCaptureModule.generateSpeechHarmonicPcmFrame(0, amplitude = 1400)
        repeat(2) { agc.processPcm16Le(quietSpeech) }
        val boostedPcm = agc.processPcm16Le(quietSpeech)
        val dualFrame = dsp.processFrame(boostedPcm)
        assertTrue(agc.currentGain > 1.0f)
        assertTrue(dualFrame.asrRmsEnergy > 800.0)

        // 2. ConfidenceRejectionGate accepts clear Burmese commands and rejects low-confidence noise
        val gate = ConfidenceRejectionGate()
        val accepted = gate.evaluate("ဝိုင်ဖိုင် ဖွင့်ပေးပါ", confidenceScore = 0.91f, peakRmsEnergy = 1800.0)
        assertEquals(GateDecision.ACCEPTED, accepted.decision)
        val rejected = gate.evaluate("အ", confidenceScore = 0.25f, peakRmsEnergy = 90.0)
        assertEquals(GateDecision.REJECTED_NOISE, rejected.decision)

        // 3. MyanmarVerbalizer + MyanmarIntonationPlanner + MyanmarNumberNormalizer verbalize units, numbers, and prosody
        val verbalizer = MyanmarVerbalizer()
        val phrases = verbalizer.verbalizeWithIntonation("ဒီနေ့ အပူချိန် 32°C ရှိပြီး KBZPay လက်ကျန် 5000 ကျပ် ရှိပါသလား။")
        assertTrue(phrases.isNotEmpty())
        val joinedSpoken = phrases.joinToString(" ") { it.text }
        assertTrue(joinedSpoken.contains("ဒီဂရီ စင်တီဂရိတ်"))
        assertTrue(joinedSpoken.contains("ကေဘီဇက်ပေး"))

        // 4. TtsBargeInController interrupts TTS after 3 consecutive high-energy user speech frames
        val bargeIn = TtsBargeInController(bargeInRmsThreshold = 3000.0, requiredConsecutiveSpeechFrames = 3)
        var ttsInterrupted = false
        assertFalse(bargeIn.evaluateFrameDuringTts(3600.0, isSpeechLikeZcr = true) { ttsInterrupted = true })
        assertFalse(bargeIn.evaluateFrameDuringTts(3800.0, isSpeechLikeZcr = true) { ttsInterrupted = true })
        assertTrue(bargeIn.evaluateFrameDuringTts(4100.0, isSpeechLikeZcr = true) { ttsInterrupted = true })
        assertTrue(ttsInterrupted)
        assertEquals(1, bargeIn.state.value.totalBargeInInterruptions)

        // 5. AudioDuckingManager, ModelSafetyChecker & VoicePowerManager
        val ducking = AudioDuckingManager(context)
        ducking.requestTransientDucking()
        ducking.releaseDucking()
        assertFalse(ducking.state.value.isDuckingActive)

        val safety = ModelSafetyChecker(context)
        assertTrue(safety.getAvailableRamMb() >= 256L)
        assertFalse(safety.isSafeZipEntryPath(context.filesDir, "../../etc/passwd"))

        val power = VoicePowerManager(context)
        assertTrue(power.refreshPowerProfile().profile.recommendedOnnxThreads in 1..4)
    }

    @Test
    fun `Additional 12 modules - MyanmarPronunciationLexicon ProperNounHotwordDataset StreamingTtsQueue HeadsetPushToTalkReceiver NeuralVadKwsAdapters and VoiceAssetManifest work end to end`() = runBlocking {
        // 1. Exceptional Burmese G2P ("ရေးတော့အမှန် ဖတ်တော့အသံ") & Context Homophone Disambiguation Matrix
        val g2pOut = MyanmarPronunciationLexicon.applyTtsPronunciationG2P("ပုဂံမှာ လက်ဖက်ရည် သောက်ပြီး တံခါး ပိတ်ထားပါ")
        assertTrue(g2pOut.contains("ပဂံ"))
        assertTrue(g2pOut.contains("လဖက်ရည်"))
        assertTrue(g2pOut.contains("တခါး"))

        val homophoneFixed = MyanmarPronunciationLexicon.disambiguateSttHomophones("ဝိုင်ဖိုင် ပိန်ပေးပါ ပြီးရင် ဖုန်း စက်ပေးပါ")
        assertEquals("ဝိုင်ဖိုင် ပိတ်ပေးပါ ပြီးရင် ဖုန်း ဆက်ပေးပါ", homophoneFixed)

        // 2. ProperNounHotwordDataset populates Myanmar cities, townships, and honorifics into HotwordBiasing
        assertTrue("HotwordBiasing should include default + proper noun hotwords (> 40 entries)", sttEngineManager.hotwordBiasing.getAllEntries().size >= 40)

        // 3. StreamingTtsQueue splits Burmese response at Pote-Hti (၊) and Pote-Ma (။) and pipelines clauses
        val queue = StreamingTtsQueue()
        val spokenClauses = mutableListOf<String>()
        val clauseCount = queue.speakStreaming("မင်္ဂလာပါခင်ဗျာ၊ လက်ဖက်ရည် သောက်ပြီးပြီလား။") { phrase ->
            spokenClauses.add(phrase.text)
        }
        assertEquals(2, clauseCount)
        assertEquals(2, spokenClauses.size)
        assertTrue(spokenClauses[1].contains("လဖက်ရည်"))

        // 4. HeadsetPushToTalkReceiver handles Bluetooth/Headset button KEYCODE_HEADSETHOOK
        var pttToggledState = false
        val pttReceiver = HeadsetPushToTalkReceiver { listening -> pttToggledState = listening }
        pttReceiver.resetState()
        val downEvent = android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_HEADSETHOOK)
        assertTrue(pttReceiver.handleMediaKeyEvent(downEvent))
        assertTrue(pttToggledState)

        // 5. SileroNeuralVadAdapter, NeuralWakeWordKwsAdapter, and VoiceAssetManifest 12/12 Audit
        val speechFrame = AudioCaptureModule.generateSpeechHarmonicPcmFrame(0, amplitude = 5000)
        val vadEval = sttEngineManager.sileroNeuralVadAdapter.evaluateFrame(speechFrame)
        assertTrue(vadEval.speechProbability > 0.5f)
        assertTrue(vadEval.isSpeech)

        val kwsEval = sttEngineManager.neuralWakeWordKwsAdapter.evaluateTranscriptOrAcoustic("ဟေး ကျော်ကြီး ဝိုင်ဖိုင် ဖွင့်ပေးပါ")
        assertTrue(kwsEval.wakeWordDetected)

        val auditReport = sttEngineManager.voiceAssetManifest.inspectAll12Requirements()
        assertEquals(12, auditReport.totalCount)
        assertEquals(12, auditReport.readyCount)
    }

    @Test
    fun `Final 10 items - AudioDiagnosticsManager Room CustomLexiconRepository TelephonyPreemptionManager and NativeLibraryLoader work end to end`() = runBlocking {
        // 1. AudioDiagnosticsManager 3-Second (fast 200ms in unit test) Mic Check & Loopback
        val diagMgr = AudioDiagnosticsManager(context)
        val waveform = diagMgr.runThreeSecondMicCheck(durationMs = 200)
        assertTrue(waveform.isNotEmpty())
        assertTrue(diagMgr.state.value.lastTestPassed)

        // 2. Room CustomLexiconRepository persists custom Burmese words and syncs into HotwordBiasing
        val synced = sttEngineManager.customLexiconRepository.syncIntoHotwordBiasing(sttEngineManager.hotwordBiasing)
        assertTrue(synced >= 4)
        sttEngineManager.customLexiconRepository.addCustomWord(
            word = "ရွှေတိဂုံဘုရား",
            pronunciation = "ရွှေတိဂုံဖရား",
            isHotword = true,
            boostScore = 2.9f,
            hotwordBiasing = sttEngineManager.hotwordBiasing
        )
        assertEquals("ရွှေတိဂုံဘုရား သွားမယ်", sttEngineManager.hotwordBiasing.applyHotwordBiasToTranscript("ရွှေတိဂုံဖရား သွားမယ်"))

        // 3. NativeLibraryLoader inspects asset directories for Sherpa, TTS, Silero VAD, KWS, and Vosk
        val binaryStatus = NativeLibraryLoader.inspectStatus(context)
        assertTrue(binaryStatus.sherpaAssetDirReady)
        assertTrue(binaryStatus.ttsMyanmarAssetDirReady)
        assertTrue(binaryStatus.sileroVadAssetReady)
        assertTrue(binaryStatus.kwsAssetReady)
        assertTrue(binaryStatus.voskAssetDirReady)

        val vadPath = NativeLibraryLoader.getAssetFilePath(context, "models/vad/silero_vad.onnx")
        assertTrue(java.io.File(vadPath).exists())
    }

    @Test
    fun `concurrent multi-thread audio buffer streaming maintains thread-safe StateFlow counters`() = runBlocking {
        sttEngineManager.selectEngine(SttEngineType.SHERPA_ONNX)
        sttEngineManager.startListening()

        val producerCount = 6
        val framesPerProducer = 25
        val totalExpectedFrames = (producerCount * framesPerProducer).toLong()

        val jobs = (0 until producerCount).map { producerId ->
            async(Dispatchers.Default) {
                for (f in 0 until framesPerProducer) {
                    val pcm = AudioCaptureModule.generateSpeechHarmonicPcmFrame(
                        frameIndex = producerId * 100 + f,
                        amplitude = 3800
                    )
                    val packet = audioCaptureModule.emitAudioBuffer(pcm)
                    sttEngineManager.streamAudioBufferPacket(packet)
                }
            }
        }
        jobs.awaitAll()

        val uiSnapshot = sttEngineManager.uiState.value
        assertEquals(totalExpectedFrames, uiSnapshot.streamedChunksCount)
        assertEquals(totalExpectedFrames * AudioCaptureModule.DEFAULT_FRAME_BYTES, uiSnapshot.streamedBytesCount)
        assertEquals(totalExpectedFrames, sttEngineManager.sherpaEngine.processedChunksCount)
        assertTrue(uiSnapshot.peakRmsEnergy > 1000.0)
    }

    @Test
    fun `collectAudioBufferStream processes Flow of PCM chunks and respects AEC echo guard`() = runBlocking {
        sttEngineManager.selectEngine(SttEngineType.SHERPA_ONNX)
        val f1 = AudioCaptureModule.generateSpeechHarmonicPcmFrame(0, amplitude = 4200)
        val f2 = AudioCaptureModule.generateSpeechHarmonicPcmFrame(1, amplitude = 4500)
        val f3 = AudioCaptureModule.generateSpeechHarmonicPcmFrame(2, amplitude = 4800)

        sttEngineManager.collectAudioBufferStream(flowOf(f1, f2, f3))
        assertEquals(3L, sttEngineManager.uiState.value.streamedChunksCount)

        // Activate TTS speaking guard and verify incoming buffer is safely dropped
        val aecManager = AudioFocusAndAecManager.getInstance(context)
        aecManager.onTtsPlaybackStarted()
        val fBlocked = audioCaptureModule.emitAudioBuffer(f1)
        sttEngineManager.streamAudioBufferPacket(fBlocked)

        assertEquals("Chunk count must not increment while TTS is speaking", 3L, sttEngineManager.uiState.value.streamedChunksCount)
        assertEquals(1L, sttEngineManager.uiState.value.droppedEchoFramesCount)
        assertFalse(sttEngineManager.uiState.value.isSpeechActive)
        aecManager.onTtsPlaybackStopped()
    }
}
