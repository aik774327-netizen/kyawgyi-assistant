package com.example

import com.kyawgyi.ir.apikey.ApiKeyValidationResult
import com.kyawgyi.ir.apikey.ApiKeyValidator
import kotlinx.coroutines.runBlocking
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class ApiKeyValidatorTest {

  private fun createMockCallFactory(
    statusCode: Int = 200,
    responseBody: String = "{}",
    shouldThrowNetworkError: Boolean = false,
    onCallMade: ((Request) -> Unit)? = null
  ): Call.Factory {
    return Call.Factory { request ->
      onCallMade?.invoke(request)
      val mockCall = org.mockito.Mockito.mock(Call::class.java)

      if (shouldThrowNetworkError) {
        org.mockito.Mockito.`when`(mockCall.execute()).thenAnswer {
          throw IOException("Simulated network connection timeout")
        }
      } else {
        val response = Response.Builder()
          .request(request)
          .protocol(Protocol.HTTP_1_1)
          .code(statusCode)
          .message(if (statusCode == 200) "OK" else "Error")
          .body(responseBody.toResponseBody("application/json".toMediaType()))
          .build()
        org.mockito.Mockito.`when`(mockCall.execute()).thenReturn(response)
      }

      mockCall
    }
  }

  @Test
  fun testEmptyInputDoesNotCallNetworkAndReturnsEmptyMessage() = runBlocking {
    var networkCallCount = 0
    val validator = ApiKeyValidator(
      callFactory = createMockCallFactory { networkCallCount++ }
    )

    val result = validator.validateKey("   ")

    assertTrue(result is ApiKeyValidationResult.EmptyInput)
    assertEquals("API key အရင်ထည့်သွင်းပါ", result.message)
    assertEquals(0, networkCallCount)
  }

  @Test
  fun testSuccess200ReturnsSuccessMessageAndMaskedKey() = runBlocking {
    val validator = ApiKeyValidator(
      callFactory = createMockCallFactory(statusCode = 200, responseBody = "{\"models\": []}")
    )

    val result = validator.validateKey("AIzaSyValidGeminiKey5678")

    assertTrue(result is ApiKeyValidationResult.Success)
    assertEquals("✓ API Key မှန်ကန်ပါသည်", result.message)
    assertEquals("••••5678", (result as ApiKeyValidationResult.Success).maskedKey)
  }

  @Test
  fun testInvalidKey400ReturnsInvalidMessage() = runBlocking {
    val validator = ApiKeyValidator(
      callFactory = createMockCallFactory(statusCode = 400, responseBody = "{\"error\": \"API_KEY_INVALID\"}")
    )

    val result = validator.validateKey("AIzaSyInvalidKey1111")

    assertTrue(result is ApiKeyValidationResult.InvalidKey)
    assertEquals("✗ API Key မမှန်ကန်ပါ — အကြောင်းရင်း: API key မှားယွင်းနေပါသည်", result.message)
    assertEquals("••••1111", (result as ApiKeyValidationResult.InvalidKey).maskedKey)
  }

  @Test
  fun testInvalidKey401ReturnsInvalidMessage() = runBlocking {
    val validator = ApiKeyValidator(
      callFactory = createMockCallFactory(statusCode = 401, responseBody = "{\"error\": \"UNAUTHENTICATED\"}")
    )

    val result = validator.validateKey("AIzaSyUnauthorized2222")

    assertTrue(result is ApiKeyValidationResult.InvalidKey)
    assertEquals("✗ API Key မမှန်ကန်ပါ — အကြောင်းရင်း: API key မှားယွင်းနေပါသည်", result.message)
    assertEquals("••••2222", (result as ApiKeyValidationResult.InvalidKey).maskedKey)
  }

  @Test
  fun testInvalidKey403ReturnsInvalidMessage() = runBlocking {
    val validator = ApiKeyValidator(
      callFactory = createMockCallFactory(statusCode = 403, responseBody = "{\"error\": \"PERMISSION_DENIED\"}")
    )

    val result = validator.validateKey("AIzaSyForbidden3333")

    assertTrue(result is ApiKeyValidationResult.InvalidKey)
    assertEquals("✗ API Key မမှန်ကန်ပါ — အကြောင်းရင်း: API key မှားယွင်းနေပါသည်", result.message)
    assertEquals("••••3333", (result as ApiKeyValidationResult.InvalidKey).maskedKey)
  }

  @Test
  fun testQuota429ReturnsQuotaMessage() = runBlocking {
    val validator = ApiKeyValidator(
      callFactory = createMockCallFactory(statusCode = 429, responseBody = "{\"error\": \"RESOURCE_EXHAUSTED\"}")
    )

    val result = validator.validateKey("AIzaSyExhaustedKey4444")

    assertTrue(result is ApiKeyValidationResult.QuotaExceeded)
    assertEquals("စစ်ဆေး၍မရပါ — အကြောင်းရင်း: API quota ကုန်နေပါသည်", result.message)
    assertEquals("••••4444", (result as ApiKeyValidationResult.QuotaExceeded).maskedKey)
  }

  @Test
  fun testNetworkErrorReturnsNetworkMessage() = runBlocking {
    val validator = ApiKeyValidator(
      callFactory = createMockCallFactory(shouldThrowNetworkError = true)
    )

    val result = validator.validateKey("AIzaSyNetworkFailKey9999")

    assertTrue(result is ApiKeyValidationResult.NetworkError)
    assertEquals("စစ်ဆေး၍မရပါ — အကြောင်းရင်း: ကွန်ယက်ချိတ်ဆက်မှု အမှား", result.message)
    assertEquals("••••9999", (result as ApiKeyValidationResult.NetworkError).maskedKey)
  }

  @Test
  fun testKeyMaskingPreservesOnlyLast4Characters() {
    assertEquals("••••1234", ApiKeyValidator.maskKey("AIzaSyMySecretKey1234"))
    assertEquals("••••9999", ApiKeyValidator.maskKey("9999"))
    assertEquals("••••12", ApiKeyValidator.maskKey("12"))
    assertEquals("", ApiKeyValidator.maskKey(""))
    assertEquals("", ApiKeyValidator.maskKey("   "))
    assertFalse(ApiKeyValidator.maskKey("AIzaSyMySecretKey1234").contains("AIzaSy"))
  }
}
