package com.kyawgyi.ir.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class KennedeIrSchema(
  @Json(name = "device") val device: String = "KENNEDE KN-5996HRB",
  @Json(name = "carrierFrequency") val carrierFrequency: Int = 38000,
  @Json(name = "carrier_frequency_assumed") val carrierFrequencyAssumed: Int = 38000,
  @Json(name = "buttons") val buttons: Map<String, IrCode> = emptyMap()
)

@JsonClass(generateAdapter = true)
data class Esp32StatusResponse(
  @Json(name = "device") val device: String = "KENNEDE KN-5996HRB",
  @Json(name = "esp32") val esp32: Boolean = false,
  @Json(name = "receiver") val receiver: Boolean = false,
  @Json(name = "transmitter") val transmitter: Boolean = false,
  @Json(name = "carrierFrequency") val carrierFrequency: Int = 38000,
  @Json(name = "learning") val learning: Boolean = false,
  @Json(name = "learningTarget") val learningTarget: String? = null,
  @Json(name = "ip") val ip: String? = null,
  @Json(name = "rssi") val rssi: Int = 0,
  @Json(name = "learned") val learned: Map<String, Boolean> = emptyMap()
)

@JsonClass(generateAdapter = true)
data class GenericApiResponse(
  @Json(name = "device") val device: String? = null,
  @Json(name = "status") val status: String? = null,
  @Json(name = "button") val button: String? = null,
  @Json(name = "action") val action: String? = null,
  @Json(name = "success") val success: Boolean = false,
  @Json(name = "message") val message: String? = null,
  @Json(name = "error") val error: String? = null
)

@JsonClass(generateAdapter = true)
data class LearnStartRequest(
  @Json(name = "button") val button: String
)
