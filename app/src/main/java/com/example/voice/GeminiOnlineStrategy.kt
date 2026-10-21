package com.example.voice

import android.content.Context
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import com.example.cognitive.KyawGyiSystemPersona
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
 * GeminiOnlineStrategy
 *
 * Connects directly to Google AI Studio via Gemini REST API:
 * 1. Collects speech frames from the central PCM audio stream.
 * 2. Encodes 16kHz PCM / Audio into Base64 inline data payload.
 * 3. Injects native Burmese Voice Assistant "KyawGyi" System Instructions.
 * 4. Supplies structured Function Calling schema for System Actions (Flashlight, Wi-Fi, Volume, Bluetooth, App Launch).
 * 5. Returns parsed AssistantResult with spoken response or device function calls.
 */
class GeminiOnlineStrategy(
    private val context: Context,
    private val apiKey: String = BuildConfig.GEMINI_API_KEY
) : VoiceAssistantStrategy {

    override val engineType: EngineType = EngineType.GEMINI_ONLINE

    companion object {
        private const val TAG = "GeminiOnlineStrategy"
        // Target modern Gemini 2.5 Flash as standard for fast voice processing
        private const val MODEL_NAME = "gemini-2.5-flash"
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    override suspend fun processAudioStream(audioFlow: Flow<ByteArray>): AssistantResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val pcmOutputStream = ByteArrayOutputStream()

        try {
            // Buffer up to ~2.0 seconds of audio frames from central flow (or until paused)
            // 1 chunk is 1280 bytes (40ms). 50 chunks = 2000ms.
            var chunksCollected = 0
            audioFlow.take(50).collect { chunk ->
                pcmOutputStream.write(chunk)
                chunksCollected++
            }

            val pcmBytes = pcmOutputStream.toByteArray()
            if (pcmBytes.isEmpty()) {
                return@withContext AssistantResult(
                    isSuccess = false,
                    transcription = "",
                    spokenResponse = "အသံဖမ်းယူမှု အချက်အလက် မရရှိပါခင်ဗျာ",
                    executionEngine = engineType,
                    latencyMs = System.currentTimeMillis() - startTime,
                    errorMessage = "Empty PCM audio buffer"
                )
            }

            // Create WAV header for 16kHz, 16-bit Mono PCM
            val wavBytes = addWavHeader(pcmBytes, 16000, 1, 16)
            val base64Audio = Base64.encodeToString(wavBytes, Base64.NO_WRAP)

            val requestBodyJson = buildGeminiRequestBody(base64Audio)

            val requestUrl = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL_NAME:generateContent?key=$apiKey"
            val request = Request.Builder()
                .url(requestUrl)
                .post(requestBodyJson.toString().toRequestBody(JSON_MEDIA))
                .build()

            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                Log.e(TAG, "Gemini API HTTP error ${response.code}: $responseBody")
                return@withContext AssistantResult(
                    isSuccess = false,
                    transcription = "",
                    spokenResponse = "ဆာဗာနှင့် ချိတ်ဆက်ရာတွင် အမှားဖြစ်ပေါ်ပါသည်",
                    executionEngine = engineType,
                    latencyMs = System.currentTimeMillis() - startTime,
                    errorMessage = "HTTP ${response.code}: $responseBody"
                )
            }

            parseGeminiResponse(responseBody, System.currentTimeMillis() - startTime)
        } catch (e: Exception) {
            Log.e(TAG, "GeminiOnlineStrategy processing exception: ${e.message}", e)
            AssistantResult(
                isSuccess = false,
                transcription = "",
                spokenResponse = "အွန်လိုင်းစနစ် ချို့ယွင်းနေပါသည်",
                executionEngine = engineType,
                latencyMs = System.currentTimeMillis() - startTime,
                errorMessage = e.message
            )
        }
    }

    private fun buildGeminiRequestBody(base64WavAudio: String): JSONObject {
        val root = JSONObject()

        // 1. System Instruction for KyawGyi
        val systemInstructionObj = JSONObject().apply {
            put("parts", JSONArray().apply {
                put(JSONObject().put("text", KyawGyiSystemPersona.MASTER_SYSTEM_PROMPT))
            })
        }
        root.put("systemInstruction", systemInstructionObj)

        // 2. Contents with Audio payload
        val contentsArray = JSONArray()
        val userContentObj = JSONObject().apply {
            put("role", "user")
            put("parts", JSONArray().apply {
                // Audio part
                val inlineDataObj = JSONObject().apply {
                    put("mimeType", "audio/wav")
                    put("data", base64WavAudio)
                }
                put(JSONObject().put("inlineData", inlineDataObj))
                // Text prompt instruction
                put(JSONObject().put("text", "Listen to this Burmese user's audio. Transcribe and either answer in Burmese or call the control_device_setting function if a system action is requested."))
            })
        }
        contentsArray.put(userContentObj)
        root.put("contents", contentsArray)

        // 3. Tools with Function Declarations
        val toolsArray = JSONArray()
        val toolObj = JSONObject()
        val functionDeclarations = JSONArray()

        val controlDeviceFunc = JSONObject().apply {
            put("name", "control_device_setting")
            put("description", "Controls Android system hardware and settings including flashlight, wifi, volume, brightness, or opening applications.")
            val parameters = JSONObject().apply {
                put("type", "OBJECT")
                val properties = JSONObject().apply {
                    put("action", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Action to perform: TOGGLE_FLASHLIGHT, TOGGLE_WIFI, ADJUST_VOLUME, ADJUST_BRIGHTNESS, OPEN_APP")
                    })
                    put("state", JSONObject().apply {
                        put("type", "BOOLEAN")
                        put("description", "True to turn on/increase, False to turn off/decrease")
                    })
                    put("target_app", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Target app name or package if action is OPEN_APP")
                    })
                }
                put("properties", properties)
                put("required", JSONArray().apply { put("action") })
            }
            put("parameters", parameters)
        }
        functionDeclarations.put(controlDeviceFunc)
        toolObj.put("functionDeclarations", functionDeclarations)
        toolsArray.put(toolObj)
        root.put("tools", toolsArray)

        return root
    }

    private fun parseGeminiResponse(jsonString: String, latency: Long): AssistantResult {
        return try {
            val root = JSONObject(jsonString)
            val candidates = root.optJSONArray("candidates")
            if (candidates == null || candidates.length() == 0) {
                return AssistantResult(
                    isSuccess = false,
                    transcription = "",
                    spokenResponse = "အဖြေ မရရှိပါခင်ဗျာ",
                    executionEngine = engineType,
                    latencyMs = latency,
                    errorMessage = "No candidates in response"
                )
            }

            val content = candidates.getJSONObject(0).optJSONObject("content")
            val parts = content?.optJSONArray("parts") ?: JSONArray()

            var textResponse = ""
            var functionCallPayload: FunctionCallPayload? = null

            for (i in 0 until parts.length()) {
                val part = parts.getJSONObject(i)
                if (part.has("text")) {
                    textResponse += part.getString("text")
                }
                if (part.has("functionCall")) {
                    val fc = part.getJSONObject("functionCall")
                    val fnName = fc.getString("name")
                    val argsObj = fc.optJSONObject("args")
                    val argsMap = mutableMapOf<String, Any?>()
                    argsObj?.keys()?.forEach { key ->
                        argsMap[key] = argsObj.get(key)
                    }
                    functionCallPayload = FunctionCallPayload(fnName, argsMap)
                }
            }

            AssistantResult(
                isSuccess = true,
                transcription = textResponse.take(80),
                spokenResponse = if (textResponse.isNotBlank()) textResponse else "အမိန့်ကို စတင် ဆောင်ရွက်ပါမည်",
                functionCall = functionCallPayload,
                executionEngine = engineType,
                latencyMs = latency
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing Gemini response: ${e.message}", e)
            AssistantResult(
                isSuccess = false,
                transcription = "",
                spokenResponse = "အဖြေ ခွဲခြမ်းစိတ်ဖြာ၍ မရပါ",
                executionEngine = engineType,
                latencyMs = latency,
                errorMessage = e.message
            )
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
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = (totalDataLen shr 8 and 0xff).toByte()
        header[6] = (totalDataLen shr 16 and 0xff).toByte()
        header[7] = (totalDataLen shr 24 and 0xff).toByte()
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
        header[25] = (sampleRate shr 8 and 0xff).toByte()
        header[26] = (sampleRate shr 16 and 0xff).toByte()
        header[27] = (sampleRate shr 24 and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = (byteRate shr 8 and 0xff).toByte()
        header[30] = (byteRate shr 16 and 0xff).toByte()
        header[31] = (byteRate shr 24 and 0xff).toByte()
        header[32] = (channels * bitsPerSample / 8).toByte()
        header[33] = 0
        header[34] = bitsPerSample.toByte()
        header[35] = 0
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        header[40] = (totalAudioLen and 0xff).toByte()
        header[41] = (totalAudioLen shr 8 and 0xff).toByte()
        header[42] = (totalAudioLen shr 16 and 0xff).toByte()
        header[43] = (totalAudioLen shr 24 and 0xff).toByte()

        val output = ByteArray(44 + pcmData.size)
        System.arraycopy(header, 0, output, 0, 44)
        System.arraycopy(pcmData, 0, output, 44, pcmData.size)
        return output
    }
}
