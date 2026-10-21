package com.example.actions

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.example.data.local.entity.UrlControlEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

data class UrlExecutionResult(
    val isSuccess: Boolean,
    val responseBody: String = "",
    val speechFeedback: String,
    val httpStatusCode: Int = 200,
    val latencyMs: Long = 0
)

class UrlVoiceControlExecutor(private val context: Context) {

    private val TAG = "UrlVoiceControlExecutor"

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.SECONDS)
        .build()

    /**
     * Executes a configured URL Link based on Myanmar Voice trigger
     */
    suspend fun execute(
        urlControl: UrlControlEntity,
        spokenVoiceText: String
    ): UrlExecutionResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        try {
            val processedUrl = substituteVariables(urlControl.url, spokenVoiceText)

            when (urlControl.actionType.uppercase()) {
                "DEEP_LINK", "OPEN_URL" -> {
                    val success = launchUrlOrDeepLink(processedUrl)
                    val latency = System.currentTimeMillis() - startTime
                    if (success) {
                        UrlExecutionResult(
                            isSuccess = true,
                            responseBody = "Launched: $processedUrl",
                            speechFeedback = urlControl.successSpeech.ifBlank { "${urlControl.name} ကို ချိတ်ဆက်ဖွင့်ပေးလိုက်ပါပြီ" },
                            httpStatusCode = 200,
                            latencyMs = latency
                        )
                    } else {
                        UrlExecutionResult(
                            isSuccess = false,
                            responseBody = "Failed to launch URL / App",
                            speechFeedback = urlControl.failureSpeech.ifBlank { "အဆိုပါ Application သို့မဟုတ် Link ကို ဖွင့်၍မရပါ" },
                            httpStatusCode = 400,
                            latencyMs = latency
                        )
                    }
                }

                "WEBHOOK_GET" -> {
                    val finalUrl = if (processedUrl.contains("?")) {
                        "$processedUrl&voice_text=${URLEncoder.encode(spokenVoiceText, StandardCharsets.UTF_8.name())}&timestamp=${System.currentTimeMillis()}"
                    } else {
                        "$processedUrl?voice_text=${URLEncoder.encode(spokenVoiceText, StandardCharsets.UTF_8.name())}&timestamp=${System.currentTimeMillis()}"
                    }

                    val requestBuilder = Request.Builder().url(finalUrl).get()
                    applyHeaders(requestBuilder, urlControl.headers)

                    val response = httpClient.newCall(requestBuilder.build()).execute()
                    val latency = System.currentTimeMillis() - startTime
                    val body = response.body?.string() ?: ""

                    if (response.isSuccessful) {
                        UrlExecutionResult(
                            isSuccess = true,
                            responseBody = body.take(300),
                            speechFeedback = urlControl.successSpeech.ifBlank { "${urlControl.name} သို့ အသံအမိန့် ပေးပို့ဆောင်ရွက်ပြီးပါပြီ" },
                            httpStatusCode = response.code,
                            latencyMs = latency
                        )
                    } else {
                        UrlExecutionResult(
                            isSuccess = false,
                            responseBody = "HTTP ${response.code}: $body",
                            speechFeedback = urlControl.failureSpeech.ifBlank { "ဆာဗာသို့ ချိတ်ဆက်ရာတွင် အမှားဖြစ်ပေါ်ပါသည်" },
                            httpStatusCode = response.code,
                            latencyMs = latency
                        )
                    }
                }

                "WEBHOOK_POST", "REMOTE_MIC_STREAM" -> {
                    val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
                    val rawBody = if (urlControl.requestBody.isNotBlank()) {
                        substituteVariables(urlControl.requestBody, spokenVoiceText)
                    } else {
                        """
                        {
                            "command": "${escapeJson(spokenVoiceText)}",
                            "trigger_name": "${escapeJson(urlControl.name)}",
                            "action_type": "${urlControl.actionType}",
                            "timestamp": ${System.currentTimeMillis()},
                            "language": "my-MM"
                        }
                        """.trimIndent()
                    }

                    val requestBody = rawBody.toRequestBody(mediaType)
                    val requestBuilder = Request.Builder()
                        .url(processedUrl)
                        .post(requestBody)

                    applyHeaders(requestBuilder, urlControl.headers)

                    val response = httpClient.newCall(requestBuilder.build()).execute()
                    val latency = System.currentTimeMillis() - startTime
                    val body = response.body?.string() ?: ""

                    if (response.isSuccessful) {
                        UrlExecutionResult(
                            isSuccess = true,
                            responseBody = body.take(300),
                            speechFeedback = urlControl.successSpeech.ifBlank { "${urlControl.name} Webhook သို့ အချက်အလက် ပို့ဆောင်ပြီးပါပြီ" },
                            httpStatusCode = response.code,
                            latencyMs = latency
                        )
                    } else {
                        UrlExecutionResult(
                            isSuccess = false,
                            responseBody = "HTTP ${response.code}: $body",
                            speechFeedback = urlControl.failureSpeech.ifBlank { "Webhook ဆာဗာသို့ ချိတ်ဆက်မှု မအောင်မြင်ပါ" },
                            httpStatusCode = response.code,
                            latencyMs = latency
                        )
                    }
                }

                else -> {
                    // Fallback to OPEN_URL
                    val success = launchUrlOrDeepLink(processedUrl)
                    val latency = System.currentTimeMillis() - startTime
                    UrlExecutionResult(
                        isSuccess = success,
                        responseBody = "Default URL executed: $processedUrl",
                        speechFeedback = if (success) urlControl.successSpeech else urlControl.failureSpeech,
                        latencyMs = latency
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error executing URL voice control for ${urlControl.name}", e)
            val latency = System.currentTimeMillis() - startTime
            UrlExecutionResult(
                isSuccess = false,
                responseBody = "Exception: ${e.localizedMessage}",
                speechFeedback = urlControl.failureSpeech.ifBlank { "လင့်ခ်သို့ ချိတ်ဆက်ရာတွင် ချို့ယွင်းချက် ရှိနေပါသည်" },
                httpStatusCode = 500,
                latencyMs = latency
            )
        }
    }

    /**
     * Forwards recognized Myanmar voice text directly to a master Remote Microphone Webhook/Stream URL
     */
    suspend fun forwardVoiceStreamToRemoteUrl(
        remoteUrl: String,
        spokenVoiceText: String
    ): Boolean = withContext(Dispatchers.IO) {
        if (remoteUrl.isBlank()) return@withContext false
        try {
            val jsonPayload = """
                {
                    "event": "MYANMAR_VOICE_STREAM_DATA",
                    "spoken_text": "${escapeJson(spokenVoiceText)}",
                    "timestamp": ${System.currentTimeMillis()},
                    "device": "KyawGyi_Assistant",
                    "status": "STREAMING_ACTIVE"
                }
            """.trimIndent()

            val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
            val body = jsonPayload.toRequestBody(mediaType)
            val request = Request.Builder().url(remoteUrl).post(body).build()

            val response = httpClient.newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) {
            Log.w(TAG, "Failed to stream audio payload to remote URL: $remoteUrl", e)
            false
        }
    }

    private fun launchUrlOrDeepLink(targetUrl: String): Boolean {
        return try {
            val uri = Uri.parse(targetUrl)
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                true
            } else {
                // Fallback for custom schemes if app isn't installed (e.g. tg://, fb://, viber://)
                val scheme = uri.scheme?.lowercase()
                val fallbackUrl = when (scheme) {
                    "tg" -> "https://t.me" + (uri.getQueryParameter("domain")?.let { "/$it" } ?: "")
                    "fb" -> "https://www.facebook.com"
                    "viber" -> "https://www.viber.com"
                    "whatsapp" -> "https://web.whatsapp.com"
                    else -> if (!targetUrl.startsWith("http://") && !targetUrl.startsWith("https://")) {
                        "https://${targetUrl.substringAfter("://")}"
                    } else null
                }

                if (fallbackUrl != null) {
                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(fallbackUrl)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(browserIntent)
                    true
                } else {
                    Log.w(TAG, "No activity available to handle $targetUrl and no web fallback exists")
                    false
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Notice: Application for $targetUrl is not installed or cannot be opened directly: ${e.message}")
            // Graceful browser fallback attempt
            try {
                if (!targetUrl.startsWith("http://") && !targetUrl.startsWith("https://")) {
                    val webUrl = "https://" + targetUrl.substringAfter("://")
                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(webUrl)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(browserIntent)
                    true
                } else {
                    false
                }
            } catch (fallbackEx: Exception) {
                Log.w(TAG, "Fallback browser launch also skipped: ${fallbackEx.message}")
                false
            }
        }
    }

    private fun applyHeaders(requestBuilder: Request.Builder, headersRaw: String) {
        if (headersRaw.isBlank()) return
        val lines = headersRaw.split("\n", ";")
        for (line in lines) {
            val parts = line.split(":", limit = 2)
            if (parts.size == 2) {
                val key = parts[0].trim()
                val value = parts[1].trim()
                if (key.isNotBlank() && value.isNotBlank()) {
                    requestBuilder.addHeader(key, value)
                }
            }
        }
    }

    private fun substituteVariables(template: String, voiceText: String): String {
        val encodedVoice = URLEncoder.encode(voiceText, StandardCharsets.UTF_8.name())
        return template
            .replace("{voice_text}", voiceText)
            .replace("{query}", voiceText)
            .replace("{encoded_voice}", encodedVoice)
            .replace("{timestamp}", System.currentTimeMillis().toString())
    }

    private fun escapeJson(input: String): String {
        return input.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }
}
