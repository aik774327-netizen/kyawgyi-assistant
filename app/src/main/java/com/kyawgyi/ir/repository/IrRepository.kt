package com.kyawgyi.ir.repository

/**
 * ESP32 IR blaster repository contract (audit #27, 2026-10-18).
 *
 * Architecture note: production IR control flows through
 * `com.example.realworld.RealHardwareBridge` / `PhoneIrBlaster` (wired in MainViewModel).
 * This repository interface defines the ESP32-backed IR contract and is retained because
 * it is covered by `RealWorldMismatchTest` (honest no-hallucination behavior when no
 * IR hardware is present). The duplicate UI screens that once consumed it
 * (com/kyawgyi/ir/ui/*, com/example/ui/screens/* IR screens) were dead and removed.
 */
import com.kyawgyi.ir.models.Esp32StatusResponse
import com.kyawgyi.ir.models.GenericApiResponse
import com.kyawgyi.ir.models.IrButton
import com.kyawgyi.ir.models.IrCode
import com.kyawgyi.ir.models.KennedeIrSchema
import com.kyawgyi.ir.models.LearnStartRequest
import com.kyawgyi.ir.network.IrApiService
import com.kyawgyi.ir.network.RetrofitProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

interface IrRepository {
  fun updateBaseUrl(hostOrIp: String)
  fun getBaseUrl(): String
  suspend fun fetchStatus(): Result<Esp32StatusResponse>
  suspend fun fetchCodes(): Result<KennedeIrSchema>
  suspend fun startLearning(buttonKey: String): Result<GenericApiResponse>
  suspend fun stopLearning(): Result<GenericApiResponse>
  suspend fun transmitButton(buttonKey: String): Result<GenericApiResponse>
  suspend fun fetchRawReport(): Result<String>
  suspend fun resetAllCodes(): Result<GenericApiResponse>
  suspend fun importCodesJson(json: String): Result<GenericApiResponse>
}

class IrRepositoryImpl(
  initialHost: String = "192.168.1.150"
) : IrRepository {

  init {
    RetrofitProvider.updateBaseUrl(initialHost)
  }

  private val apiService: IrApiService
    get() = RetrofitProvider.getApiService()

  override fun updateBaseUrl(hostOrIp: String) {
    RetrofitProvider.updateBaseUrl(hostOrIp)
  }

  override fun getBaseUrl(): String = RetrofitProvider.getCurrentHost()

  override suspend fun fetchStatus(): Result<Esp32StatusResponse> = withContext(Dispatchers.IO) {
    try {
      val response = apiService.getStatus()
      if (response.isSuccessful && response.body() != null) {
        Result.success(response.body()!!)
      } else {
        Result.failure(Exception("HTTP ${response.code()}: ${response.message()}"))
      }
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  override suspend fun fetchCodes(): Result<KennedeIrSchema> = withContext(Dispatchers.IO) {
    try {
      val response = apiService.getCodes()
      if (response.isSuccessful && response.body() != null) {
        Result.success(response.body()!!)
      } else {
        Result.failure(Exception("HTTP ${response.code()}: ${response.message()}"))
      }
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  override suspend fun startLearning(buttonKey: String): Result<GenericApiResponse> = withContext(Dispatchers.IO) {
    try {
      val response = apiService.startLearning(LearnStartRequest(buttonKey))
      if (response.isSuccessful && response.body() != null) {
        Result.success(response.body()!!)
      } else {
        Result.failure(Exception("Failed to start learning (HTTP ${response.code()})"))
      }
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  override suspend fun stopLearning(): Result<GenericApiResponse> = withContext(Dispatchers.IO) {
    try {
      val response = apiService.stopLearning()
      if (response.isSuccessful && response.body() != null) {
        Result.success(response.body()!!)
      } else {
        Result.failure(Exception("Failed to stop learning (HTTP ${response.code()})"))
      }
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  override suspend fun transmitButton(buttonKey: String): Result<GenericApiResponse> = withContext(Dispatchers.IO) {
    try {
      val response = apiService.sendButton(buttonKey)
      if (response.isSuccessful && response.body() != null) {
        Result.success(response.body()!!)
      } else {
        val errorMsg = response.errorBody()?.string() ?: "HTTP ${response.code()}"
        Result.failure(Exception(errorMsg))
      }
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  override suspend fun fetchRawReport(): Result<String> = withContext(Dispatchers.IO) {
    try {
      val response = apiService.getRawReport()
      if (response.isSuccessful && response.body() != null) {
        Result.success(response.body()!!.string())
      } else {
        Result.failure(Exception("HTTP ${response.code()}"))
      }
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  override suspend fun resetAllCodes(): Result<GenericApiResponse> = withContext(Dispatchers.IO) {
    try {
      val response = apiService.resetAllCodes()
      if (response.isSuccessful && response.body() != null) {
        Result.success(response.body()!!)
      } else {
        Result.failure(Exception("Failed to reset codes (HTTP ${response.code()})"))
      }
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  override suspend fun importCodesJson(json: String): Result<GenericApiResponse> = withContext(Dispatchers.IO) {
    try {
      val mediaType = "application/json; charset=utf-8".toMediaType()
      val body = json.toRequestBody(mediaType)
      val response = apiService.importCodes(body)
      if (response.isSuccessful && response.body() != null) {
        Result.success(response.body()!!)
      } else {
        Result.failure(Exception("Import failed (HTTP ${response.code()})"))
      }
    } catch (e: Exception) {
      Result.failure(e)
    }
  }
}
