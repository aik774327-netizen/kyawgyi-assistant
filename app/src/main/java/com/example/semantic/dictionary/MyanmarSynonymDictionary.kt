package com.example.semantic.dictionary

/**
 * Myanmar Synonym and Vocabulary Dictionary
 * Captures spoken particles, polite endings, filler words, references, and action synonyms.
 */
object MyanmarSynonymDictionary {

    // Polite markers and honorifics
    val POLITE_PARTICLES = listOf(
        "ပေးပါဦး", "ပေးပါဦး", "ပေးပါ", "ပါဦး", "ပါဦး", "ပါ", "လေး",
        "နော်", "ကွာ", "ရေ", "ခင်ဗျာ", "ရှင့်", "ရှင်", "ဗျာ", "ဦး", "ဦး",
        "ပါလား", "ပါရစေ", "စမ်းပါ"
    )

    // Conversational fillers and hesitations
    val CONVERSATIONAL_FILLERS = listOf(
        "ဟိုလေ", "ဟိုဒင်း", "တချက်လောက်", "တစ်ချက်လောက်",
        "နည်းနည်းလောက်", "ပြောပြပါဦး", "လုပ်စမ်းပါ", "လုပ်ပေးပါ", "လုပ်ပေး",
        "ဘာလဲဆိုတော့", "ဟိုဟာ", "အဲလေ"
    )

    // Deictic and anaphoric reference triggers
    val APP_REFERENCES = listOf(
        "အဲဒီ app", "အဲ့ဒီ app", "အဲဒီ app ထဲ", "အဲ့ဒီ app ထဲ", "အဲ့ဒီ app ထဲဝင်", "အဲဒီဆော့ဖ်ဝဲ", "အဲ့ဒီဆော့ဖ်ဝဲလ်", "အဲ့ဒါ", "အဲဒါ", "အဲဒီဟာ"
    )

    val CONTAINER_REFERENCES = listOf(
        "အဲဒီမှာ", "အဲ့ဒီမှာ", "အဲထဲမှာ", "အဲ့ထဲမှာ", "အဲဒီထဲ", "အဲ့ဒီထဲ", "အဲဒီထဲက", "အဲ့ဒီထဲက", "အဲဒီက"
    )

    val MEDIA_REFERENCES = listOf(
        "ဒီသီချင်း", "ဒီသီချင်းကို", "ခုဖွင့်နေတဲ့သီချင်း", "လက်ရှိသီချင်း", "ဒီဟာ", "ဒီပုဒ်"
    )

    val CONTACT_REFERENCES = listOf(
        "သူ့ဆီ", "သူ့ကို", "အဲဒီလူဆီ", "အဲ့ဒီလူဆီ", "သူ့ဖုန်း"
    )

    // Action Synonyms: Open / Enter / Access
    val OPEN_ACTION_SYNONYMS = listOf(
        "ဖွင့်", "ဖွင့်ပေး", "ဖွင့်လိုက်", "ဖွင့်ပါ", "ဝင်", "ဝင်ပေး", "ဝင်လိုက်", "ဝင်ပါ",
        "သွား", "သွားမယ်", "ကြည့်", "ကြည့်မယ်", "ကြည့်ချင်လို့", "ပြ", "ပြပေး", "run", "launch", "open"
    )

    // Action Synonyms: Close / Exit
    val CLOSE_ACTION_SYNONYMS = listOf(
        "ပိတ်", "ပိတ်ပေး", "ပိတ်လိုက်", "ပိတ်ပါ", "ပိတ်ပစ်", "ထွက်", "ထွက်မယ်", "ရပ်", "ရပ်လိုက်", "close", "exit"
    )

    // Action Synonyms: Re-open / Return
    val REOPEN_ACTION_SYNONYMS = listOf(
        "ပြန်ဖွင့်", "ပြန်ဖွင့်ပေး", "ပြန်ဝင်", "ပြန်ဝင်ပေး", "ပြန်သွား", "reopen", "open again"
    )

    // Action Synonyms: Call / Phone
    val CALL_ACTION_SYNONYMS = listOf(
        "ဖုန်းဆက်", "ဖုန်းဆက်ပေး", "ဖုန်းခေါ်", "ဖုန်းခေါ်ပေး", "ဆက်", "ဆက်ပေး", "ခေါ်", "ခေါ်ပေး",
        "လှမ်းခေါ်", "call", "dial", "phone"
    )

    // Action Synonyms: SMS / Message
    val SMS_ACTION_SYNONYMS = listOf(
        "မက်ဆေ့ပို့", "မက်ဆေ့ခ်ျပို့", "မက်ဆေ့ပို့ပေး", "စာပို့", "စာပို့ပေး", "စာတိုပို့", "sms", "message", "text"
    )

    // Action Synonyms: Music / Play
    val PLAY_MUSIC_SYNONYMS = listOf(
        "သီချင်းဖွင့်", "သီချင်းဖွင့်ပေး", "သီချင်းနားထောင်", "နားထောင်ချင်လို့", "သီချင်းရှာဖွင့်",
        "play music", "play song", "ဖွင့်"
    )

    // Track Navigation Synonyms
    val NEXT_TRACK_SYNONYMS = listOf(
        "နောက်တစ်ပုဒ်", "နောက်သီချင်း", "နောက်တစ်ခု", "နောက်တစ်ပုဒ်ပြောင်း", "နောက်သီချင်းပြောင်း",
        "နောက်တစ်ပုဒ်ကျော်", "next", "next track", "skip"
    )

    val PREVIOUS_TRACK_SYNONYMS = listOf(
        "ရှေ့တစ်ပုဒ်", "ရှေ့သီချင်း", "ရှေ့တစ်ခု", "အရင်တစ်ပုဒ်", "အရင်သီချင်း",
        "previous", "previous track", "prev"
    )

    // Volume Modifiers
    val VOLUME_UP_SYNONYMS = listOf(
        "အသံမြှင့်", "အသံမြှင့်ပေး", "အသံကျယ်", "အသံကျယ်ပေး", "အသံတိုးပေး", // Note: Spoken Myanmar often uses 'အသံတိုး' or contextually differentiates
        "volume up", "increase volume", "louder"
    )

    val VOLUME_DOWN_SYNONYMS = listOf(
        "အသံလျှော့", "အသံလျှော့ပေး", "အသံချ", "အသံချပေး", "အသံတိုး", "အသံတိုးပေး", "အသံနည်းနည်းတိုးပေး",
        "volume down", "decrease volume", "softer", "lower volume"
    )

    // Setting toggles
    val WIFI_KEYWORDS = listOf("wifi", "wi-fi", "ဝိုင်ဖိုင်", "ဝိုက်ဖိုင်")
    val BLUETOOTH_KEYWORDS = listOf("bluetooth", "ဘလူးတုသ်", "ဗလူးတု", "ဘလူးတု")
    val FLASHLIGHT_KEYWORDS = listOf("flashlight", "torch", "flash", "ဓာတ်မီး", "ဓါတ်မီး", "မီးရောင်", "မီး", "မီးလင်း", "မီးလင်းပေး", "မီးဖွင့်")

    // Alarm / Clock keywords
    val ALARM_KEYWORDS = listOf(
        "alarm", "နှိုးစက်", "နှိုး", "နှိုးပေး", "နှိုးပါ", "နှိုးစက်ထား", "alarm ထားပေး", "နှိုးစက်ပေး"
    )
}
