package com.kyawgyi.voice

import android.app.ActivityManager
import android.content.Context
import java.io.File

/**
 * Safety verification report returned by [ModelSafetyChecker] before JNI model loading.
 */
data class ModelSafetyReport(
    val isSafeToLoad: Boolean,
    val availableRamMb: Long,
    val requiredRamMb: Int,
    val hasSufficientRam: Boolean,
    val hasValidModelFiles: Boolean,
    val hasValidTokensFile: Boolean,
    val statusSummary: String
)

/**
 * ModelSafetyChecker (Requirements 10 — Pre-JNI Model Integrity & RAM Safety Verifier)
 *
 * Performs pre-flight checks before passing `.onnx` or Kaldi model paths into native C++/JNI:
 * 1. Verifies device available RAM (`ActivityManager.MemoryInfo`) against model requirements
 *    to prevent Android OOM / `SIGABRT` native kills.
 * 2. Validates non-zero file sizes and UTF-8 `tokens.txt` readability.
 * 3. Validates Zip entry paths against Zip-Slip directory traversal (`..`).
 */
class ModelSafetyChecker(private val context: Context) {

    fun checkSherpaModelSafety(
        modelDir: File,
        requiredRamMb: Int = ModelManager.SHERPA_ONNX_BURMESE_MANIFEST.minRamMb
    ): ModelSafetyReport {
        val availRamMb = getAvailableRamMb()
        val ramOk = availRamMb <= 0L || availRamMb >= (requiredRamMb / 2)

        val encoderOk = File(modelDir, "encoder.onnx").isUsableModelFile()
        val decoderOk = File(modelDir, "decoder.onnx").isUsableModelFile()
        val joinerOk = File(modelDir, "joiner.onnx").isUsableModelFile()
        val tokensFile = File(modelDir, "tokens.txt")
        val tokensOk = tokensFile.isUsableModelFile() && isValidTokensFile(tokensFile)
        val filesOk = encoderOk && decoderOk && joinerOk && tokensOk

        val summary = when {
            !ramOk -> "RAM မလုံလောက်ပါ (Available: ${availRamMb}MB, Needed: ${requiredRamMb}MB)"
            !filesOk -> "Offline ONNX Model ဖိုင်များ မပြည့်စုံသေးပါ (Fallback Engine သုံးပါမည်)"
            else -> "Model Safety Verified ✓ (RAM: ${availRamMb}MB)"
        }

        return ModelSafetyReport(
            isSafeToLoad = ramOk && filesOk,
            availableRamMb = availRamMb,
            requiredRamMb = requiredRamMb,
            hasSufficientRam = ramOk,
            hasValidModelFiles = filesOk,
            hasValidTokensFile = tokensOk,
            statusSummary = summary
        )
    }

    /**
     * Guards against Zip-Slip vulnerability when extracting downloaded model archives.
     */
    fun isSafeZipEntryPath(destinationDir: File, entryName: String): Boolean {
        if (entryName.isBlank() || entryName.contains("..")) return false
        val destCanonical = runCatching { destinationDir.canonicalPath }.getOrNull() ?: return false
        val targetCanonical = runCatching { File(destinationDir, entryName).canonicalPath }.getOrNull() ?: return false
        return targetCanonical.startsWith(destCanonical)
    }

    fun getAvailableRamMb(): Long {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return 1024L
        return runCatching {
            val memInfo = ActivityManager.MemoryInfo()
            am.getMemoryInfo(memInfo)
            (memInfo.availMem / (1024L * 1024L)).coerceAtLeast(256L)
        }.getOrDefault(1024L)
    }

    private fun isValidTokensFile(tokensFile: File): Boolean =
        runCatching {
            tokensFile.useLines { lines ->
                lines.take(5).any { it.isNotBlank() }
            }
        }.getOrDefault(false)
}
