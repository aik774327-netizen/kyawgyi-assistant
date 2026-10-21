package com.example.data

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import com.example.security.api.ApiKeyQuotaManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class GeminiQuotaExceededException(
    message: String = GeminiApiService.GEMINI_QUOTA_ERROR_MESSAGE,
    cause: Throwable? = null
) : IOException(message, cause)

/**
 * Thrown when a Gemini call is attempted but NO API key is configured at all.
 * Deliberately distinct from [GeminiQuotaExceededException]: before
 * 2026-10-22 the empty-pool case fabricated an "HTTP 429 Quota Exhausted"
 * error even though no request had ever been sent (FAKE-07).
 */
class NoApiKeyConfiguredException(
    message: String = GeminiApiService.NO_API_KEY_MESSAGE,
    cause: Throwable? = null
) : IOException(message, cause)


class GeminiApiService(private val context: Context? = null) {

    companion object {
        private const val TAG = "GeminiApiService"
        private const val MODEL_NAME = "gemini-3.5-flash"
        const val GEMINI_QUOTA_ERROR_MESSAGE = "Gemini API Quota ပြည့်သွားပါသဖြင့် ခေတ္တစောင့်ဆိုင်းပါ သို့မဟုတ် ကိုယ်ပိုင် API Key ထည့်ပါ"
        const val NO_API_KEY_MESSAGE = "API key မထည့်ရသေးပါ — Settings မှာ Gemini API Key ထည့်ပါ"
        const val CONNECTION_ERROR_MESSAGE = "Gemini ချိတ်ဆက်၍ မရပါ — API key ထည့်ထားခြင်း ရှိမရှိနဲ့ internet ကို စစ်ပါ"

        fun isNoApiKeyException(e: Throwable): Boolean {
            if (e is NoApiKeyConfiguredException) return true
            return e.cause?.let { isNoApiKeyException(it) } ?: false
        }


        fun isQuotaOrRateLimitException(e: Throwable): Boolean {
            val msg = (e.message ?: "").lowercase()
            return e is GeminiQuotaExceededException ||
                    msg.contains("429") ||
                    msg.contains("resource_exhausted") ||
                    msg.contains("quota_exceeded") ||
                    msg.contains("quota exceeded") ||
                    msg.contains("rate limit") ||
                    msg.contains("quota exhausted") ||
                    msg.contains("all in cooldown") ||
                    msg.contains("too many requests") ||
                    (e.cause?.let { isQuotaOrRateLimitException(it) } ?: false)
        }
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun generateResponse(
        prompt: String,
        mode: String,
        engine: String,
        systemPrompt: String,
        history: List<ChatMessageEntity> = emptyList()
    ): Pair<String, String?> = withContext(Dispatchers.IO) {
        val quotaManager = context?.let { ApiKeyQuotaManager.getInstance(it) }
            ?: ApiKeyQuotaManager.getInstance(null)

        if (quotaManager != null) {
            try {
                return@withContext quotaManager.executeWithRetry(
                    operationName = "Gemini Content Generation",
                    maxRetries = 3
                ) { activeApiKey ->
                    executeGeminiRequest(
                        apiKey = activeApiKey,
                        prompt = prompt,
                        mode = mode,
                        engine = engine,
                        systemPrompt = systemPrompt,
                        history = history
                    )
                }
            } catch (e: Exception) {
                if (isNoApiKeyException(e)) {
                    Log.w(TAG, "No Gemini API key configured: ${e.message}")
                    return@withContext Pair(
                        NO_API_KEY_MESSAGE,
                        "Error: NO_API_KEY_CONFIGURED - no Gemini API key has been added in Settings"
                    )
                }

                if (isQuotaOrRateLimitException(e)) {
                    Log.w(TAG, "Gemini API 429 Quota Exhausted: ${e.message}")
                    return@withContext Pair(
                        GEMINI_QUOTA_ERROR_MESSAGE,
                        "Error 429: RESOURCE_EXHAUSTED - Gemini API Quota ပြည့်သွားပါပြီ"
                    )
                }
                // Honest failure: never substitute canned local replies for a
                // real Gemini response (FAKE-06, removed 2026-10-22).
                Log.w(TAG, "Gemini request failed: ${e.message}")
                return@withContext Pair(
                    CONNECTION_ERROR_MESSAGE,
                    "Error: ${e.javaClass.simpleName}: ${e.message}"
                )

            }
        }

        // Direct fallback when QuotaManager is uninitialized
        val apiKey = BuildConfig.GEMINI_API_KEY
        val cleanApiKey = if (apiKey != "MY_GEMINI_API_KEY" && apiKey.isNotBlank()) apiKey else null
        if (cleanApiKey == null) {
            return@withContext Pair(
                NO_API_KEY_MESSAGE,
                "Error: NO_API_KEY_CONFIGURED - no Gemini API key has been added in Settings"
            )
        }

        try {
            executeGeminiRequest(
                apiKey = cleanApiKey,
                prompt = prompt,
                mode = mode,
                engine = engine,
                systemPrompt = systemPrompt,
                history = history
            )
        } catch (e: Exception) {
            if (isQuotaOrRateLimitException(e)) {
                Log.w(TAG, "Direct Gemini call 429 Quota Exhausted: ${e.message}")
                return@withContext Pair(
                    GEMINI_QUOTA_ERROR_MESSAGE,
                    "Error 429: RESOURCE_EXHAUSTED - Gemini API Quota ပြည့်သွားပါပြီ"
                )
            }
            Log.e(TAG, "Direct Gemini call failed: ${e.message}")
            Pair(
                CONNECTION_ERROR_MESSAGE,
                "Error: ${e.javaClass.simpleName}: ${e.message}"
            )
        }
    }

    private fun executeGeminiRequest(
        apiKey: String,
        prompt: String,
        mode: String,
        engine: String,
        systemPrompt: String,
        history: List<ChatMessageEntity>
    ): Pair<String, String?> {
        val rootObj = JSONObject()
        val detectedMood = com.example.cognitive.KyawGyiSystemPersona.detectUserMood(prompt)
        val baseCompanionPrompt = if (systemPrompt.contains("# SYSTEM PROMPT: Personal AI Companion")) {
            systemPrompt
        } else {
            "${com.example.cognitive.KyawGyiSystemPersona.PERSONAL_AI_COMPANION_SYSTEM_PROMPT}\n\nAdditional Persona Context: $systemPrompt"
        }

        // System Instruction
        val systemObj = JSONObject()
        systemObj.put("role", "user")
        val systemParts = JSONArray()
        val systemPart = JSONObject()
        systemPart.put(
            "text",
            "$baseCompanionPrompt\n\n" +
                "--- RUNTIME CONTEXT ---\n" +
                "Active Engine: $engine | Response Mode: $mode\n" +
                "Detected User Mood / Tone State: ${detectedMood.labelMM}\n" +
                "Dynamic Adaptation Directive: ${detectedMood.adaptationRule}\n" +
                "CRITICAL: Jump directly into the conversation in natural, warm, conversational Burmese (adapting to English seamlessly for technical concepts). Never use robotic meta-announcements."
        )
        systemParts.put(systemPart)
        systemObj.put("parts", systemParts)
        rootObj.put("systemInstruction", systemObj)

        // Contents List
        val contentsArr = JSONArray()
        history.takeLast(8).forEach { msg ->
            val msgObj = JSONObject()
            msgObj.put("role", if (msg.role == "user") "user" else "model")
            val parts = JSONArray()
            val p = JSONObject()
            p.put("text", msg.content)
            parts.put(p)
            msgObj.put("parts", parts)
            contentsArr.put(msgObj)
        }

        val currentMsgObj = JSONObject()
        currentMsgObj.put("role", "user")
        val currentParts = JSONArray()
        val cp = JSONObject()
        cp.put("text", prompt)
        currentParts.put(cp)
        currentMsgObj.put("parts", currentParts)
        contentsArr.put(currentMsgObj)

        rootObj.put("contents", contentsArr)

        val requestJson = rootObj.toString()
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL_NAME:generateContent?key=$apiKey"

        val httpRequest = Request.Builder()
            .url(url)
            .post(requestJson.toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(httpRequest).execute()
        val responseCode = response.code
        val responseString = response.body?.string() ?: ""

        // Detect HTTP 429 or Quota Exhaustion
        if (responseCode == 429 || responseString.contains("RESOURCE_EXHAUSTED", ignoreCase = true) || responseString.contains("quota exceeded", ignoreCase = true)) {
            Log.w(TAG, "Gemini API 429 Quota Exhausted detected: $responseString")
            throw GeminiQuotaExceededException("HTTP 429 RESOURCE_EXHAUSTED: Rate limit or quota exhausted ($responseCode)")
        }

        if (!response.isSuccessful) {
            Log.w(TAG, "Gemini API call unsuccessful ($responseCode): $responseString")
            throw IOException("HTTP $responseCode Error: $responseString")
        }

        if (responseString.isNotBlank()) {
            val jsonResp = JSONObject(responseString)
            val candidates = jsonResp.optJSONArray("candidates")
            if (candidates != null && candidates.length() > 0) {
                val firstCandidate = candidates.getJSONObject(0)
                val contentObj = firstCandidate.optJSONObject("content")
                if (contentObj != null) {
                    val partsArr = contentObj.optJSONArray("parts")
                    if (partsArr != null && partsArr.length() > 0) {
                        val replyText = partsArr.getJSONObject(0).optString("text", "")
                        if (replyText.isNotBlank()) {
                            val thoughtChain = if (engine == "Brain Core" || mode == "EXPERT") {
                                "Mood: ${detectedMood.name} | Mode: $mode | Engine: $engine"
                            } else null
                            return Pair(replyText, thoughtChain)
                        }
                    }
                }
            }
        }

        // A 200 response with no usable candidate text is a failure, not an
        // occasion to invent a reply locally.
        throw IOException("Gemini returned an empty response")
    }

    suspend fun testKeyConnection(testKey: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val clean = testKey.trim()
        if (clean.isBlank()) return@withContext Pair(false, "API Key is empty")

        try {
            val rootObj = JSONObject()
            val contentsArr = JSONArray()
            val msgObj = JSONObject()
            msgObj.put("role", "user")
            val parts = JSONArray()
            val p = JSONObject()
            p.put("text", "Ping")
            parts.put(p)
            msgObj.put("parts", parts)
            contentsArr.put(msgObj)
            rootObj.put("contents", contentsArr)

            val url = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL_NAME:generateContent?key=$clean"
            val request = Request.Builder()
                .url(url)
                .post(rootObj.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val code = response.code
            val body = response.body?.string() ?: ""

            if (code == 429 || body.contains("RESOURCE_EXHAUSTED", ignoreCase = true)) {
                return@withContext Pair(false, GEMINI_QUOTA_ERROR_MESSAGE)
            }

            if (response.isSuccessful) {
                Pair(true, "Key is active and responsive ✓")
            } else {
                Pair(false, "HTTP $code: Invalid key or access denied")
            }
        } catch (e: Exception) {
            Pair(false, "Network error: ${e.message}")
        }
    }
}
