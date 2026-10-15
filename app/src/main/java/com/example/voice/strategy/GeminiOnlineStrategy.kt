package com.example.voice.strategy

import android.content.Context
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import com.kyawgyi.ir.apikey.ApiKeyManager
import com.kyawgyi.ir.apikey.ApiKeySecureStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.take
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
 * Online Strategy connecting to Google AI Studio Gemini API.
 *
 * Implements:
 * 1. Native Audio Multimodal Prompting (audio/wav or raw PCM inlineData).
 * 2. System Instruction configured for Burmese Voice Assistant "ကျော်ကြီး (KyawGyi)".
 * 3. Gemini Function Calling declaration: `control_device_setting` and `transmit_ir_command`.
 * 4. Automatic API Key failover using [ApiKeyManager] and fallback to [BuildConfig.GEMINI_API_KEY].
 */
class GeminiOnlineStrategy(
  private val context: Context,
  private val apiKeyManager: ApiKeyManager = ApiKeyManager.getInstance(context)
) : VoiceAssistantStrategy {

  override val engineName: String = "GeminiOnlineStrategy (Google AI Studio)"

  companion object {
    private const val TAG = "GeminiOnlineStrategy"
    private const val GEMINI_MODEL = "gemini-2.5-flash-native-audio-preview-12-2025"
    private const val GEMINI_FALLBACK_MODEL = "gemini-2.5-flash"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"

    private const val SYSTEM_INSTRUCTION_BURMESE = """
You are "ကျော်ကြီး (KyawGyi)", an intelligent, obedient, and respectful Burmese smart home voice assistant.
LANGUAGE CONFIGURATION:
- Primary Language: Myanmar / Burmese (my-MM / မြန်မာဘာသာ).
- The user's audio input stream is spoken exclusively in Myanmar (Burmese, my-MM).
- Accurately recognize and transcribe spoken Burmese phonetics into correct standard Myanmar Unicode script.
- Always reply exclusively in natural, polite Burmese (ခင်ဗျာ/ရှင်).
You can control physical smart fan buttons (POWER, SPEED, OSCILLATION, LIGHT, CENTER), device flashlight/torch, phone volume, phone dialer (contacts via ACTION_DIAL), and navigation.
When the user asks to call someone (e.g. "အမေ့ဆီ ဖုန်းခေါ်ပါ", "မိတ်ဆွေဆီ ဖုန်းဆက်ပါ", "ဖုန်းခေါ်ပါ"), ALWAYS invoke the function `call_phone_contact`.
When the user asks to control device settings, ALWAYS invoke the function `control_device_setting` or `transmit_ir_command`.
Example:
User: "ကျော်ကြီး အမေ့ဆီ ဖုန်းခေါ်ပေးပါ" -> Call call_phone_contact(name="အမေ") and reply "အမေ့ဆီ ဖုန်းခေါ်ဆိုရန် ဖုန်းခေါ်စနစ် (Dialer) ဖွင့်ပေးလိုက်ပါပြီ ခင်ဗျာ"
User: "ကျော်ကြီး ပန်ကာဖွင့်ပေးပါ" -> Call transmit_ir_command(button="POWER") and reply "ကျော်ကြီး ပန်ကာဖွင့်ပေးလိုက်ပါပြီ ခင်ဗျာ"
User: "ဓာတ်မီး ဖွင့်ပေး" -> Call control_device_setting(action="FLASHLIGHT_ON") and reply "လက်နှိပ်ဓာတ်မီး ဖွင့်ပေးလိုက်ပါပြီ ခင်ဗျာ"
User: "အသံတိုးပေး" -> Call control_device_setting(action="VOLUME_DOWN") and reply "အသံလျှော့ပေးလိုက်ပါပြီ ခင်ဗျာ"
"""
  }

  private val secureStorage = ApiKeySecureStorage(context)

  private val httpClient = OkHttpClient.Builder()
    .connectTimeout(5, TimeUnit.SECONDS)
    .readTimeout(10, TimeUnit.SECONDS)
    .writeTimeout(10, TimeUnit.SECONDS)
    .build()

  override suspend fun processAudioStream(audioFlow: Flow<ByteArray>): AssistantResult = withContext(Dispatchers.IO) {
    val startTime = System.currentTimeMillis()

    // 1. Collect audio chunks from central flow (accumulate up to ~2-3 seconds of speech)
    val pcmStream = ByteArrayOutputStream()
    try {
      audioFlow.take(40).collect { chunk ->
        pcmStream.write(chunk)
      }
    } catch (e: Exception) {
      Log.w(TAG, "Audio collection ended: ${e.message}")
    }

    val rawPcm = pcmStream.toByteArray()
    if (rawPcm.isEmpty()) {
      return@withContext AssistantResult.Error("စကားသံ မကြားရပါ (Empty audio stream)")
    }

    // Convert raw 16kHz PCM to standard WAV
    val wavBytes = addWavHeader(rawPcm, 16000, 1, 16)
    val base64Audio = Base64.encodeToString(wavBytes, Base64.NO_WRAP)

    // 2. Resolve Active Gemini API Key
    var activeKey = ""

    // Priority 1: User-entered runtime key from ApiKeyManager (must be a genuine user key, never a seed placeholder)
    val userKey = apiKeyManager.getActiveSecretKey()
    if (userKey.isNotBlank() && !ApiKeyManager.isPlaceholderKey(userKey)) {
      activeKey = userKey
    }

    // Priority 2: BuildConfig.GEMINI_API_KEY fallback ONLY when no user key exists
    if (activeKey.isBlank()) {
      // =========================================================================
      // SECURITY WARNING (D9):
      // Compiled-in keys (such as BuildConfig.GEMINI_API_KEY) are extractable
      // from the client APK via reverse engineering or static analysis.
      // Production apps should use user-provided runtime key entry (stored securely
      // in Android Keystore / EncryptedSharedPreferences) or a backend server proxy.
      // NOTE: Full server-proxy fix is NOT DONE (needs server infrastructure).
      // =========================================================================
      val envKey = BuildConfig.GEMINI_API_KEY
      if (envKey.isNotBlank() && envKey != "MY_GEMINI_API_KEY" && !ApiKeyManager.isPlaceholderKey(envKey)) {
        activeKey = envKey
      }
    }

    // Priority 3: Neither exists -> return clear error in Burmese (do NOT send a fake key to network)
    if (activeKey.isBlank()) {
      return@withContext AssistantResult.Error("Gemini API Key မတွေ့ရှိပါ (Offline Strategy သို့ လွှဲပြောင်းပါမည်)")
    }

    // 3. Construct Gemini Multimodal + Function Calling Payload
    val requestJson = buildGeminiRequestJson(base64Audio)
    val requestBody = requestJson.toString().toRequestBody("application/json".toMediaType())

    val url = "$BASE_URL/$GEMINI_MODEL:generateContent?key=$activeKey"
    val request = Request.Builder()
      .url(url)
      .post(requestBody)
      .build()

    try {
      val response = httpClient.newCall(request).execute()
      val responseBodyString = response.body?.string() ?: ""

      if (!response.isSuccessful) {
        Log.e(TAG, "Gemini API HTTP ${response.code}: $responseBodyString")
        apiKeyManager.handleHttpError(response.code, responseBodyString)

        // Try Fallback to standard model if native audio preview is restricted
        return@withContext callGeminiFallback(activeKey, base64Audio, startTime)
      }

      apiKeyManager.recordUsage(amount = 1)
      parseGeminiResponse(responseBodyString, startTime)
    } catch (e: Exception) {
      Log.e(TAG, "Gemini network exception", e)
      AssistantResult.Error("Gemini API ချိတ်ဆက်မှု မအောင်မြင်ပါ: ${e.localizedMessage}", e)
    }
  }

  private fun callGeminiFallback(apiKey: String, base64Audio: String, startTime: Long): AssistantResult {
    return try {
      val requestJson = buildGeminiRequestJson(base64Audio)
      val url = "$BASE_URL/$GEMINI_FALLBACK_MODEL:generateContent?key=$apiKey"
      val request = Request.Builder()
        .url(url)
        .post(requestJson.toString().toRequestBody("application/json".toMediaType()))
        .build()

      val response = httpClient.newCall(request).execute()
      val bodyStr = response.body?.string() ?: ""
      if (response.isSuccessful) {
        apiKeyManager.recordUsage(amount = 1)
        parseGeminiResponse(bodyStr, startTime)
      } else {
        AssistantResult.Error("Gemini Fallback HTTP ${response.code}: $bodyStr")
      }
    } catch (e: Exception) {
      AssistantResult.Error("Gemini Fallback Error: ${e.localizedMessage}", e)
    }
  }

  private fun buildGeminiRequestJson(base64Audio: String): JSONObject {
    val root = JSONObject()

    // Contents with audio part
    val partsArray = JSONArray().apply {
      // Audio inline data
      put(JSONObject().apply {
        put("inlineData", JSONObject().apply {
          put("mimeType", "audio/wav")
          put("data", base64Audio)
        })
      })
      // Text prompt with explicit Myanmar (my-MM) language configuration declaration
      put(JSONObject().apply {
        put("text", "Language: Myanmar / Burmese (my-MM). Listen to this Myanmar voice audio stream. Transcribe the user's speech accurately into standard Myanmar Unicode text, determine intent, and respond politely in Burmese (my-MM) or call corresponding functions.")
      })
    }

    val contentsArray = JSONArray().apply {
      put(JSONObject().apply {
        put("role", "user")
        put("parts", partsArray)
      })
    }
    root.put("contents", contentsArray)

    // System instruction
    root.put("systemInstruction", JSONObject().apply {
      put("parts", JSONArray().apply {
        put(JSONObject().apply {
          put("text", SYSTEM_INSTRUCTION_BURMESE)
        })
      })
    })

    // Tools - Function Calling
    val functionDeclarations = JSONArray().apply {
      // Tool 1: control_device_setting
      put(JSONObject().apply {
        put("name", "control_device_setting")
        put("description", "Controls device flashlight, volume, accessibility navigation or system settings")
        put("parameters", JSONObject().apply {
          put("type", "OBJECT")
          val properties = JSONObject().apply {
            put("action", JSONObject().apply {
              put("type", "STRING")
              put("description", "Action to perform: FLASHLIGHT_ON, FLASHLIGHT_OFF, VOLUME_UP, VOLUME_DOWN, VOLUME_MUTE, NAV_HOME, NAV_BACK")
            })
          }
          put("properties", properties)
          put("required", JSONArray().apply { put("action") })
        })
      })

      // Tool 2: transmit_ir_command
      put(JSONObject().apply {
        put("name", "transmit_ir_command")
        put("description", "Transmits infrared command to KENNEDE KN-5996HRB rechargeable fan")
        put("parameters", JSONObject().apply {
          put("type", "OBJECT")
          val properties = JSONObject().apply {
            put("button", JSONObject().apply {
              put("type", "STRING")
              put("description", "Button key: POWER, SPEED, OSCILLATION, LIGHT, CENTER")
            })
          }
          put("properties", properties)
          put("required", JSONArray().apply { put("button") })
        })
      })

      // Tool 3: call_phone_contact
      put(JSONObject().apply {
        put("name", "call_phone_contact")
        put("description", "Opens the phone dialer via ACTION_DIAL for a contact or family member by relationship or name (e.g. အမေ, အဖေ, မိတ်ဆွေ, ရုံး)")
        put("parameters", JSONObject().apply {
          put("type", "OBJECT")
          val properties = JSONObject().apply {
            put("name", JSONObject().apply {
              put("type", "STRING")
              put("description", "Name or relationship tag of the contact to call: e.g. အမေ, အဖေ, ရုံး, သူငယ်ချင်း, မိတ်ဆွေ")
            })
          }
          put("properties", properties)
          put("required", JSONArray().apply { put("name") })
        })
      })
    }

    root.put("tools", JSONArray().apply {
      put(JSONObject().apply {
        put("functionDeclarations", functionDeclarations)
      })
    })

    return root
  }

  private fun parseGeminiResponse(jsonStr: String, startTime: Long): AssistantResult {
    return try {
      val root = JSONObject(jsonStr)
      val candidates = root.optJSONArray("candidates")
      if (candidates == null || candidates.length() == 0) {
        return AssistantResult.Error("Gemini မှ တုံ့ပြန်မှု မရရှိပါ")
      }

      val content = candidates.getJSONObject(0).optJSONObject("content")
      val parts = content?.optJSONArray("parts")

      var responseText = ""
      var functionCallPayload: FunctionCallPayload? = null

      if (parts != null) {
        for (i in 0 until parts.length()) {
          val part = parts.getJSONObject(i)
          if (part.has("text")) {
            responseText += part.getString("text") + " "
          }
          if (part.has("functionCall")) {
            val fn = part.getJSONObject("functionCall")
            val name = fn.optString("name")
            val argsObj = fn.optJSONObject("args")
            val argsMap = mutableMapOf<String, String>()
            argsObj?.keys()?.forEach { key ->
              argsMap[key] = argsObj.optString(key)
            }
            functionCallPayload = FunctionCallPayload(name, argsMap)
          }
        }
      }

      val latency = System.currentTimeMillis() - startTime
      val cleanReply = responseText.trim().ifBlank {
        if (functionCallPayload != null) "အမိန့်ကို လက်ခံဆောင်ရွက်ပြီးပါပြီ ခင်ဗျာ" else "နားလည်ပါသည် ခင်ဗျာ"
      }

      val actionKey = when (functionCallPayload?.name) {
        "transmit_ir_command" -> "KENNEDE_FAN_${functionCallPayload.arguments["button"]?.uppercase() ?: "POWER"}"
        "control_device_setting" -> functionCallPayload.arguments["action"]?.uppercase()
        "call_phone_contact" -> "CALL_CONTACT_${functionCallPayload.arguments["name"]?.trim() ?: "အမေ"}"
        else -> null
      }

      AssistantResult.Success(
        transcript = cleanReply,
        responseBurmese = cleanReply,
        actionKey = actionKey,
        functionCall = functionCallPayload,
        isOfflineFallback = false,
        latencyMs = latency
      )
    } catch (e: Exception) {
      AssistantResult.Error("Gemini JSON parse error: ${e.localizedMessage}", e)
    }
  }

  private fun addWavHeader(pcmData: ByteArray, sampleRate: Int, channels: Int, bitsPerSample: Int): ByteArray {
    val totalAudioLen = pcmData.size.toLong()
    val totalDataLen = totalAudioLen + 36
    val byteRate = (sampleRate * channels * bitsPerSample / 8).toLong()

    val header = ByteArray(44)
    header[0] = 'R'.code.toByte()
    header[1] = 'I'.code.toByte()
    header[2] = 'F'.code.toByte()
    header[3] = 'F'.code.toByte()
    header[4] = (totalDataLen and 0xffL).toByte()
    header[5] = ((totalDataLen shr 8) and 0xffL).toByte()
    header[6] = ((totalDataLen shr 16) and 0xffL).toByte()
    header[7] = ((totalDataLen shr 24) and 0xffL).toByte()
    header[8] = 'W'.code.toByte()
    header[9] = 'A'.code.toByte()
    header[10] = 'V'.code.toByte()
    header[11] = 'E'.code.toByte()
    header[12] = 'f'.code.toByte()
    header[13] = 'm'.code.toByte()
    header[14] = 't'.code.toByte()
    header[15] = ' '.code.toByte()
    header[16] = 16
    header[17] = 0
    header[18] = 0
    header[19] = 0
    header[20] = 1
    header[21] = 0
    header[22] = channels.toByte()
    header[23] = 0
    header[24] = (sampleRate and 0xff).toByte()
    header[25] = ((sampleRate shr 8) and 0xff).toByte()
    header[26] = ((sampleRate shr 16) and 0xff).toByte()
    header[27] = ((sampleRate shr 24) and 0xff).toByte()
    header[28] = (byteRate and 0xffL).toByte()
    header[29] = ((byteRate shr 8) and 0xffL).toByte()
    header[30] = ((byteRate shr 16) and 0xffL).toByte()
    header[31] = ((byteRate shr 24) and 0xffL).toByte()
    header[32] = (channels * bitsPerSample / 8).toByte()
    header[33] = 0
    header[34] = bitsPerSample.toByte()
    header[35] = 0
    header[36] = 'd'.code.toByte()
    header[37] = 'a'.code.toByte()
    header[38] = 't'.code.toByte()
    header[39] = 'a'.code.toByte()
    header[40] = (totalAudioLen and 0xffL).toByte()
    header[41] = ((totalAudioLen shr 8) and 0xffL).toByte()
    header[42] = ((totalAudioLen shr 16) and 0xffL).toByte()
    header[43] = ((totalAudioLen shr 24) and 0xffL).toByte()

    val wavOut = ByteArray(header.size + pcmData.size)
    System.arraycopy(header, 0, wavOut, 0, header.size)
    System.arraycopy(pcmData, 0, wavOut, header.size, pcmData.size)
    return wavOut
  }
}
