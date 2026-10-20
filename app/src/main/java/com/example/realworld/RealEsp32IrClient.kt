package com.example.realworld

import android.util.Log
import com.example.models.Esp32Status
import com.example.models.IrButtonProfile
import com.example.models.KennedeButton
import com.example.models.KennedeIrDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class ConnectionDiagnostic(
  val isSuccess: Boolean,
  val host: String,
  val latencyMs: Long,
  val httpCode: Int?,
  val errorCategory: String?,
  val failureReason: String?,
  val technicalDetails: String?
)

class RealEsp32IrClient(private var esp32Host: String = "192.168.1.150") {

  private val client = OkHttpClient.Builder()
    .connectTimeout(1500, TimeUnit.MILLISECONDS)
    .readTimeout(5, TimeUnit.SECONDS)
    .writeTimeout(3, TimeUnit.SECONDS)
    .retryOnConnectionFailure(false)
    .build()

  private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

  fun setHost(host: String) {
    esp32Host = host.trim().removePrefix("http://").removeSuffix("/")
  }

  fun getHost(): String = esp32Host

  private fun getBaseUrl(): String = "http://$esp32Host"

  suspend fun getStatus(): Result<Esp32Status> = withContext(Dispatchers.IO) {
    try {
      val request = Request.Builder()
        .url("${getBaseUrl()}/api/status")
        .get()
        .build()

      val response = client.newCall(request).execute()
      if (!response.isSuccessful) {
        return@withContext Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
      }

      val bodyStr = response.body?.string() ?: ""
      val json = JSONObject(bodyStr)

      val learnedMap = mutableMapOf<String, Boolean>()
      if (json.has("learned")) {
        val lObj = json.getJSONObject("learned")
        lObj.keys().forEach { k ->
          learnedMap[k] = lObj.optBoolean(k, false)
        }
      }

      // Audit B11: capability flags previously defaulted to true when the
      // JSON omitted them, so any JSON server could be misread as a healthy
      // ESP32 with receiver+transmitter. Absent now means absent (false).
      val status = Esp32Status(
        device = json.optString("device", "KENNEDE KN-5996HRB"),
        esp32 = json.optBoolean("esp32", false),
        receiver = json.optBoolean("receiver", false),
        transmitter = json.optBoolean("transmitter", false),
        carrierFrequency = json.optInt("carrierFrequency", 38000),
        learning = json.optBoolean("learning", false),
        learningTarget = json.optString("learningTarget", ""),
        ip = json.optString("ip", esp32Host),
        rssi = json.optInt("rssi", 0),
        learned = learnedMap
      )
      Result.success(status)
    } catch (e: Exception) {
      Log.d("RealEsp32IrClient", "getStatus: ESP32 not reachable at $esp32Host (${e.message})")
      Result.failure(e)
    }
  }

  suspend fun diagnoseConnection(targetHost: String? = null): ConnectionDiagnostic = withContext(Dispatchers.IO) {
    val testHost = (targetHost ?: esp32Host).trim().removePrefix("http://").removeSuffix("/")
    val startTime = System.currentTimeMillis()
    try {
      val request = Request.Builder()
        .url("http://$testHost/api/status")
        .get()
        .build()
      val response = client.newCall(request).execute()
      val elapsed = System.currentTimeMillis() - startTime
      val code = response.code
      val body = response.body?.string() ?: ""
      response.close()

      if (code in 200..299 && (body.contains("esp32") || body.contains("device") || body.contains("transmitter"))) {
        ConnectionDiagnostic(
          isSuccess = true,
          host = testHost,
          latencyMs = elapsed,
          httpCode = code,
          errorCategory = null,
          failureReason = null,
          technicalDetails = "HTTP $code OK ($elapsed ms) - KENNEDE IR firmware verified"
        )
      } else if (code in 200..299) {
        ConnectionDiagnostic(
          isSuccess = false,
          host = testHost,
          latencyMs = elapsed,
          httpCode = code,
          errorCategory = "လက်တွေ့မကိုက်ညီမှု",
          failureReason = "IP ($testHost) မှ တုံ့ပြန်မှုရသော်လည်း KENNEDE ESP32 IR firmware မဟုတ်ပါ",
          technicalDetails = "HTTP $code OK သို့သော် Payload တွင် ESP32 status မပါဝင်ပါ"
        )
      } else {
        ConnectionDiagnostic(
          isSuccess = false,
          host = testHost,
          latencyMs = elapsed,
          httpCode = code,
          errorCategory = "နည်းပညာပိုင်း",
          failureReason = "HTTP အမှားကုဒ် $code ပြန်လည်ရောက်ရှိပါသည်",
          technicalDetails = "HTTP Error $code"
        )
      }
    } catch (e: java.net.ConnectException) {
      val elapsed = System.currentTimeMillis() - startTime
      ConnectionDiagnostic(
        isSuccess = false,
        host = testHost,
        latencyMs = elapsed,
        httpCode = null,
        errorCategory = "အရင်းအမြစ်",
        failureReason = "Connection Refused: ကွန်ရက်လမ်းကြောင်း မရှိပါ သို့မဟုတ် ESP32 ပါဝါပိတ်ထားသည် (IP: $testHost)",
        technicalDetails = e.message ?: "Connection refused"
      )
    } catch (e: java.net.SocketTimeoutException) {
      val elapsed = System.currentTimeMillis() - startTime
      ConnectionDiagnostic(
        isSuccess = false,
        host = testHost,
        latencyMs = elapsed,
        httpCode = null,
        errorCategory = "နည်းပညာပိုင်း",
        failureReason = "Connection Timeout ($elapsed ms): ESP32 သို့ မရောက်ရှိနိုင်ပါ (Router AP Isolation သို့မဟုတ် Cloud NAT ကန့်သတ်ချက်)",
        technicalDetails = e.message ?: "Timeout"
      )
    } catch (e: Exception) {
      val elapsed = System.currentTimeMillis() - startTime
      ConnectionDiagnostic(
        isSuccess = false,
        host = testHost,
        latencyMs = elapsed,
        httpCode = null,
        errorCategory = "အရင်းအမြစ်",
        failureReason = "ကွန်ရက် အမှားအယွင်း: ${e.localizedMessage}",
        technicalDetails = e.toString()
      )
    }
  }

  suspend fun getCodes(): Result<KennedeIrDatabase> = withContext(Dispatchers.IO) {
    try {
      val request = Request.Builder()
        .url("${getBaseUrl()}/api/codes")
        .get()
        .build()

      val response = client.newCall(request).execute()
      if (!response.isSuccessful) {
        return@withContext Result.failure(Exception("HTTP ${response.code}"))
      }

      val bodyStr = response.body?.string() ?: ""
      val json = JSONObject(bodyStr)

      val buttonsMap = mutableMapOf<String, IrButtonProfile>()
      if (json.has("buttons")) {
        val bObj = json.getJSONObject("buttons")
        bObj.keys().forEach { key ->
          val btn = bObj.getJSONObject(key)
          val captured = btn.optBoolean("captured", false)
          val proto = if (btn.isNull("protocol")) null else btn.optString("protocol")
          val addr = if (btn.isNull("address")) null else btn.optString("address")
          val cmd = if (btn.isNull("command")) null else btn.optString("command")
          val raw = if (btn.isNull("rawData")) null else btn.optString("rawData")
          val bits = if (btn.isNull("bits")) null else btn.optInt("bits")
          val repeats = btn.optInt("repeatCount", 0)

          val rawTimings = mutableListOf<Int>()
          if (btn.has("rawTiming")) {
            val arr = btn.getJSONArray("rawTiming")
            for (i in 0 until arr.length()) {
              rawTimings.add(arr.getInt(i))
            }
          }

          buttonsMap[key] = IrButtonProfile(
            captured = captured,
            protocol = proto,
            address = addr,
            command = cmd,
            rawData = raw,
            bits = bits,
            rawTiming = rawTimings,
            repeatCount = repeats
          )
        }
      }

      Result.success(
        KennedeIrDatabase(
          device = json.optString("device", "KENNEDE KN-5996HRB"),
          carrierFrequency = json.optInt("carrierFrequency", 38000),
          carrier_frequency_assumed = json.optInt("carrier_frequency_assumed", 38000),
          buttons = buttonsMap
        )
      )
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  suspend fun startLearning(button: KennedeButton): Result<String> = withContext(Dispatchers.IO) {
    try {
      val json = JSONObject().apply { put("button", button.key) }
      val body = json.toString().toRequestBody(jsonMediaType)
      val request = Request.Builder()
        .url("${getBaseUrl()}/api/learn/start")
        .post(body)
        .build()

      val response = client.newCall(request).execute()
      val bodyStr = response.body?.string() ?: ""
      if (response.isSuccessful) {
        Result.success(bodyStr)
      } else {
        Result.failure(Exception("Start learning failed: HTTP ${response.code}"))
      }
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  suspend fun stopLearning(): Result<String> = withContext(Dispatchers.IO) {
    try {
      val request = Request.Builder()
        .url("${getBaseUrl()}/api/learn/stop")
        .post("{}".toRequestBody(jsonMediaType))
        .build()

      val response = client.newCall(request).execute()
      val bodyStr = response.body?.string() ?: ""
      Result.success(bodyStr)
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  suspend fun sendButton(button: KennedeButton): Result<String> = withContext(Dispatchers.IO) {
    try {
      val request = Request.Builder()
        .url("${getBaseUrl()}/api/send/${button.key}")
        .post("{}".toRequestBody(jsonMediaType))
        .build()

      val response = client.newCall(request).execute()
      val bodyStr = response.body?.string() ?: ""
      if (response.isSuccessful) {
        val json = JSONObject(bodyStr)
        val msg = json.optString("message", "Transmitted successfully")
        Result.success(msg)
      } else {
        val err = try {
          JSONObject(bodyStr).optString("error", "ESP32 Error ${response.code}")
        } catch (_: Exception) {
          "ESP32 HTTP ${response.code}"
        }
        Result.failure(Exception(err))
      }
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  suspend fun getRawReport(): Result<String> = withContext(Dispatchers.IO) {
    try {
      val request = Request.Builder()
        .url("${getBaseUrl()}/api/raw")
        .get()
        .build()

      val response = client.newCall(request).execute()
      val bodyStr = response.body?.string() ?: ""
      Result.success(bodyStr)
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  suspend fun resetAllCodes(): Result<String> = withContext(Dispatchers.IO) {
    try {
      val request = Request.Builder()
        .url("${getBaseUrl()}/api/reset")
        .post("{}".toRequestBody(jsonMediaType))
        .build()

      val response = client.newCall(request).execute()
      val bodyStr = response.body?.string() ?: ""
      Result.success(bodyStr)
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  suspend fun importCodes(jsonString: String): Result<String> = withContext(Dispatchers.IO) {
    try {
      val body = jsonString.toRequestBody(jsonMediaType)
      val request = Request.Builder()
        .url("${getBaseUrl()}/api/codes/import")
        .post(body)
        .build()

      val response = client.newCall(request).execute()
      val bodyStr = response.body?.string() ?: ""
      if (response.isSuccessful) {
        Result.success(bodyStr)
      } else {
        Result.failure(Exception("Import failed: HTTP ${response.code}"))
      }
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  suspend fun getFlashStatus(): Result<FlashStorageInfo> = withContext(Dispatchers.IO) {
    try {
      val request = Request.Builder()
        .url("${getBaseUrl()}/api/flash/status")
        .get()
        .build()

      val response = client.newCall(request).execute()
      val bodyStr = response.body?.string() ?: ""
      if (response.isSuccessful) {
        val json = org.json.JSONObject(bodyStr)
        val buttonsArr = json.optJSONArray("buttons_captured")
        val capturedList = mutableListOf<String>()
        if (buttonsArr != null) {
          for (i in 0 until buttonsArr.length()) {
            capturedList.add(buttonsArr.getString(i))
          }
        }
        val info = FlashStorageInfo(
          isConnected = true,
          flashSizeBytes = json.optLong("flash_chip_size", 4194304L),
          freeHeap = json.optLong("free_heap", 0L),
          partitionScheme = json.optString("partition_scheme", "Custom 4MB (24KB NVS, 1.6MB App0, 1.6MB App1, 704KB SPIFFS)"),
          buttonsStored = json.optInt("buttons_stored", capturedList.size),
          capturedButtons = capturedList,
          nvsStatus = json.optString("nvs_status", "HEALTHY"),
          rawResponse = bodyStr
        )
        Result.success(info)
      } else {
        Result.failure(Exception("Flash status check failed: HTTP ${response.code}"))
      }
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  suspend fun clearFlashStorage(): Result<String> = withContext(Dispatchers.IO) {
    try {
      val body = "".toRequestBody(jsonMediaType)
      val request = Request.Builder()
        .url("${getBaseUrl()}/api/flash/clear")
        .post(body)
        .build()
      val response = client.newCall(request).execute()
      val bodyStr = response.body?.string() ?: ""
      if (response.isSuccessful) {
        Result.success(bodyStr)
      } else {
        Result.failure(Exception("Clear flash failed: HTTP ${response.code}"))
      }
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  suspend fun exportCodes(): Result<String> = withContext(Dispatchers.IO) {
    try {
      val request = Request.Builder()
        .url("${getBaseUrl()}/api/codes/export")
        .get()
        .build()
      val response = client.newCall(request).execute()
      val bodyStr = response.body?.string() ?: ""
      if (response.isSuccessful) {
        Result.success(bodyStr)
      } else {
        Result.failure(Exception("Export codes failed: HTTP ${response.code}"))
      }
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  suspend fun importCodesDetailed(jsonString: String): ImportResult = withContext(Dispatchers.IO) {
    // 1. Pre-validation of JSON structure before network transmission
    try {
      val json = org.json.JSONObject(jsonString)
      if (!json.has("buttons")) {
        return@withContext ImportResult(
          isSuccess = false,
          errorCategory = "လက်တွေ့မကိုက်ညီမှု",
          message = "ပေးပို့သော JSON Format တွင် 'buttons' object မပါရှိပါ",
          technicalDetails = "Missing root 'buttons' object in IR payload"
        )
      }
    } catch (e: Exception) {
      return@withContext ImportResult(
        isSuccess = false,
        errorCategory = "နည်းပညာပိုင်း",
        message = "JSON Syntax မမှန်ကန်ပါ: ${e.message}",
        technicalDetails = e.stackTraceToString().take(200)
      )
    }

    // 2. HTTP POST to ESP32 Flash Storage
    try {
      val body = jsonString.toRequestBody(jsonMediaType)
      val request = Request.Builder()
        .url("${getBaseUrl()}/api/codes/import")
        .post(body)
        .build()

      val response = client.newCall(request).execute()
      val bodyStr = response.body?.string() ?: ""
      if (response.isSuccessful) {
        val respJson = try { org.json.JSONObject(bodyStr) } catch (_: Exception) { null }
        val count = respJson?.optInt("buttons_stored", 5) ?: 5
        ImportResult(
          isSuccess = true,
          importedCount = count,
          message = "ESP32 Flash Memory (NVS) သို့ ကုဒ် $count ခု အောင်မြင်စွာ သိမ်းဆည်းပြီးပါပြီ",
          technicalDetails = "HTTP 200 OK | Body: $bodyStr"
        )
      } else {
        val category = if (response.code in 400..499) "နည်းပညာပိုင်း" else "အရင်းအမြစ်"
        ImportResult(
          isSuccess = false,
          errorCategory = category,
          message = "ESP32 Flash သို့ သွင်းယူမှု မအောင်မြင်ပါ (HTTP ${response.code})",
          technicalDetails = "Status: ${response.code}, Response: $bodyStr"
        )
      }
    } catch (e: java.net.ConnectException) {
      ImportResult(
        isSuccess = false,
        errorCategory = "အရင်းအမြစ်",
        message = "ESP32 ချိတ်ဆက်မှု ပြတ်တောက်နေပါသည် (Device Offline)",
        technicalDetails = e.message
      )
    } catch (e: java.net.SocketTimeoutException) {
      ImportResult(
        isSuccess = false,
        errorCategory = "နည်းပညာပိုင်း",
        message = "ESP32 မှ အချိန်မီ မတုံ့ပြန်ပါ (Connection Timeout)",
        technicalDetails = "Flash write timeout: ${e.message}"
      )
    } catch (e: Exception) {
      ImportResult(
        isSuccess = false,
        errorCategory = "နည်းပညာပိုင်း",
        message = "ကုဒ်သွင်းယူရာတွင် အမှားဖြစ်ပေါ်ပါသည်: ${e.message}",
        technicalDetails = e.stackTraceToString().take(200)
      )
    }
  }
}

data class FlashStorageInfo(
  val isConnected: Boolean,
  val flashSizeBytes: Long = 4194304L,
  val freeHeap: Long = 0L,
  val partitionScheme: String = "Custom 4MB (24KB NVS, 1.6MB App0, 1.6MB App1, 704KB SPIFFS)",
  val buttonsStored: Int = 0,
  val capturedButtons: List<String> = emptyList(),
  val nvsStatus: String = "HEALTHY",
  val rawResponse: String? = null,
  val errorDetails: String? = null
)

data class ImportResult(
  val isSuccess: Boolean,
  val importedCount: Int = 0,
  val message: String = "",
  val errorCategory: String? = null,
  val technicalDetails: String? = null
)
