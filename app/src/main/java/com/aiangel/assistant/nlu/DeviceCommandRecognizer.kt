package com.aiangel.assistant.nlu

/**
 * Recognizes device-control utterances (fan/appliance commands) after
 * MyanmarDialectAndContextNormalizer has already collapsed dialect
 * variants and slang into a canonical form. This class only does
 * keyword-to-intent mapping — it holds no hardware or DB dependency,
 * so it stays unit-testable in isolation from RealDeviceActionExecutor.
 */
class DeviceCommandRecognizer {

    // Device name -> keywords that identify it in speech.
    // Extend this map as more IR/BLE devices are added to the household.
    private val deviceKeywords: Map<String, List<String>> = mapOf(
        "Kennede Fan" to listOf("ပန်ကာ", "ဖန်ကာ", "fan"),
        "EcoFlow" to listOf("ecoflow", "အီကိုဖလို", "ဘက်ထရီ")
    )

    // Command label -> keywords/phrases that trigger it.
    // Ordered so more specific multi-word phrases are checked before
    // short generic ones (e.g. "အမြန်နှုန်း လျှော့ပါ" before "လျှော့ပါ").
    private val commandKeywords: List<Pair<String, List<String>>> = listOf(
        "SPEED_UP" to listOf("အမြန်နှုန်း မြှင့်ပါ", "အမြန်နှုန်း တိုးပါ", "မြန်အောင်ဖွင့်ပါ", "speed up"),
        "SPEED_DOWN" to listOf("အမြန်နှုန်း လျှော့ပါ", "အမြန်နှုန်း လျော့ချပါ", "နှေးအောင်ဖွင့်ပါ", "slow down"),
        "OSCILLATE" to listOf("လှည့်ပါ", "ဘေးလှည့်ပါ", "oscillate", "swing"),
        "TIMER" to listOf("အချိန်သတ်မှတ်ပါ", "timer"),
        // POWER is a toggle command matching a single physical button on
        // most IR fan remotes, so both "on" and "off" phrasing map to it.
        "POWER" to listOf("ဖွင့်ပါ", "ဖွင့်ပီး", "ပိတ်ပါ", "ပိတ်ပီး", "on ", "off ")
    )

    /**
     * Returns a DeviceControl intent if the utterance names both a known
     * device and a known command, otherwise null so the caller can fall
     * through to the next intent recognizer (music, scholar, etc).
     */
    fun recognize(normalizedUtterance: String): ParsedIntent.DeviceControl? {
        val text = normalizedUtterance.trim()
        if (text.isEmpty()) return null

        val matchedDevice = deviceKeywords.entries
            .firstOrNull { (_, keywords) -> keywords.any { text.contains(it, ignoreCase = true) } }
            ?.key ?: return null

        val matchedCommand = commandKeywords
            .firstOrNull { (_, keywords) -> keywords.any { text.contains(it, ignoreCase = true) } }
            ?.first ?: return null

        return ParsedIntent.DeviceControl(
            deviceName = matchedDevice,
            commandLabel = matchedCommand,
            rawUtterance = text
        )
    }

    /** Exposed for MainViewModel to build "unknown device" error messages. */
    fun knownDeviceNames(): List<String> = deviceKeywords.keys.toList()
}
