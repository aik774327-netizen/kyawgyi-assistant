package com.kyawgyi.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.ModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

interface DirectPcmRecognizer {
    fun prepare()
    fun start()
    fun stop()
    fun close()
}

/** Owns the only microphone AudioRecord in the voice pipeline. */
class CentralAudioRecorder(
    private val context: Context,
    private val scope: CoroutineScope,
    val sampleRate: Int = 16_000,
) {
    private val _dspPcmFlow = MutableSharedFlow<FloatArray>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val dspPcmFlow: SharedFlow<FloatArray> = _dspPcmFlow
    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running
    private var job: Job? = null
    private var recorder: AudioRecord? = null

    fun start() {
        check(job?.isActive != true) { "CentralAudioRecorder is already running" }
        check(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            "RECORD_AUDIO permission is required"
        }
        val min = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        check(min > 0) { "AudioRecord buffer size is invalid" }
        val samples = maxOf(sampleRate / 10, min / 2)
        val ar = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            samples * 2
        )
        recorder = ar
        job = scope.launch(Dispatchers.Default) {
            val shorts = ShortArray(samples)
            val floats = FloatArray(samples)
            try {
                ar.startRecording()
                _running.value = true
                while (true) {
                    ensureActive()
                    val n = ar.read(shorts, 0, shorts.size, AudioRecord.READ_BLOCKING)
                    if (n <= 0) continue
                    for (i in 0 until n) floats[i] = shorts[i] / 32768f
                    _dspPcmFlow.emit(floats.copyOf(n))
                }
            } finally {
                _running.value = false
                runCatching { ar.stop() }
                ar.release()
                if (recorder === ar) recorder = null
            }
        }
    }

    suspend fun stop() {
        job?.cancel()
        job?.join()
        job = null
    }

    fun close() {
        job?.cancel()
        _running.value = false
        recorder?.let { runCatching { it.release() } }
        recorder = null
    }
}

/** Fan-out helper: STT engines consume PCM without opening another microphone. */
class DirectPcmStreamFeed(
    private val recorder: CentralAudioRecorder,
    private val scope: CoroutineScope,
) {
    fun collect(onChunk: suspend (FloatArray) -> Unit): Job =
        scope.launch(Dispatchers.Default) {
            recorder.dspPcmFlow.collect { onChunk(it) }
        }
}

/** Streaming transducer wrapper fed by CentralAudioRecorder, not a second microphone. */
class SherpaOfflineSpeechRecognizer(
    private val modelDir: File,
    private val recorder: CentralAudioRecorder,
    private val scope: CoroutineScope,
    private val onPartial: (String) -> Unit,
    private val onFinal: (String) -> Unit,
) : DirectPcmRecognizer {
    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready
    private var recognizer: OnlineRecognizer? = null
    private var feedJob: Job? = null
    private var stream: com.k2fsa.sherpa.onnx.OnlineStream? = null
    private val endpointing = AdaptiveEndpointing()

    override fun prepare() {
        val required = listOf("encoder.onnx", "decoder.onnx", "joiner.onnx", "tokens.txt")
        require(required.all { File(modelDir, it).isFile && File(modelDir, it).length() > 0 }) {
            "Sherpa model files are missing in ${modelDir.absolutePath}"
        }
        val model = ModelConfig(
            tokens = File(modelDir, "tokens.txt").path,
            transducerEncoder = File(modelDir, "encoder.onnx").path,
            transducerDecoder = File(modelDir, "decoder.onnx").path,
            transducerJoiner = File(modelDir, "joiner.onnx").path,
            numThreads = 2,
            provider = "cpu",
            debug = false,
        )
        recognizer = OnlineRecognizer(
            OnlineRecognizerConfig(
                featConfig = FeatureConfig(sampleRate = 16_000, featureDim = 80),
                modelConfig = model,
                enableEndpoint = true,
                rule1MinTrailingSilence = 1.2f,
                rule2MinTrailingSilence = 1.5f,
                rule3MinUtteranceLength = 0.22f,
                decodingMethod = "greedy_search",
            )
        )
        stream = recognizer!!.createStream()
        _ready.value = true
    }

    override fun start() {
        check(_ready.value) { "Call prepare() first" }
        check(feedJob?.isActive != true) { "Recognizer already started" }
        val r = requireNotNull(recognizer)
        val s = requireNotNull(stream)
        endpointing.reset()
        feedJob = DirectPcmStreamFeed(recorder, scope).collect { pcm ->
            s.acceptWaveform(pcm, 16_000)
            while (r.isReady(s)) r.decode(s)
            val text = r.getResult(s).text.normalizedMyanmar()
            if (text.isNotBlank()) onPartial(text)
            if (endpointing.accept(pcm) || r.isEndpoint(s)) {
                if (text.isNotBlank()) onFinal(text)
                r.reset(s)
                endpointing.reset()
            }
        }
    }

    override fun stop() {
        feedJob?.cancel()
        feedJob = null
        stream?.let { recognizer?.reset(it) }
    }

    override fun close() {
        stop()
        stream = null
        recognizer = null
        _ready.value = false
    }
}
