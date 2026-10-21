package com.example.service

import java.util.Locale

/**
 * Master Voice Command Parsing Engine & Mappings
 */

enum class MyanmarCommandType {
    TAP, LONG_PRESS, OPEN_APP, CLOSE_APP, SCROLL, BACK, HOME, RECENTS,
    NOTIFICATIONS, QUICK_SETTINGS, LOCK_SCREEN, DICTATE,
    SHOW_GRID, SHOW_LABELS, COPY, PASTE, DELETE, SEARCH,
    VOLUME_UP, VOLUME_DOWN,
    PLAY_MUSIC, PAUSE_MUSIC, STOP_MUSIC, CALL_CONTACT,
    READ_SCREEN,
    SELECT_TEXT, CUSTOM, CUSTOM_SHORTCUT, WAKE_WORD, UNKNOWN
}

enum class ServiceState {
    STOPPED, SLEEPING, LISTENING
}

enum class ScrollDirection { UP, DOWN, LEFT, RIGHT }

/**
 * တစ်ကြောင်းစီ voice input ကို parse လုပ်ပြီးရလာတဲ့ ရလဒ်။
 * isWake / isStop ကို service ထဲက state machine (wake-word gating) အတွက် သီးသန့်ခွဲထားသည်။
 */
data class ParsedCommand(
    val type: MyanmarCommandType? = null,
    val appId: String? = null,
    val direction: ScrollDirection? = null,
    val isWake: Boolean = false,
    val isStop: Boolean = false,
    val raw: String = ""
)

object MyanmarCommands {

    const val CORE_WAKE_WORD = "ကျော်ကြီး"
    const val WAKE_WORD_ALT = "kyaw gyi"
    const val WAKE_WORD_SPACE = "ကျော် ကြီး"

    val WAKE_WORDS = listOf(
        CORE_WAKE_WORD, WAKE_WORD_ALT, WAKE_WORD_SPACE,
        "အသံနဲ့ထိန်းချုပ်", "အသံဖြင့်", "ဟေး လူကြီး", "နိုးပါ", "စတင်ပါ"
    )

    val STOP_WORDS = listOf(
        "ရပ်ပါ",
        "နားထောင်ရပ်",
        "အိပ်ပါ",
        "ပိတ်ပါ",
        "ငြိမ်းပါ"
    )

    val COMMAND_PATTERNS = mapOf(
        MyanmarCommandType.TAP to listOf("နှိပ်", "တို့", "ရွေး", "နှိပ်ပါ", "တို့ပါ", "ရွေးပါ"),
        MyanmarCommandType.LONG_PRESS to listOf("ဖိကိုင်", "ဖိထား", "ဖိကိုင်ပါ", "ဖိထားပါ"),
        MyanmarCommandType.OPEN_APP to listOf("ဖွင့်", "ဖွင့်ပါ", "ဖွင့်ပေး", "စတင်", "စတင်ပါ", "ဖွင့်လိုက်", "ဖွင့်ပေးပါ"),
        MyanmarCommandType.CLOSE_APP to listOf("ပိတ်", "ပိတ်ပါ", "ပိတ်ပေး", "ပိတ်လိုက်", "ပိတ်ပေးပါ", "ထွက်", "ထွက်ပါ", "ရပ်ပစ်", "ရပ်နား"),
        MyanmarCommandType.SCROLL to listOf("ရွှေ့", "လှိမ့်", "ရွှေ့ပါ", "လှိမ့်ပါ", "အပေါ်ရွှေ့", "အောက်ရွှေ့", "ဘယ်ရွှေ့", "ညာရွှေ့"),
        MyanmarCommandType.BACK to listOf("နောက်ပြန်", "ပြန်", "နောက်ပြန်သွား", "ပြန်သွား", "နောက်ကို", "နောက်သို့"),
        MyanmarCommandType.HOME to listOf("ပင်မ", "ပင်မစာမျက်နှာ", "ပင်မသို့", "ပင်မသို့သွား", "ဟုမ်း", "အိမ်"),
        MyanmarCommandType.RECENTS to listOf("မကြာခင်က", "မကြာမီက", "အက်ပ်များ", "မကြာခင်အက်ပ်များ"),
        MyanmarCommandType.NOTIFICATIONS to listOf("အသိပေးချက်", "အသိပေးချက်များ", "နိုတီ", "အကြောင်းကြား"),
        MyanmarCommandType.QUICK_SETTINGS to listOf("အမြန်ဆက်တင်", "ဆက်တင်", "ဆက်တင်များ"),
        MyanmarCommandType.LOCK_SCREEN to listOf("သော့ခတ်", "သော့ပိတ်", "လော့ခ်", "သော့ခတ်ပါ"),
        MyanmarCommandType.DICTATE to listOf("ရိုက်", "ရိုက်ပါ", "ရိုက်ပေး", "ထည့်", "ထည့်ပါ", "စာရိုက်", "စာရိုက်ပါ"),
        MyanmarCommandType.SHOW_GRID to listOf("ဇယားပြ", "ဇယားပြပါ", "ဂရစ်ပြ", "ကွက်လပ်ပြ"),
        MyanmarCommandType.SHOW_LABELS to listOf("နံပါတ်ပြ", "နံပါတ်များပြ", "အမှတ်အသားပြ", "နံပါတ်ပြပါ", "နံပါတ်များပြပါ"),
        MyanmarCommandType.COPY to listOf("ကူး", "ကူးယူ", "ကူးပါ", "ကူးယူပါ", "ကော်ပီ"),
        MyanmarCommandType.PASTE to listOf("ကပ်", "ကပ်ထည့်", "ကပ်ပါ", "ကပ်ထည့်ပါ", "ပေ့စ်"),
        MyanmarCommandType.DELETE to listOf("ဖျက်", "ဖျက်ပါ", "ဖျက်ပစ်", "ဖျက်လိုက်"),
        MyanmarCommandType.SEARCH to listOf("ရှာ", "ရှာပါ", "ရှာဖွေ", "ရှာဖွေပါ"),
        MyanmarCommandType.VOLUME_UP to listOf("အသံတိုး", "အသံတိုးပါ", "အသံကြီးပါ"),
        MyanmarCommandType.VOLUME_DOWN to listOf("အသံလျှော့", "အသံလျှော့ပါ", "အသံသေးပါ"),
        MyanmarCommandType.PLAY_MUSIC to listOf("သီချင်းဖွင့်", "သီချင်းဖွင့်ပါ", "သီချင်းဖွင့်ပေး", "သီချင်းဖွင့်ပေးပါ", "သီချင်းဖွင့်လိုက်", "mp3ဖွင့်", "mp3ဖွင့်ပါ", "mp3 ဖွင့်", "mp3 ဖွင့်ပါ", "play music", "play song"),
        MyanmarCommandType.PAUSE_MUSIC to listOf("သီချင်းခဏရပ်", "သီချင်းခေတ္တရပ်", "သီချင်းရပ်", "သီချင်းရပ်ပါ", "pause music"),
        MyanmarCommandType.STOP_MUSIC to listOf("သီချင်းပိတ်", "သီချင်းပိတ်ပါ", "သီချင်းပိတ်ပေး", "stop music"),
        MyanmarCommandType.CALL_CONTACT to listOf("ဖုန်းခေါ်", "ဖုန်းခေါ်ပါ", "ဖုန်းခေါ်ပေး", "ဖုန်းခေါ်ပေးပါ", "ဖုန်းခေါ်မယ်", "ဖုန်းဆက်", "ဖုန်းဆက်ပါ", "call", "dial"),
        MyanmarCommandType.READ_SCREEN to listOf(
            "မျက်နှာပြင်ပေါ်ရှိ စာများကို ဖတ်ပြပါ",
            "မျက်နှာပြင်ပေါ်ရှိ စာများကို ဖတ်ပြ",
            "မျက်နှာပြင်ပေါ်ရှိ စာများ ဖတ်ပြပါ",
            "မျက်နှာပြင်ပေါ်က စာတွေကို ဖတ်ပြပါ",
            "မျက်နှာပြင်ပေါ်က စာတွေ ဖတ်ပြပါ",
            "မျက်နှာပြင်ပေါ်က စာဖတ်ပြပါ",
            "မျက်နှာပြင်ပေါ်ရှိ စာဖတ်ပြပါ",
            "မျက်နှာပြင်ပေါ်ရှိ စာဖတ်ပြ",
            "မျက်နှာပြင် ဖတ်ပြပါ",
            "မျက်နှာပြင်ဖတ်ပြပါ",
            "မျက်နှာပြင်ပေါ်က စာဖတ်",
            "စာဖတ်ပြပါ",
            "screen ပေါ်က စာဖတ်ပြပါ",
            "screen ဖတ်ပြပါ",
            "read screen",
            "read screen text"
        )
    )

    val DIRECTION_WORDS = mapOf(
        "အပေါ်" to ScrollDirection.UP,
        "အပေါ်ကို" to ScrollDirection.UP,
        "အပေါ်သို့" to ScrollDirection.UP,
        "အောက်" to ScrollDirection.DOWN,
        "အောက်ကို" to ScrollDirection.DOWN,
        "အောက်သို့" to ScrollDirection.DOWN,
        "ဘယ်" to ScrollDirection.LEFT,
        "ဘယ်ကို" to ScrollDirection.LEFT,
        "ဘယ်သို့" to ScrollDirection.LEFT,
        "ညာ" to ScrollDirection.RIGHT,
        "ညာကို" to ScrollDirection.RIGHT,
        "ညာသို့" to ScrollDirection.RIGHT
    )

    val APP_NAME_MAPPINGS: Map<String, String> = mapOf(
        "ဖေ့စ်ဘွတ်" to "facebook", "ဖေ့စ်ဘုတ်" to "facebook", "ဖေ့ဘုတ်" to "facebook", "facebook" to "facebook",
        "တယ်လီဂရမ်" to "telegram", "တယ်လီ" to "telegram", "telegram" to "telegram",
        "မက်ဆင်ဂျာ" to "messenger", "messenger" to "messenger",
        "တစ်တော့" to "tiktok", "tiktok" to "tiktok",
        "ဗိုင်ဘာ" to "viber", "ဝိုင်ဘာ" to "viber", "viber" to "viber",
        "ခရုမ်း" to "chrome", "ကရုမ်း" to "chrome", "chrome" to "chrome",
        "ဂျီမေးလ်" to "gmail", "ဂျီမေး" to "gmail", "gmail" to "gmail",
        "ယူကျု" to "youtube", "ယူချု" to "youtube", "youtube" to "youtube",
        "မြေပုံ" to "maps", "ဂူဂဲမြေပုံ" to "maps", "maps" to "maps",
        "ဝေဝေး" to "wavemoney", "ဝေး" to "wavemoney",
        "ကေဘီဇဲ့ပေး" to "kbzpay", "ကေဘီဇဲ့" to "kbzpay",
        "စီဘီ" to "cbpay",
        "ကင်မရာ" to "camera", "camera" to "camera",
        "ဓာတ်ပုံ" to "photos", "ဓာတ်ပုံများ" to "photos", "photos" to "photos",
        "နာရီ" to "clock", "အချိန်" to "clock", "clock" to "clock",
        "ဂီတ" to "music", "တေးဂီတ" to "music", "music" to "music",
        "ဖုန်း" to "phone", "ဖုန်းခေါ်" to "phone", "phone" to "phone",
        "စာတို" to "messaging", "မက်ဆေ့" to "messaging", "messaging" to "messaging",
        "ဆက်တင်" to "settings", "ဆက်တင်များ" to "settings", "settings" to "settings",
        "ပြက္ခဒိန်" to "calendar", "ကယ်လ်စင်ဒါ" to "calendar", "calendar" to "calendar"
    )

    val APP_PACKAGES: Map<String, String> = mapOf(
        "facebook" to "com.facebook.katana",
        "telegram" to "org.telegram.messenger",
        "messenger" to "com.facebook.orca",
        "tiktok" to "com.zhiliaoapp.musically",
        "viber" to "com.viber.voip",
        "chrome" to "com.android.chrome",
        "gmail" to "com.google.android.gm",
        "youtube" to "com.google.android.youtube",
        "maps" to "com.google.android.apps.maps",
        "camera" to "com.android.camera",
        "photos" to "com.google.android.apps.photos",
        "clock" to "com.google.android.deskclock",
        "music" to "com.google.android.apps.youtube.music",
        "phone" to "com.google.android.dialer",
        "messaging" to "com.google.android.apps.messaging",
        "settings" to "com.android.settings",
        "calendar" to "com.google.android.calendar",
        "wavemoney" to "com.wavemoney.wavepay",
        "kbzpay" to "com.kbzbank.kpaycustomer",
        "cbpay" to "com.cbbank.cbpay"
    )

    val NUMBER_WORDS = mapOf(
        "သုည" to 0, "တစ်" to 1, "နှစ်" to 2, "သုံး" to 3, "လေး" to 4,
        "ငါး" to 5, "ခြောက်" to 6, "ခုနစ်" to 7, "ရှစ်" to 8, "ကိုး" to 9,
        "ဆယ်" to 10, "တစ်ဆယ့်တစ်" to 11, "တစ်ဆယ့်နှစ်" to 12, "တစ်ဆယ့်သုံး" to 13,
        "တစ်ဆယ့်လေး" to 14, "တစ်ဆယ့်ငါး" to 15, "နှစ်ဆယ်" to 20, "သုံးဆယ်" to 30,
        "လေးဆယ်" to 40, "ငါးဆယ်" to 50
    )

    /**
     * Recognized text တစ်ခုကို ParsedCommand အဖြစ် ဖြေရှင်းပေးသည်။
     * - STOP word ပါရင် isStop=true ချက်ချင်း ပြန်ပေးသည် (wake word ရှာစရာ မလိုတော့ပါ)
     * - WAKE word ပါရင် ဖြုတ်ပြီး ကျန်စာသားကို command အဖြစ် ဆက်ခွဲသည်
     *   (တစ်ကြောင်းထဲ "ကျော်ကြီး Facebook ဖွင့်ပါ" ပြောလည်း အလုပ်လုပ်ပါမည်)
     * - Wake word မပါဘဲ command word ပဲပါရင်လည်း အသိအမှတ်ပြုသည် (VoiceForegroundService က
     *   ACTIVE state ထဲမှာ ရှိမှသာ ဒီလို command ကို အလုပ်လုပ်ခွင့်ပြုမည်)
     */
    fun parse(text: String): ParsedCommand? {
        val stripped = text.trim()
        if (stripped.isEmpty()) return null

        val hasWake = WAKE_WORDS.any { stripped.contains(it) }
        val remainder = if (hasWake) {
            WAKE_WORDS.fold(stripped) { acc, w -> acc.replace(w, "") }.trim()
        } else stripped

        val appNameHit = APP_NAME_MAPPINGS.entries.firstOrNull { remainder.contains(it.key) }
        val appId = appNameHit?.value

        // Check for CALL_CONTACT explicitly (e.g., "မောင်မောင်ကို ဖုန်းခေါ်ပေးပါ", "အမေ့ဆီ ဖုန်းဆက်", "Call Mom")
        val isCallWord = listOf("ဖုန်းခေါ်", "ဖုန်းခေါ်ပါ", "ဖုန်းခေါ်ပေး", "ဖုန်းခေါ်ပေးပါ", "ဖုန်းခေါ်မယ်", "ဖုန်းဆက်", "ဖုန်းဆက်ပါ", "call", "dial").any { remainder.contains(it) }
        if (isCallWord) {
            return ParsedCommand(
                type = MyanmarCommandType.CALL_CONTACT,
                appId = null,
                isWake = hasWake,
                raw = remainder
            )
        }

        // Check for PLAY_MUSIC explicitly (e.g., "မာယာ သီချင်းဖွင့်", "Mp3 ဖိုင်ထဲက မာယာသီချင်းကိုဖွင့်ပေးပါ", "သီချင်းဖွင့်ပါ")
        val isPlayMusicWord = listOf("သီချင်းဖွင့်", "သီချင်းဖွင့်ပါ", "သီချင်းဖွင့်ပေး", "သီချင်းဖွင့်ပေးပါ", "သီချင်းဖွင့်လိုက်", "mp3ဖွင့်", "mp3 ဖွင့်", "play music", "play song").any { remainder.contains(it) } ||
            (remainder.contains("သီချင်း") && (remainder.contains("ဖွင့်") || remainder.contains("play")))
        if (isPlayMusicWord) {
            return ParsedCommand(
                type = MyanmarCommandType.PLAY_MUSIC,
                appId = null,
                isWake = hasWake,
                raw = remainder
            )
        }

        // Check for PAUSE_MUSIC or STOP_MUSIC explicitly
        if (listOf("သီချင်းခဏရပ်", "သီချင်းခေတ္တရပ်", "သီချင်းရပ်", "သီချင်းရပ်ပါ", "pause music").any { remainder.contains(it) }) {
            return ParsedCommand(type = MyanmarCommandType.PAUSE_MUSIC, isWake = hasWake, raw = remainder)
        }
        if (listOf("သီချင်းပိတ်", "သီချင်းပိတ်ပါ", "သီချင်းပိတ်ပေး", "stop music").any { remainder.contains(it) }) {
            return ParsedCommand(type = MyanmarCommandType.STOP_MUSIC, isWake = hasWake, raw = remainder)
        }

        // Check for CLOSE_APP explicitly (e.g., "Facebook ပိတ်ပါ", "ယူကျုပိတ်", "အက်ပ်ပိတ်")
        val isCloseAppWord = listOf("ပိတ်", "ပိတ်ပါ", "ပိတ်ပေး", "ပိတ်လိုက်", "ပိတ်ပေးပါ", "ထွက်", "ထွက်ပါ", "ရပ်ပစ်").any { remainder.contains(it) }
        if (isCloseAppWord && (appId != null || remainder.contains("အက်ပ်") || remainder.contains("app"))) {
            return ParsedCommand(
                type = MyanmarCommandType.CLOSE_APP,
                appId = appId,
                isWake = hasWake,
                raw = remainder
            )
        }

        // Check for READ_SCREEN explicitly (e.g., "မျက်နှာပြင်ပေါ်ရှိ စာများကို ဖတ်ပြပါ", "မျက်နှာပြင်ပေါ်က စာတွေ ဖတ်ပြပါ", "screen ပေါ်က စာဖတ်ပြပါ")
        val isReadScreenWord = listOf(
            "မျက်နှာပြင်ပေါ်ရှိ စာများကို ဖတ်ပြ",
            "မျက်နှာပြင်ပေါ်ရှိ စာများ ဖတ်ပြ",
            "မျက်နှာပြင်ပေါ်က စာတွေကို ဖတ်ပြ",
            "မျက်နှာပြင်ပေါ်က စာတွေ ဖတ်ပြ",
            "မျက်နှာပြင်ပေါ်က စာဖတ်ပြ",
            "မျက်နှာပြင်ပေါ်ရှိ စာဖတ်ပြ",
            "မျက်နှာပြင် ဖတ်ပြ",
            "မျက်နှာပြင်ဖတ်ပြ",
            "စာဖတ်ပြ",
            "screen ပေါ်က စာဖတ်ပြ",
            "read screen"
        ).any { remainder.contains(it) || stripped.contains(it) } || (
            (remainder.contains("မျက်နှာပြင်") || remainder.contains("screen")) &&
            (remainder.contains("ဖတ်") || remainder.contains("read"))
        )
        if (isReadScreenWord) {
            return ParsedCommand(
                type = MyanmarCommandType.READ_SCREEN,
                appId = null,
                isWake = hasWake,
                raw = remainder
            )
        }

        if (STOP_WORDS.any { stripped.contains(it) } && appId == null) {
            return ParsedCommand(isStop = true, raw = stripped)
        }

        var type: MyanmarCommandType? = null
        for ((cmdType, words) in COMMAND_PATTERNS) {
            if (words.any { remainder.contains(it) }) {
                type = cmdType
                break
            }
        }

        val direction = DIRECTION_WORDS.entries.firstOrNull { remainder.contains(it.key) }?.value

        if (type == null && appId == null && !hasWake) return null

        return ParsedCommand(
            type = type,
            appId = appId,
            direction = direction,
            isWake = hasWake,
            raw = remainder
        )
    }

    /**
     * Helper for legacy callers expecting Pair<MyanmarCommandType, String>
     */
    fun parseCommand(
        text: String,
        customShortcuts: List<com.example.data.CustomShortcutEntity>? = null,
        customWakeWord: String? = null
    ): Pair<MyanmarCommandType, String> {
        val parsed = parse(text)
        if (parsed != null) {
            if (parsed.isStop) {
                return Pair(MyanmarCommandType.LOCK_SCREEN, "STOP")
            }
            if (parsed.isWake && parsed.type == null && parsed.appId == null) {
                return Pair(MyanmarCommandType.WAKE_WORD, "ကျော်ကြီး အသင့်ရှိပါသည်")
            }
            if (parsed.type == MyanmarCommandType.CLOSE_APP) {
                val appKey = parsed.appId ?: ""
                val targetQuery = APP_NAME_MAPPINGS[appKey] ?: appKey
                return Pair(MyanmarCommandType.CLOSE_APP, targetQuery)
            }
            if (parsed.type == MyanmarCommandType.OPEN_APP || parsed.appId != null) {
                val appKey = parsed.appId ?: ""
                val targetQuery = APP_NAME_MAPPINGS[appKey] ?: appKey
                return Pair(MyanmarCommandType.OPEN_APP, targetQuery)
            }
            if (parsed.type != null) {
                val payload = parsed.direction?.name ?: parsed.appId ?: parsed.raw
                return Pair(parsed.type, payload)
            }
        }

        val normalizedText = com.example.engine.BurmesePhoneticNormalizer.normalizeBurmeseText(text)
        // Check custom shortcuts
        if (!customShortcuts.isNullOrEmpty()) {
            for (shortcut in customShortcuts) {
                if (shortcut.isEnabled) {
                    val p = com.example.engine.BurmesePhoneticNormalizer.normalizeBurmeseText(shortcut.phrase)
                    if (com.example.engine.BurmesePhoneticNormalizer.isFuzzyMatch(normalizedText, p, 0.65f)) {
                        return Pair(MyanmarCommandType.CUSTOM, shortcut.targetPackageOrAction)
                    }
                }
            }
        }

        return Pair(MyanmarCommandType.UNKNOWN, text)
    }
}
