package com.kyawgyi.voice

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Snapshot of Native JNI `.so` and ONNX asset readiness across Items #6–#10.
 */
data class NativeAndModelBinaryStatus(
    val isOnnxRuntimeLoaded: Boolean,
    val isSherpaOnnxJniLoaded: Boolean,
    val isVoskJniLoaded: Boolean,
    val sherpaAssetDirReady: Boolean,
    val ttsMyanmarAssetDirReady: Boolean,
    val sileroVadAssetReady: Boolean,
    val kwsAssetReady: Boolean,
    val voskAssetDirReady: Boolean,
    val statusSummary: String
)

/**
 * NativeLibraryLoader (Items #6–#10 — Native C++ `.so` Shared Library Loader & Asset Path Resolver)
 *
 * Safely loads `libonnxruntime.so`, `libsherpa-onnx-jni.so`, and `libvosk.so` when present in
 * `jniLibs/arm64-v8a/` without crashing the app if the native binaries are not yet bundled,
 * and resolves asset paths for:
 * - Item #7: `assets/models/myanmar/` (`encoder.onnx`, `decoder.onnx`, `joiner.onnx`, `tokens.txt`)
 * - Item #8: `assets/models/tts-myanmar/` (`f5_myanmar_dit_fp16.onnx`, `vocos_24k.onnx`, `vocab.txt`)
 * - Item #9: `assets/models/vad/silero_vad.onnx` & `assets/models/kws/kws-hey-kyawgyi.onnx`
 * - Item #10: `assets/models/vosk-model-small-my/` (`am/final.mdl`, `graph/HCLr.fst`, `graph/Gr.fst`, `graph/words.txt`)
 */
class NativeLibraryLoader {

    companion object {
        private const val TAG = "NativeLoader"

        @Volatile
        var isOnnxRuntimeLoaded: Boolean = false
            private set

        @Volatile
        var isSherpaJniLoaded: Boolean = false
            private set

        @Volatile
        var isVoskJniLoaded: Boolean = false
            private set

        fun loadSherpaOnnx(): Boolean {
            return try {
                System.loadLibrary("onnxruntime")
                isOnnxRuntimeLoaded = true
                System.loadLibrary("sherpa-onnx-jni")
                isSherpaJniLoaded = true
                Log.i(TAG, "Sherpa-ONNX Native Shared Libraries Loaded Successfully.")
                true
            } catch (e: UnsatisfiedLinkError) {
                Log.i(TAG, "Sherpa-ONNX native .so not bundled in jniLibs yet; using streaming/system fallback (${e.message}).")
                false
            }
        }

        fun loadVosk(): Boolean {
            return try {
                System.loadLibrary("vosk")
                isVoskJniLoaded = true
                Log.i(TAG, "Vosk Native Shared Library (libvosk.so) Loaded Successfully.")
                true
            } catch (e: UnsatisfiedLinkError) {
                Log.i(TAG, "Vosk native .so not bundled in jniLibs; Vosk engine unavailable (${e.message}).")
                false
            }
        }

        /**
         * Resolves or extracts an asset file (e.g. `models/vad/silero_vad.onnx`) into internal
         * `filesDir` so native C++ ONNX Runtime can open it by filesystem path.
         */
        fun getAssetFilePath(context: Context, assetRelativePath: String): String {
            val outFile = File(context.filesDir, assetRelativePath)
            if (outFile.exists() && outFile.length() > 0L) {
                return outFile.absolutePath
            }
            runCatching {
                outFile.parentFile?.mkdirs()
                context.assets.open(assetRelativePath).use { input ->
                    outFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
            return outFile.absolutePath
        }

        fun inspectStatus(context: Context): NativeAndModelBinaryStatus {
            // Readiness means a *usable* model file (>= 1 KB of real content),
            // not merely a file with the right name. The former text-placeholder
            // asset files were deleted on 2026-10-22, so these checks now
            // truthfully report false for the offline STT/TTS/KWS models.
            val sherpaReady = hasUsableAsset(context, "models/myanmar/encoder.onnx")
            val ttsReady = hasUsableAsset(context, "models/tts-myanmar/model.onnx")
            val vadReady = hasUsableAsset(context, "models/vad/silero_vad.onnx")
            val kwsReady = hasUsableAsset(context, "models/kws/kws-hey-kyawgyi.onnx")
            val voskReady = hasUsableAsset(context, "models/vosk-model-small-my/am/final.mdl")

            // Factual summary only — no "Verified" claims for states that
            // were never actually verified.
            val summary = "Native JNI loaded: onnxruntime=$isOnnxRuntimeLoaded, sherpa=$isSherpaJniLoaded, vosk=$isVoskJniLoaded; usable model assets: sherpa=$sherpaReady, tts=$ttsReady, vad=$vadReady, kws=$kwsReady, vosk=$voskReady"

            return NativeAndModelBinaryStatus(
                isOnnxRuntimeLoaded = isOnnxRuntimeLoaded,
                isSherpaOnnxJniLoaded = isSherpaJniLoaded,
                isVoskJniLoaded = isVoskJniLoaded,
                sherpaAssetDirReady = sherpaReady,
                ttsMyanmarAssetDirReady = ttsReady,
                sileroVadAssetReady = vadReady,
                kwsAssetReady = kwsReady,
                voskAssetDirReady = voskReady,
                statusSummary = summary
            )
        }

        private fun hasUsableAsset(context: Context, relativePath: String): Boolean =
            runCatching {
                context.assets.open(relativePath).use { input ->
                    val buffer = ByteArray(8192)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        total += read
                        if (total >= 1024L) return@use true
                    }
                    total >= 1024L
                }
            }.getOrDefault(false)
    }
}
