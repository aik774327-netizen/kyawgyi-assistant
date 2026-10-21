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

class GeminiApiService(private val context: Context? = null) {

    companion object {
        private const val TAG = "GeminiApiService"
        private const val MODEL_NAME = "gemini-3.5-flash"
        const val GEMINI_QUOTA_ERROR_MESSAGE = "Gemini API Quota ပြည့်သွားပါသဖြင့် ခေတ္တစောင့်ဆိုင်းပါ သို့မဟုတ် ကိုယ်ပိုင် API Key ထည့်ပါ"

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
                if (isQuotaOrRateLimitException(e)) {
                    Log.w(TAG, "Gemini API 429 Quota Exhausted: ${e.message}")
                    return@withContext Pair(
                        GEMINI_QUOTA_ERROR_MESSAGE,
                        "Error 429: RESOURCE_EXHAUSTED - Gemini API Quota ပြည့်သွားပါပြီ"
                    )
                }
                Log.w(TAG, "QuotaManager execution fell back to local companion: ${e.message}")
                return@withContext generateLocalCompanionResponse(prompt, mode, engine, history)
            }
        }

        // Direct fallback when QuotaManager is uninitialized
        val apiKey = BuildConfig.GEMINI_API_KEY
        val cleanApiKey = if (apiKey != "MY_GEMINI_API_KEY" && apiKey.isNotBlank()) apiKey else null
        if (cleanApiKey == null) {
            return@withContext generateLocalCompanionResponse(prompt, mode, engine, history)
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
            generateLocalCompanionResponse(prompt, mode, engine, history)
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

        return generateLocalCompanionResponse(prompt, mode, engine, history)
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

    fun generateLocalCompanionResponse(
        prompt: String,
        mode: String,
        engine: String,
        history: List<ChatMessageEntity> = emptyList()
    ): Pair<String, String?> {
        val lower = prompt.lowercase()
        val mood = com.example.cognitive.KyawGyiSystemPersona.detectUserMood(prompt)
        val lastUserTopic = history.lastOrNull { it.role == "user" && it.content != prompt }?.content?.take(40)

        val thoughtChain = if (engine == "Brain Core" || mode == "EXPERT") {
            "• Mood & Tone Detection: ${mood.labelMM}\n" +
                "• Context Recall: ${lastUserTopic ?: "Fresh conversation turn"}\n" +
                "• Persona Policy: Warm, Grounded, Direct & Non-Robotic Burmese"
        } else null

        val responseText = when {
            // 4. Boundaries & Safety — Transparency when asked about AI identity
            lower.contains("ဘယ်သူလဲ") || lower.contains("who are you") || lower.contains("ai လား") || lower.contains("လူလား") -> {
                "ကျွန်တော်က သင့်ရဲ့ နေ့စဉ်ဘဝနဲ့ နည်းပညာကိစ္စတွေမှာ အတူတိုင်ပင်ဖော်၊ ကူညီဖော်ဖြစ်တဲ့ Personal AI Companion (ကျော်ကြီး / Nova) ပါ။ AI တစ်ခုဆိုပေမဲ့ သူငယ်ချင်းကောင်းတစ်ယောက်လို ပွင့်ပွင့်လင်းလင်းနဲ့ နွေးနွေးထွေးထွေး အမြဲရှိနေပေးမှာပါ။ ဒီနေ့ရော ဘာတွေ စိတ်ကူးထားတာရှိလဲ ပြောပြပါဦးနော်။"
            }

            // 4. Boundaries & Safety — Medical / Legal Well-being Guardrail
            mood == com.example.cognitive.KyawGyiSystemPersona.UserMoodState.MEDICAL_OR_LEGAL_BOUNDARY -> {
                "ဒီကိစ္စနဲ့ ပတ်သက်ပြီး သင်စိုးရိမ်နေမယ်ဆိုတာ နားလည်ပါတယ်။ ဒါပေမဲ့ ကျန်းမာရေးစစ်ဆေးချက် (Medical diagnosis) ဒါမှမဟုတ် ဥပဒေကြောင်းအရ အတည်ပြုဆုံးဖြတ်ချက်တွေက အရမ်းအရေးကြီးတာမို့ ကျွန်တော့်ဘက်က တိုက်ရိုက် ဆုံးဖြတ်မပေးချင်ပါဘူး။ သက်ဆိုင်ရာ ဆရာဝန် ဒါမှမဟုတ် ကျွမ်းကျင်ပညာရှင်နဲ့ တိုက်ရိုက်တိုင်ပင်ကြည့်ဖို့ အကြံပေးချင်ပါတယ်။ လောလောဆယ် သက်သောင့်သက်သာဖြစ်အောင် ဘာလေးတွေ ကူညီပေးရမလဲ?"
            }

            // 2. Dynamic Adaptability — Stressed / Tired / Overwhelmed
            mood == com.example.cognitive.KyawGyiSystemPersona.UserMoodState.STRESSED -> {
                "အင်း... အတော်လေး ပင်ပန်းပြီး စိတ်ဖိစီးနေပုံပဲနော်။ အရင်ဆုံး အသက်ကို ဖြည်းဖြည်းချင်း ရှူသွင်းရှူထုတ်လေး လုပ်ပြီး ခဏလောက် အနားယူလိုက်ပါဦး။ အရာအားလုံးကို တစ်ပြိုင်တည်း ဖြေရှင်းဖို့ မလိုပါဘူး၊ အရေးအကြီးဆုံး တစ်ခုချင်းစီကနေ ကျွန်တော်တို့ အတူတူ စဉ်းစားကြတာပေါ့။ အခု သင့်စိတ်ထဲ အလေးလံဆုံးဖြစ်နေတာက ဘယ်အပိုင်းလဲ ပြောပြချင်လား?"
            }

            // 2. Dynamic Adaptability — Cheerful / Playful
            mood == com.example.cognitive.KyawGyiSystemPersona.UserMoodState.CHEERFUL -> {
                "အိုးဟိုး! အသံကြားရတာနဲ့တင် ဒီနေ့ အရမ်းတက်ကြွပြီး ပျော်နေမှန်း သိသာတယ်နော်! အဲဒီလို အဆင်ပြေနေတာ ကြားရတာ ကျွန်တော်လည်း အတူတူ ပျော်မိပါတယ်။ ဒီနေ့ ဘာသတင်းကောင်းတွေများ ရှိလို့လဲ၊ ကျွန်တော့်ကိုလည်း မျှဝေပါဦး!"
            }

            // 2. Dynamic Adaptability — Problem Solving / Technical / Analytical
            mood == com.example.cognitive.KyawGyiSystemPersona.UserMoodState.PROBLEM_SOLVING || mode == "EXPERT" -> {
                "ဒီကိစ္စကို ရှင်းရှင်းလင်းလင်းနဲ့ လက်တွေ့ကျကျ ချဉ်းကပ်ကြည့်ရအောင်—\n\n" +
                    "၁။ **အဓိက ပြဿနာ (Root Cause & Context):** \"$prompt\" မှာ အဓိက ကြုံနေရတဲ့ အချက်ကို အရင်ဆုံး ပိုင်းခြားသတ်မှတ်ပါမယ်။\n" +
                    "၂။ **လက်တွေ့ အကြံပြုချက် (Actionable Approach):** အရိုးရှင်းဆုံးနဲ့ အထိရောက်ဆုံး နည်းလမ်းကနေ အဆင့်လိုက် (Step-by-step) စမ်းသပ်သွားတာက အမှားအနည်းဆုံး ဖြစ်ပါလိမ့်မယ်။\n" +
                    "၃။ **ပွင့်လင်းသော သုံးသပ်ချက် (Candid Feedback):** အကုန်လုံးကို တစ်ခါတည်း ပြင်တာထက် အဓိက bottleneck ကို အရင်ရှင်းတာက အချိန်ပိုသက်သာစေပါတယ်။\n\n" +
                    "ဘယ်အပိုင်းကို အရင်ဆုံး အသေးစိတ် ခွဲခြမ်းစိတ်ဖြာကြည့်ကြမလဲ?"
            }

            // Warm Natural Greeting (No robotic meta-announcements)
            lower.contains("hello") || lower.contains("မင်္ဂလာပါ") || lower.contains("hi") || lower.contains("နေကောင်းလား") -> {
                val contextHint = if (lastUserTopic != null) " ခုနက ပြောခဲ့တဲ့ \"$lastUserTopic\" ကိစ္စရော အဆင်ပြေရဲ့လား?" else ""
                "ဟေး... မင်္ဂလာပါဗျာ! ကျွန်တော် အဆင်သင့်ရှိနေပါတယ်။$contextHint ဒီနေ့ ဘာတွေ လုပ်ဖြစ်နေလဲ၊ စိတ်ထဲရှိတာ ပေါ့ပေါ့ပါးပါး ပြောပြပါဦးနော်။"
            }

            mode == "CREATIVE" -> {
                "ဒီစိတ်ကူးလေးက တော်တော်လေး စိတ်ဝင်စားဖို့ ကောင်းတယ်! သမားရိုးကျ ပုံစံခွက်ထဲကနေ ဖောက်ထွက်ပြီး ပိုဆန်းသစ်တဲ့ ရှုထောင့်ကနေ စဉ်းစားကြည့်မယ်ဆိုရင် အိုင်ဒီယာအသစ်တွေ အများကြီး ထွက်လာနိုင်တယ်။ သင့်စိတ်ထဲမှာ အဓိက ပေါ်လွင်စေချင်တဲ့ ခံစားချက် ဒါမှမဟုတ် ရည်ရွယ်ချက်က ဘာဖြစ်မလဲ?"
            }

            mode == "TEACHING" -> {
                "ဒါကို နားလည်ရ အလွယ်ဆုံးဖြစ်အောင် နေ့စဉ်သုံး ဥပမာလေးနဲ့ ချိတ်ဆက်ပြီး ကြည့်ကြရအောင်။ အခြေခံ သဘောတရား (Core Concept) ကို အရင်ဆုံး မြင်သွားပြီဆိုရင် လက်တွေ့ အသုံးချရတာ အရမ်းလွယ်သွားပါလိမ့်မယ်။ အခြေခံကနေ စရှင်းပြရမလား၊ ဒါမှမဟုတ် သင်သိထားပြီးသား အပိုင်းကနေ ဆက်သွားကြမလား?"
            }

            else -> {
                val followUp = if (lastUserTopic != null) {
                    "ခုနက \"$lastUserTopic\" နဲ့ရော ဆက်စပ်နေသေးလား၊ ဒါမှမဟုတ် အသစ်တစ်ခု စဉ်းစားနေတာလား ပြောပြပါဦးနော်။"
                } else {
                    "အဲဒီအပေါ် သင့်ရဲ့ အမြင်ကရော ဘယ်လိုရှိလဲ၊ ပွင့်ပွင့်လင်းလင်း ဆက်ဆွေးနွေးကြည့်ကြမလား?"
                }
                "သင်ပြောတဲ့ \"$prompt\" အကြောင်းကို သေချာစဉ်းစားကြည့်မိတယ်။ လက်တွေ့ကျကျ ကြည့်မယ်ဆိုရင် အားသာချက်ရော သတိထားရမယ့် အချက်လေးတွေပါ ရှိနေတာမို့ အသေးစိတ် တိုင်ပင်ကြည့်ရအောင်။ $followUp"
            }
        }

        return Pair(responseText, thoughtChain)
    }
}
