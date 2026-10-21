package com.example.ocr

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.example.data.FailureCategory
import com.example.utils.NetworkMonitor
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * PRODUCTION HYBRID SCREEN OCR ENGINE
 *
 * အဆင့်ဆင့် စနစ်တကျ ဖတ်ရှုသော Hybrid OCR Pipeline:
 * Stage 1: On-Device Fast ML Kit Pass (Latin Scripts, Digits, English UI)
 * Stage 2: Script Classification & Garbage Rejection (MyanmarScriptDetector)
 * Stage 3: Myanmar Multimodal Vision OCR Pass (Gemini Multimodal Vision API for Burmese script & Flutter/Canvas)
 * Stage 4: On-Device Visual / Glyph Fallback & FailureMemoryEngine Logging (Zero Fake Success, Real Failure Auditing)
 */
object ScreenOcrEngine {

    private const val TAG = "ScreenOcrEngine"

    private val mlKitRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    /**
     * Primary synchronous-style suspending entry point for Screen OCR.
     */
    suspend fun processScreenBitmap(bitmap: Bitmap, context: Context? = null): OcrResult =
        withContext(Dispatchers.Default) {
            val totalStartTime = System.currentTimeMillis()

            // 1. Fast On-Device ML Kit Pass (Fastest path for English/Latin UI)
            val mlKitRaw = executeMlKitInternal(bitmap)
            val isGarbage = MyanmarScriptDetector.isProbableLatinOcrGarbage(mlKitRaw)
            val script = MyanmarScriptDetector.detectScript(mlKitRaw)

            // If ML Kit found valid Latin text and it is NOT garbage circular approximations
            if (mlKitRaw.isNotBlank() && !isGarbage && script == MyanmarScriptDetector.ScriptType.LATIN) {
                val duration = System.currentTimeMillis() - totalStartTime
                Log.i(TAG, "Fast-path: Latin text resolved via ML Kit in ${duration}ms")
                return@withContext OcrResult.success(
                    text = mlKitRaw,
                    confidence = 0.92f,
                    scriptType = MyanmarScriptDetector.ScriptType.LATIN,
                    engineUsed = "ML_KIT_LATIN",
                    userMessageMm = mlKitRaw,
                    processingTimeMs = duration
                )
            }

            // 2. If ML Kit found nothing, garbage, or contains Myanmar Script:
            // Route to Multimodal Vision OCR for accurate Myanmar Unicode extraction
            val isOnline = context?.let { NetworkMonitor.isInternetAvailable(it) } ?: true
            if (isOnline) {
                Log.i(TAG, "Invoking Myanmar Multimodal Vision OCR engine...")
                val visionEngine = MyanmarMultimodalVisionOcrEngine(context)
                val visionResult = visionEngine.extractText(bitmap)
                if (visionResult.isSuccess) {
                    return@withContext visionResult
                }
            } else {
                Log.w(TAG, "Device is offline. Cannot invoke online Multimodal Myanmar OCR.")
            }

            // 3. Fallback to On-Device Glyph Engine (Zero fake results, truthful diagnostics)
            val offlineResult = OnDeviceMyanmarGlyphEngine.evaluateOffline(bitmap)

            // 4. Check repeat failure warning
            val repeatWarning = FailureMemoryEngine.getRepeatWarningIfFailed("SCREEN_OCR", "EXTRACT_TEXT")
            val finalResult = if (repeatWarning != null) {
                offlineResult.copy(
                    isRepeatedFailure = true,
                    userMessageMm = "$repeatWarning\n\n${offlineResult.userMessageMm}"
                )
            } else {
                offlineResult
            }

            return@withContext finalResult
        }

    /**
     * Executes ML Kit Latin text recognition directly on a software bitmap.
     */
    private suspend fun executeMlKitInternal(bitmap: Bitmap): String =
        suspendCancellableCoroutine { continuation ->
            try {
                val softwareBitmap = if (bitmap.config == Bitmap.Config.HARDWARE) {
                    bitmap.copy(Bitmap.Config.ARGB_8888, false)
                } else {
                    bitmap
                }

                if (softwareBitmap == null) {
                    Log.e(TAG, "Failed to copy hardware bitmap for ML Kit")
                    continuation.resume("")
                    return@suspendCancellableCoroutine
                }

                val image = InputImage.fromBitmap(softwareBitmap, 0)
                mlKitRecognizer.process(image)
                    .addOnSuccessListener { visionText ->
                        val resultBuilder = StringBuilder()
                        for (block in visionText.textBlocks) {
                            val blockText = block.text.trim()
                            if (blockText.isNotEmpty()) {
                                resultBuilder.append(blockText).append("\n")
                            }
                        }
                        continuation.resume(resultBuilder.toString().trim())
                    }
                    .addOnFailureListener { e ->
                        Log.w(TAG, "ML Kit recognition error: ${e.message}")
                        continuation.resume("")
                    }
                    .addOnCanceledListener {
                        continuation.resume("")
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error in executeMlKitInternal: ${e.message}", e)
                continuation.resume("")
            }
        }

    /**
     * Legacy helper method for existing codebase compatibility.
     * Returns extracted string directly (or empty string on failure).
     */
    suspend fun extractText(bitmap: Bitmap, context: Context? = null): String {
        val result = processScreenBitmap(bitmap, context)
        return if (result.isSuccess) result.text else ""
    }

    /**
     * Asynchronous callback API for Services (Accessibility / Voice Session).
     */
    fun extractTextAsync(bitmap: Bitmap, context: Context? = null, onComplete: (OcrResult) -> Unit) {
        CoroutineScope(Dispatchers.Default).launch {
            val result = processScreenBitmap(bitmap, context)
            withContext(Dispatchers.Main) {
                onComplete(result)
            }
        }
    }
}
