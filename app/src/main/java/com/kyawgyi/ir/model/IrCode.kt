package com.kyawgyi.ir.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Defines the JSON structure for IR signals supporting KENNEDE KN-5996HRB database requirements.
 * Contains fields for protocol, address, command, rawTiming, and captured status.
 */
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
