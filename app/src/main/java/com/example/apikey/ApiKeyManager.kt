package com.example.apikey

import android.content.Context

typealias ApiKeyManager = com.kyawgyi.ir.apikey.ApiKeyManager
typealias ApiKeyInterceptor = com.kyawgyi.ir.apikey.ApiKeyInterceptor
typealias ManagedApiKey = com.kyawgyi.ir.apikey.ManagedApiKey
typealias ApiKeyTier = com.kyawgyi.ir.apikey.ApiKeyTier
typealias ApiKeyStatus = com.kyawgyi.ir.apikey.ApiKeyStatus
typealias ApiKeyQuotaAlert = com.kyawgyi.ir.apikey.ApiKeyQuotaAlert

fun getApiKeyManager(context: Context): com.kyawgyi.ir.apikey.ApiKeyManager {
  return com.kyawgyi.ir.apikey.ApiKeyManager.getInstance(context)
}
