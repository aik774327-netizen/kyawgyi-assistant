package com.example.engine

/**
 * Human-Centric Voice Session State Machine & Event Model
 */
enum class VoiceSessionState {
    IDLE,                          // စနစ် အနားယူနေသည် (Idle / Inactive)
    WAKE_WORD_LISTENING,           // "ကျော်ကြီး" Wake-word ကို သီးခြား စောင့်ကြည့်နေသည်
    WAKE_WORD_DETECTED,            // Wake-word ကို ဖမ်းယူရရှိခဲ့ပြီး Command Session စတင်ရန် အဆင်သင့်ဖြစ်သည်
    COMMAND_LISTENING,             // အသုံးပြုသူ၏ အသံအမိန့်ကို စတင်နားထောင်နေသည်
    USER_SPEAKING,                 // အသုံးပြုသူ စကားပြောနေဆဲ (Live Speech Active)
    USER_SILENCE_CONFIRMATION,     // စကားပြောပြီးနောက် ခေတ္တနားချိန် သို့မဟုတ် စကားပြီးဆုံးမှု စစ်ဆေးနေသည်
    PROCESSING_STT,                // Speech to Text အသံလှိုင်းကို စာသားပြောင်းလဲ အတည်ပြုနေသည်
    AI_THINKING,                   // AI Model (Gemini / Cloud) ဖြင့် အဖြေထုတ်ယူနေသည်
    LOCAL_NLU,                     // စက်တွင်း မြန်မာ NLU ဖြင့် အမိန့်ခွဲခြမ်းစိတ်ဖြာနေသည်
    EXECUTING_COMMAND,             // အမိန့် (App Launch, Accessibility, Device Control) ကို လုပ်ဆောင်နေသည်
    SPEAKING_TTS,                  // တုံ့ပြန်စကားသံကို Text-To-Speech ဖြင့် ပြောကြားနေသည်
    SESSION_COMPLETE,              // အမိန့်လုပ်ဆောင်ချက်နှင့် အသံတုံ့ပြန်မှု ပြီးဆုံးသည်
    ERROR                          // အမှားအယွင်း ဖြစ်ပေါ်သည် (Graceful Recovery Mode)
}

enum class VoiceSessionEvent {
    MIC_STARTED,
    MIC_READY,
    SPEECH_STARTED,
    PARTIAL_TRANSCRIPT,
    SPEECH_CONTINUED,
    SPEECH_ENDED,
    SILENCE_CONFIRMED,
    COMMAND_FINALIZED,
    MIC_STOPPED,
    COMMAND_EXECUTED,
    TTS_STARTED,
    TTS_COMPLETED,
    ERROR_OCCURRED,
    TIMEOUT_OCCURRED
}

enum class PauseType {
    SHORT_PAUSE,       // < 600ms - စကားလုံးများကြား သဘာဝအတိုင်း ခဏနားခြင်း
    NORMAL_PAUSE,      // 600ms - 1500ms - စကားဆက်စပ်တွေးတောခြင်း / အပိုဒ်ခွဲနားခြင်း
    FINAL_SILENCE      // > 1500ms (သို့မဟုတ် Sentence particle ပါပါက > 1100ms) - စကားပြော ပြီးဆုံးမှု
}

/**
 * Configurable parameters for Voice Session timing and thresholds
 */
data class VoiceSessionConfig(
    val initialListeningTimeoutMs: Long = 7000L,        // Wake word ပြီးနောက် အသုံးပြုသူ စကားစပြောရန် အများဆုံးစောင့်မည့်အချိန်
    val shortPauseThresholdMs: Long = 600L,            // Short pause အပိုင်းအခြား
    val normalPauseThresholdMs: Long = 1400L,          // Normal pause အပိုင်းအခြား
    val speechEndSilenceTimeoutMs: Long = 1800L,       // မပြည့်စုံသေးသော phrase အတွက် silence confirmation
    val speechEndCompletePhraseSilenceMs: Long = 1100L, // စကားအဆုံးသတ် particle ပါပါက silence confirmation
    val maximumCommandDurationMs: Long = 20000L,       // Single command အတွက် အများဆုံးနားထောင်ချိန် (20s)
    val postWakeGracePeriodMs: Long = 1200L,            // Wake word ကြားပြီးနောက် grace period
    val minimumSessionDurationMs: Long = 500L,          // Microphone start/stop debounce အနည်းဆုံးကြာချိန်
    val micDebounceIntervalMs: Long = 400L,             // Rapid mic toggle request coalescing interval
    val confidenceThreshold: Float = 0.40f              // အနိမ့်ဆုံး စိတ်ချရမှု threshold
)

interface VoiceSessionListener {
    fun onStateChanged(state: VoiceSessionState, feedbackMessage: String)
    fun onEvent(event: VoiceSessionEvent, payload: String? = null)
    fun onPartialTranscript(partialText: String)
    fun onFinalTranscript(finalText: String, confidence: Float)
    fun onError(errorCode: Int, errorMessage: String)
}
