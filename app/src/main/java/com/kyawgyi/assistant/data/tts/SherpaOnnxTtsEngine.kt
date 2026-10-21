package com.kyawgyi.assistant.data.tts

import android.content.Context
import com.example.voice.SherpaOnnxTtsStrategy

/**
 * SherpaOnnxTtsEngine (Layer 2 & 4)
 *
 * Exposes the offline TTS synthesis engine for DI and Domain consumption.
 */
class SherpaOnnxTtsEngine(private val context: Context) {
    private val strategy = SherpaOnnxTtsStrategy.getInstance(context)

    suspend fun speak(text: String, onFinished: (() -> Unit)? = null): Boolean {
        return strategy.speak(text, onFinished)
    }

    fun stop() {
        strategy.stop()
    }

    fun isSpeaking(): Boolean = strategy.isSpeaking()
}
