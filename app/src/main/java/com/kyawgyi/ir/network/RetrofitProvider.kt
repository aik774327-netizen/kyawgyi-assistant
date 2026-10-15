package com.kyawgyi.ir.network

import android.content.Context
import com.kyawgyi.ir.apikey.ApiKeyInterceptor
import com.kyawgyi.ir.apikey.ApiKeyManager
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Singleton provider for OkHttpClient, Retrofit, and IrApiService.
 * Encapsulates network client initialization, base URL updates, and converter configurations
 * with support for the robust ApiKeyManager failover system.
 */
object RetrofitProvider {

  private const val DEFAULT_HOST = "192.168.1.150"

  @Volatile
  private var currentHost: String = DEFAULT_HOST

  @Volatile
  private var attachedApiKeyManager: ApiKeyManager? = null

  val moshi: Moshi by lazy {
    Moshi.Builder()
      .addLast(KotlinJsonAdapterFactory())
      .build()
  }

  val okHttpClient: OkHttpClient
    get() = buildOkHttpClient(attachedApiKeyManager)

  fun buildOkHttpClient(apiKeyManager: ApiKeyManager? = attachedApiKeyManager): OkHttpClient {
    val builder = OkHttpClient.Builder()
      .connectTimeout(4, TimeUnit.SECONDS)
      .readTimeout(8, TimeUnit.SECONDS)
      .writeTimeout(5, TimeUnit.SECONDS)
      .addInterceptor(HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
      })

    if (apiKeyManager != null) {
      builder.addInterceptor(ApiKeyInterceptor(apiKeyManager))
    }

    return builder.build()
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

  /**
   * Enables the robust ApiKeyManager system for all subsequent requests.
   */
  @Synchronized
  fun enableApiKeyManager(apiKeyManager: ApiKeyManager) {
    attachedApiKeyManager = apiKeyManager
    retrofit = buildRetrofit(currentHost)
    apiService = retrofit.create(IrApiService::class.java)
  }

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

