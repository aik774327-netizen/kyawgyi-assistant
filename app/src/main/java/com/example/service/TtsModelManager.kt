package com.example.service

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * Supported Text-to-Speech (TTS) Models / Providers:
 * 1. OpenAI TTS (tts-1, tts-1-hd)
 * 2. Google Cloud TTS (my-MM-Standard-A / Wavenet)
 * 3. ElevenLabs TTS (eleven_multilingual_v2)
 * 4. Gemini TTS (gemini-2.0-flash Audio)
 * 5. System Android TTS (Built-in offline engine)
 */
enum class TtsProviderType(val displayName: String, val description: String) {
    OPENAI("OpenAI TTS", "OpenAI tts-1 / tts-1-hd (Alloy, Nova, Shimmer)"),
    GOOGLE_CLOUD("Google Cloud TTS", "Google Cloud my-MM-Standard-A (Native Burmese voice)"),
    ELEVENLABS("ElevenLabs TTS", "ElevenLabs Multilingual v2 (Expressive voice)"),
    GEMINI("Gemini 2.0 Flash TTS", "Gemini 2.0 Flash Direct AI Vocalization"),
    SYSTEM_LOCAL("System Default TTS", "Android Built-in Google Speech Services")
}

data class TtsSettingsState(
    val selectedProvider: TtsProviderType = TtsProviderType.SYSTEM_LOCAL,
    val openaiApiKey: String = "",
    val googleTtsApiKey: String = "",
    val elevenLabsApiKey: String = "",
    val elevenLabsVoiceId: String = "21m00Tcm4TlvDq8ikWAM",
    val geminiApiKey: String = "",
    val selectedVoice: String = "Default",
    val isPlaying: Boolean = false,
    val statusMessage: String? = null
)

class TtsModelManager(
    private val context: Context,
    private val systemTtsEngine: MyanmarTtsEngine
) {
    private val TAG = "TtsModelManager"
    private val prefs = context.getSharedPreferences("ai_angel_tts_prefs", Context.MODE_PRIVATE)

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val _state = MutableStateFlow(loadSettings())
    val state: StateFlow<TtsSettingsState> = _state.asStateFlow()

    private var mediaPlayer: MediaPlayer? = null

    var onSpeechStarted: (() -> Unit)? = null
    var onSpeechCompleted: (() -> Unit)? = null
    var onSpeechError: ((String) -> Unit)? = null

    private fun loadSettings(): TtsSettingsState {
        val providerName = prefs.getString("selected_provider", TtsProviderType.SYSTEM_LOCAL.name)
        val provider = try {
            TtsProviderType.valueOf(providerName ?: TtsProviderType.SYSTEM_LOCAL.name)
        } catch (_: Exception) {
            TtsProviderType.SYSTEM_LOCAL
        }

        return TtsSettingsState(
            selectedProvider = provider,
            openaiApiKey = prefs.getString("openai_key", "") ?: "",
            googleTtsApiKey = prefs.getString("google_tts_key", "") ?: "",
            elevenLabsApiKey = prefs.getString("elevenlabs_key", "") ?: "",
            elevenLabsVoiceId = prefs.getString("elevenlabs_voice_id", "21m00Tcm4TlvDq8ikWAM") ?: "21m00Tcm4TlvDq8ikWAM",
            geminiApiKey = prefs.getString("gemini_key", "") ?: "",
            selectedVoice = prefs.getString("selected_voice", "Default") ?: "Default"
        )
    }

    fun setProvider(provider: TtsProviderType) {
        prefs.edit().putString("selected_provider", provider.name).apply()
        _state.value = _state.value.copy(selectedProvider = provider, statusMessage = "${provider.displayName} ကို ရွေးချယ်လိုက်ပါပြီ")
    }

    fun updateApiKeys(
        openaiKey: String? = null,
        googleKey: String? = null,
        elevenLabsKey: String? = null,
        elevenLabsVoiceId: String? = null,
        geminiKey: String? = null
    ) {
        val editor = prefs.edit()
        var current = _state.value

        openaiKey?.let {
            editor.putString("openai_key", it)
            current = current.copy(openaiApiKey = it)
        }
        googleKey?.let {
            editor.putString("google_tts_key", it)
            current = current.copy(googleTtsApiKey = it)
        }
        elevenLabsKey?.let {
            editor.putString("elevenlabs_key", it)
            current = current.copy(elevenLabsApiKey = it)
        }
        elevenLabsVoiceId?.let {
            editor.putString("elevenlabs_voice_id", it)
            current = current.copy(elevenLabsVoiceId = it)
        }
        geminiKey?.let {
            editor.putString("gemini_key", it)
            current = current.copy(geminiApiKey = it)
        }

        editor.apply()
        _state.value = current
    }

    /**
     * Synthesizes and speaks text using the user's preferred TTS provider.
     * Falls back to System Android TTS if cloud API fails or keys are missing.
     */
    suspend fun speak(text: String) = withContext(Dispatchers.IO) {
        val currentState = _state.value

        when (currentState.selectedProvider) {
            TtsProviderType.SYSTEM_LOCAL -> {
                speakLocally(text)
            }
            TtsProviderType.OPENAI -> {
                val key = currentState.openaiApiKey.ifBlank { System.getenv("OPENAI_API_KEY") ?: "" }
                if (key.isBlank()) {
                    speakLocally(text, "OpenAI API Key မရှိသေး၍ စက်တွင်း TTS ဖြင့် ဖတ်ကြားပါမည်")
                } else {
                    synthesizeOpenAi(text, key)
                }
            }
            TtsProviderType.GOOGLE_CLOUD -> {
                val key = currentState.googleTtsApiKey.ifBlank { System.getenv("GOOGLE_TTS_API_KEY") ?: "" }
                if (key.isBlank()) {
                    speakLocally(text, "Google Cloud TTS Key မရှိသေး၍ စက်တွင်း TTS ဖြင့် ဖတ်ကြားပါမည်")
                } else {
                    synthesizeGoogleCloud(text, key)
                }
            }
            TtsProviderType.ELEVENLABS -> {
                val key = currentState.elevenLabsApiKey.ifBlank { System.getenv("ELEVENLABS_API_KEY") ?: "" }
                if (key.isBlank()) {
                    speakLocally(text, "ElevenLabs API Key မရှိသေး၍ စက်တွင်း TTS ဖြင့် ဖတ်ကြားပါမည်")
                } else {
                    synthesizeElevenLabs(text, key, currentState.elevenLabsVoiceId)
                }
            }
            TtsProviderType.GEMINI -> {
                val key = currentState.geminiApiKey.ifBlank { System.getenv("GEMINI_API_KEY") ?: "" }
                if (key.isBlank()) {
                    speakLocally(text, "Gemini API Key မရှိသေး၍ စက်တွင်း TTS ဖြင့် ဖတ်ကြားပါမည်")
                } else {
                    synthesizeGemini(text, key)
                }
            }
        }
    }

    private fun speakLocally(text: String, fallbackReason: String? = null) {
        if (fallbackReason != null) {
            Log.w(TAG, fallbackReason)
            _state.value = _state.value.copy(statusMessage = fallbackReason)
        }
        systemTtsEngine.speak(text)
    }

    private suspend fun synthesizeOpenAi(text: String, apiKey: String) {
        try {
            val body = JSONObject().apply {
                put("model", "tts-1")
                put("input", text)
                put("voice", "nova")
                put("response_format", "mp3")
            }

            val request = Request.Builder()
                .url("https://api.openai.com/v1/audio/speech")
                .header("Authorization", "Bearer $apiKey")
                .post(body.toString().toRequestBody(jsonMediaType))
                .build()

            executeAudioRequest(request, "openai_tts.mp3")
        } catch (e: Exception) {
            Log.e(TAG, "OpenAI TTS error: ${e.message}", e)
            speakLocally(text, "OpenAI TTS ချို့ယွင်းသဖြင့် စက်တွင်း TTS ဖြင့် အစားထိုးပါသည်")
        }
    }

    private suspend fun synthesizeGoogleCloud(text: String, apiKey: String) {
        try {
            val body = JSONObject().apply {
                put("input", JSONObject().put("text", text))
                put("voice", JSONObject().apply {
                    put("languageCode", "my-MM")
                    put("name", "my-MM-Standard-A")
                })
                put("audioConfig", JSONObject().apply {
                    put("audioEncoding", "MP3")
                    put("speakingRate", 0.95)
                })
            }

            val request = Request.Builder()
                .url("https://texttospeech.googleapis.com/v1/text:synthesize?key=$apiKey")
                .post(body.toString().toRequestBody(jsonMediaType))
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw Exception("Google Cloud TTS HTTP ${response.code}")
                }
                val json = JSONObject(response.body?.string() ?: "")
                val audioBase64 = json.optString("audioContent")
                val bytes = Base64.decode(audioBase64, Base64.DEFAULT)
                playAudioBytes(bytes, "google_tts.mp3")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Google Cloud TTS error: ${e.message}", e)
            speakLocally(text, "Google Cloud TTS ချို့ယွင်းသဖြင့် စက်တွင်း TTS ဖြင့် အစားထိုးပါသည်")
        }
    }

    private suspend fun synthesizeElevenLabs(text: String, apiKey: String, voiceId: String) {
        try {
            val body = JSONObject().apply {
                put("text", text)
                put("model_id", "eleven_multilingual_v2")
            }

            val request = Request.Builder()
                .url("https://api.elevenlabs.io/v1/text-to-speech/$voiceId?output_format=mp3_44100_128")
                .header("xi-api-key", apiKey)
                .post(body.toString().toRequestBody(jsonMediaType))
                .build()

            executeAudioRequest(request, "elevenlabs_tts.mp3")
        } catch (e: Exception) {
            Log.e(TAG, "ElevenLabs TTS error: ${e.message}", e)
            speakLocally(text, "ElevenLabs TTS ချို့ယွင်းသဖြင့် စက်တွင်း TTS ဖြင့် အစားထိုးပါသည်")
        }
    }

    private suspend fun synthesizeGemini(text: String, apiKey: String) {
        try {
            val body = JSONObject().apply {
                val parts = JSONArray().put(JSONObject().put("text", "Please speak this text clearly in natural Burmese: $text"))
                val contents = JSONArray().put(JSONObject().put("parts", parts))
                put("contents", contents)
                put("generationConfig", JSONObject().apply {
                    put("responseModalities", JSONArray().put("AUDIO"))
                    put("speechConfig", JSONObject().put("voiceConfig", JSONObject().put("prebuiltVoiceConfig", JSONObject().put("voiceName", "Aoede"))))
                })
            }

            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=$apiKey")
                .post(body.toString().toRequestBody(jsonMediaType))
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw Exception("Gemini TTS HTTP ${response.code}")
                }
                val json = JSONObject(response.body?.string() ?: "")
                val inlineData = json.optJSONArray("candidates")
                    ?.optJSONObject(0)
                    ?.optJSONObject("content")
                    ?.optJSONArray("parts")
                    ?.optJSONObject(0)
                    ?.optJSONObject("inlineData")

                val base64Data = inlineData?.optString("data") ?: throw Exception("No inlineData in Gemini response")
                val bytes = Base64.decode(base64Data, Base64.DEFAULT)
                playAudioBytes(bytes, "gemini_tts.wav")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Gemini TTS error: ${e.message}", e)
            speakLocally(text, "Gemini TTS ချို့ယွင်းသဖြင့် စက်တွင်း TTS ဖြင့် အစားထိုးပါသည်")
        }
    }

    private fun executeAudioRequest(request: Request, filename: String) {
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val error = response.body?.string() ?: "HTTP ${response.code}"
                throw Exception("Audio API error: $error")
            }
            val bytes = response.body?.bytes() ?: throw Exception("Empty audio response body")
            playAudioBytes(bytes, filename)
        }
    }

    private fun playAudioBytes(bytes: ByteArray, filename: String) {
        stop()
        val tempFile = File(context.cacheDir, filename)
        FileOutputStream(tempFile).use { it.write(bytes) }

        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                    .build()
            )
            setDataSource(tempFile.absolutePath)
            setOnPreparedListener {
                _state.value = _state.value.copy(isPlaying = true)
                onSpeechStarted?.invoke()
                start()
            }
            setOnCompletionListener {
                _state.value = _state.value.copy(isPlaying = false)
                onSpeechCompleted?.invoke()
                release()
                mediaPlayer = null
            }
            setOnErrorListener { _, what, extra ->
                _state.value = _state.value.copy(isPlaying = false)
                onSpeechError?.invoke("MediaPlayer error what=$what, extra=$extra")
                release()
                mediaPlayer = null
                true
            }
            prepareAsync()
        }
    }

    fun stop() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (_: Exception) {}
        mediaPlayer = null
        _state.value = _state.value.copy(isPlaying = false)
        systemTtsEngine.stop()
    }
}
