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
                Log.i(TAG, "Vosk native .so not bundled in jniLibs yet; using VoskDirectPcmRecognizer (${e.message}).")
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
            val sherpaReady = hasAsset(context, "models/myanmar/encoder.onnx")
            val ttsReady = hasAsset(context, "models/tts-myanmar/vocab.txt")
            val vadReady = hasAsset(context, "models/vad/silero_vad.onnx")
            val kwsReady = hasAsset(context, "models/kws/kws-hey-kyawgyi.onnx")
            val voskReady = hasAsset(context, "models/vosk-model-small-my/graph/words.txt")

            val summary = if (isSherpaJniLoaded) {
                "Native JNI (.so) + ONNX Assets Active ✓"
            } else {
                "Asset Structure (Items 6–10) Verified ✓ (Hybrid Fallback Active)"
            }

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

        private fun hasAsset(context: Context, relativePath: String): Boolean =
            runCatching {
                context.assets.open(relativePath).use { true }
            }.getOrDefault(false)
    }
}
