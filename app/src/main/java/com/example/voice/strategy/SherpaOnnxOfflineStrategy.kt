package com.example.voice.strategy

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.sqrt

/**
 * Offline Voice Recognition Strategy.
 *
 * NOTE: Offline voice recognition (Sherpa-ONNX / local neural acoustic model) is NOT implemented
 * because no offline neural acoustic model asset is bundled in the application.
 *
 * Behavior:
 * 1. Checks network connectivity: if internet is available, routes directly to [GeminiOnlineStrategy].
 * 2. If offline / no network, honestly returns an unavailable/error result carrying a clear Burmese
 *    user-facing message explaining that offline recognition is not available and the app will fall back
 *    to online recognition when network is available.
 * 3. Does NOT pretend any model is loading or synthesize fake recognition results.
 */
class SherpaOnnxOfflineStrategy(
  private val context: Context,
  private val onlineStrategy: VoiceAssistantStrategy = GeminiOnlineStrategy(context)
) : VoiceAssistantStrategy {

  override val engineName: String = "SherpaOnnxOfflineStrategy (Offline Engine - No Bundled Model)"

  companion object {
    private const val TAG = "SherpaOnnxOffline"
    const val OFFLINE_UNAVAILABLE_MESSAGE_BURMESE =
      "အော့ဖ်လိုင်း စကားသံအသိအမှတ်ပြုစနစ် မရရှိနိုင်သေးပါ (မော်ဒယ်ဖိုင် ထည့်သွင်းထားခြင်း မရှိပါ)။ အင်တာနက်လိုင်း ရရှိပါက အွန်လိုင်းစနစ်ဖြင့် အလိုအလျောက် ဆောင်ရွက်ပေးပါမည်။"
  }

  // Audit C3: an offlineCommands regex table + evaluateOfflineText() used
  // to live here. They had zero callers (the live text path is
  // MyanmarVoiceParser, which now covers those command families itself),
  // so the dead duplicate matcher was removed.

  fun isNetworkAvailable(): Boolean {
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

  override suspend fun processAudioStream(audioFlow: Flow<ByteArray>): AssistantResult = withContext(Dispatchers.Default) {
    // 1. Consume audio chunks without dropping buffer so online strategy has access to audio
    val pcmStream = ByteArrayOutputStream()
    try {
      audioFlow.take(30).collect { chunk ->
        pcmStream.write(chunk)
      }
    } catch (e: Exception) {
      Log.w(TAG, "Audio stream collection ended: ${e.message}")
    }

    val rawBytes = pcmStream.toByteArray()
    if (rawBytes.isEmpty()) {
      return@withContext AssistantResult.Error("အသံဖမ်းယူမှု မကြားရပါ (Empty buffer)")
    }

    // Energy / Voice Activity Detection check
    val hasVoiceActivity = checkVad(rawBytes)
    if (!hasVoiceActivity) {
      return@withContext AssistantResult.Error("စကားသံ လုံလောက်စွာ မကြားရပါ")
    }

    // If network is available, route directly to online recognition strategy
    if (isNetworkAvailable()) {
      Log.i(TAG, "Network is available. Routing speech to online strategy.")
      val replayFlow = kotlinx.coroutines.flow.flow {
        var offset = 0
        val chunkSize = 1024
        while (offset < rawBytes.size) {
          val len = minOf(chunkSize, rawBytes.size - offset)
          val chunk = ByteArray(len)
          System.arraycopy(rawBytes, offset, chunk, 0, len)
          emit(chunk)
          offset += len
        }
      }
      return@withContext onlineStrategy.processAudioStream(replayFlow)
    }

    // Honest behavior: offline neural model is not bundled/implemented
    Log.w(TAG, "Offline recognition requested but no model is bundled.")
    AssistantResult.Error(
      errorMessage = OFFLINE_UNAVAILABLE_MESSAGE_BURMESE,
      isRetryable = true
    )
  }

  private fun checkVad(bytes: ByteArray): Boolean {
    var sum = 0.0
    val count = bytes.size / 2
    if (count <= 0) return false

    for (i in 0 until bytes.size - 1 step 2) {
      val s = (bytes[i].toInt() and 0xFF) or (bytes[i + 1].toInt() shl 8)
      sum += s * s
    }
    val rms = sqrt(sum / count)
    return rms > 300.0 // RMS threshold for human speech
  }
}
