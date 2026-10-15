package com.aiangel.assistant.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiangel.assistant.nlu.ParsedIntent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Button labels the Fan Remote Pad renders, in display order. */
enum class FanCommand(val label: String, val displayText: String) {
    POWER("POWER", "ဖွင့်/ပိတ်"),
    SPEED_UP("SPEED_UP", "အမြန်နှုန်း +"),
    SPEED_DOWN("SPEED_DOWN", "အမြန်နှုန်း -"),
    OSCILLATE("OSCILLATE", "လှည့်ပါ"),
    TIMER("TIMER", "အချိန်သတ်မှတ်")
}

/**
 * UI-facing state for the Kennede Fan Remote Pad dialog. isBusy prevents
 * double-taps from firing overlapping IR transmits (the hardware can only
 * send one pulse train at a time).
 */
data class FanRemoteUiState(
    val isBusy: Boolean = false,
    val statusMessage: String? = null,
    val awaitingImportFor: String? = null // non-null command label when NeedsLearning fires
)

class FanRemoteViewModel(
    private val deviceName: String = "Kennede Fan",
    private val deviceControlHandler: DeviceControlHandler
) : ViewModel() {

    private val _uiState = MutableStateFlow(FanRemoteUiState())
    val uiState: StateFlow<FanRemoteUiState> = _uiState.asStateFlow()

    fun sendCommand(command: FanCommand) {
        if (_uiState.value.isBusy) return // ignore taps while a transmit is in flight

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isBusy = true, statusMessage = null, awaitingImportFor = null)

            val intent = ParsedIntent.DeviceControl(
                deviceName = deviceName,
                commandLabel = command.label,
                rawUtterance = "[UI tap] ${command.displayText}"
            )

            when (val outcome = deviceControlHandler.handle(intent)) {
                is DeviceControlOutcome.Spoken -> {
                    _uiState.value = _uiState.value.copy(isBusy = false, statusMessage = outcome.message)
                }
                is DeviceControlOutcome.NeedsLearning -> {
                    _uiState.value = _uiState.value.copy(
                        isBusy = false,
                        statusMessage = outcome.message,
                        awaitingImportFor = outcome.commandLabel
                    )
                }
            }
        }
    }

    fun dismissStatus() {
        _uiState.value = _uiState.value.copy(statusMessage = null, awaitingImportFor = null)
    }
}
