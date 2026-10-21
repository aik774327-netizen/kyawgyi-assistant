package com.example.ocr

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import com.example.data.FailureCategory
import com.example.security.api.ApiKeyQuotaManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

/**
 * MYANMAR MULTIMODAL VISION OCR ENGINE
 *
 * Uses Gemini Multimodal Vision API to perform high-precision verbatim
 * transcription of Myanmar Unicode, mixed English/Burmese, and complex layout UI text.
 */
class MyanmarMultimodalVisionOcrEngine(private val context: Context? = null) {

    companion object {
        private const val TAG = "MyanmarVisionOcr"
        private const val MODEL_NAME = "gemini-2.5-flash"

        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        private val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Extracts Myanmar Unicode and screen text from a Bitmap.
     */
    suspend fun extractText(bitmap: Bitmap): OcrResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()

        // 1. Scale down large bitmaps to 1024px max dimension for fast transmission
        val scaledBitmap = scaleBitmapIfNeeded(bitmap, maxDimension = 1024)
        val base64Image = bitmapToBase64Jpeg(scaledBitmap, quality = 80)
        if (scaledBitmap != bitmap) {
            scaledBitmap.recycle()
        }

        if (base64Image.isBlank()) {
            val duration = System.currentTimeMillis() - startTime
            FailureMemoryEngine.recordFailure(
                component = "SCREEN_OCR",
                operation = "BASE64_ENCODE",
                category = FailureCategory.TECHNICAL,
                rootCauseMm = "မျက်နှာပြင် ပုံရိပ်အား Base64 သို့ ပြောင်းလဲခြင်း မအောင်မြင်ပါ",
                technicalDetails = "Bitmap to JPEG Base64 compression returned empty buffer",
                suggestedRemediationMm = "မျက်နှာပြင် ရယူမှု Buffer အား ပြန်လည်စစ်ဆေးပါ"
            )
            return@withContext OcrResult.failure(
                category = FailureCategory.TECHNICAL,
                engineUsed = "GEMINI_MULTIMODAL_VISION",
                userMessageMm = "မျက်နှာပြင် ပုံရိပ်အား ဖတ်ရှုရန် ပြင်ဆင်၍ မရပါခင်ဗျာ",
                technicalDiagnostic = "Base64 encoding failure",
                processingTimeMs = duration
            )
        }

        val payload = buildMultimodalPayload(base64Image)

        // 2. Resolve via ApiKeyQuotaManager if available
        val quotaManager = context?.let { ApiKeyQuotaManager.getInstance(it) }
            ?: ApiKeyQuotaManager.getInstance(null)

        if (quotaManager != null) {
            try {
                return@withContext quotaManager.executeWithRetry(
                    operationName = "Gemini Vision OCR",
                    maxRetries = 2
                ) { activeApiKey ->
                    executeVisionCall(activeApiKey, payload, startTime)
                }
            } catch (e: Exception) {
                val duration = System.currentTimeMillis() - startTime
                val isQuota = com.example.data.GeminiApiService.isQuotaOrRateLimitException(e)
                val record = FailureMemoryEngine.recordFailure(
                    component = "SCREEN_OCR",
                    operation = "GEMINI_VISION_CALL",
                    category = if (isQuota) FailureCategory.RESOURCE else FailureCategory.TECHNICAL,
                    rootCauseMm = if (isQuota) "Gemini API Quota ပြည့်သွားပါသည်" else "Gemini API ခေါ်ယူမှု မအောင်မြင်ပါ: ${e.message}",
                    technicalDetails = "${e.javaClass.simpleName}: ${e.message}",
                    suggestedRemediationMm = "ခေတ္တစောင့်ဆိုင်းပါ သို့မဟုတ် ကိုယ်ပိုင် API Key ထည့်သွင်းပါ"
                )
                return@withContext OcrResult.failure(
                    category = if (isQuota) FailureCategory.RESOURCE else FailureCategory.TECHNICAL,
                    engineUsed = "GEMINI_MULTIMODAL_VISION",
                    userMessageMm = if (isQuota) "Gemini API Quota ပြည့်နေသဖြင့် စာဖတ်၍ မရသေးပါခင်ဗျာ" else "မျက်နှာပြင် ဖတ်ရှုရာတွင် ချို့ယွင်းချက် ဖြစ်ပွားပါသည်",
                    technicalDiagnostic = "Exception: ${e.message}",
                    isRepeated = record.occurrences > 1,
                    processingTimeMs = duration
                )
            }
        }

        // Direct fallback when QuotaManager is null
        val directKey = BuildConfig.GEMINI_API_KEY
        if (directKey.isBlank() || directKey == "MY_GEMINI_API_KEY") {
            val duration = System.currentTimeMillis() - startTime
            val record = FailureMemoryEngine.recordFailure(
                component = "SCREEN_OCR",
                operation = "API_KEY_RESOLUTION",
                category = FailureCategory.RESOURCE,
                rootCauseMm = "Gemini API Key မရှိသေးသဖြင့် Online Myanmar Vision OCR အား ခေါ်ယူနိုင်ခြင်းမရှိပါ",
                technicalDetails = "No active Gemini API key configured",
                suggestedRemediationMm = "ဆက်တင်များထဲတွင် Gemini API Key ထည့်သွင်းပေးပါ"
            )
            return@withContext OcrResult.failure(
                category = FailureCategory.RESOURCE,
                engineUsed = "GEMINI_MULTIMODAL_VISION",
                userMessageMm = "မြန်မာစာဖတ်ရှုရန် Gemini API Key ထည့်သွင်းထားရန် လိုအပ်ပါသည်ခင်ဗျာ",
                technicalDiagnostic = "Missing Gemini API key",
                isRepeated = record.occurrences > 1,
                processingTimeMs = duration
            )
        }

        return@withContext executeVisionCall(directKey, payload, startTime)
    }

    private fun executeVisionCall(apiKey: String, payload: JSONObject, startTime: Long): OcrResult {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL_NAME:generateContent?key=$apiKey"
        val httpRequest = Request.Builder()
            .url(url)
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val response = client.newCall(httpRequest).execute()
        val duration = System.currentTimeMillis() - startTime
        val responseBody = response.body?.string().orEmpty()

        if (!response.isSuccessful) {
            val isQuota = response.code == 429
            val record = FailureMemoryEngine.recordFailure(
                component = "SCREEN_OCR",
                operation = "GEMINI_VISION_CALL",
                category = if (isQuota) FailureCategory.RESOURCE else FailureCategory.TECHNICAL,
                rootCauseMm = if (isQuota) "Gemini API Quota ပြည့်သွားပါသည် (HTTP 429)" else "Gemini API HTTP ${response.code} အမှားဖြစ်ပေါ်ပါသည်",
                technicalDetails = "HTTP ${response.code}: $responseBody",
                suggestedRemediationMm = "ခေတ္တစောင့်ဆိုင်းပါ သို့မဟုတ် အခြား API Key သို့ ပြောင်းလဲပါ"
            )
            return OcrResult.failure(
                category = if (isQuota) FailureCategory.RESOURCE else FailureCategory.TECHNICAL,
                engineUsed = "GEMINI_MULTIMODAL_VISION",
                userMessageMm = if (isQuota) "Gemini API Quota ပြည့်နေသဖြင့် စာဖတ်၍ မရသေးပါခင်ဗျာ" else "မျက်နှာပြင် ဖတ်ရှုရာတွင် ကွန်ရက် အမှားဖြစ်ပွားပါသည်",
                technicalDiagnostic = "HTTP ${response.code}: ${responseBody.take(120)}",
                isRepeated = record.occurrences > 1,
                processingTimeMs = duration
            )
        }

        val extractedText = parseGeminiResponse(responseBody).trim()
        val script = MyanmarScriptDetector.detectScript(extractedText)

        if (extractedText.isBlank()) {
            return OcrResult.failure(
                category = FailureCategory.PRAGMATIC_CONTEXTUAL,
                engineUsed = "GEMINI_MULTIMODAL_VISION",
                userMessageMm = "မျက်နှာပြင်ပေါ်တွင် ဖတ်ရှုရန် စာသား မတွေ့ရှိပါခင်ဗျာ",
                technicalDiagnostic = "Vision OCR succeeded but image contained no readable text",
                processingTimeMs = duration
            )
        }

        val confidence = when (script) {
            MyanmarScriptDetector.ScriptType.MYANMAR_UNICODE -> 0.95f
            MyanmarScriptDetector.ScriptType.MIXED -> 0.92f
            MyanmarScriptDetector.ScriptType.LATIN -> 0.90f
            MyanmarScriptDetector.ScriptType.UNKNOWN -> 0.50f
        }

        Log.i(TAG, "✅ Multimodal Myanmar OCR Success: ${extractedText.length} chars ($script) in ${duration}ms")

        return OcrResult.success(
            text = extractedText,
            confidence = confidence,
            scriptType = script,
            engineUsed = "GEMINI_MULTIMODAL_VISION",
            userMessageMm = extractedText,
            processingTimeMs = duration
        )
    }

    private fun buildMultimodalPayload(base64Image: String): JSONObject {
        val root = JSONObject()
        val contents = JSONArray()
        val contentObj = JSONObject()
        val parts = JSONArray()

        val textPromptPart = JSONObject().apply {
            put(
                "text",
                "Extract and transcribe all readable text from this mobile application screen snapshot verbatim. " +
                        "Strictly preserve Myanmar (Burmese) Unicode script, numbers, English labels, and text hierarchy. " +
                        "Do NOT summarize, comment, or format as markdown fences. Return ONLY the transcribed text verbatim."
            )
        }

        val inlineImagePart = JSONObject().apply {
            val inlineData = JSONObject().apply {
                put("mimeType", "image/jpeg")
                put("data", base64Image)
            }
            put("inlineData", inlineData)
        }

        parts.put(textPromptPart)
        parts.put(inlineImagePart)
        contentObj.put("parts", parts)
        contents.put(contentObj)
        root.put("contents", contents)

        return root
    }

    private fun parseGeminiResponse(responseJson: String): String {
        return try {
            val root = JSONObject(responseJson)
            val candidates = root.optJSONArray("candidates") ?: return ""
            if (candidates.length() == 0) return ""
            val firstCandidate = candidates.getJSONObject(0)
            val content = firstCandidate.optJSONObject("content") ?: return ""
            val parts = content.optJSONArray("parts") ?: return ""
            val sb = StringBuilder()
            for (i in 0 until parts.length()) {
                val part = parts.getJSONObject(i)
                sb.append(part.optString("text", ""))
            }
            sb.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing Gemini Vision response JSON: ${e.message}")
            ""
        }
    }

    private fun scaleBitmapIfNeeded(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxDimension && height <= maxDimension) {
            return if (bitmap.config == Bitmap.Config.HARDWARE) {
                bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: bitmap
            } else {
                bitmap
            }
        }

        val ratio = width.toFloat() / height.toFloat()
        val newWidth: Int
        val newHeight: Int
        if (width > height) {
            newWidth = maxDimension
            newHeight = (maxDimension / ratio).toInt()
        } else {
            newHeight = maxDimension
            newWidth = (maxDimension * ratio).toInt()
        }

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    private fun bitmapToBase64Jpeg(bitmap: Bitmap, quality: Int): String {
        return try {
            val outputStream = ByteArrayOutputStream()
            val softwareBitmap = if (bitmap.config == Bitmap.Config.HARDWARE) {
                bitmap.copy(Bitmap.Config.ARGB_8888, false)
            } else {
                bitmap
            } ?: return ""

            softwareBitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
            Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to compress bitmap to base64 JPEG: ${e.message}")
            ""
        }
    }
}
