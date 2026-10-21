package com.example.semantic.dictionary

import java.util.Locale

/**
 * Myanmar App Dictionary
 * Comprehensive dictionary of Myanmar app names, loanwords, phonetic spellings,
 * canonical target identifiers, and Android package names.
 */
object MyanmarAppDictionary {

    data class AppMetadata(
        val canonicalName: String,
        val displayNameMM: String,
        val defaultPackageName: String,
        val aliases: List<String>,
        val isSystemUtility: Boolean = false
    )

    private val APP_DATABASE = listOf(
        AppMetadata(
            canonicalName = "CAMERA",
            displayNameMM = "ကင်မရာ",
            defaultPackageName = "com.google.android.GoogleCamera",
            aliases = listOf(
                "camera", "ကင်မရာ", "ကင်မရာလေး", "ကင်မရာထဲ", "ဓာတ်ပုံရိုက်",
                "ဓါတ်ပုံရိုက်", "ဖုန်းကင်မရာ", "cam"
            ),
            isSystemUtility = true
        ),
        AppMetadata(
            canonicalName = "PHOTOS",
            displayNameMM = "ဓာတ်ပုံများ",
            defaultPackageName = "com.google.android.apps.photos",
            aliases = listOf(
                "photos", "gallery", "album", "ဂယ်လာရီ", "ဓာတ်ပုံ", "ဓာတ်ပုံများ",
                "ဓါတ်ပုံ", "ဓါတ်ပုံများ", "ပုံတွေ", "photo"
            ),
            isSystemUtility = true
        ),
        AppMetadata(
            canonicalName = "YOUTUBE",
            displayNameMM = "YouTube",
            defaultPackageName = "com.google.android.youtube",
            aliases = listOf(
                "youtube", "ယူကျူ", "ယူကျု", "ယူးကျူ", "ယူးကျု", "ယူကျူ့ဗ်", "yt"
            )
        ),
        AppMetadata(
            canonicalName = "FACEBOOK",
            displayNameMM = "Facebook",
            defaultPackageName = "com.facebook.katana",
            aliases = listOf(
                "facebook", "fb", "ဖေ့ဘုတ်", "ဖေ့ဘွတ်", "ဖေ့စ်ဘွတ်", "ဖေ့ဘုတ်ခ်", "ဖေ့စ်ဘုတ်"
            )
        ),
        AppMetadata(
            canonicalName = "TIKTOK",
            displayNameMM = "TikTok",
            defaultPackageName = "com.zhiliaoapp.musically",
            aliases = listOf(
                "tiktok", "တစ်တော့", "တိတ်တောက်", "တစ်တောက်", "တစ်တော့ခ်", "tik tok"
            )
        ),
        AppMetadata(
            canonicalName = "TELEGRAM",
            displayNameMM = "Telegram",
            defaultPackageName = "org.telegram.messenger",
            aliases = listOf(
                "telegram", "တယ်လီဂရမ်", "တဲလီဂရမ်", "tg"
            )
        ),
        AppMetadata(
            canonicalName = "VIBER",
            displayNameMM = "Viber",
            defaultPackageName = "com.viber.voip",
            aliases = listOf(
                "viber", "ဗိုက်ဘာ", "ဗိုင်ဘာ"
            )
        ),
        AppMetadata(
            canonicalName = "MESSENGER",
            displayNameMM = "Messenger",
            defaultPackageName = "com.facebook.orca",
            aliases = listOf(
                "messenger", "မက်ဆင်ဂျာ", "မက်စင်ဂျာ", "msngr"
            )
        ),
        AppMetadata(
            canonicalName = "KBZPAY",
            displayNameMM = "KBZPay",
            defaultPackageName = "com.kbzbank.kpay",
            aliases = listOf(
                "kpay", "kbzpay", "kbz pay", "ကေပီ", "ကေဘီဇက်ပီ", "ကေဘီဇက် pay", "ကေပေး"
            )
        ),
        AppMetadata(
            canonicalName = "WAVEPAY",
            displayNameMM = "WavePay",
            defaultPackageName = "mymoneypay.w提提.wavepay",
            aliases = listOf(
                "wavepay", "wave pay", "wavemoney", "wave money", "ဝေ့မန်းနီး", "ဝေ့မုန်း", "ဝေ့ပီ", "ဝေ့ဖ်ပီ"
            )
        ),
        AppMetadata(
            canonicalName = "GMAIL",
            displayNameMM = "Gmail",
            defaultPackageName = "com.google.android.gm",
            aliases = listOf(
                "gmail", "google mail", "ဂျီမေးလ်", "ဂျီမေး", "အီးမေးလ်", "မေးလ်"
            ),
            isSystemUtility = true
        ),
        AppMetadata(
            canonicalName = "INSTAGRAM",
            displayNameMM = "Instagram",
            defaultPackageName = "com.instagram.android",
            aliases = listOf(
                "instagram", "ig", "အင်စတာဂရမ်", "အင်စတာ"
            )
        ),
        AppMetadata(
            canonicalName = "MAPS",
            displayNameMM = "Google မြေပုံ",
            defaultPackageName = "com.google.android.apps.maps",
            aliases = listOf(
                "maps", "google maps", "မြေပုံ", "လမ်းညွှန်မြေပုံ", "မြေပုံအက်ပ်"
            ),
            isSystemUtility = true
        ),
        AppMetadata(
            canonicalName = "SETTINGS",
            displayNameMM = "စနစ်ဆက်တင်",
            defaultPackageName = "com.android.settings",
            aliases = listOf(
                "settings", "setting", "ဆက်တင်", "စနစ်ဆက်တင်", "ဖုန်းဆက်တင်"
            ),
            isSystemUtility = true
        ),
        AppMetadata(
            canonicalName = "CLOCK",
            displayNameMM = "နာရီနှင့်နှိုးစက်",
            defaultPackageName = "com.google.android.deskclock",
            aliases = listOf(
                "clock", "alarm", "နာရီ", "နှိုးစက်", "အချိန်မှတ်စက်"
            ),
            isSystemUtility = true
        ),
        AppMetadata(
            canonicalName = "MESSAGES",
            displayNameMM = "မက်ဆေ့ခ်ျ",
            defaultPackageName = "com.google.android.apps.messaging",
            aliases = listOf(
                "messages", "messaging", "မက်ဆေ့ခ်ျအက်ပ်", "မက်ဆေ့အက်ပ်", "စာတိုအက်ပ်"
            ),
            isSystemUtility = true
        ),
        AppMetadata(
            canonicalName = "PHONE",
            displayNameMM = "ဖုန်းခေါ်ဆိုရန်",
            defaultPackageName = "com.google.android.dialer",
            aliases = listOf(
                "dialer", "ဖုန်းစာအုပ်", "ကီးပတ်", "ဖုန်းအက်ပ်"
            ),
            isSystemUtility = true
        ),
        AppMetadata(
            canonicalName = "GOOGLE",
            displayNameMM = "Google",
            defaultPackageName = "com.google.android.googlequicksearchbox",
            aliases = listOf(
                "google", "ဂူဂဲလ်", "ဂူဂယ်", "ဂူဂဲ", "ရှာဖွေမှု"
            ),
            isSystemUtility = true
        ),
        AppMetadata(
            canonicalName = "CHROME",
            displayNameMM = "Google Chrome",
            defaultPackageName = "com.android.chrome",
            aliases = listOf(
                "chrome", "google chrome", "browser", "ခရုမ်း", "ဘရောက်ဆာ"
            ),
            isSystemUtility = true
        )
    )

    /**
     * Resolves an app alias or spoken name to canonical app metadata
     */
    fun resolveApp(query: String): AppMetadata? {
        val clean = query.trim().lowercase(Locale.ROOT)
        // Check exact alias matches first
        for (app in APP_DATABASE) {
            for (alias in app.aliases) {
                if (clean == alias.lowercase(Locale.ROOT)) {
                    return app
                }
            }
        }

        // Check substring containment (sorted by alias length descending)
        val sortedApps = APP_DATABASE.flatMap { app -> app.aliases.map { alias -> alias to app } }
            .sortedByDescending { it.first.length }

        for ((alias, app) in sortedApps) {
            if (clean.contains(alias.lowercase(Locale.ROOT))) {
                return app
            }
        }

        return null
    }

    fun getAllApps(): List<AppMetadata> = APP_DATABASE
}
