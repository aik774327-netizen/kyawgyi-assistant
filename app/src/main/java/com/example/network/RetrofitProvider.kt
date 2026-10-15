package com.example.network

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Retrofit provider singleton object to initialize the OkHttpClient and Retrofit instance
 * required for communication with the ESP32 IP address.
 */
object RetrofitProvider {

  private const val DEFAULT_HOST = "192.168.1.150"

  @Volatile
  private var currentHost: String = DEFAULT_HOST

  val moshi: Moshi by lazy {
    Moshi.Builder()
      .addLast(KotlinJsonAdapterFactory())
      .build()
  }

  val okHttpClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
      .connectTimeout(4, TimeUnit.SECONDS)
      .readTimeout(8, TimeUnit.SECONDS)
      .writeTimeout(5, TimeUnit.SECONDS)
      .addInterceptor(HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
      })
      .build()
  }

  @Volatile
  private var retrofit: Retrofit = buildRetrofit(currentHost)

  @Volatile
  private var apiService: IrApiService = retrofit.create(IrApiService::class.java)

  private fun buildRetrofit(host: String): Retrofit {
    val cleanHost = host.trim().removePrefix("http://").removeSuffix("/")
    val baseUrl = "http://$cleanHost/"
    return Retrofit.Builder()
      .baseUrl(baseUrl)
      .client(okHttpClient)
      .addConverterFactory(MoshiConverterFactory.create(moshi))
      .build()
  }

  fun getRetrofit(): Retrofit = retrofit

  fun getApiService(): IrApiService = apiService

  fun getCurrentHost(): String = currentHost

  @Synchronized
  fun updateBaseUrl(hostOrIp: String) {
    val cleanHost = hostOrIp.trim().removePrefix("http://").removeSuffix("/")
    if (cleanHost.isNotEmpty() && cleanHost != currentHost) {
      currentHost = cleanHost
      retrofit = buildRetrofit(currentHost)
      apiService = retrofit.create(IrApiService::class.java)
    }
  }
}
