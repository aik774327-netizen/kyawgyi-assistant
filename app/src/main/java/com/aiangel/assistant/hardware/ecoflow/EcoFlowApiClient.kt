package com.aiangel.assistant.hardware.ecoflow

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Result of any EcoFlow cloud call, with a Burmese user-facing reason on failure. */
sealed class EcoFlowResult<out T> {
    data class Success<T>(val data: T) : EcoFlowResult<T>()
    data class Failure(val reason: String, val errorCode: String? = null) : EcoFlowResult<Nothing>()
}

data class EcoFlowDevice(
    val serialNumber: String,
    val productName: String,
    val isOnline: Boolean
)

data class EcoFlowConnectionTestResult(
    val isSuccess: Boolean,
    val message: String,
    val deviceCount: Int,
    val matchedDevice: EcoFlowDevice?,
    val batteryPercentage: Int? = null,
    val inputWatts: Int? = null,
    val outputWatts: Int? = null,
    val remainingMinutes: Int? = null,
    val temperatureCelsius: Int? = null
)

/**
 * Talks to the EcoFlow Developer cloud API over the phone's normal WiFi/mobile
 * data connection — this is the officially supported path, authenticated
 * per-request via EcoFlowSigner's HMAC-SHA256 scheme.
 *
 * accessKey/secretKey come from the EcoFlow Developer Console
 * (https://developer.ecoflow.com) — saved securely in Room/EncryptedSharedPreferences.
 */
class EcoFlowApiClient(
    private val accessKey: String,
    private val secretKey: String,
    private val host: String = "https://api-a.ecoflow.com" // Default to api-a for Asia/Americas; api-e for EU
) {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json;charset=UTF-8".toMediaType()

    /** GET /iot-open/sign/device/list — all devices bound to this developer account. */
    suspend fun getDeviceList(): EcoFlowResult<List<EcoFlowDevice>> = withContext(Dispatchers.IO) {
        if (accessKey.isBlank() || secretKey.isBlank()) {
            return@withContext EcoFlowResult.Failure(
                "EcoFlow AccessKey (သို့) SecretKey မထည့်သွင်းရသေးပါ ခင်ဗျာ။ developer.ecoflow.com တွင် ရယူပါ",
                "MISSING_KEYS"
            )
        }
        val headers = EcoFlowSigner.sign(accessKey, secretKey)
        val request = Request.Builder()
            .url("$host/iot-open/sign/device/list")
            .get()
            .applySignedHeaders(headers)
            .build()

        executeJson(request) { body ->
            val dataArray = body.optJSONArray("data") ?: return@executeJson EcoFlowResult.Success(emptyList())
            val devices = buildList {
                for (i in 0 until dataArray.length()) {
                    val entry = dataArray.optJSONObject(i) ?: continue
                    add(
                        EcoFlowDevice(
                            serialNumber = entry.optString("sn"),
                            productName = entry.optString("productName", "EcoFlow Power Station"),
                            isOnline = entry.optInt("online", 0) == 1
                        )
                    )
                }
            }
            EcoFlowResult.Success(devices)
        }
    }

    /** GET /iot-open/sign/device/quota/all?sn=... — full current state snapshot for one device. */
    suspend fun getDeviceQuota(serialNumber: String): EcoFlowResult<JSONObject> = withContext(Dispatchers.IO) {
        if (accessKey.isBlank() || secretKey.isBlank()) {
            return@withContext EcoFlowResult.Failure(
                "EcoFlow Developer Keys မထည့်သွင်းရသေးပါ",
                "MISSING_KEYS"
            )
        }
        if (serialNumber.isBlank()) {
            return@withContext EcoFlowResult.Failure(
                "Device Serial Number (SN) မထည့်သွင်းရသေးပါ",
                "MISSING_SN"
            )
        }
        val params = mapOf("sn" to serialNumber)
        val headers = EcoFlowSigner.sign(accessKey, secretKey, params)
        val request = Request.Builder()
            .url("$host/iot-open/sign/device/quota/all?sn=$serialNumber")
            .get()
            .applySignedHeaders(headers)
            .build()

        executeJson(request) { body ->
            val data = body.optJSONObject("data") ?: JSONObject()
            EcoFlowResult.Success(data)
        }
    }

    /**
     * Test connection against EcoFlow Cloud Open API, verifying keys and retrieving
     * live device quota if available.
     */
    suspend fun testConnection(serialNumber: String): EcoFlowResult<EcoFlowConnectionTestResult> = withContext(Dispatchers.IO) {
        if (accessKey.isBlank() || secretKey.isBlank()) {
            return@withContext EcoFlowResult.Failure(
                "AccessKey နှင့် SecretKey ကို ဦးစွာ ထည့်သွင်းပေးပါ ခင်ဗျာ။",
                "NO_KEYS"
            )
        }

        // 1. Check account device list
        val listResult = getDeviceList()
        if (listResult is EcoFlowResult.Failure) {
            return@withContext EcoFlowResult.Failure(listResult.reason, listResult.errorCode)
        }

        val devices = (listResult as EcoFlowResult.Success).data
        val matched = devices.find { it.serialNumber.equals(serialNumber.trim(), ignoreCase = true) }
            ?: devices.firstOrNull()

        val targetSn = if (serialNumber.isNotBlank()) serialNumber.trim() else (matched?.serialNumber ?: "")

        var battery: Int? = null
        var inWatts: Int? = null
        var outWatts: Int? = null
        var remainM: Int? = null
        var temp: Int? = null

        if (targetSn.isNotBlank()) {
            val quotaResult = getDeviceQuota(targetSn)
            if (quotaResult is EcoFlowResult.Success) {
                val data = quotaResult.data
                battery = if (data.has("soc")) data.optInt("soc") else if (data.has("batteryPercentage")) data.optInt("batteryPercentage") else null
                inWatts = if (data.has("wattsInSum")) data.optInt("wattsInSum") else if (data.has("inputWatts")) data.optInt("inputWatts") else null
                outWatts = if (data.has("wattsOutSum")) data.optInt("wattsOutSum") else if (data.has("outputWatts")) data.optInt("outputWatts") else null
                remainM = if (data.has("remainTime")) data.optInt("remainTime") else null
                temp = if (data.has("temp")) data.optInt("temp") else null
            }
        }

        val msg = if (matched != null) {
            "EcoFlow Cloud ချိတ်ဆက်မှု အောင်မြင်ပါသည်! စက်: ${matched.productName} (SN: ${matched.serialNumber}, Status: ${if (matched.isOnline) "Online" else "Offline"})"
        } else if (devices.isNotEmpty()) {
            "EcoFlow Cloud အကောင့်သို့ ချိတ်ဆက်မိပါသည် (တွေ့ရှိသောစက်: ${devices.size} ခု)"
        } else {
            "EcoFlow Cloud ချိတ်ဆက်မှု အောင်မြင်သော်လည်း သင့် Developer Account တွင် စက် ချိတ်ဆက်ထားခြင်း မရှိသေးပါ"
        }

        EcoFlowResult.Success(
            EcoFlowConnectionTestResult(
                isSuccess = true,
                message = msg,
                deviceCount = devices.size,
                matchedDevice = matched,
                batteryPercentage = battery,
                inputWatts = inWatts,
                outputWatts = outWatts,
                remainingMinutes = remainM,
                temperatureCelsius = temp
            )
        )
    }

    /**
     * PUT /iot-open/sign/device/quota — send a control command to the device.
     */
    suspend fun setDeviceCommand(
        serialNumber: String,
        cmdCode: String,
        params: Map<String, Any>
    ): EcoFlowResult<Unit> = withContext(Dispatchers.IO) {
        if (accessKey.isBlank() || secretKey.isBlank()) {
            return@withContext EcoFlowResult.Failure("API Keys မရှိဘဲ စက်သို့ အမိန့်ပေးပို့၍ မရပါ", "NO_KEYS")
        }
        if (serialNumber.isBlank()) {
            return@withContext EcoFlowResult.Failure("Device Serial Number (SN) မရှိပါ", "NO_SN")
        }

        val bodyJson = JSONObject().apply {
            put("sn", serialNumber)
            put("cmdCode", cmdCode)
            put("params", JSONObject(params))
        }

        val signParams = mapOf("sn" to serialNumber, "cmdCode" to cmdCode)
        val headers = EcoFlowSigner.sign(accessKey, secretKey, signParams)

        val request = Request.Builder()
            .url("$host/iot-open/sign/device/quota")
            .put(bodyJson.toString().toRequestBody(jsonMediaType))
            .applySignedHeaders(headers)
            .build()

        executeJson(request) { EcoFlowResult.Success(Unit) }
    }

    suspend fun setAcPort(serialNumber: String, enabled: Boolean): EcoFlowResult<Unit> {
        return setDeviceCommand(serialNumber, "acOutCfg", mapOf("enabled" to if (enabled) 1 else 0))
    }

    suspend fun setDcPort(serialNumber: String, enabled: Boolean): EcoFlowResult<Unit> {
        return setDeviceCommand(serialNumber, "dcOutCfg", mapOf("enabled" to if (enabled) 1 else 0))
    }

    suspend fun setChargingWatts(serialNumber: String, watts: Int): EcoFlowResult<Unit> {
        return setDeviceCommand(serialNumber, "acChgCfg", mapOf("chgWatts" to watts))
    }

    suspend fun setXBoost(serialNumber: String, enabled: Boolean): EcoFlowResult<Unit> {
        return setDeviceCommand(serialNumber, "xboost", mapOf("xboost" to if (enabled) 1 else 0))
    }

    suspend fun setBeep(serialNumber: String, enabled: Boolean): EcoFlowResult<Unit> {
        return setDeviceCommand(serialNumber, "quietMode", mapOf("enabled" to if (enabled) 0 else 1))
    }

    private fun Request.Builder.applySignedHeaders(headers: EcoFlowSigner.SignedHeaders): Request.Builder =
        this
            .header("Content-Type", "application/json;charset=UTF-8")
            .header("accessKey", headers.accessKey)
            .header("nonce", headers.nonce)
            .header("timestamp", headers.timestamp)
            .header("sign", headers.sign)

    private fun <T> executeJson(
        request: Request,
        onSuccess: (JSONObject) -> EcoFlowResult<T>
    ): EcoFlowResult<T> {
        return try {
            httpClient.newCall(request).execute().use { response ->
                val bodyText = response.body?.string()
                    ?: return EcoFlowResult.Failure("EcoFlow server မှ response body ပြန်မရပါ", "EMPTY_BODY")

                if (!response.isSuccessful) {
                    val burmeseErr = when (response.code) {
                        401 -> "EcoFlow Developer AccessKey (သို့) SecretKey မှားယွင်းနေပါသည် (401 Unauthorized)"
                        403 -> "ဤ API အား ခေါ်ဆိုရန် ခွင့်ပြုချက် မရှိပါ (403 Forbidden)"
                        404 -> "တောင်းဆိုသော စက် (SN) သို့မဟုတ် API လမ်းကြောင်း မတွေ့ပါ (404 Not Found)"
                        500, 502, 503 -> "EcoFlow Cloud ဆာဗာ ယာယီချို့ယွင်းနေပါသည် (${response.code})"
                        else -> "EcoFlow API error (HTTP ${response.code}): $bodyText"
                    }
                    return EcoFlowResult.Failure(burmeseErr, response.code.toString())
                }

                val json = JSONObject(bodyText)
                val code = json.optString("code")
                if (code != "0" && code != "200") {
                    val message = json.optString("message", "အကြောင်းအရင်း မသိရပါ")
                    val burmeseReason = when (code) {
                        "10001", "10002", "10003" -> "AccessKey သို့မဟုတ် SecretKey မှားယွင်းနေပါသည် (Auth Failure)"
                        "10004" -> "Request လက်မှတ် (HMAC Signature) မမှန်ကန်ပါ"
                        "10005" -> "အချိန်သတ်မှတ်ချက် (Timestamp) ကွာဟမှု ရှိနေပါသည်"
                        "20001" -> "စက်နံပါတ် (SN) ရှာမတွေ့ပါ သို့မဟုတ် Developer Account တွင် ချိတ်ဆက်မထားပါ"
                        "20002" -> "စက်သည် အော့ဖ်လိုင်း (Offline) ဖြစ်နေပါသည်"
                        else -> "EcoFlow API code=$code: $message"
                    }
                    return EcoFlowResult.Failure(burmeseReason, code)
                }

                onSuccess(json)
            }
        } catch (e: IOException) {
            EcoFlowResult.Failure("Network error — Wi-Fi/အင်တာနက် ချိတ်ဆက်မှု စစ်ကြည့်ပါ: ${e.message}", "NETWORK_ERROR")
        } catch (e: Exception) {
            EcoFlowResult.Failure("EcoFlow request failure: ${e.message}", "EXCEPTION")
        }
    }
}
