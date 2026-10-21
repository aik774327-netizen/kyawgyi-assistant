package com.example.engine

enum class EngineState {
    IDLE,       // စနစ် အနားယူနေသည်
    LISTENING,  // အသံ နားထောင်နေသည်
    PROCESSING, // ကြားရသော အသံကို စာသားပြောင်းနေသည်
    SPEAKING,   // တုံ့ပြန်အသံ ထွက်နေသည်
    ERROR       // အမှားအယွင်း ဖြစ်ပေါ်သည်
}

interface EngineCallback {
    fun onStateChanged(state: EngineState, message: String)
    fun onSpeechResult(text: String)
    fun onError(errorCode: Int, errorMessage: String)
}
