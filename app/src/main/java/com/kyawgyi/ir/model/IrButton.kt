package com.kyawgyi.ir.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Defines a button representation for KENNEDE KN-5996HRB fan remote control.
 */
@JsonClass(generateAdapter = true)
data class IrButton(
  @Json(name = "key") val key: String,
  @Json(name = "name") val name: String,
  @Json(name = "burmeseLabel") val burmeseLabel: String = "",
  @Json(name = "code") val code: IrCode = IrCode()
)
