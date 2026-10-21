package com.example.utils

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

object NetworkMonitor {

    private const val TAG = "NetworkMonitor"

    fun isInternetAvailable(context: Context): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false

        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false

        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
               capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
               capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    /**
     * Real-time network connectivity flow using Android's ConnectivityManager.NetworkCallback.
     */
    fun observeNetwork(context: Context): Flow<Boolean> = callbackFlow {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

        if (connectivityManager == null) {
            trySend(false)
            close()
            return@callbackFlow
        }

        // Send initial value immediately
        trySend(isInternetAvailable(context))

        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(true)
            }

            override fun onLost(network: Network) {
                trySend(isInternetAvailable(context))
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                                  networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                trySend(hasInternet)
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.registerNetworkCallback(request, networkCallback)

        awaitClose {
            try {
                connectivityManager.unregisterNetworkCallback(networkCallback)
            } catch (e: Exception) {
                Log.w(TAG, "Error unregistering network callback: ${e.message}")
            }
        }
    }.distinctUntilChanged()

    /**
     * Opens Android System Voice Input / Offline Speech Recognition Settings.
     * Allows user to download Myanmar/Burmese or English offline speech models.
     */
    fun openVoiceInputSettings(context: Context) {
        // Attempt 1: Direct Voice Input Settings
        val voiceInputIntent = Intent(Settings.ACTION_VOICE_INPUT_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            context.startActivity(voiceInputIntent)
            return
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "ACTION_VOICE_INPUT_SETTINGS not found, attempting alternatives")
        }

        // Attempt 2: Google Voice Offline Recognition Settings Intent
        val offlineSpeechIntent = Intent("com.google.android.voicesearch.intent.action.OFFLINE_RECOGNITION").apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            context.startActivity(offlineSpeechIntent)
            return
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "OFFLINE_RECOGNITION action not found")
        }

        // Attempt 3: General Language & Input Settings
        try {
            val inputIntent = Intent(Settings.ACTION_LOCALE_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(inputIntent)
        } catch (e: Exception) {
            // Attempt 4: Fallback to General Settings
            val generalSettings = Intent(Settings.ACTION_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(generalSettings)
        }
    }
}

