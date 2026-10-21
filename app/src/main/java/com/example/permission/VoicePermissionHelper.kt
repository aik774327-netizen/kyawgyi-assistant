package com.example.permission

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * VoicePermissionHelper
 *
 * Verifies microphone access status before voice engine initialization and
 * triggers a friendly, accessible rationale dialog if user permissions are denied.
 */
class VoicePermissionHelper(
    private val context: Context,
    private val onPermissionGranted: () -> Unit = {},
    private val onPermissionDenied: () -> Unit = {}
) {

    companion object {
        private const val TAG = "VoicePermissionHelper"
        const val PERMISSION_RECORD_AUDIO = Manifest.permission.RECORD_AUDIO

        /**
         * Quick static check whether RECORD_AUDIO is granted.
         */
        fun hasMicrophonePermission(context: Context): Boolean {
            return ContextCompat.checkSelfPermission(
                context,
                PERMISSION_RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private val _showRationaleDialog = MutableStateFlow(false)
    val showRationaleDialog: StateFlow<Boolean> = _showRationaleDialog.asStateFlow()

    private var permissionLauncher: ActivityResultLauncher<String>? = null

    /**
     * Register permission launcher with an Android ComponentActivity.
     * Must be called during Activity onCreate or initialization.
     */
    fun registerLauncher(activity: ComponentActivity) {
        permissionLauncher = activity.registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                Log.i(TAG, "Microphone permission granted by user.")
                _showRationaleDialog.value = false
                onPermissionGranted()
            } else {
                Log.w(TAG, "Microphone permission denied by user.")
                val shouldShowRationale = ActivityCompat.shouldShowRequestPermissionRationale(
                    activity,
                    PERMISSION_RECORD_AUDIO
                )
                // Always display friendly rationale if denied so user understands why voice engine needs it
                _showRationaleDialog.value = true
                onPermissionDenied()
            }
        }
    }

    /**
     * Verifies microphone access status before voice engine initialization.
     * If granted, invokes [onGrantedAction] or [onPermissionGranted].
     * If denied/not granted, shows friendly rationale dialog and/or requests permission.
     */
    fun checkAndRequestMicrophonePermission(
        activity: Activity? = null,
        onGrantedAction: (() -> Unit)? = null
    ): Boolean {
        if (hasMicrophonePermission(context)) {
            Log.d(TAG, "Microphone permission already granted.")
            _showRationaleDialog.value = false
            onGrantedAction?.invoke() ?: onPermissionGranted()
            return true
        }

        Log.i(TAG, "Microphone permission not granted. Checking rationale status...")

        val shouldShowRationale = activity?.let {
            ActivityCompat.shouldShowRequestPermissionRationale(it, PERMISSION_RECORD_AUDIO)
        } ?: false

        if (shouldShowRationale) {
            _showRationaleDialog.value = true
        } else {
            // First time or direct request
            requestPermissionDirectly(activity)
        }
        return false
    }

    /**
     * Trigger permission request directly using either registered launcher or ActivityCompat.
     */
    fun requestPermissionDirectly(activity: Activity? = null) {
        permissionLauncher?.let {
            it.launch(PERMISSION_RECORD_AUDIO)
        } ?: run {
            activity?.let {
                ActivityCompat.requestPermissions(
                    it,
                    arrayOf(PERMISSION_RECORD_AUDIO),
                    1001
                )
            } ?: run {
                _showRationaleDialog.value = true
            }
        }
    }

    fun dismissRationaleDialog() {
        _showRationaleDialog.value = false
    }

    fun onRationaleAccepted(activity: Activity? = null) {
        _showRationaleDialog.value = false
        requestPermissionDirectly(activity)
    }
}
