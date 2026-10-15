package com.example.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class IrCode(
  @Json(name = "captured") val captured: Boolean = false,
  @Json(name = "protocol") val protocol: String? = null,
  @Json(name = "address") val address: String? = null,
  @Json(name = "command") val command: String? = null,
  @Json(name = "rawData") val rawData: String? = null,
  @Json(name = "bits") val bits: Int? = null,
  @Json(name = "rawTiming") val rawTiming: List<Int> = emptyList(),
  @Json(name = "repeatCount") val repeatCount: Int = 0
)

@JsonClass(generateAdapter = true)
data class IrButton(
  @Json(name = "key") val key: String,
  @Json(name = "name") val name: String,
  @Json(name = "burmeseLabel") val burmeseLabel: String = "",
  @Json(name = "code") val code: IrCode = IrCode()
)

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
