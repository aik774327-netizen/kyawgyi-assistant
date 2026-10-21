package com.example.service

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import android.app.assist.AssistContent
import android.app.assist.AssistStructure
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * KYAW GYI VOICE INTERACTION SERVICE
 * Enables Kyaw Gyi to be selected as Android's Default Digital Assistant App (Assist & Voice Input).
 * Allows screen analysis, long-press home / gesture assist trigger, and hands-free voice interactions.
 */
class KyawGyiVoiceInteractionService : VoiceInteractionService() {

    companion object {
        private const val TAG = "KyawGyiVoiceInter"

        fun isSessionActive(context: Context): Boolean {
            return isActiveService(context, android.content.ComponentName(context, KyawGyiVoiceInteractionService::class.java))
        }
    }

    override fun onReady() {
        super.onReady()
        Log.i(TAG, "Kyaw Gyi VoiceInteractionService is READY as Default Digital Assistant")
    }

    override fun onShutdown() {
        super.onShutdown()
        Log.i(TAG, "Kyaw Gyi VoiceInteractionService shutdown")
    }
}

/**
 * KYAW GYI VOICE INTERACTION SESSION SERVICE
 * Creates and manages voice interaction sessions when the user triggers the assist gesture.
 */
class KyawGyiVoiceInteractionSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession {
        return KyawGyiVoiceSession(this)
    }
}

