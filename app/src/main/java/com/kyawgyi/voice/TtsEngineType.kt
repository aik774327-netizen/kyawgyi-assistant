package com.kyawgyi.voice

/**
 * User-selectable TTS engine (2026-10-23, §7 plan).
 *
 * [SYSTEM] is the Android system TextToSpeech path via
 * MyanmarTTSEngine (with the FIX-02 installed-my-MM-voice gate).
 * [GEMINI_ONLINE] synthesizes speech with the Gemini API
 * (GeminiApiService.synthesizeSpeech, shared key pool + failure
 * classifier) and plays the returned 24 kHz PCM via AudioTrack;
 * it requires internet and a user-configured API key, sends the
 * reply text to Google, and falls back to the system path — with
 * the failure surfaced — when synthesis fails.
 *
 * Persisted by name in VoiceSettings; unknown values fall back to
 * [SYSTEM].
 */
enum class TtsEngineType {
    SYSTEM,
    GEMINI_ONLINE
}
