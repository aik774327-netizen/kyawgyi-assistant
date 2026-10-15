package com.example.network

import com.example.model.Esp32StatusResponse
import com.example.model.GenericApiResponse
import com.example.model.KennedeIrSchema
import com.example.model.LearnStartRequest
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface IrApiService {

  @GET("/api/status")
  suspend fun getStatus(): Response<Esp32StatusResponse>

  @GET("/api/codes")
  suspend fun getCodes(): Response<KennedeIrSchema>

  @GET("/api/raw")
  suspend fun getRawReport(): Response<ResponseBody>

  @POST("/api/learn/start")
  suspend fun startLearning(@Body request: LearnStartRequest): Response<GenericApiResponse>

  @POST("/api/learn/stop")
  suspend fun stopLearning(): Response<GenericApiResponse>

  @POST("/api/send/{button}")
  suspend fun sendButton(@Path("button") button: String): Response<GenericApiResponse>

  @POST("/api/codes/import")
  suspend fun importCodes(@Body body: RequestBody): Response<GenericApiResponse>

  @POST("/api/codes/export")
  suspend fun exportCodes(): Response<KennedeIrSchema>

  @POST("/api/reset")
  suspend fun resetAllCodes(): Response<GenericApiResponse>
}
