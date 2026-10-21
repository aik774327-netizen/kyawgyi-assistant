package com.example.utils

import android.content.Context
import android.util.Log
import com.kyawgyi.ir.service.VoiceForegroundService

/**
 * Unified KyawGyi Service Controller.
 * Manages background voice operations through the canonical VoiceForegroundService
 * with Android 14+ safe execution guardrails.
 */
object KyawGyiServiceController {

    private const val TAG = "KyawGyiServiceController"

    fun startService(context: Context) {
        try {
            VoiceForegroundService.startService(context)
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to start canonical voice service: ${e.message}", e)
        }
    }

    fun startListening(context: Context) {
        try {
            VoiceForegroundService.startListening(context)
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to dispatch start listening: ${e.message}", e)
        }
    }

    fun stopListening(context: Context) {
        try {
            VoiceForegroundService.stopListening(context)
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to dispatch stop listening: ${e.message}", e)
        }
    }

    fun speak(context: Context, text: String) {
        try {
            VoiceForegroundService.speak(context, text)
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to dispatch speak: ${e.message}", e)
        }
    }

    fun stopService(context: Context) {
        try {
            VoiceForegroundService.stopService(context)
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to stop voice service: ${e.message}", e)
        }
    }
}
