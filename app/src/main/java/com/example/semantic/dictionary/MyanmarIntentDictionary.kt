package com.example.semantic.dictionary

import com.example.semantic.model.SemanticIntent

/**
 * Myanmar Intent Dictionary
 * Maps intent definitions to semantic intent patterns, key phrases, and goal classifiers.
 */
object MyanmarIntentDictionary {

    data class IntentPattern(
        val intent: SemanticIntent,
        val keywords: List<String>,
        val verbStems: List<String>,
        val antiKeywords: List<String> = emptyList(),
        val defaultTarget: String? = null,
        val minConfidence: Float = 0.85f
    )

    val PATTERNS = listOf(
        // Camera Direct & Indirect Goals
        IntentPattern(
            intent = SemanticIntent.OPEN_CAMERA,
            keywords = listOf("ကင်မရာ", "ဓာတ်ပုံရိုက်", "ဓါတ်ပုံရိုက်", "photo ရိုက်", "cam"),
            verbStems = listOf("ဖွင့်", "ဝင်", "ရိုက်ချင်လို့", "သွား", "ယူ"),
            defaultTarget = "CAMERA",
            minConfidence = 0.92f
        ),

        // Photos & Gallery Direct & Indirect
        IntentPattern(
            intent = SemanticIntent.OPEN_PHOTOS,
            keywords = listOf("ဓာတ်ပုံ", "ဓါတ်ပုံ", "ပုံတွေ", "gallery", "photos", "ဂယ်လာရီ"),
            verbStems = listOf("ကြည့်", "ဖွင့်", "ဝင်", "ပြ"),
            antiKeywords = listOf("ရိုက်"), // "ဓာတ်ပုံရိုက်" is Camera
            defaultTarget = "PHOTOS",
            minConfidence = 0.90f
        ),

        // Music Playback
        IntentPattern(
            intent = SemanticIntent.PLAY_MUSIC,
            keywords = listOf("သီချင်း", "song", "music", "သီချင်းတစ်ပုဒ်"),
            verbStems = listOf("ဖွင့်", "နားထောင်", "ရှာဖွင့်", "play"),
            defaultTarget = "MUSIC",
            minConfidence = 0.88f
        ),

        // Track Navigation
        IntentPattern(
            intent = SemanticIntent.NEXT_TRACK,
            keywords = listOf("နောက်တစ်ပုဒ်", "နောက်သီချင်း", "next song", "next track"),
            verbStems = listOf("ပြောင်း", "ကျော်", "ဖွင့်", "သွား"),
            defaultTarget = "NEXT",
            minConfidence = 0.90f
        ),
        IntentPattern(
            intent = SemanticIntent.PREVIOUS_TRACK,
            keywords = listOf("ရှေ့တစ်ပုဒ်", "ရှေ့သီချင်း", "အရင်တစ်ပုဒ်", "previous song"),
            verbStems = listOf("ပြောင်း", "ဖွင့်", "ပြန်သွား"),
            defaultTarget = "PREV",
            minConfidence = 0.90f
        ),

        // Media State Controls
        IntentPattern(
            intent = SemanticIntent.PAUSE_MEDIA,
            keywords = listOf("သီချင်း", "မီဒီယာ", "အသံ"),
            verbStems = listOf("ရပ်", "ခဏရပ်", "pause", "ခေတ္တရပ်"),
            minConfidence = 0.88f
        ),
        IntentPattern(
            intent = SemanticIntent.RESUME_MEDIA,
            keywords = listOf("သီချင်း", "မီဒီယာ"),
            verbStems = listOf("ဆက်ဖွင့်", "ပြန်ဖွင့်", "resume", "ဆက်တီး"),
            minConfidence = 0.88f
        ),

        // Phone & SMS
        IntentPattern(
            intent = SemanticIntent.CALL_CONTACT,
            keywords = listOf("ဖုန်း", "phone", "call"),
            verbStems = listOf("ဆက်", "ဆက်ပေး", "ခေါ်", "ခေါ်ပေး", "လှမ်းခေါ်"),
            minConfidence = 0.90f
        ),
        IntentPattern(
            intent = SemanticIntent.SEND_SMS,
            keywords = listOf("မက်ဆေ့", "မက်ဆေ့ခ်ျ", "စာတို", "sms", "message"),
            verbStems = listOf("ပို့", "ပို့ပေး", "ပေးပို့"),
            minConfidence = 0.90f
        ),

        // Volume Controls
        IntentPattern(
            intent = SemanticIntent.VOLUME_DOWN,
            keywords = listOf("အသံ", "volume"),
            verbStems = listOf("တိုး", "လျှော့", "ချ", "တိုးပေး", "နည်းနည်းတိုး", "နဲနဲတိုး"),
            antiKeywords = listOf("ကျယ်", "မြှင့်"),
            minConfidence = 0.90f
        ),
        IntentPattern(
            intent = SemanticIntent.VOLUME_UP,
            keywords = listOf("အသံ", "volume"),
            verbStems = listOf("မြှင့်", "ကျယ်", "တင်", "မြှင့်ပေး", "ကျယ်ပေး"),
            antiKeywords = listOf("လျှော့", "ချ"),
            minConfidence = 0.90f
        ),
        IntentPattern(
            intent = SemanticIntent.MUTE,
            keywords = listOf("အသံ", "volume", "sound"),
            verbStems = listOf("ပိတ်", "တိတ်", "mute", "ပိတ်ထား"),
            minConfidence = 0.90f
        ),

        // Connectivity Toggles
        IntentPattern(
            intent = SemanticIntent.TOGGLE_WIFI,
            keywords = listOf("wifi", "wi-fi", "ဝိုင်ဖိုင်", "ဝိုက်ဖိုင်"),
            verbStems = listOf("ဖွင့်", "ပိတ်", "toggle", "on", "off"),
            defaultTarget = "WIFI",
            minConfidence = 0.92f
        ),
        IntentPattern(
            intent = SemanticIntent.TOGGLE_BLUETOOTH,
            keywords = listOf("bluetooth", "ဘလူးတုသ်", "ဗလူးတု"),
            verbStems = listOf("ဖွင့်", "ပိတ်", "on", "off"),
            defaultTarget = "BLUETOOTH",
            minConfidence = 0.92f
        ),
        IntentPattern(
            intent = SemanticIntent.TOGGLE_FLASHLIGHT,
            keywords = listOf("ဓာတ်မီး", "ဓါတ်မီး", "flashlight", "torch", "မီးရောင်", "မီး"),
            verbStems = listOf("ဖွင့်", "ပိတ်", "လင်း", "ထွန်း", "လင်းပေး"),
            defaultTarget = "FLASHLIGHT",
            minConfidence = 0.92f
        ),

        // Alarms & Timers
        IntentPattern(
            intent = SemanticIntent.SET_ALARM,
            keywords = listOf("alarm", "နှိုးစက်", "နာရီ"),
            verbStems = listOf("ထား", "ပေး", "ထားပေး", "နှိုး", "နှိုးပေး", "သတ်မှတ်"),
            defaultTarget = "ALARM",
            minConfidence = 0.90f
        ),

        // System Navigation
        IntentPattern(
            intent = SemanticIntent.NAVIGATE_HOME,
            keywords = listOf("home", "ပင်မ", "ပင်မမျက်နှာပြင်", "အိမ်"),
            verbStems = listOf("သွား", "ပြန်သွား", "ထွက်", "ပြန်"),
            minConfidence = 0.92f
        ),
        IntentPattern(
            intent = SemanticIntent.NAVIGATE_BACK,
            keywords = listOf("back", "နောက်", "နောက်သို့", "ပြန်လှည့်"),
            verbStems = listOf("သွား", "ပြန်", "ဆုတ်", "နောက်ပြန်သွား"),
            minConfidence = 0.92f
        ),
        IntentPattern(
            intent = SemanticIntent.SHOW_RECENTS,
            keywords = listOf("recents", "လတ်တလော", "ဖွင့်ထားသော", "apps"),
            verbStems = listOf("ပြ", "ပြပေး", "ဖွင့်"),
            minConfidence = 0.90f
        ),

        // Anaphoric / Contextual App Reopen
        IntentPattern(
            intent = SemanticIntent.REOPEN_APP,
            keywords = listOf("အဲဒီ app", "အဲ့ဒီ app", "အဲဒီဆော့ဖ်ဝဲ", "အဲ့ဒီဟာ", "အဲ့ဒါ", "အဲဒါ"),
            verbStems = listOf("ပြန်ဖွင့်", "ပြန်ဝင်", "reopen", "ဖွင့်", "ဝင်", "ဝင်ပေး", "ထဲဝင်"),
            minConfidence = 0.90f
        )
    )
}
