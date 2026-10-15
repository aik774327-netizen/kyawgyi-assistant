package com.aiangel.assistant.viewmodel

import com.aiangel.assistant.hardware.IrResult
import com.aiangel.assistant.hardware.RealDeviceActionExecutor
import com.aiangel.assistant.nlu.ParsedIntent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What the UI/TTS layer should say and do after a device-control attempt. */
sealed class DeviceControlOutcome {
    data class Spoken(val message: String) : DeviceControlOutcome()
    data class NeedsLearning(val deviceName: String, val commandLabel: String, val message: String) :
        DeviceControlOutcome()
}

/**
 * Sits between MainViewModel and RealDeviceActionExecutor. Owns the
 * "did we recognize the command, does it exist yet, did the transmit
 * succeed" decision tree so MainViewModel itself stays a thin orchestrator.
 */
class DeviceControlHandler(
    private val executor: RealDeviceActionExecutor
) {
    private val _lastOutcome = MutableStateFlow<DeviceControlOutcome?>(null)
    val lastOutcome: StateFlow<DeviceControlOutcome?> = _lastOutcome.asStateFlow()

    /**
     * Called by MainViewModel when MyanmarVoiceParser/DeviceCommandRecognizer
     * has produced a DeviceControl intent. Sets lastActiveContext handling
     * is left to the caller (MainViewModel owns that StateFlow already).
     */
    suspend fun handle(intent: ParsedIntent.DeviceControl): DeviceControlOutcome {
        if (!executor.isIrAvailable && intent.deviceName != "EcoFlow") {
            val outcome = DeviceControlOutcome.Spoken(
                "ဒီဖုန်းမှာ IR blaster hardware မရှိလို့ ${intent.deviceName} ကို ထိန်းချုပ်လို့ မရပါ"
            )
            _lastOutcome.value = outcome
            return outcome
        }

        val hasCommand = executor.hasCommand(intent.deviceName, intent.commandLabel)
        if (!hasCommand) {
            val outcome = DeviceControlOutcome.NeedsLearning(
                deviceName = intent.deviceName,
                commandLabel = intent.commandLabel,
                message = "${intent.deviceName} ရဲ့ \"${intent.commandLabel}\" command ကို မသင်ယူရသေးပါ။ " +
                    "မူလ remote ကို အသင့်ထားပြီး Learning Mode ကနေ ပထမဆုံး သင်ကြားပေးပါ"
            )
            _lastOutcome.value = outcome
            return outcome
        }

        val result = executor.sendCommand(intent.deviceName, intent.commandLabel)
        val outcome = when (result) {
            is IrResult.Success ->
                DeviceControlOutcome.Spoken("${intent.deviceName} ကို ${labelToSpokenAction(intent.commandLabel)} ပြီးပါပြီ")
            is IrResult.Failure ->
                DeviceControlOutcome.Spoken("${intent.deviceName} ကို ထိန်းချုပ်လို့ မရပါ — ${result.reason}")
        }
        _lastOutcome.value = outcome
        return outcome
    }

    private fun labelToSpokenAction(commandLabel: String): String = when (commandLabel) {
        "POWER" -> "ဖွင့်/ပိတ်"
        "SPEED_UP" -> "အမြန်နှုန်း မြှင့်"
        "SPEED_DOWN" -> "အမြန်နှုန်း လျှော့"
        "OSCILLATE" -> "လှည့်"
        "TIMER" -> "အချိန်သတ်မှတ်"
        else -> commandLabel
    }
}
