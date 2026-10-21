package com.kyawgyi.voice

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Screen domains for contextual hotword biasing.
 */
enum class AppScreenDomain(val descriptionMM: String) {
    HOME("ပင်မ မျက်နှာပြင်"),
    SETTINGS("ဆက်တင်စ်နှင့် စနစ်ထိန်းချုပ်မှု"),
    COMMUNICATION("ဖုန်းခေါ်ဆိုမှုနှင့် မက်ဆေ့ခ်ျ"),
    FINTECH("မိုဘိုင်းလ်ဘဏ်လုပ်ငန်းနှင့် ငွေပေးချေမှု"),
    ENTERTAINMENT("မီဒီယာနှင့် ဖျော်ဖြေရေး")
}

/**
 * ScreenContextHotwordManager (Requirements 10 — Screen-Aware Contextual Hotword Dynamic Biasing)
 *
 * Bridges the user's active UI destination to the underlying STT engine's [HotwordBiasing]:
 * - When in `SETTINGS`: Boosts Wi-Fi, Bluetooth, Hotspot, Display, Volume, and Battery terms.
 * - When in `COMMUNICATION`: Boosts calling, SMS, contacts, and messaging phrases.
 * - When in `FINTECH`: Boosts KBZPay, WavePay, AYA Pay, CB Pay, and transfer terms.
 * - When in `ENTERTAINMENT`: Boosts YouTube, Facebook, TikTok, Spotify, and playback controls.
 *
 * This contextual boosting dramatically reduces Word Error Rate (WER) for domain-specific words
 * when the user is actively viewing those screens.
 */
class ScreenContextHotwordManager(
    val hotwordBiasing: HotwordBiasing = HotwordBiasing()
) {

    companion object {
        private const val TAG = "ScreenContextHotwords"

        val DOMAIN_HOTWORD_PRESETS: Map<AppScreenDomain, List<HotwordEntry>> = mapOf(
            AppScreenDomain.SETTINGS to listOf(
                HotwordEntry("ဝိုင်ဖိုင်", "Wi-Fi", 3.2f, "SETTINGS"),
                HotwordEntry("ဘလူးတုသ်", "Bluetooth", 3.2f, "SETTINGS"),
                HotwordEntry("ဟော့စပေါ့တ်", "Hotspot", 3.0f, "SETTINGS"),
                HotwordEntry("လက်နှိပ်ဓာတ်မီး", "လက်နှိပ်ဓာတ်မီး", 3.0f, "SETTINGS"),
                HotwordEntry("ဆက်တင်စ်", "Settings", 2.8f, "SETTINGS"),
                HotwordEntry("အသံအတိုးအကျယ်", "အသံအတိုးအကျယ်", 2.8f, "SETTINGS"),
                HotwordEntry("ဘက်ထရီ", "ဘက်ထရီ", 2.8f, "SETTINGS")
            ),
            AppScreenDomain.COMMUNICATION to listOf(
                HotwordEntry("ဖုန်းခေါ်ပေးပါ", "ဖုန်းခေါ်ပေးပါ", 3.5f, "CALL"),
                HotwordEntry("ဖုန်းခေါ်ပါ", "ဖုန်းခေါ်ပါ", 3.4f, "CALL"),
                HotwordEntry("မက်ဆေ့ခ်ျပို့ပါ", "မက်ဆေ့ခ်ျပို့ပါ", 3.2f, "SMS"),
                HotwordEntry("အက်စ်အမ်အက်စ်", "SMS", 3.0f, "SMS"),
                HotwordEntry("တယ်လီဂရမ်", "Telegram", 2.8f, "MESSENGER"),
                HotwordEntry("မက်ဆင်ဂျာ", "Messenger", 2.8f, "MESSENGER"),
                HotwordEntry("ဗိုင်ဘာ", "Viber", 2.8f, "MESSENGER")
            ),
            AppScreenDomain.FINTECH to listOf(
                HotwordEntry("ကေဘီဇက်ပေး", "KBZPay", 3.6f, "FINTECH"),
                HotwordEntry("ဝေ့ဗ်ပေး", "WavePay", 3.6f, "FINTECH"),
                HotwordEntry("ဧရာပေး", "AYA Pay", 3.2f, "FINTECH"),
                HotwordEntry("စီဘီပေး", "CB Pay", 3.2f, "FINTECH"),
                HotwordEntry("လက်ကျန်ငွေ", "လက်ကျန်ငွေ", 3.0f, "FINTECH"),
                HotwordEntry("ငွေလွှဲပေးပါ", "ငွေလွှဲပေးပါ", 3.0f, "FINTECH")
            ),
            AppScreenDomain.ENTERTAINMENT to listOf(
                HotwordEntry("ယူကျု့ဘ်", "YouTube", 3.6f, "MEDIA"),
                HotwordEntry("ဖေ့စ်ဘွတ်ခ်", "Facebook", 3.4f, "MEDIA"),
                HotwordEntry("တစ်တော့ခ်", "TikTok", 3.4f, "MEDIA"),
                HotwordEntry("သီချင်းဖွင့်ပါ", "သီချင်းဖွင့်ပါ", 3.2f, "MEDIA"),
                HotwordEntry("ရပ်ပါ", "ရပ်ပါ", 3.0f, "MEDIA")
            ),
            AppScreenDomain.HOME to listOf(
                HotwordEntry("ဟေး ကျော်ကြီး", "ဟေး ကျော်ကြီး", 3.5f, "WAKE_WORD"),
                HotwordEntry("ကိုကျော်ကြီး", "ကိုကျော်ကြီး", 3.2f, "WAKE_WORD"),
                HotwordEntry("ဘာလုပ်ပေးရမလဲ", "ဘာလုပ်ပေးရမလဲ", 2.5f, "ASSISTANT")
            )
        )
    }

    private val _currentDomain = MutableStateFlow(AppScreenDomain.HOME)
    val currentDomain: StateFlow<AppScreenDomain> = _currentDomain.asStateFlow()

    private val _activeBoostedCount = MutableStateFlow(0)
    val activeBoostedCount: StateFlow<Int> = _activeBoostedCount.asStateFlow()

    init {
        // Initial setup for default screen
        onScreenChanged(AppScreenDomain.HOME)
    }

    /**
     * Call when user navigates to a new screen or tab in Jetpack Compose.
     */
    @Synchronized
    fun onScreenChanged(domain: AppScreenDomain) {
        _currentDomain.value = domain
        val presets = DOMAIN_HOTWORD_PRESETS[domain] ?: emptyList()

        for (entry in presets) {
            hotwordBiasing.registerHotword(
                phrase = entry.phrase,
                canonicalReplacement = entry.canonicalReplacement,
                boostScore = entry.boostScore,
                category = entry.category
            )
        }

        val total = hotwordBiasing.getAllEntries().size
        _activeBoostedCount.value = total
        Log.i(TAG, "Switched active hotword domain to [$domain]. Total active hotwords: $total")
    }

    /**
     * Map screen route string (e.g. from Navigation Compose) to [AppScreenDomain].
     */
    fun onRouteChanged(route: String?) {
        val domain = when {
            route.isNullOrBlank() -> AppScreenDomain.HOME
            route.contains("setting", ignoreCase = true) -> AppScreenDomain.SETTINGS
            route.contains("contact", ignoreCase = true) || route.contains("call", ignoreCase = true) -> AppScreenDomain.COMMUNICATION
            route.contains("wallet", ignoreCase = true) || route.contains("pay", ignoreCase = true) -> AppScreenDomain.FINTECH
            route.contains("media", ignoreCase = true) || route.contains("video", ignoreCase = true) -> AppScreenDomain.ENTERTAINMENT
            else -> AppScreenDomain.HOME
        }
        onScreenChanged(domain)
    }
}
