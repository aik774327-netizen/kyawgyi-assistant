package com.kyawgyi.ir.repository

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kyawgyi.ir.db.EventLog
import com.kyawgyi.ir.db.EventLogDao
import com.kyawgyi.ir.models.Esp32StatusResponse
import com.kyawgyi.ir.models.IrButton
import com.kyawgyi.ir.models.IrCode
import com.kyawgyi.ir.models.KennedeIrSchema
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
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
  val failureWarning: String? = null,
  val errorCategory: String? = null,
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

typealias IRViewModel = IrViewModel

class IrViewModel(
  private val repository: IrRepository = IrRepositoryImpl(),
  private val eventLogDao: EventLogDao? = null
) : ViewModel() {

  private val _uiState = MutableStateFlow(IrUiState(host = repository.getBaseUrl()))
  val uiState: StateFlow<IrUiState> = _uiState.asStateFlow()

  val roomEventLogs: StateFlow<List<EventLog>> = eventLogDao?.getAllLogs()
    ?.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    ?: MutableStateFlow(emptyList())

  private val failedActionMemory = mutableMapOf<String, Int>()

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
    val actionKey = "IR_LEARN_$buttonKey"
    val priorFailures = failedActionMemory[actionKey] ?: 0

    viewModelScope.launch {
      // Check if ESP32 is offline (Phone has no IR receiver, so learning on phone is physically impossible)
      if (!_uiState.value.isConnected) {
        val count = priorFailures + 1
        failedActionMemory[actionKey] = count

        val warning = if (count > 1) {
          "⚠️ ယခင်က မအောင်မြင်ခဲ့သော လုပ်ဆောင်ချက် ဖြစ်ပါသည် (လက်တွေ့မကိုက်ညီမှု • ကြိမ်ရေ: $count ကြိမ်)\n" +
            "စမတ်ဖုန်းများတွင် အချက်ပြထုတ်လွှင့်သည့် Emitter သာပါရှိပြီး ပြင်ပရီမုဒ်မှ အချက်ပြဖမ်းယူနိုင်သည့် IR Receiver Sensor မပါရှိပါ။\n" +
            "• မအောင်မြင်ခဲ့သော ဖုန်းဖြင့် တိုက်ရိုက်ဖမ်းယူနည်းကို ထပ်မံအကြံမပြုပါ။\n" +
            "• လက်တွေ့ကျသော နည်းလမ်း: ESP32 (VS1838B Sensor) Wi-Fi Node ကို ချိတ်ဆက်ပါ သို့မဟုတ် စက်ရုံထုတ် မူလကုဒ်များကို ရယူပါ။"
        } else {
          "⚠️ [လက်တွေ့မကိုက်ညီမှု - Real-World Mismatch]\n" +
            "စမတ်ဖုန်းများတွင် IR Emitter သာပါဝင်ပြီး ပြင်ပရီမုဒ်မှ အချက်ပြဖမ်းယူနိုင်သည့် Receiver Sensor မပါရှိပါ (အတုအယောင် ဟန်ပြ သင်ယူမှု လုံးဝမပြုလုပ်ပါ)။\n" +
            "• မအောင်မြင်သော နည်းလမ်း: ဖုန်းမျက်နှာပြင်ဆီသို့ ရီမုဒ်ထိုး၍ တိုက်ရိုက်ဖမ်းယူခြင်း\n" +
            "• အကြံပြုချက်: ESP32 VS1838B IR Receiver Module ကို ချိတ်ဆက်ပါ သို့မဟုတ် စက်ရုံထုတ် Preset Codes ကို ရယူပါ။"
        }

        _uiState.value = _uiState.value.copy(
          isLearning = false,
          activeLearningButton = null,
          errorMessage = "ဖုန်းတွင် IR Receiver ဟာ့ဒ်ဝဲ မပါရှိပါ (ESP32 VS1838B Module လိုအပ်သည်)",
          failureWarning = warning,
          errorCategory = "လက်တွေ့မကိုက်ညီမှု (Real-World Mismatch)"
        )

        recordEvent(
          type = "LEARN_FAILED_REAL_WORLD_MISMATCH",
          buttonKey = buttonKey,
          message = "ဖုန်းတွင် IR Receiver ဟာ့ဒ်ဝဲ မပါရှိပါ (Real-World Mismatch)",
          details = warning,
          isSuccess = false
        )
        addLog("LEARN_FAILED_REAL_WORLD_MISMATCH: $buttonKey (Phone has no IR receiver sensor)")
        return@launch
      }

      // If ESP32 is connected, clear failure warning and proceed with real hardware learning
      failedActionMemory.remove(actionKey)
      _uiState.value = _uiState.value.copy(
        isLearning = true,
        activeLearningButton = buttonKey,
        statusMessage = "ဖမ်းယူနေပါသည်... ESP32 (VS1838B) သို့ မူရင်းရီမုဒ်မှ $buttonKey ခလုတ်ကို ချိန်၍ နှိပ်ပါ",
        errorMessage = null,
        failureWarning = null,
        errorCategory = null
      )
      addLog("START_LEARN: $buttonKey on ESP32 VS1838B")

      val result = repository.startLearning(buttonKey)
      if (result.isSuccess) {
        recordEvent("LEARN_STARTED", buttonKey, "ခလုတ် $buttonKey အတွက် ESP32 အချက်ပြ ဖမ်းယူမှု စတင်ပါသည်", isSuccess = true)
        pollLearningOutcome(buttonKey)
      } else {
        val err = result.exceptionOrNull()?.message ?: "Unknown error"
        _uiState.value = _uiState.value.copy(
          isLearning = false,
          activeLearningButton = null,
          errorMessage = "ဖမ်းယူမှု စတင်၍မရပါ: $err",
          failureWarning = "ESP32 ဆက်သွယ်မှု မအောင်မြင်ပါ (နည်းပညာပိုင်းဆိုင်ရာ ချို့ယွင်းချက်)",
          errorCategory = "နည်းပညာပိုင်းဆိုင်ရာ (Technical Limitations)"
        )
        recordEvent("LEARN_FAILED", buttonKey, "ဖမ်းယူမှု မအောင်မြင်ပါ: $err", details = err, isSuccess = false)
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
                recordEvent("LEARN_SUCCESS", buttonKey, successMsg, details = "Protocol=${code.protocol}, Cmd=${code.command}, Bits=${code.bits}", isSuccess = true)
              } else {
                val failMsg = "ကုဒ်ဖမ်းယူမှု မအောင်မြင်ပါ (No signal captured)"
                _uiState.value = _uiState.value.copy(errorMessage = failMsg)
                recordEvent("LEARN_FAILED", buttonKey, failMsg, details = "Empty signal response", isSuccess = false)
              }
            }
            break
          }
        }
      }

      if (_uiState.value.isLearning) {
        val timeoutMsg = "အချိန်ပြည့်သွားပါပြီ (Timeout 35s)။ ထပ်မံကြိုးစားပါ။"
        _uiState.value = _uiState.value.copy(
          isLearning = false,
          activeLearningButton = null,
          errorMessage = timeoutMsg
        )
        repository.stopLearning()
        recordEvent("LEARN_FAILED", buttonKey, timeoutMsg, details = "Timeout after 35s", isSuccess = false)
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
      recordEvent("LEARN_STOPPED", null, "ဖမ်းယူမှု ရပ်တန့်လိုက်ပါပြီ", isSuccess = true)
    }
  }

  fun transmitButton(buttonKey: String) {
    viewModelScope.launch {
      val btn = _uiState.value.buttons.find { it.key == buttonKey }
      if (btn == null || !btn.code.captured) {
        val warnMsg = "IR CODE NOT LEARNED: $buttonKey အတွက် မဖမ်းယူရသေးပါ"
        _uiState.value = _uiState.value.copy(errorMessage = warnMsg)
        recordEvent("TRANSMIT_FAILED", buttonKey, warnMsg, details = "Button not captured yet", isSuccess = false)
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
        val succMsg = "ထုတ်လွှင့်မှု အောင်မြင်ပါသည်: $buttonKey (${resp.message ?: "OK"})"
        _uiState.value = _uiState.value.copy(statusMessage = succMsg)
        recordEvent("TRANSMIT_SUCCESS", buttonKey, succMsg, details = "Server response: ${resp.message}", isSuccess = true)
      } else {
        val errMsg = "ထုတ်လွှင့်မှု မအောင်မြင်ပါ: ${result.exceptionOrNull()?.message}"
        _uiState.value = _uiState.value.copy(errorMessage = errMsg)
        recordEvent("TRANSMIT_FAILED", buttonKey, errMsg, details = result.exceptionOrNull()?.message, isSuccess = false)
      }
    }
  }

  fun recordEvent(
    type: String,
    buttonKey: String? = null,
    message: String,
    details: String? = null,
    isSuccess: Boolean = true
  ) {
    addLog("$type: $message")
    viewModelScope.launch {
      try {
        eventLogDao?.insertLog(
          EventLog(
            eventType = type,
            buttonKey = buttonKey,
            message = message,
            details = details,
            isSuccess = isSuccess
          )
        )
      } catch (_: Exception) {}
    }
  }

  fun logVoiceCommand(command: String, detectedButton: String?, isSuccess: Boolean) {
    recordEvent(
      type = "VOICE_COMMAND",
      buttonKey = detectedButton,
      message = "အသံအမိန့်: \"$command\"",
      details = if (detectedButton != null) "Detected: $detectedButton" else "No matching intent",
      isSuccess = isSuccess
    )
  }

  fun clearDbLogs() {
    viewModelScope.launch {
      try {
        eventLogDao?.clearAllLogs()
        _uiState.value = _uiState.value.copy(logs = emptyList())
      } catch (_: Exception) {}
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
