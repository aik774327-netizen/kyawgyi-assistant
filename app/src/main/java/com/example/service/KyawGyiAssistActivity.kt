package com.example.service

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.MainActivity

/**
 * KYAW GYI ASSIST ACTIVITY
 * Handles Android ACTION_ASSIST and ACTION_VOICE_ASSIST intents.
 * Triggers when user long-presses Home button or swipes from bottom corner gesture.
 */
class KyawGyiAssistActivity : Activity() {

    companion object {
        private const val TAG = "KyawGyiAssistAct"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "Assist intent received: ${intent?.action}")

        // Start Kyaw Gyi Floating Assistant Overlay or Foreground Service
        try {
            val serviceIntent = Intent(this, KyawGyiFloatingService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(this, serviceIntent)
            } else {
                startService(serviceIntent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch floating service from assist", e)
            // Fallback: Launch MainActivity with voice active
            val mainIntent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("auto_start_listening", true)
            }
            startActivity(mainIntent)
        }

        // Finish immediately so the user stays on their current screen while overlay is active
        finish()
    }
}
