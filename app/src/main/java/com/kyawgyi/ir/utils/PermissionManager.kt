package com.kyawgyi.ir.utils

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Handles runtime permission requests for Microphone (RECORD_AUDIO) and Location
 * (ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION) using ActivityResultContracts.
 */
class PermissionManager(
  private val activity: ComponentActivity,
  private val onPermissionsResult: ((allGranted: Boolean, grantedMap: Map<String, Boolean>) -> Unit)? = null
) {

  private var permissionLauncher: ActivityResultLauncher<Array<String>>? = null

  private val _hasMicrophonePermission = MutableStateFlow(hasMicrophonePermission(activity))
  val hasMicrophonePermission: StateFlow<Boolean> = _hasMicrophonePermission.asStateFlow()

  private val _hasLocationPermission = MutableStateFlow(hasLocationPermission(activity))
  val hasLocationPermission: StateFlow<Boolean> = _hasLocationPermission.asStateFlow()

  private val _hasNotificationPermission = MutableStateFlow(hasNotificationPermission(activity))
  val hasNotificationPermission: StateFlow<Boolean> = _hasNotificationPermission.asStateFlow()

  private val _allEssentialPermissionsGranted = MutableStateFlow(checkAllPermissionsGranted())
  val allEssentialPermissionsGranted: StateFlow<Boolean> = _allEssentialPermissionsGranted.asStateFlow()

  companion object {
    val REQUIRED_PERMISSIONS: Array<String>
      get() {
        val permissions = mutableListOf(
          Manifest.permission.RECORD_AUDIO
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        return permissions.toTypedArray()
      }

    val OPTIONAL_LOCATION_PERMISSIONS: Array<String>
      get() = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
      )

    fun hasMicrophonePermission(context: Context): Boolean {
      return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.RECORD_AUDIO
      ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasLocationPermission(context: Context): Boolean {
      val fine = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION
      ) == PackageManager.PERMISSION_GRANTED
      val coarse = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_COARSE_LOCATION
      ) == PackageManager.PERMISSION_GRANTED
      return fine || coarse
    }

    fun hasNotificationPermission(context: Context): Boolean {
      return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(
          context,
          Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
      } else {
        true
      }
    }
  }

  init {
    registerLauncher()
  }

  @SuppressLint("InvalidFragmentVersionForActivityResult")
  private fun registerLauncher() {
    permissionLauncher = activity.registerForActivityResult(
      ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
      updateStates()
      val micGranted = result[Manifest.permission.RECORD_AUDIO] 
        ?: hasMicrophonePermission(activity)
      val allGranted = micGranted

      onPermissionsResult?.invoke(allGranted, result)
    }
  }

  fun updateStates() {
    _hasMicrophonePermission.value = hasMicrophonePermission(activity)
    _hasLocationPermission.value = hasLocationPermission(activity)
    _hasNotificationPermission.value = hasNotificationPermission(activity)
    _allEssentialPermissionsGranted.value = checkAllPermissionsGranted()
  }

  fun checkAllPermissionsGranted(): Boolean {
    return hasMicrophonePermission(activity)
  }

  /**
   * Triggers the runtime permission dialog flow for essential permissions (Microphone & Notifications).
   * Location is never requested unnecessarily.
   */
  fun requestEssentialPermissions() {
    updateStates()
    val permissionsToRequest = REQUIRED_PERMISSIONS.filter { permission ->
      ContextCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED
    }.toTypedArray()

    if (permissionsToRequest.isNotEmpty()) {
      permissionLauncher?.launch(permissionsToRequest)
    } else {
      updateStates()
      onPermissionsResult?.invoke(true, emptyMap())
    }
  }

  /**
   * Triggers optional location request solely when a weather feature or legacy BLE is explicitly queried.
   */
  fun requestOptionalLocationPermission() {
    permissionLauncher?.launch(OPTIONAL_LOCATION_PERMISSIONS)
  }

  /**
   * Checks microphone permission and activates VoiceForegroundService if permitted.
   * If permission is missing, requests essential permissions first.
   */
  fun startVoiceServiceIfPermitted(): Boolean {
    updateStates()
    return if (hasMicrophonePermission(activity)) {
      com.kyawgyi.ir.service.VoiceForegroundService.startService(activity)
    } else {
      requestEssentialPermissions()
      false
    }
  }
}
