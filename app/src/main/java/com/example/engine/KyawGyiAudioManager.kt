package com.example.engine

import android.content.Context
import com.example.audio.KyawGyiAudioPolicyManager

/**
 * Human-Centric Audio Focus & Media Protection Engine for Kyaw Gyi AI
 * Ensures ambient wake-word & STT listening NEVER pause or stop background music playback (YouTube / Spotify / Local Media).
 * Applies non-intrusive transient ducking (AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK) ONLY during active TTS spoken feedback.
 */
class KyawGyiAudioManager(private val context: Context) {

    companion object {
        @Volatile
        private var instance: KyawGyiAudioManager? = null

        fun getInstance(context: Context): KyawGyiAudioManager {
            return instance ?: synchronized(this) {
                instance ?: KyawGyiAudioManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val policyManager = KyawGyiAudioPolicyManager.getInstance(context)

    /**
     * Check if music / media is actively playing on the device
     */
    fun isMusicActive(): Boolean {
        return policyManager.isExternalMusicActive()
    }

    /**
     * Request Transient Ducking Audio Focus ONLY when Kyaw Gyi TTS speaks.
     */
    @Synchronized
    fun requestTtsDuckingFocus(): Boolean {
        return policyManager.requestTtsDuckingFocus()
    }

    /**
     * Abandon Audio Focus immediately after TTS speech completes.
     */
    @Synchronized
    fun abandonTtsFocus() {
        policyManager.abandonTtsFocus()
    }
}
