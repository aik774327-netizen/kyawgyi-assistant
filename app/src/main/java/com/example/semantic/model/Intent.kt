package com.example.semantic.model

/**
 * Myanmar Semantic Intelligence Layer (MSIL) - Semantic Intent Definitions
 * Represents high-level user intentions decoupled from surface wording.
 */
enum class SemanticIntent(
    val category: IntentCategory,
    val isPotentiallyDangerous: Boolean = false,
    val defaultDescriptionMM: String
) {
    // App Lifecycle
    OPEN_APP(IntentCategory.APP_CONTROL, false, "အက်ပ်ဖွင့်ခြင်း"),
    CLOSE_APP(IntentCategory.APP_CONTROL, false, "အက်ပ်ပိတ်ခြင်း"),
    REOPEN_APP(IntentCategory.APP_CONTROL, false, "အက်ပ်ပြန်ဖွင့်ခြင်း"),

    // Communication (Potentially dangerous - requires validation)
    CALL_CONTACT(IntentCategory.COMMUNICATION, true, "ဖုန်းခေါ်ဆိုခြင်း"),
    SEND_SMS(IntentCategory.COMMUNICATION, true, "မက်ဆေ့ခ်ျပေးပို့ခြင်း"),

    // Media Controls
    PLAY_MUSIC(IntentCategory.MEDIA, false, "သီချင်းဖွင့်ခြင်း"),
    PAUSE_MEDIA(IntentCategory.MEDIA, false, "မီဒီယာခဏရပ်ခြင်း"),
    RESUME_MEDIA(IntentCategory.MEDIA, false, "မီဒီယာဆက်ဖွင့်ခြင်း"),
    NEXT_TRACK(IntentCategory.MEDIA, false, "နောက်သီချင်းတစ်ပုဒ်သို့ပြောင်းခြင်း"),
    PREVIOUS_TRACK(IntentCategory.MEDIA, false, "ရှေ့သီချင်းတစ်ပုဒ်သို့ပြောင်းခြင်း"),
    STOP_MEDIA(IntentCategory.MEDIA, false, "မီဒီယာရပ်ခြင်း"),

    // Device Settings & System Controls
    VOLUME_UP(IntentCategory.DEVICE_CONTROL, false, "အသံမြှင့်ခြင်း"),
    VOLUME_DOWN(IntentCategory.DEVICE_CONTROL, false, "အသံလျှော့ခြင်း"),
    SET_VOLUME(IntentCategory.DEVICE_CONTROL, false, "အသံပမာဏချိန်ညှိခြင်း"),
    MUTE(IntentCategory.DEVICE_CONTROL, false, "အသံပိတ်ခြင်း"),
    UNMUTE(IntentCategory.DEVICE_CONTROL, false, "အသံပြန်ဖွင့်ခြင်း"),
    TOGGLE_WIFI(IntentCategory.DEVICE_CONTROL, false, "Wi-Fi အဖွင့်/အပိတ်"),
    TOGGLE_BLUETOOTH(IntentCategory.DEVICE_CONTROL, false, "Bluetooth အဖွင့်/အပိတ်"),
    TOGGLE_FLASHLIGHT(IntentCategory.DEVICE_CONTROL, false, "ဓာတ်မီး အဖွင့်/အပိတ်"),

    // Utilities & Productivity
    SET_ALARM(IntentCategory.UTILITY, false, "နှိုးစက်ပေးခြင်း"),
    SET_TIMER(IntentCategory.UTILITY, false, "အချိန်မှတ်စက်သတ်မှတ်ခြင်း"),
    SEARCH(IntentCategory.UTILITY, false, "အချက်အလက်ရှာဖွေခြင်း"),

    // Direct Camera / Media Utilities
    OPEN_CAMERA(IntentCategory.APP_CONTROL, false, "ကင်မရာဖွင့်ခြင်း"),
    OPEN_PHOTOS(IntentCategory.APP_CONTROL, false, "ဓာတ်ပုံကြည့်ခြင်း"),
    OPEN_MAPS(IntentCategory.APP_CONTROL, false, "မြေပုံကြည့်ခြင်း"),
    OPEN_SETTINGS(IntentCategory.DEVICE_CONTROL, false, "ဆက်တင်ဖွင့်ခြင်း"),

    // Navigation & System Gestures
    NAVIGATE_HOME(IntentCategory.SYSTEM_NAVIGATION, false, "ပင်မမျက်နှာပြင်သို့သွားခြင်း"),
    NAVIGATE_BACK(IntentCategory.SYSTEM_NAVIGATION, false, "နောက်သို့ပြန်သွားခြင်း"),
    SHOW_RECENTS(IntentCategory.SYSTEM_NAVIGATION, false, "လတ်တလောအက်ပ်များပြခြင်း"),
    READ_SCREEN(IntentCategory.DEVICE_CONTROL, false, "မျက်နှာပြင်ပေါ်ရှိ စာများကို ဖတ်ပြခြင်း"),

    // Financial & Critical Actions (Critical Danger)
    PAYMENT(IntentCategory.CRITICAL, true, "ငွေပေးချေခြင်း"),
    DELETE_ITEM(IntentCategory.CRITICAL, true, "ဖျက်ပစ်ခြင်း"),
    UNINSTALL_APP(IntentCategory.CRITICAL, true, "အက်ပ်ဖျက်ပစ်ခြင်း"),

    // Conversational Flow
    CONFIRM(IntentCategory.CONVERSATION, false, "အတည်ပြုခြင်း"),
    CANCEL(IntentCategory.CONVERSATION, false, "ပယ်ဖျက်ခြင်း"),
    UNKNOWN(IntentCategory.CONVERSATION, false, "နားမလည်သောစကား")
}

enum class IntentCategory {
    APP_CONTROL,
    COMMUNICATION,
    MEDIA,
    DEVICE_CONTROL,
    UTILITY,
    SYSTEM_NAVIGATION,
    CRITICAL,
    CONVERSATION
}
