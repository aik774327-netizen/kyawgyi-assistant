package com.example.voice

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.example.actions.CommandResult
import com.example.actions.KyawGyiActionExecutor
import com.example.audio.CentralAudioRecorder
import com.example.nlu.BurmeseNluPreprocessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * HybridEngineRouter
 *
 * Coordinates real-time Voice Engine Routing & Fallback Mechanism:
 * 1. Active Network Health Check (Pings actual Google API endpoint, not just Wi-Fi connection state).
 * 2. Pre-caches incoming audio stream from CentralAudioRecorder to prevent dropped audio packets during fallback.
 * 3. Strict 1.5-second (1500ms) execution timeout on GeminiOnlineStrategy.
 * 4. Zero-loss Instant Fallback to SherpaOnnxOfflineStrategy if offline, timeout, or API error occurs.
 * 5. Dispatches Function Calls directly to KyawGyiActionExecutor (CameraManager, AudioManager, Wifi, etc.).
 */
class HybridEngineRouter(
    private val context: Context,
    private val geminiStrategy: GeminiOnlineStrategy = GeminiOnlineStrategy(context),
    private val offlineStrategy: SherpaOnnxOfflineStrategy = SherpaOnnxOfflineStrategy(context),
    private val actionExecutor: KyawGyiActionExecutor = KyawGyiActionExecutor(context)
) {

    companion object {
        private const val TAG = "HybridEngineRouter"
        const val ONLINE_TIMEOUT_MS = 1500L
        private const val PING_TIMEOUT_MS = 800

        @Volatile
        private var instance: HybridEngineRouter? = null

        fun getInstance(context: Context): HybridEngineRouter {
            return instance ?: synchronized(this) {
                instance ?: HybridEngineRouter(context.applicationContext).also { instance = it }
            }
        }
    }

    private val centralRecorder = CentralAudioRecorder.getInstance(context)
    private val pingClient = OkHttpClient.Builder()
        .connectTimeout(PING_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
        .readTimeout(PING_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
        .build()

    /**
     * Active Network Health Check:
     * Verifies actual internet reachability by pinging Google API endpoint (8.8.8.8 DNS or HTTPS HEAD).
     */
    suspend fun isNetworkHealthy(): Boolean = withContext(Dispatchers.IO) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val activeNet = cm?.activeNetwork ?: return@withContext false
        val caps = cm.getNetworkCapabilities(activeNet) ?: return@withContext false

        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ||
            !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        ) {
            return@withContext false
        }

        // Active socket check to google.com port 443 / 8.8.8.8 port 53 with low timeout
        return@withContext try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress("8.8.8.8", 53), PING_TIMEOUT_MS)
                true
            }
        } catch (e: Exception) {
            Log.w(TAG, "Active socket health check failed: ${e.message}")
            false
        }
    }

    /**
     * Executes the speech stream through the Hybrid Architecture with automatic fallback.
     */
    suspend fun processVoiceStream(): AssistantResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val isOnline = isNetworkHealthy()

        Log.i(TAG, "processVoiceStream initiated. Active network health: $isOnline")

        // 1. Pre-buffer incoming chunks into memory so if online times out, the EXACT same audio is fed to offline
        val bufferedChunks = mutableListOf<ByteArray>()
        val bufferReady = AtomicBoolean(false)

        // Ensure CentralAudioRecorder is active
        centralRecorder.startRecording("HybridEngineRouter")

        try {
            // Collect first 40 chunks (~1.6 seconds)
            var collected = 0
            centralRecorder.audioSharedFlow.collect { chunk ->
                bufferedChunks.add(chunk)
                collected++
                if (collected >= 40) {
                    bufferReady.set(true)
                    return@collect
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Audio buffer collection note: ${e.message}")
        } finally {
            centralRecorder.stopRecording("HybridEngineRouter")
        }

        if (bufferedChunks.isEmpty()) {
            return@withContext AssistantResult(
                isSuccess = false,
                transcription = "",
                spokenResponse = "အသံဖမ်းယူမှု မအောင်မြင်ပါခင်ဗျာ",
                executionEngine = EngineType.HYBRID_FALLBACK,
                latencyMs = System.currentTimeMillis() - startTime,
                errorMessage = "No audio recorded"
            )
        }

        // Replayable Flow created from pre-buffered memory
        val audioStreamFactory = { bufferedChunks.asFlow() }

        var result: AssistantResult? = null

        // 2. Online Execution Attempt if network is healthy
        if (isOnline) {
            try {
                Log.i(TAG, "Routing to GeminiOnlineStrategy with ${ONLINE_TIMEOUT_MS}ms strict timeout...")
                withTimeout(ONLINE_TIMEOUT_MS) {
                    val onlineResult = geminiStrategy.processAudioStream(audioStreamFactory())
                    if (onlineResult.isSuccess) {
                        result = onlineResult
                    } else {
                        Log.w(TAG, "GeminiOnlineStrategy returned failure: ${onlineResult.errorMessage}. Falling back.")
                    }
                }
            } catch (timeout: TimeoutCancellationException) {
                Log.w(TAG, "GeminiOnlineStrategy timed out after ${ONLINE_TIMEOUT_MS}ms! Triggering instant offline fallback.")
            } catch (e: Exception) {
                Log.w(TAG, "GeminiOnlineStrategy error: ${e.message}. Triggering instant offline fallback.")
            }
        } else {
            Log.i(TAG, "Network is OFFLINE. Bypassing Gemini to save battery and latency.")
        }

        // 3. Offline Fallback Execution (if Online failed or was bypassed)
        if (result == null || !result!!.isSuccess) {
            Log.i(TAG, "Executing SherpaOnnxOfflineStrategy with buffered audio...")
            val offlineResult = offlineStrategy.processAudioStream(audioStreamFactory())
            result = offlineResult.copy(
                executionEngine = EngineType.HYBRID_FALLBACK
            )
        }

        val rawResult = result!!
        // Layer 2 Post-ASR Preprocessing: Diacritic Re-ordering and Unicode Sanitization
        val sanitizedTranscription = BurmeseNluPreprocessor.normalize(rawResult.transcription)
        val finalResult = rawResult.copy(transcription = sanitizedTranscription)

        // 4. System Action Execution if Function Call is requested
        if (finalResult.functionCall != null) {
            Log.i(TAG, "Executing mapped system action from function call: ${finalResult.functionCall.name}")
            executeSystemAction(finalResult.functionCall)
        }

        // Release or unbind heavy offline acoustic models on low RAM devices if not actively in fallback
        if (finalResult.executionEngine != EngineType.SHERPA_ONNX_OFFLINE && finalResult.executionEngine != EngineType.HYBRID_FALLBACK) {
            offlineStrategy.releaseModel()
        }

        Log.i(TAG, "Hybrid Voice Processing Finished: engine=${finalResult.executionEngine}, latency=${finalResult.latencyMs}ms")
        finalResult
    }

    /**
     * Executes Gemini Function Call (control_device_setting) using KyawGyiActionExecutor
     */
    private fun executeSystemAction(functionCall: FunctionCallPayload): CommandResult? {
        val args = functionCall.arguments
        val action = args["action"] as? String ?: return null
        val state = args["state"] as? Boolean ?: true
        val targetApp = args["target_app"] as? String ?: ""

        Log.i(TAG, "Dispatching system action: action=$action, state=$state, targetApp=$targetApp")

        return when (action.uppercase()) {
            "TOGGLE_FLASHLIGHT" -> actionExecutor.setFlashlight(state)
            "TOGGLE_WIFI" -> actionExecutor.setWifi(state)
            "TOGGLE_BLUETOOTH" -> actionExecutor.setBluetooth(state)
            "SCAN_BLE" -> actionExecutor.scanBleDevices()
            "CONNECT_BLE" -> actionExecutor.connectBleDevice(targetApp)
            "DISCONNECT_BLE" -> actionExecutor.disconnectBleDevice()
            "STATUS_BLE" -> actionExecutor.getBleStatus()
            "ADJUST_VOLUME" -> actionExecutor.executeSystemAction(if (state) "volume up" else "volume down")
            "ADJUST_BRIGHTNESS" -> actionExecutor.executeSystemAction(if (state) "brightness up" else "brightness down")
            "OPEN_APP" -> if (targetApp.isNotBlank()) actionExecutor.openApplication(targetApp, targetApp) else null
            else -> actionExecutor.executeSystemAction(action)
        }
    }
}
