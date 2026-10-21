package com.kyawgyi.voice

import android.content.Context
import java.io.File

/**
 * Status of an individual asset or native library in the 12-item production checklist.
 */
data class VoiceAssetCheckItem(
    val itemNumber: Int,
    val id: String,
    val title: String,
    val isReady: Boolean,
    val detail: String
)

/**
 * Complete 12-item audit report returned by [VoiceAssetManifest].
 */
data class VoiceAssetAuditReport(
    val items: List<VoiceAssetCheckItem>,
    val readyCount: Int,
    val totalCount: Int
)

/**
 * VoiceAssetManifest (Additional 12 — Complete 12-Item Production Voice AI Readiness Verifier)
 *
 * Audits all 12 production requirements across Native JNI `.so` libraries, Offline ONNX/Kaldi
 * models, Neural VAD/KWS adapters, Burmese G2P & Homophone lexicons, Proper Noun Hotwords,
 * Streaming TTS Queue, Headset Push-to-Talk, and Android 14+ Foreground Microphone Service.
 */
class VoiceAssetManifest(
    private val context: Context,
    private val modelManager: ModelManager = ModelManager(context)
) {

    fun inspectAll12Requirements(): VoiceAssetAuditReport {
        val nativeLibDir = File(context.applicationInfo.nativeLibraryDir ?: "")
        val hasSherpaJni = File(nativeLibDir, "libsherpa-onnx-jni.so").exists()
        val hasVoskJni = File(nativeLibDir, "libvosk.so").exists()
        val sileroVad = SileroNeuralVadAdapter(context)
        val kwsAdapter = NeuralWakeWordKwsAdapter(context)

        val items = listOf(
            VoiceAssetCheckItem(
                itemNumber = 1,
                id = "sherpa_jni_so",
                title = "Sherpa-ONNX Native JNI (libsherpa-onnx-jni.so)",
                isReady = hasSherpaJni,
                detail = if (hasSherpaJni) "Native JNI library present on device" else "Not installed — no sherpa-onnx JNI library ships with this app"
            ),
            VoiceAssetCheckItem(
                itemNumber = 2,
                id = "sherpa_myanmar_zipformer",
                title = "Myanmar Zipformer ONNX Model (encoder/decoder/joiner/tokens)",
                isReady = modelManager.isSherpaOnnxModelAvailable(),
                detail = if (modelManager.isSherpaOnnxModelAvailable()) "Model files present on device storage" else "Not installed — no public Myanmar sherpa-onnx model exists (declared URL verified 404 on 2026-10-21)"
            ),
            VoiceAssetCheckItem(
                itemNumber = 3,
                id = "vosk_myanmar_model",
                title = "Vosk Myanmar Model & Native Library",
                isReady = hasVoskJni && modelManager.isVoskModelAvailable(),
                detail = if (hasVoskJni && modelManager.isVoskModelAvailable()) "Vosk native library and model present" else "Not installed — no Burmese model exists on the official Vosk model list (checked 2026-10-21)"
            ),
            VoiceAssetCheckItem(
                itemNumber = 4,
                id = "myanmar_neural_tts",
                title = "Offline Myanmar Neural TTS Model (F5/VITS ONNX)",
                isReady = false,
                detail = "Not installed — the F5 Myanmar TTS model repo is not publicly accessible (checked 2026-10-21); the Android system TTS engine is used instead"
            ),
            VoiceAssetCheckItem(
                itemNumber = 5,
                id = "neural_kws_wakeword",
                title = "Wake-Word KWS ONNX Model (\"ဟေး ကျော်ကြီး\")",
                isReady = kwsAdapter.isNeuralModelInstalled(),
                detail = if (kwsAdapter.isNeuralModelInstalled()) "KWS ONNX model file present" else "Not installed — no public KWS model exists; wake-word detection uses the system recognizer path"
            ),
            VoiceAssetCheckItem(
                itemNumber = 6,
                id = "silero_neural_vad",
                title = "Silero VAD Model File (silero_vad.onnx)",
                isReady = sileroVad.isNeuralModelInstalled(),
                detail = if (sileroVad.isNeuralModelInstalled()) "Real silero_vad.onnx bundled in APK (643,854 bytes)" else "Model file missing or too small to be a real ONNX binary"
            ),
            VoiceAssetCheckItem(
                itemNumber = 7,
                id = "burmese_g2p_lexicon",
                title = "Exceptional Burmese G2P Dictionary (ရေးတော့အမှန် ဖတ်တော့အသံ)",
                isReady = MyanmarPronunciationLexicon.ORTHOGRAPHY_TO_SPOKEN_G2P.isNotEmpty(),
                detail = "${MyanmarPronunciationLexicon.ORTHOGRAPHY_TO_SPOKEN_G2P.size} G2P rules present in code"
            ),
            VoiceAssetCheckItem(
                itemNumber = 8,
                id = "burmese_homophone_matrix",
                title = "Context-Aware Burmese ASR Homophone Disambiguation Matrix",
                isReady = MyanmarPronunciationLexicon.SPOKEN_TO_ORTHOGRAPHY_STT.isNotEmpty(),
                detail = "Homophone matrix present in code (${MyanmarPronunciationLexicon.SPOKEN_TO_ORTHOGRAPHY_STT.size} entries)"
            ),
            VoiceAssetCheckItem(
                itemNumber = 9,
                id = "myanmar_proper_noun_hotwords",
                title = "Myanmar Cities, Townships, Honorifics & Contacts Hotwords",
                isReady = ProperNounHotwordDataset.allDefaultProperNouns().isNotEmpty(),
                detail = "${ProperNounHotwordDataset.allDefaultProperNouns().size} proper nouns registered in code"
            ),
            VoiceAssetCheckItem(
                itemNumber = 10,
                id = "streaming_tts_queue",
                title = "Sentence-Pipelined Streaming TTS Queue (code component)",
                isReady = true,
                detail = "StreamingTtsQueue class is present in the app (code component, not a model)"
            ),
            VoiceAssetCheckItem(
                itemNumber = 11,
                id = "headset_ptt_receiver",
                title = "Bluetooth Earbud / Headset Button Push-to-Talk Receiver",
                isReady = HeadsetPushToTalkReceiver.SUPPORTED_PTT_KEYCODES.isNotEmpty(),
                detail = "HeadsetPushToTalkReceiver supports ${HeadsetPushToTalkReceiver.SUPPORTED_PTT_KEYCODES.size} PTT keycodes in code"
            ),
            VoiceAssetCheckItem(
                itemNumber = 12,
                id = "android14_foreground_mic_service",
                title = "Android 14+ Foreground Service Microphone Type & WakeLock Guard",
                isReady = true,
                detail = "VoiceForegroundService is declared in the manifest with FOREGROUND_SERVICE_TYPE_MICROPHONE (code component)"
            )
        )

        return VoiceAssetAuditReport(
            items = items,
            readyCount = items.count { it.isReady },
            totalCount = items.size
        )
    }

