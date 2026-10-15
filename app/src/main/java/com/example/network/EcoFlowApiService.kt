package com.example.network

import com.example.models.EcoFlowApiResponse
import com.example.models.EcoFlowPortControlRequest
import com.example.models.EcoFlowPowerMetrics
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface EcoFlowApiService {

  @GET("/api/v1/ecoflow/metrics")
  suspend fun getPowerMetrics(): Response<EcoFlowPowerMetrics>

  @GET("/iot-service/open/api/device/queryDeviceQuota")
  suspend fun queryDeviceQuota(): Response<EcoFlowApiResponse<EcoFlowPowerMetrics>>

  @POST("/api/v1/ecoflow/port")
  suspend fun setPortState(@Body request: EcoFlowPortControlRequest): Response<EcoFlowApiResponse<Boolean>>
}
