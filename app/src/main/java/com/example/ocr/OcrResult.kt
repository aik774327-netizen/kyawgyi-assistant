package com.example.ocr

import com.example.data.FailureCategory

/**
 * OCR RESULT DATA MODEL
 * Represents a strongly-typed, confidence-validated OCR extraction result.
 */
data class OcrResult(
    val text: String,
    val confidence: Float,                          // 0.0f to 1.0f
    val scriptType: MyanmarScriptDetector.ScriptType,
    val engineUsed: String,                         // "ML_KIT_LATIN", "GEMINI_MULTIMODAL_VISION", "ON_DEVICE_GLYPH_ANALYZER", "ACCESSIBILITY_BRIDGE"
    val processingTimeMs: Long,
    val isSuccess: Boolean,
    val failureCategory: FailureCategory = FailureCategory.NONE,
    val userMessageMm: String = "",
    val technicalDiagnostic: String = "",
    val isRepeatedFailure: Boolean = false
) {
    companion object {
        fun failure(
            category: FailureCategory,
            engineUsed: String,
            userMessageMm: String,
            technicalDiagnostic: String,
            isRepeated: Boolean = false,
            processingTimeMs: Long = 0L
        ): OcrResult {
            return OcrResult(
                text = "",
                confidence = 0.0f,
                scriptType = MyanmarScriptDetector.ScriptType.UNKNOWN,
                engineUsed = engineUsed,
                processingTimeMs = processingTimeMs,
                isSuccess = false,
                failureCategory = category,
                userMessageMm = userMessageMm,
                technicalDiagnostic = technicalDiagnostic,
                isRepeatedFailure = isRepeated
            )
        }

        fun success(
            text: String,
            confidence: Float,
            scriptType: MyanmarScriptDetector.ScriptType,
            engineUsed: String,
            userMessageMm: String,
            processingTimeMs: Long
        ): OcrResult {
            return OcrResult(
                text = text,
                confidence = confidence,
                scriptType = scriptType,
                engineUsed = engineUsed,
                processingTimeMs = processingTimeMs,
                isSuccess = true,
                failureCategory = FailureCategory.NONE,
                userMessageMm = userMessageMm,
                technicalDiagnostic = "SUCCESS: Extracted ${text.length} chars ($scriptType) with confidence $confidence"
            )
        }
    }
}
