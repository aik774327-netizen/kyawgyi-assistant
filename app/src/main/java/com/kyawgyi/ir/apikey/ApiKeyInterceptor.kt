package com.kyawgyi.ir.apikey

import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

/**
 * OkHttp Interceptor that connects existing and new API clients to the robust
 * ApiKeyManager system.
 *
 * Capabilities:
 * 1. Automatically attaches the active API key to outbound requests.
 * 2. Pre-checks and reverts to reset free-tier keys.
 * 3. Monitors quota usage and approaches limits.
 * 4. Intercepts HTTP 429 (Too Many Requests) and HTTP 403 (Quota Exhausted).
 * 5. Automatically rotates to an alternate backup key and retries seamlessly.
 */
class ApiKeyInterceptor(
  private val apiKeyManager: ApiKeyManager,
  private val headerName: String = "x-goog-api-key",
  private val queryParamKey: String? = "key",
  private val maxRetriesOnQuotaExceeded: Int = 3
) : Interceptor {

  @Throws(IOException::class)
  override fun intercept(chain: Interceptor.Chain): Response {
    // 1. Proactive check: Revert to free tier key if it cooled down or reset
    apiKeyManager.checkAndRevertToFreeTierIfReset()

    var request = chain.request()
    var currentKey = apiKeyManager.getActiveSecretKey()
    if (currentKey.isBlank()) {
      val envKey = com.example.BuildConfig.GEMINI_API_KEY
      if (envKey.isNotBlank() && envKey != "MY_GEMINI_API_KEY" && !ApiKeyManager.isPlaceholderKey(envKey)) {
        currentKey = envKey
      }
    }

    // 2. Attach current active key to request
    request = attachApiKey(request, currentKey)

    var response = chain.proceed(request)
    var retryCount = 0

    // 3. Transparent retry loop if Rate Limited (429) or Quota Exceeded (403)
    while (shouldFailover(response) && retryCount < maxRetriesOnQuotaExceeded) {
      val errorCode = response.code
      val errorBodyPreview = try {
        response.peekBody(1024).string()
      } catch (_: Exception) {
        null
      }

      // Trigger failover to alternate key
      val canRetryWithNewKey = apiKeyManager.handleHttpError(errorCode, errorBodyPreview)
      if (!canRetryWithNewKey) {
        // No more usable keys available in pool
        break
      }

      // Close previous failed response
      response.close()
      retryCount++

      // Fetch newly rotated key and rebuild request
      currentKey = apiKeyManager.getActiveSecretKey()
      request = attachApiKey(chain.request(), currentKey)

      // Retry request with new key
      response = chain.proceed(request)
    }

    // 4. Record successful usage and check approaching quota warnings
    if (response.isSuccessful) {
      apiKeyManager.recordUsage(1)
    }

    return response
  }

  private fun shouldFailover(response: Response): Boolean {
    if (response.code == 429) return true
    if (response.code == 403) {
      val bodyText = try {
        response.peekBody(512).string().lowercase()
      } catch (_: Exception) {
        ""
      }
      return bodyText.contains("quota") ||
        bodyText.contains("exhausted") ||
        bodyText.contains("resource") ||
        bodyText.contains("limit")
    }
    return false
  }

  private fun attachApiKey(original: Request, apiKey: String): Request {
    if (apiKey.isEmpty()) return original

    val builder = original.newBuilder()

    // Attach as header
    if (headerName.isNotBlank()) {
      builder.header(headerName, apiKey)
    }

    // Attach as query parameter if requested
    if (!queryParamKey.isNullOrBlank()) {
      val newUrl = original.url.newBuilder()
        .removeAllQueryParameters(queryParamKey)
        .addQueryParameter(queryParamKey, apiKey)
        .build()
      builder.url(newUrl)
    }

    return builder.build()
  }
}
