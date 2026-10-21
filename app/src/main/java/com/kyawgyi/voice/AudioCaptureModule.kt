package com.kyawgyi.voice

import android.content.Context
import android.util.Log
import com.example.audio.CentralAudioRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Immutable 16kHz 16-bit Mono PCM audio frame buffer emitted by [AudioCaptureModule].
 * Pre-computes normalized [-1.0f, 1.0f] float samples for model-based STT engines (e.g., Sherpa-ONNX Zipformer)
 * alongside RMS energy and dBFS acoustic telemetry.
 */
data class AudioBufferPacket(
    val sequenceNumber: Long,
    val pcmBytes: ByteArray,
    val normalizedFloatSamples: FloatArray,
    val sampleRateHz: Int = AudioCaptureModule.SAMPLE_RATE_HZ,
    val rmsEnergy: Double,
    val rmsDbFs: Float,
    val zeroCrossingRate: Float,
    val isSpeechFrame: Boolean,
    val timestampMs: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AudioBufferPacket) return false
        return sequenceNumber == other.sequenceNumber &&
            sampleRateHz == other.sampleRateHz &&
            timestampMs == other.timestampMs &&
            pcmBytes.contentEquals(other.pcmBytes)
    }

    override fun hashCode(): Int {
        var result = sequenceNumber.hashCode()
        result = 31 * result + pcmBytes.contentHashCode()
        result = 31 * result + sampleRateHz
        result = 31 * result + timestampMs.hashCode()
        return result
    }
}

/**
 * Thread-safe capture state exposed by [AudioCaptureModule] for UI observation.
 */
data class AudioCaptureModuleState(
    val isCapturing: Boolean = false,
    val sampleRateHz: Int = AudioCaptureModule.SAMPLE_RATE_HZ,
    val frameSizeBytes: Int = AudioCaptureModule.DEFAULT_FRAME_BYTES,
    val emittedPacketsCount: Long = 0L,
    val emittedBytesCount: Long = 0L,
    val lastRmsEnergy: Double = 0.0,
    val lastRmsDbFs: Float = -90f,
    val isSpeechDetected: Boolean = false,
    val activeSourceTag: String = "IDLE",
    val activeInputRoute: String = AudioInputRoute.BUILTIN_MIC.displayLabel,
    val preRollBufferedMs: Int = 0,
    val isMicrophoneMuted: Boolean = false,
    val isAppOpsPrivacyBlocked: Boolean = false,
    val isCallPreempted: Boolean = false,
    val bufferPoolHitRatioPercent: Float = 100f,
    val currentAgcGain: Float = 1.0f,
    val isAudioDuckingActive: Boolean = false,
    val errorMessage: String? = null
)

/**
 * AudioCaptureModule (Layer 1 Streaming Audio Producer for Model-Based STT)
 *
 * Captures and normalizes 16kHz 16-bit Little-Endian Mono PCM audio buffers from
 * hardware microphone capture ([CentralAudioRecorder]) or injected PCM streams,
 * converting each frame into both raw PCM bytes and normalized [-1.0f, 1.0f] float waveforms
 * required by neural transducers like Sherpa-ONNX Zipformer.
 *
 * Integrates Requirements 9, 10 & 12 audio pipeline components:
 * - [AudioInputRouter] for Bluetooth SCO / BLE / Wired / Built-in mic routing
 * - [SincResampler] for anti-aliased resampling to 16kHz
 * - [DynamicAgc] & [DualPathDsp] for noise-gated AGC and split VAD/ASR conditioning
 * - [PreRollPcmRingBuffer] for 500ms circular pre-roll lookback buffering
 * - [MicrophoneMuteMonitor] & [SystemMicPrivacyMonitor] for hardware mute & Android 12+ privacy detection
 * - [AudioBufferPool] for zero-GC buffer recycling
 * - [TelephonyPreemption] & [AudioDuckingManager] for call preemption and media ducking
 *
 * Guarantees thread-safe [StateFlow] updates via atomic [MutableStateFlow.update].
 */
class AudioCaptureModule(
    private val context: Context,
    private val centralAudioRecorder: CentralAudioRecorder = CentralAudioRecorder.getInstance(context)
) {

    val audioInputRouter = AudioInputRouter(context)
    val sincResampler = SincResampler()
    val dynamicAgc = DynamicAgc()
    val dualPathDsp = DualPathDsp(sampleRateHz = SAMPLE_RATE_HZ)
    val preRollRingBuffer = PreRollPcmRingBuffer(sampleRateHz = SAMPLE_RATE_HZ, preRollDurationMs = 500)
    val microphoneMuteMonitor = MicrophoneMuteMonitor(context)
    val systemMicPrivacyMonitor = SystemMicPrivacyMonitor(context)
    val audioBufferPool = AudioBufferPool(defaultByteBufferSize = DEFAULT_FRAME_BYTES)
    val telephonyPreemption = TelephonyPreemption(context)
    val audioDuckingManager = AudioDuckingManager(context)

    companion object {
        private const val TAG = "AudioCaptureModule"
        const val SAMPLE_RATE_HZ = 16000
        const val DEFAULT_FRAME_BYTES = 1280 // 40ms @ 16kHz 16-bit Mono (640 samples)
        private const val SPEECH_RMS_THRESHOLD = 220.0
        private const val CONSUMER_ID = "AudioCaptureModule_ModelStt"

        @Volatile
        private var instance: AudioCaptureModule? = null

        fun getInstance(context: Context): AudioCaptureModule {
            return instance ?: synchronized(this) {
                instance ?: AudioCaptureModule(context.applicationContext).also { instance = it }
            }
        }

        /**
         * Converts 16-bit Little-Endian PCM bytes into a [AudioBufferPacket] with
         * normalized float samples in [-1.0f, 1.0f], RMS energy, dBFS, and ZCR.
         */
        fun buildPacketFromPcm(
            pcmBytes: ByteArray,
            sequenceNumber: Long,
            sampleRateHz: Int = SAMPLE_RATE_HZ
        ): AudioBufferPacket {
            val sampleCount = pcmBytes.size / 2
            if (sampleCount <= 0) {
                return AudioBufferPacket(
                    sequenceNumber = sequenceNumber,
                    pcmBytes = pcmBytes,
                    normalizedFloatSamples = FloatArray(0),
                    sampleRateHz = sampleRateHz,
                    rmsEnergy = 0.0,
                    rmsDbFs = -90f,
                    zeroCrossingRate = 0f,
                    isSpeechFrame = false
                )
            }

            val shorts = ShortArray(sampleCount)
            ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)

            val floats = FloatArray(sampleCount)
            var sumSquares = 0.0
            var zeroCrossings = 0
            var prevSample: Short = 0

            for (i in 0 until sampleCount) {
                val s = shorts[i]
                floats[i] = (s / 32768.0f).coerceIn(-1.0f, 1.0f)
                sumSquares += s.toDouble() * s.toDouble()
                if (i > 0 && ((s >= 0 && prevSample < 0) || (s < 0 && prevSample >= 0))) {
                    zeroCrossings++
                }
                prevSample = s
            }

            val rms = sqrt(sumSquares / sampleCount)
            val normalizedRms = (rms / 32768.0).coerceIn(1e-9, 1.0)
            val dbFs = (20.0 * log10(normalizedRms)).toFloat().coerceIn(-90f, 0f)
            val zcr = zeroCrossings.toFloat() / sampleCount.toFloat()
            val isSpeech = rms >= SPEECH_RMS_THRESHOLD && zcr in 0.01f..0.45f

            return AudioBufferPacket(
                sequenceNumber = sequenceNumber,
                pcmBytes = pcmBytes.copyOf(),
                normalizedFloatSamples = floats,
                sampleRateHz = sampleRateHz,
                rmsEnergy = rms,
                rmsDbFs = dbFs,
                zeroCrossingRate = zcr,
                isSpeechFrame = isSpeech
            )
        }

        /**
         * Synthesizes a realistic 16kHz 16-bit Mono PCM speech-like harmonic frame
         * for deterministic streaming pipeline verification and UI diagnostics.
         */
        fun generateSpeechHarmonicPcmFrame(
            frameIndex: Int,
            amplitude: Int = 4200,
            fundamentalFreqHz: Double = 165.0
        ): ByteArray {
            val sampleCount = DEFAULT_FRAME_BYTES / 2
            val buffer = ByteBuffer.allocate(DEFAULT_FRAME_BYTES).order(ByteOrder.LITTLE_ENDIAN)
            val phaseOffset = frameIndex * sampleCount
            for (i in 0 until sampleCount) {
                val t = (phaseOffset + i).toDouble() / SAMPLE_RATE_HZ
                // Fundamental + 2nd & 3rd voice harmonics with natural envelope modulation
                val carrier = sin(2.0 * Math.PI * fundamentalFreqHz * t) +
                    0.45 * sin(2.0 * Math.PI * (fundamentalFreqHz * 2.1) * t) +
                    0.25 * sin(2.0 * Math.PI * (fundamentalFreqHz * 3.2) * t)
                val scaled = (carrier * 0.58 * amplitude).toInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                    .toShort()
                buffer.putShort(scaled)
            }
            return buffer.array()
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val captureMutex = Mutex()
    private val isActiveCapture = AtomicBoolean(false)
    private val packetCounter = AtomicLong(0L)
    private val totalBytesCounter = AtomicLong(0L)
    private var collectorJob: Job? = null

    private val _captureState = MutableStateFlow(AudioCaptureModuleState())
    val captureState: StateFlow<AudioCaptureModuleState> = _captureState.asStateFlow()

    private val _audioBufferFlow = MutableSharedFlow<AudioBufferPacket>(
        replay = 1,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val audioBufferFlow: SharedFlow<AudioBufferPacket> = _audioBufferFlow.asSharedFlow()

    private val _rawPcmFlow = MutableSharedFlow<ByteArray>(
        replay = 1,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val rawPcmFlow: SharedFlow<ByteArray> = _rawPcmFlow.asSharedFlow()

    /**
     * Starts streaming audio buffers from hardware [CentralAudioRecorder] and emits
     * normalized [AudioBufferPacket] frames to subscribed STT engines.
     */
    suspend fun startCapture(consumerTag: String = CONSUMER_ID): Boolean = captureMutex.withLock {
        if (isActiveCapture.get()) {
            Log.d(TAG, "AudioCaptureModule already capturing (tag=$consumerTag).")
            return@withLock true
        }

        isActiveCapture.set(true)
        audioInputRouter.activateRoute()
        val routeSnapshot = audioInputRouter.routeState.value
        val muteSnapshot = microphoneMuteMonitor.refreshHardwareMuteState()
        val hardwareStarted = centralAudioRecorder.startRecording(consumerTag)

        _captureState.update { current ->
            current.copy(
                isCapturing = true,
                activeSourceTag = if (hardwareStarted) "HARDWARE_MIC_16KHZ" else "STREAM_BUFFER_BUS",
                activeInputRoute = routeSnapshot.activeRoute.displayLabel,
                preRollBufferedMs = preRollRingBuffer.getBufferedDurationMs(),
                isMicrophoneMuted = muteSnapshot.isEffectivelyMuted,
                errorMessage = muteSnapshot.warningMessage
            )
        }

        collectorJob?.cancel()
        collectorJob = scope.launch {
            try {
                centralAudioRecorder.audioSharedFlow.collect { pcmChunk ->
                    if (!isActiveCapture.get()) return@collect
                    emitAudioBuffer(pcmChunk)
                }
            } catch (e: Exception) {
                Log.w(TAG, "AudioCaptureModule stream collection ended: ${e.message}")
            }
        }

        Log.i(TAG, "AudioCaptureModule started (hardwareMic=$hardwareStarted)")
        return@withLock true
    }

    /**
     * Stops streaming audio capture and releases the consumer registration.
     */
    suspend fun stopCapture(consumerTag: String = CONSUMER_ID) = captureMutex.withLock {
        if (!isActiveCapture.getAndSet(false)) return@withLock
        collectorJob?.cancel()
        collectorJob = null
        try {
            centralAudioRecorder.stopRecording(consumerTag)
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping CentralAudioRecorder: ${e.message}")
        }

        _captureState.update { current ->
            current.copy(
                isCapturing = false,
                isSpeechDetected = false,
                activeSourceTag = "IDLE"
            )
        }
        Log.i(TAG, "AudioCaptureModule stopped.")
    }

    /**
     * Thread-safely ingests a 16-bit Mono PCM chunk (automatically resampling via [SincResampler]
     * if [inputSampleRateHz] != 16kHz), updates [PreRollPcmRingBuffer] and [MicrophoneMuteMonitor],
     * updates [captureState], and emits to [audioBufferFlow] and [rawPcmFlow].
     */
    fun emitAudioBuffer(
        pcmBytes: ByteArray,
        inputSampleRateHz: Int = SAMPLE_RATE_HZ
    ): AudioBufferPacket {
        val normalized16kPcm = if (inputSampleRateHz != SAMPLE_RATE_HZ) {
            sincResampler.resamplePcm16Le(pcmBytes, inputSampleRateHz, SAMPLE_RATE_HZ)
        } else {
            pcmBytes
        }

        val agcPcm = dynamicAgc.processPcm16Le(normalized16kPcm)
        val dspFrame = dualPathDsp.processFrame(agcPcm, agcGainMultiplier = 1.0f)

        val pooledBuf = audioBufferPool.acquireByteBuffer(dspFrame.asrPathPcmBytes.size)
        System.arraycopy(dspFrame.asrPathPcmBytes, 0, pooledBuf, 0, dspFrame.asrPathPcmBytes.size)

        val muteSnapshot = microphoneMuteMonitor.inspectPcmFrame(normalized16kPcm)
        val privacySnapshot = systemMicPrivacyMonitor.privacyState.value
        val callSnapshot = telephonyPreemption.state.value
        val seq = packetCounter.incrementAndGet()
        val totalBytes = totalBytesCounter.addAndGet(pooledBuf.size.toLong())
        val packet = buildPacketFromPcm(pooledBuf, seq, SAMPLE_RATE_HZ)
        audioBufferPool.releaseByteBuffer(pooledBuf)

        // Continuously keep the last 500ms in the pre-roll ring buffer prior to speech onset
        if (!packet.isSpeechFrame) {
            preRollRingBuffer.write(dspFrame.asrPathPcmBytes)
        }

        val routeLabel = audioInputRouter.routeState.value.activeRoute.displayLabel
        val preRollMs = preRollRingBuffer.getBufferedDurationMs()
        val poolHitRatio = audioBufferPool.getMetrics().hitRatioPercent

        _captureState.update { current ->
            current.copy(
                isCapturing = true,
                emittedPacketsCount = seq,
                emittedBytesCount = totalBytes,
                lastRmsEnergy = packet.rmsEnergy,
                lastRmsDbFs = packet.rmsDbFs,
                isSpeechDetected = packet.isSpeechFrame,
                activeInputRoute = routeLabel,
                preRollBufferedMs = preRollMs,
                isMicrophoneMuted = muteSnapshot.isEffectivelyMuted || privacySnapshot.isPrivacyBlocked,
                isAppOpsPrivacyBlocked = privacySnapshot.isPrivacyBlocked,
                isCallPreempted = callSnapshot.isInCallOrRinging,
                bufferPoolHitRatioPercent = poolHitRatio,
                currentAgcGain = dynamicAgc.currentGain,
                isAudioDuckingActive = audioDuckingManager.state.value.isDuckingActive,
                errorMessage = muteSnapshot.warningMessage ?: current.errorMessage
            )
        }

        _rawPcmFlow.tryEmit(packet.pcmBytes)
        _audioBufferFlow.tryEmit(packet)
        return packet
    }

    /**
     * Emits a sequence of 16kHz speech-like harmonic frames followed by trailing silence frames
     * to trigger end-of-speech endpoint detection in streaming model-based STT engines.
     */
    suspend fun emitSimulatedSpeechStream(
        speechFrames: Int = 10,
        trailingSilenceFrames: Int = 4,
        frameIntervalMs: Long = 15L
    ) {
        _captureState.update {
            it.copy(isCapturing = true, activeSourceTag = "SIMULATED_16KHZ_PCM_STREAM", errorMessage = null)
        }
        for (i in 0 until speechFrames) {
            val pcm = generateSpeechHarmonicPcmFrame(
                frameIndex = i,
                amplitude = 4500 + (i % 3) * 600,
                fundamentalFreqHz = 155.0 + (i % 4) * 12.0
            )
            emitAudioBuffer(pcm)
            if (frameIntervalMs > 0) delay(frameIntervalMs)
        }
        val silenceFrame = ByteArray(DEFAULT_FRAME_BYTES)
        for (j in 0 until trailingSilenceFrames) {
            emitAudioBuffer(silenceFrame)
            if (frameIntervalMs > 0) delay(frameIntervalMs)
        }
    }

    fun resetTelemetry() {
        packetCounter.set(0L)
        totalBytesCounter.set(0L)
        _captureState.update {
            AudioCaptureModuleState(isCapturing = isActiveCapture.get())
        }
    }
}
