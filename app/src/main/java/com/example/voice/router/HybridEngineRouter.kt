package com.example.voice.router

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.example.voice.core.CentralAudioRecorder
import com.example.voice.strategy.AssistantResult
import com.example.voice.strategy.GeminiOnlineStrategy
import com.example.voice.strategy.SherpaOnnxOfflineStrategy
import com.example.voice.strategy.VoiceAssistantStrategy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream

/**
 * Hybrid Engine Router.
 *
 * Implements:
 * 1. Network Health Checks: Uses ConnectivityManager to check active internet capabilities.
 * 2. Strict 1.5-Second Timeout: If Online Gemini takes longer than 1500ms, or throws network error,
 *    instantly falls back to [SherpaOnnxOfflineStrategy].
 * 3. Zero Audio Buffer Loss: Audio chunks captured from [CentralAudioRecorder] are cached in memory
 *    so the fallback engine receives the exact same audio buffer without asking the user to repeat.
 */
class HybridEngineRouter(
  private val context: Context,
  private val geminiStrategy: GeminiOnlineStrategy = GeminiOnlineStrategy(context),
  private val offlineStrategy: SherpaOnnxOfflineStrategy = SherpaOnnxOfflineStrategy(context)
) {

  companion object {
    private const val TAG = "HybridEngineRouter"
    const val GEMINI_TIMEOUT_MS = 1500L
  }

  private val _currentActiveEngine = MutableStateFlow<String>("Automatic (Hybrid)")
  val currentActiveEngine: StateFlow<String> = _currentActiveEngine.asStateFlow()

  private val _lastFallbackReason = MutableStateFlow<String?>(null)
  val lastFallbackReason: StateFlow<String?> = _lastFallbackReason.asStateFlow()

  /**
   * Fast Network Capability Check.
   */
  fun isNetworkOnline(): Boolean {
    return try {
      val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
      val net = cm.activeNetwork ?: return false
      val cap = cm.getNetworkCapabilities(net) ?: return false
      cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
          cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    } catch (_: Exception) {
      false
    }
  }

  /**
   * Routes the speech processing stream through Gemini Online -> Fallback Offline.
   * Preserves the audio buffer across fallback without dropping frames.
   */
  suspend fun routeSpeech(audioFlow: Flow<ByteArray>): AssistantResult = withContext(Dispatchers.IO) {
    // 1. Buffer first ~2.5 seconds of PCM frames from central flow so fallback has identical audio
    val bufferedStream = ByteArrayOutputStream()
    try {
      audioFlow.take(35).collect { chunk ->
        bufferedStream.write(chunk)
      }
    } catch (e: Exception) {
      Log.w(TAG, "Buffer collection finished: ${e.message}")
    }

    val audioBytes = bufferedStream.toByteArray()
    if (audioBytes.isEmpty()) {
      return@withContext AssistantResult.Error("အသံဖမ်းယူမှု မရှိပါ")
    }

    // Helper flow that replays cached buffer
    fun makeReplayFlow(): Flow<ByteArray> = kotlinx.coroutines.flow.flow {
      var offset = 0
      val chunkSize = 1024
      while (offset < audioBytes.size) {
        val len = minOf(chunkSize, audioBytes.size - offset)
        val chunk = ByteArray(len)
        System.arraycopy(audioBytes, offset, chunk, 0, len)
        emit(chunk)
        offset += len
      }
    }

    val online = isNetworkOnline()

    if (!online) {
      _currentActiveEngine.value = offlineStrategy.engineName
      _lastFallbackReason.value = "အင်တာနက်လိုင်း မရှိပါ (Offline Fallback)"
      Log.i(TAG, "Offline detected. Routing directly to Sherpa-ONNX.")
      return@withContext offlineStrategy.processAudioStream(makeReplayFlow())
    }

    // Attempt Online with 1.5s timeout
    _currentActiveEngine.value = geminiStrategy.engineName
    Log.d(TAG, "Online detected. Routing to Gemini with 1.5s timeout.")

    val geminiResult = withTimeoutOrNull(GEMINI_TIMEOUT_MS) {
      try {
        geminiStrategy.processAudioStream(makeReplayFlow())
      } catch (e: Exception) {
        Log.w(TAG, "Gemini online processing failed: ${e.message}")
        null
      }
    }

    if (geminiResult is AssistantResult.Success) {
      _lastFallbackReason.value = null
      return@withContext geminiResult
    }

    // Timeout or Gemini failure occurred -> Fallback to Sherpa-ONNX
    val reason = if (geminiResult == null) {
      "Gemini API အချိန် ကျော်လွန်သွားပါသည် (>1.5s Timeout) - အော့ဖ်လိုင်းသို့ လွှဲပြောင်းပါသည်"
    } else {
      (geminiResult as? AssistantResult.Error)?.errorMessage ?: "Gemini အမှားကြောင့် အော့ဖ်လိုင်းသို့ လွှဲပြောင်းပါသည်"
    }

    Log.w(TAG, "Fallback triggered: $reason")
    _lastFallbackReason.value = reason
    _currentActiveEngine.value = offlineStrategy.engineName

    val fallbackResult = offlineStrategy.processAudioStream(makeReplayFlow())
    return@withContext when (fallbackResult) {
      is AssistantResult.Success -> fallbackResult.copy(isOfflineFallback = true)
      else -> fallbackResult
    }
  }
}
