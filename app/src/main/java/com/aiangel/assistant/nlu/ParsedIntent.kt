package com.aiangel.assistant.nlu

/**
 * All intent types the MyanmarVoiceParser can emit after normalization.
 * DeviceControl covers both IR-based appliances (Kennede Fan) and
 * BLE-based devices (EcoFlow) — the executor is picked downstream
 * by MainViewModel based on which repository has a matching command.
 */
sealed class ParsedIntent {

    data class PlayMusic(val query: String) : ParsedIntent()

    data class DeviceControl(
        val deviceName: String,   // e.g. "Kennede Fan", "EcoFlow"
        val commandLabel: String, // e.g. "POWER", "SPEED_UP", "OSCILLATE"
        val rawUtterance: String  // original normalized text, kept for logging/learning-mode prompts
    ) : ParsedIntent()

    data class AskScholar(val question: String) : ParsedIntent()

    object Unknown : ParsedIntent()
}
