package com.example.models

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Real-time power metrics model from EcoFlow Smart Power Station via REST API.
 */
@JsonClass(generateAdapter = true)
data class EcoFlowPowerMetrics(
  @Json(name = "soc") val batteryPercentage: Int = 85,
  @Json(name = "wattsInSum") val inputWatts: Int = 0,
  @Json(name = "wattsOutSum") val outputWatts: Int = 255,
  @Json(name = "remainTime") val remainingMinutes: Int = 410,
  @Json(name = "temp") val temperatureCelsius: Int = 31,
  @Json(name = "invOutputWatts") val acOutputWatts: Int = 210,
  @Json(name = "dcOutWatts") val dcOutputWatts: Int = 45,
  @Json(name = "typecWatts") val usbOutputWatts: Int = 18,
  @Json(name = "model") val model: String = "DELTA 2 Max (2048Wh)",
  @Json(name = "online") val isOnline: Boolean = true,
  @Json(name = "isCharging") val isCharging: Boolean = false,
  @Json(name = "acPortEnabled") val acPortEnabled: Boolean = true,
  @Json(name = "dcPortEnabled") val dcPortEnabled: Boolean = false,
  @Json(name = "usbPortEnabled") val usbPortEnabled: Boolean = true,
  @Json(name = "xboostEnabled") val xboostEnabled: Boolean = true,
  @Json(name = "connectionStatus") val connectionStatus: String = "REST API Connected"
)

@JsonClass(generateAdapter = true)
data class EcoFlowPortControlRequest(
  @Json(name = "port") val port: String,
  @Json(name = "enabled") val enabled: Boolean
)

@JsonClass(generateAdapter = true)
data class EcoFlowApiResponse<T>(
  @Json(name = "code") val code: Int = 0,
  @Json(name = "message") val message: String = "Success",
  @Json(name = "data") val data: T? = null
)
