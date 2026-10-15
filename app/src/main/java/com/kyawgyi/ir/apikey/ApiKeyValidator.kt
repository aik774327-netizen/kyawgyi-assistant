package com.kyawgyi.ir.apikey

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Result of API Key validation.
 */
sealed class ApiKeyValidationResult(val message: String, val isSuccess: Boolean) {
  data class Success(val maskedKey: String) :
    ApiKeyValidationResult("✓ API Key မှန်ကန်ပါသည်", isSuccess = true)

  data class InvalidKey(val maskedKey: String) :
    ApiKeyValidationResult("✗ API Key မမှန်ကန်ပါ — အကြောင်းရင်း: API key မှားယွင်းနေပါသည်", isSuccess = false)

  data class QuotaExceeded(val maskedKey: String) :
    ApiKeyValidationResult("စစ်ဆေး၍မရပါ — အကြောင်းရင်း: API quota ကုန်နေပါသည်", isSuccess = false)

  data class NetworkError(val maskedKey: String) :
    ApiKeyValidationResult("စစ်ဆေး၍မရပါ — အကြောင်းရင်း: ကွန်ယက်ချိတ်ဆက်မှု အမှား", isSuccess = false)

  object EmptyInput :
    ApiKeyValidationResult("API key အရင်ထည့်သွင်းပါ", isSuccess = false)
}

/**
 * Validates Google Gemini / Generative Language API keys via a lightweight GET models call.
 * Uses an injectable Call.Factory (OkHttpClient) for testability.
 * Also provides static format verification and masking utilities.
 */
class ApiKeyValidator(
  private val callFactory: Call.Factory = defaultHttpClient()
) {

  companion object {
    private const val TAG = "ApiKeyValidator"
    const val VALIDATION_URL = "https://generativelanguage.googleapis.com/v1beta/models"

    private fun defaultHttpClient(): OkHttpClient {
      return OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()
    }

    /**
     * Validates standard API key format (e.g. Gemini / AI Studio / Google Cloud API keys).
     * Rules:
     * - Must not be blank.
     * - Must be at least 16 characters long.
     * - Must not contain whitespace.
     * - Standard Google/Gemini key prefix is typically "AIzaSy" but other custom keys may be supported.
     */
    fun validateKeyFormat(rawKey: String): KeyValidationResult {
      val trimmed = rawKey.trim()
      if (trimmed.isEmpty()) {
        return KeyValidationResult.Invalid("API key cannot be empty")
      }
      if (trimmed.contains(" ") || trimmed.contains("\t") || trimmed.contains("\n")) {
        return KeyValidationResult.Invalid("API key must not contain whitespace characters")
      }
      if (trimmed.length < 16) {
        return KeyValidationResult.Invalid("API key is too short (minimum 16 characters required)")
      }
      if (!trimmed.matches(Regex("^[A-Za-z0-9_-]+$"))) {
        return KeyValidationResult.Invalid("API key contains invalid characters")
      }
      return KeyValidationResult.Valid(trimmed)
    }

    /**
     * Masks an API key, showing only the last 4 characters (e.g. ••••1234).
     * Never logs or exposes the full secret.
     */
    fun maskKey(key: String): String {
      val trimmed = key.trim()
      if (trimmed.isEmpty()) return ""
      return if (trimmed.length > 4) {
        "••••" + trimmed.takeLast(4)
      } else {
        "••••$trimmed"
      }
    }

    /**
     * Pure round-trip serialization / desensitization helper.
     */
    fun serializeKeyRecord(id: String, label: String, rawKey: String, tier: ApiKeyTier, maxQuota: Int): ManagedApiKey {
      return ManagedApiKey(
        id = id,
        label = label.trim(),
        maskedKey = maskKey(rawKey),
        tier = tier,
        maxQuota = maxQuota.coerceAtLeast(100),
        usedQuota = 0,
        status = ApiKeyStatus.HEALTHY_ACTIVE
      )
    }
  }

  sealed class KeyValidationResult {
    data class Valid(val normalizedKey: String) : KeyValidationResult()
    data class Invalid(val reason: String) : KeyValidationResult()
  }

  /**
   * Validates the provided raw API key via GET models endpoint.
   * If key is blank, returns EmptyInput without making any network call.
   */
  suspend fun validateKey(rawKey: String): ApiKeyValidationResult = withContext(Dispatchers.IO) {
    val trimmed = rawKey.trim()
    if (trimmed.isEmpty()) {
      return@withContext ApiKeyValidationResult.EmptyInput
    }

    val masked = maskKey(trimmed)
    val request = Request.Builder()
      .url("$VALIDATION_URL?key=$trimmed")
      .get()
      .build()

    try {
      val response = callFactory.newCall(request).execute()
      response.use { resp ->
        when (resp.code) {
          200 -> ApiKeyValidationResult.Success(masked)
          400, 401, 403 -> ApiKeyValidationResult.InvalidKey(masked)
          429 -> ApiKeyValidationResult.QuotaExceeded(masked)
          else -> ApiKeyValidationResult.InvalidKey(masked)
        }
      }
    } catch (e: IOException) {
      Log.w(TAG, "Network error during validation of key ($masked): ${e.message}")
      ApiKeyValidationResult.NetworkError(masked)
    } catch (e: Exception) {
      Log.w(TAG, "Unexpected error during validation of key ($masked): ${e.message}")
      ApiKeyValidationResult.NetworkError(masked)
    }
  }
}
