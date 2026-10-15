package com.example.repository

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.Esp32StatusResponse
import com.example.model.IrButton
import com.example.model.IrCode
import com.example.model.KennedeIrSchema
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class StandardButton(val key: String, val displayName: String, val burmeseLabel: String) {
  POWER("power", "POWER", "ပါဝါ (ဖွင့်/ပိတ်)"),
  LIGHT("light", "LIGHT", "မီးရောင် (အဖွင့်/အပိတ်)"),
  SPEED("speed", "SPEED", "လေအရှိန် (အဆင့်ဆင့်)"),
  OSCILLATION("oscillation", "OSCILLATION", "ဘယ်/ညာ လှည့်"),
  CENTER("center", "CENTER", "အလယ်ခလုတ်")
}

data class IrUiState(
  val host: String = "192.168.1.150",
  val isConnected: Boolean = false,
  val isCheckingStatus: Boolean = false,
  val statusResponse: Esp32StatusResponse? = null,
  val buttons: List<IrButton> = defaultButtons(),
  val activeLearningButton: String? = null,
  val isLearning: Boolean = false,
  val statusMessage: String = "ESP32 ချိတ်ဆက်ရန် အသင့်ရှိပါသည်",
  val errorMessage: String? = null,
  val lastTransmittedButton: String? = null,
  val rawTimingReport: String = "",
  val logs: List<String> = emptyList()
) {
  companion object {
    fun defaultButtons(): List<IrButton> = StandardButton.values().map { btn ->
      IrButton(
        key = btn.key,
        name = btn.displayName,
        burmeseLabel = btn.burmeseLabel,
        code = IrCode(captured = false)
      )
    }
  }
}

class IrViewModel(
  private val repository: IrRepository = IrRepositoryImpl()
) : ViewModel() {

  private val _uiState = MutableStateFlow(IrUiState(host = repository.getBaseUrl()))
  val uiState: StateFlow<IrUiState> = _uiState.asStateFlow()

  init {
    refreshStatus()
  }

  fun updateHost(newHost: String) {
    repository.updateBaseUrl(newHost)
    _uiState.value = _uiState.value.copy(
      host = repository.getBaseUrl(),
      statusMessage = "ESP32 Host: ${repository.getBaseUrl()}"
    )
    refreshStatus()
  }

  fun refreshStatus() {
    viewModelScope.launch {
      _uiState.value = _uiState.value.copy(isCheckingStatus = true, errorMessage = null)
      val result = repository.fetchStatus()
      if (result.isSuccess) {
        val status = result.getOrThrow()
        _uiState.value = _uiState.value.copy(
          isConnected = status.esp32,
          isCheckingStatus = false,
          statusResponse = status,
          isLearning = status.learning,
          activeLearningButton = status.learningTarget,
          statusMessage = if (status.esp32) "ESP32 ချိတ်ဆက်မှု အောင်မြင်ပါသည် (${status.ip ?: _uiState.value.host})" else "ESP32 Offline ဖြစ်နေပါသည်"
        )
        fetchCodes()
      } else {
        _uiState.value = _uiState.value.copy(
          isConnected = false,
          isCheckingStatus = false,
          errorMessage = "ESP32 ချိတ်ဆက်၍မရပါ: ${result.exceptionOrNull()?.message}",
          statusMessage = "ESP32 Offline (Wi-Fi စစ်ဆေးပါ)"
        )
      }
    }
  }

  fun fetchCodes() {
    viewModelScope.launch {
      val result = repository.fetchCodes()
      if (result.isSuccess) {
        val schema = result.getOrThrow()
        updateButtonsFromSchema(schema)
      }
    }
  }

  private fun updateButtonsFromSchema(schema: KennedeIrSchema) {
    val currentButtons = _uiState.value.buttons.toMutableList()
    val updatedButtons = currentButtons.map { btn ->
      val code = schema.buttons[btn.key] ?: IrCode(captured = false)
      btn.copy(code = code)
    }
    _uiState.value = _uiState.value.copy(buttons = updatedButtons)
  }

  fun startLearning(buttonKey: String) {
    viewModelScope.launch {
      _uiState.value = _uiState.value.copy(
        isLearning = true,
        activeLearningButton = buttonKey,
        statusMessage = "ဖမ်းယူနေပါသည်... မူရင်းရီမုဒ်မှ $buttonKey ခလုတ်ကို နှိပ်ပါ",
        errorMessage = null
      )
      addLog("START_LEARN: $buttonKey")

      val result = repository.startLearning(buttonKey)
      if (result.isSuccess) {
        pollLearningOutcome(buttonKey)
      } else {
        _uiState.value = _uiState.value.copy(
          isLearning = false,
          activeLearningButton = null,
          errorMessage = "ဖမ်းယူမှု စတင်၍မရပါ: ${result.exceptionOrNull()?.message}"
        )
        addLog("LEARN_FAILED: ${result.exceptionOrNull()?.message}")
      }
    }
  }

  private fun pollLearningOutcome(buttonKey: String) {
    viewModelScope.launch {
      var pollCount = 0
      while (_uiState.value.isLearning && pollCount < 25) {
        delay(1500)
        pollCount++
        val statusRes = repository.fetchStatus()
        if (statusRes.isSuccess) {
          val status = statusRes.getOrThrow()
          if (!status.learning) {
            _uiState.value = _uiState.value.copy(
              isLearning = false,
              activeLearningButton = null
            )
            val codesRes = repository.fetchCodes()
            if (codesRes.isSuccess) {
              val schema = codesRes.getOrThrow()
              updateButtonsFromSchema(schema)
              val code = schema.buttons[buttonKey]
              if (code != null && code.captured) {
                val successMsg = "အောင်မြင်ပါသည်! $buttonKey ကုဒ် ဖမ်းယူပြီးပါပြီ (Protocol: ${code.protocol ?: "UNKNOWN"})"
                _uiState.value = _uiState.value.copy(statusMessage = successMsg)
                addLog("LEARN_SUCCESS: $buttonKey -> Protocol=${code.protocol}, Cmd=${code.command}")
              } else {
                _uiState.value = _uiState.value.copy(
                  errorMessage = "ကုဒ်ဖမ်းယူမှု မအောင်မြင်ပါ (No signal captured)"
                )
                addLog("LEARN_TIMEOUT_OR_EMPTY: $buttonKey")
              }
            }
            break
          }
        }
      }

      if (_uiState.value.isLearning) {
        _uiState.value = _uiState.value.copy(
          isLearning = false,
          activeLearningButton = null,
          errorMessage = "အချိန်ပြည့်သွားပါပြီ (Timeout 35s)။ ထပ်မံကြိုးစားပါ။"
        )
        repository.stopLearning()
        addLog("LEARN_TIMEOUT: $buttonKey")
      }
    }
  }

  fun stopLearning() {
    viewModelScope.launch {
      repository.stopLearning()
      _uiState.value = _uiState.value.copy(
        isLearning = false,
        activeLearningButton = null,
        statusMessage = "ဖမ်းယူမှု ရပ်တန့်လိုက်ပါပြီ"
      )
      addLog("STOP_LEARN_USER_CANCEL")
    }
  }

  fun transmitButton(buttonKey: String) {
    viewModelScope.launch {
      val btn = _uiState.value.buttons.find { it.key == buttonKey }
      if (btn == null || !btn.code.captured) {
        _uiState.value = _uiState.value.copy(
          errorMessage = "IR CODE NOT LEARNED: $buttonKey အတွက် မဖမ်းယူရသေးပါ"
        )
        addLog("SEND_ABORTED: $buttonKey not learned yet")
        return@launch
      }

      _uiState.value = _uiState.value.copy(
        statusMessage = "ထုတ်လွှင့်နေပါသည်: $buttonKey...",
        errorMessage = null,
        lastTransmittedButton = buttonKey
      )

      val result = repository.transmitButton(buttonKey)
      if (result.isSuccess) {
        val resp = result.getOrThrow()
        _uiState.value = _uiState.value.copy(
          statusMessage = "ထုတ်လွှင့်မှု အောင်မြင်ပါသည်: $buttonKey (${resp.message ?: "OK"})"
        )
        addLog("SEND_SUCCESS: $buttonKey -> ${resp.message}")
      } else {
        _uiState.value = _uiState.value.copy(
          errorMessage = "ထုတ်လွှင့်မှု မအောင်မြင်ပါ: ${result.exceptionOrNull()?.message}"
        )
        addLog("SEND_ERROR: $buttonKey -> ${result.exceptionOrNull()?.message}")
      }
    }
  }

  fun fetchRawReport() {
    viewModelScope.launch {
      val res = repository.fetchRawReport()
      if (res.isSuccess) {
        _uiState.value = _uiState.value.copy(rawTimingReport = res.getOrThrow())
      }
    }
  }

  fun resetAllCodes() {
    viewModelScope.launch {
      val res = repository.resetAllCodes()
      if (res.isSuccess) {
        _uiState.value = _uiState.value.copy(
          buttons = IrUiState.defaultButtons(),
          statusMessage = "ကုဒ်များအားလုံးကို ရှင်းလင်းပြီးပါပြီ"
        )
        addLog("FACTORY_RESET_CODES")
      }
    }
  }

  fun importJson(json: String) {
    viewModelScope.launch {
      val res = repository.importCodesJson(json)
      if (res.isSuccess) {
        fetchCodes()
        _uiState.value = _uiState.value.copy(statusMessage = "JSON Backup သွင်းယူပြီးပါပြီ")
        addLog("IMPORT_JSON_SUCCESS")
      } else {
        _uiState.value = _uiState.value.copy(errorMessage = "Import မအောင်မြင်ပါ: ${res.exceptionOrNull()?.message}")
      }
    }
  }

  private fun addLog(message: String) {
    val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
    val entry = "[$time] $message"
    _uiState.value = _uiState.value.copy(logs = listOf(entry) + _uiState.value.logs.take(30))
  }
}
