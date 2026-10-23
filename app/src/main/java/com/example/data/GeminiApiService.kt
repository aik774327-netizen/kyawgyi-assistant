package com.example.data

import android.content.Context
import android.util.Base64
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


/** FIX-05: the default production HTTP client (unchanged timeouts). */
private fun defaultGeminiHttpClient(): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(30, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .build()

class GeminiApiService(
    private val context: Context? = null,
    // FIX-05: injectable HTTP seam so request/HTTP-status behavior is
    // unit-testable with a fake transport. Production call sites use
    // the default client with the same timeouts as before.
    private val httpClient: OkHttpClient = defaultGeminiHttpClient()
) {

    companion object {
        private const val TAG = "GeminiApiService"
        private const val MODEL_NAME = "gemini-3.5-flash"

        /**
         * Gemini TTS model (2026-10-23, §7 plan). The id follows the
         * `…-preview-tts` family named in the inspection report; its
         * currency against Google's live catalog is NOT VERIFIED —
         * if Google renames it, synthesizeSpeech fails with an
         * ENDPOINT (404) classification, never a fake success.
         */
        const val TTS_MODEL_NAME = "gemini-2.5-flash-preview-tts"

        /** Default prebuilt Gemini TTS voice for Burmese replies. */
        const val DEFAULT_TTS_VOICE_NAME = "Kore"

        /** Gemini TTS returns raw L16 PCM at 24 kHz, mono, no WAV header. */
        const val TTS_OUTPUT_SAMPLE_RATE_HZ = 24_000

        private const val TRANSCRIPTION_INSTRUCTION =
            "Transcribe the speech in this audio verbatim. Return ONLY the transcript text — " +
                "no commentary, no labels, no quotation marks, no translation."
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

    // (The HTTP client is constructor-injected — see httpClient and
    // the file-level defaultGeminiHttpClient() below.)

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
                // FIX-05: classified failures (auth rejection, HTTP
                // server/endpoint errors, timeout, unreachable) get
                // their own honest message — never the generic
                // connection line, which falsely implied a network
                // problem for e.g. an HTTP 403 auth rejection.
                val failureClass = GeminiFailureClassifier.classify(e)
                if (failureClass != GeminiFailureClass.UNKNOWN) {
                    Log.w(TAG, "Gemini request failed (class=$failureClass): ${e.javaClass.simpleName}")
                    return@withContext Pair(
                        GeminiFailureClassifier.userMessageFor(
                            failureClass,
                            (e as? GeminiHttpException)?.code
                        ),
                        "Error: GEMINI_${failureClass.name} - ${e.javaClass.simpleName}"
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
            // FIX-05: same classification as the manager path above.
            val failureClass = GeminiFailureClassifier.classify(e)
            if (failureClass != GeminiFailureClass.UNKNOWN) {
                Log.w(TAG, "Direct Gemini call failed (class=$failureClass): ${e.javaClass.simpleName}")
                return@withContext Pair(
                    GeminiFailureClassifier.userMessageFor(
                        failureClass,
                        (e as? GeminiHttpException)?.code
                    ),
                    "Error: GEMINI_${failureClass.name} - ${e.javaClass.simpleName}"
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

        val response = httpClient.newCall(httpRequest).execute()
        val responseCode = response.code
        val responseString = response.body?.string() ?: ""

        // Detect HTTP 429 or Quota Exhaustion
        if (responseCode == 429 || responseString.contains("RESOURCE_EXHAUSTED", ignoreCase = true) || responseString.contains("quota exceeded", ignoreCase = true)) {
            Log.w(TAG, "Gemini API 429 Quota Exhausted detected: $responseString")
            throw GeminiQuotaExceededException("HTTP 429 RESOURCE_EXHAUSTED: Rate limit or quota exhausted ($responseCode)")
        }

        if (!response.isSuccessful) {
            // FIX-05: carry ONLY the status code (never the key, never
            // the URL) so generateResponse can classify the failure —
            // the pre-fix generic IOException collapsed auth rejections
            // and server errors into one "connection problem" message.
            Log.w(TAG, "Gemini API call unsuccessful (HTTP $responseCode)")
            throw GeminiHttpException(responseCode)
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

            val response = httpClient.newCall(request).execute()
            val code = response.code
            val body = response.body?.string() ?: ""

            if (code == 429 || body.contains("RESOURCE_EXHAUSTED", ignoreCase = true)) {
                return@withContext Pair(false, GEMINI_QUOTA_ERROR_MESSAGE)
            }

            if (response.isSuccessful) {
                Pair(true, "Key is active and responsive ✓")
            } else {
                // FIX-05: the true outcome class per status — the
                // pre-fix code labeled EVERY non-429 failure
                // (including HTTP 500 and 404) "Invalid key or access
                // denied", sending the user to fix a key that was
                // never the problem.
                val failureClass = GeminiFailureClassifier.classifyHttpCode(code)
                Pair(false, GeminiFailureClassifier.userMessageFor(failureClass, code))
            }
        } catch (e: Exception) {
            // FIX-05: timeouts vs unreachable vs other network errors
            // are distinct outcomes, not one "Network error" line.
            val failureClass = GeminiFailureClassifier.classify(e)
            Pair(false, GeminiFailureClassifier.userMessageFor(failureClass))
        }
    }

    // ------------------------------------------------------------------
    // Online STT / TTS (2026-10-23, §7 plan of the Gemini online
    // STT/TTS inspection). Unlike generateResponse — which converts
    // failures into a Pair for the chat UI — these methods THROW the
    // typed failures (NoApiKeyConfiguredException,
    // GeminiQuotaExceededException, GeminiHttpException,
    // GeminiEmptyResponseException, IOException) so the voice engines
    // can classify them via GeminiFailureClassifier and fall back
    // honestly. All calls run through the shared key pool
    // (ApiKeyQuotaManager.executeWithRetry) and the injectable
    // httpClient, exactly like the chat path. The API key is never
    // logged and never embedded in an exception message.
    // ------------------------------------------------------------------

    /**
     * Transcribes a complete WAV utterance with Gemini audio
     * understanding and returns the transcript text only.
     *
     * @param wavBytes a complete WAV file (16 kHz mono PCM16 in
     * production use; the bytes are sent as an `audio/wav` inlineData
     * part, so the container must be self-describing).
     * @param languageTag expected speech language (e.g. "my-MM"),
     * sent as a transcription hint.
     * @throws NoApiKeyConfiguredException when no key is configured
     * @throws GeminiQuotaExceededException on HTTP 429 / pool cooldown
     * @throws GeminiHttpException on other non-2xx responses
     * @throws GeminiEmptyResponseException on a 200 with no transcript
     */
    suspend fun transcribeAudio(wavBytes: ByteArray, languageTag: String): String =
        withContext(Dispatchers.IO) {
            require(wavBytes.isNotEmpty()) { "transcribeAudio requires non-empty WAV bytes" }
            executeAudioWithKeyPool("Gemini Audio Transcription") { apiKey ->
                executeTranscriptionRequest(apiKey, wavBytes, languageTag)
            }
        }

    /**
     * Synthesizes speech with the Gemini TTS model and returns the
     * raw PCM16 audio (24 kHz mono, NO WAV header — see
     * [TTS_OUTPUT_SAMPLE_RATE_HZ]).
     *
     * @throws NoApiKeyConfiguredException when no key is configured
     * @throws GeminiQuotaExceededException on HTTP 429 / pool cooldown
     * @throws GeminiHttpException on other non-2xx responses
     * @throws GeminiEmptyResponseException on a 200 with no audio part
     */
    suspend fun synthesizeSpeech(
        text: String,
        voiceName: String = DEFAULT_TTS_VOICE_NAME
    ): ByteArray = withContext(Dispatchers.IO) {
        require(text.isNotBlank()) { "synthesizeSpeech requires non-blank text" }
        executeAudioWithKeyPool("Gemini Speech Synthesis") { apiKey ->
            executeSynthesisRequest(apiKey, text, voiceName)
        }
    }

    /**
     * Runs [block] with a pooled API key (same pool/cooldown/rotation
     * as chat), or with the screened BuildConfig key when the quota
     * manager is uninitialized. Throws [NoApiKeyConfiguredException]
     * when no key exists at all — an audio call must never silently
     * pretend to have run.
     */
    private suspend fun <T> executeAudioWithKeyPool(
        operationName: String,
        block: (apiKey: String) -> T
    ): T {
        val quotaManager = context?.let { ApiKeyQuotaManager.getInstance(it) }
            ?: ApiKeyQuotaManager.getInstance(null)
        if (quotaManager != null) {
            return quotaManager.executeWithRetry(
                operationName = operationName,
                maxRetries = 3
            ) { activeApiKey -> block(activeApiKey) }
        }
        val apiKey = BuildConfig.GEMINI_API_KEY
        val cleanApiKey = if (apiKey != "MY_GEMINI_API_KEY" && apiKey.isNotBlank()) apiKey else null
            ?: throw NoApiKeyConfiguredException()
        return block(cleanApiKey)
    }

    private fun executeTranscriptionRequest(
        apiKey: String,
        wavBytes: ByteArray,
        languageTag: String
    ): String {
        val audioPart = JSONObject()
        val inlineData = JSONObject()
        inlineData.put("mimeType", "audio/wav")
        inlineData.put("data", Base64.encodeToString(wavBytes, Base64.NO_WRAP))
        audioPart.put("inlineData", inlineData)

        val instructionPart = JSONObject()
        instructionPart.put(
            "text",
            "$TRANSCRIPTION_INSTRUCTION Expected language: $languageTag."
        )

        val parts = JSONArray()
        parts.put(audioPart)
        parts.put(instructionPart)

        val msgObj = JSONObject()
        msgObj.put("role", "user")
        msgObj.put("parts", parts)

        val contentsArr = JSONArray()
        contentsArr.put(msgObj)

        val rootObj = JSONObject()
        rootObj.put("contents", contentsArr)

        val jsonResp = postGenerateContent(MODEL_NAME, apiKey, rootObj)
        // Defensive: scan every part for the first usable text — the
        // model may interleave non-text parts.
        val partsArr = firstCandidateParts(jsonResp)
        if (partsArr != null) {
            for (i in 0 until partsArr.length()) {
                val text = partsArr.getJSONObject(i).optString("text", "")
                if (text.isNotBlank()) return text.trim()
            }
        }
        throw GeminiEmptyResponseException("Gemini transcription returned no text")
    }

    private fun executeSynthesisRequest(
        apiKey: String,
        text: String,
        voiceName: String
    ): ByteArray {
        val textPart = JSONObject()
        textPart.put("text", text)
        val parts = JSONArray()
        parts.put(textPart)
        val msgObj = JSONObject()
        msgObj.put("parts", parts)
        val contentsArr = JSONArray()
        contentsArr.put(msgObj)

        val voiceConfig = JSONObject()
        val prebuilt = JSONObject()
        prebuilt.put("voiceName", voiceName)
        voiceConfig.put("prebuiltVoiceConfig", prebuilt)
        val speechConfig = JSONObject()
        speechConfig.put("voiceConfig", voiceConfig)
        val generationConfig = JSONObject()
        val modalities = JSONArray()
        modalities.put("AUDIO")
        generationConfig.put("responseModalities", modalities)
        generationConfig.put("speechConfig", speechConfig)

        val rootObj = JSONObject()
        rootObj.put("contents", contentsArr)
        rootObj.put("generationConfig", generationConfig)

        val jsonResp = postGenerateContent(TTS_MODEL_NAME, apiKey, rootObj)
        val partsArr = firstCandidateParts(jsonResp)
        if (partsArr != null) {
            for (i in 0 until partsArr.length()) {
                val data = partsArr.getJSONObject(i)
                    .optJSONObject("inlineData")
                    ?.optString("data", "")
                if (!data.isNullOrBlank()) {
                    val pcm = Base64.decode(data, Base64.DEFAULT)
                    if (pcm.isNotEmpty()) return pcm
                }
            }
        }
        throw GeminiEmptyResponseException("Gemini speech synthesis returned no audio part")
    }

    /**
     * POSTs a generateContent request for [model] and returns the
     * parsed JSON body. Error mapping matches the chat path: HTTP 429
     * (or a quota body) → [GeminiQuotaExceededException]; any other
     * non-2xx → [GeminiHttpException] carrying ONLY the status code.
     */
    private fun postGenerateContent(model: String, apiKey: String, rootObj: JSONObject): JSONObject {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
        val httpRequest = Request.Builder()
            .url(url)
            .post(rootObj.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = httpClient.newCall(httpRequest).execute()
        val responseCode = response.code
        val responseString = response.body?.string() ?: ""

        if (responseCode == 429 || responseString.contains("RESOURCE_EXHAUSTED", ignoreCase = true)) {
            Log.w(TAG, "Gemini API 429 Quota Exhausted detected for model $model")
            throw GeminiQuotaExceededException("HTTP 429 RESOURCE_EXHAUSTED: Rate limit or quota exhausted ($responseCode)")
        }
        if (!response.isSuccessful) {
            Log.w(TAG, "Gemini API call unsuccessful for model $model (HTTP $responseCode)")
            throw GeminiHttpException(responseCode)
        }
        if (responseString.isBlank()) {
            throw GeminiEmptyResponseException("Gemini returned an empty body for model $model")
        }
        return JSONObject(responseString)
    }

    private fun firstCandidateParts(jsonResp: JSONObject): JSONArray? {
        val candidates = jsonResp.optJSONArray("candidates") ?: return null
        if (candidates.length() == 0) return null
        return candidates.getJSONObject(0).optJSONObject("content")?.optJSONArray("parts")
    }
}
