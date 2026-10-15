package com.example.permissions

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.google.firebase.FirebaseApp

/**
 * Dedicated Firebase & Cloud Network State Verification Manager.
 * Separates Android network capability, actual internet transit, and Firebase SDK initialization.
 */
class FirebasePermissionManager(private val context: Context) {

  fun isNetworkConnected(): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
      ?: return false
    val activeNetwork = cm.activeNetwork ?: return false
    val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
      caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
  }

  fun isFirebaseConfigured(): Boolean {
    return try {
      FirebaseApp.getApps(context).isNotEmpty()
    } catch (_: Exception) {
      false
    }
  }

  fun checkNetworkReadiness(): NetworkReadiness {
    return when {
      !isNetworkConnected() -> NetworkReadiness.NETWORK_DISCONNECTED
      isFirebaseConfigured() -> NetworkReadiness.FIREBASE_AVAILABLE
      else -> NetworkReadiness.NETWORK_CONNECTED
    }
  }
}
