package com.example.service

import android.content.Context
import android.util.Log
import com.example.database.ErrorCategory
import com.example.models.EcoFlowDeviceState
import com.example.models.EcoFlowEmergencyAutomation
import com.example.models.EcoFlowModel
import com.example.notification.NotificationManagementHelper
import com.example.realworld.RealDeviceActionExecutor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Real-time Automatic Emergency Automation Engine for EcoFlow Power Stations (RIVER & DELTA Series).
 * Monitors battery level, thermal metrics, load wattage, and grid continuity.
 * Directly triggers real protection rules, system notifications, speech warnings, and error-learning records.
 */
class EcoFlowEmergencyEngine(
  private val context: Context,
  private val errorLearningEngine: ErrorLearningEngine,
  private val notificationHelper: NotificationManagementHelper,
  private val realActionExecutor: RealDeviceActionExecutor
) {
  private val scope = CoroutineScope(Dispatchers.Default)

  private val _automationConfig = MutableStateFlow(EcoFlowEmergencyAutomation())
  val automationConfig: StateFlow<EcoFlowEmergencyAutomation> = _automationConfig.asStateFlow()

  private var lastBatteryLevel: Int = -1
  private var lastInputWatts: Int = -1
  private var isEmergencyCutoffActive: Boolean = false

  fun updateConfig(config: EcoFlowEmergencyAutomation) {
    _automationConfig.value = config
  }

  fun setLowBatteryCutoffThreshold(threshold: Int) {
    _automationConfig.value = _automationConfig.value.copy(lowBatteryCutoffThreshold = threshold)
  }

  fun toggleLowBatteryCutoff(enabled: Boolean) {
    _automationConfig.value = _automationConfig.value.copy(autoLowBatteryCutoffEnabled = enabled)
  }

  fun toggleOverheatProtection(enabled: Boolean) {
    _automationConfig.value = _automationConfig.value.copy(autoOverheatHaltEnabled = enabled)
  }

  fun toggleOverloadProtection(enabled: Boolean) {
    _automationConfig.value = _automationConfig.value.copy(autoOverloadProtectionEnabled = enabled)
  }

  fun toggleGridOutageUps(enabled: Boolean) {
    _automationConfig.value = _automationConfig.value.copy(autoGridOutageUpsEnabled = enabled)
  }

  /**
   * Evaluates incoming real-time telemetry state and applies automatic protection triggers.
   * Returns updated device state if changes were forced by safety automation.
   */
  suspend fun evaluateEmergencyState(
    currentState: EcoFlowDeviceState,
    onStateUpdated: (EcoFlowDeviceState) -> Unit
  ) {
    val cfg = _automationConfig.value
    var state = currentState

    // 1. Low Battery Automatic AC Cutoff Rule
    if (cfg.autoLowBatteryCutoffEnabled &&
      state.batteryPercentage <= cfg.lowBatteryCutoffThreshold &&
      state.acPortEnabled &&
      !state.isCharging
    ) {
      Log.w("EcoFlowEmergency", "Low battery emergency cutoff triggered at ${state.batteryPercentage}%")
      isEmergencyCutoffActive = true
      state = state.copy(
        acPortEnabled = false,
        automation = cfg.copy(
          lastTriggeredEvent = "LOW_BATTERY_AC_CUTOFF",
          lastTriggeredTimestamp = System.currentTimeMillis()
        )
      )

      val warningSpeech = "သတိပေးချက်: EcoFlow ဘက်ထရီ ${state.batteryPercentage}% သို့ ရောက်ရှိသွားပါသဖြင့် ဘက်ထရီကာကွယ်ရန် AC ပလပ်ပေါက်များကို အလိုအလျောက် ပိတ်လိုက်ပါပြီ ခင်ဗျာ။"
      realActionExecutor.speakText(warningSpeech)
      notificationHelper.checkAndNotifyLowBattery(state.batteryPercentage, state.remainingMinutes, state.isCharging)

      // Record in ErrorLearningEngine under RESOURCE category
      errorLearningEngine.recordFailure(
        actionKey = "ECOFLOW_LOW_BATTERY_PROTECTION",
        rawCommand = "အရေးပေါ် ဘက်ထရီကာကွယ်ရေး စနစ် လှုံ့ဆော်မှု",
        category = ErrorCategory.RESOURCE,
        reasonDetail = "EcoFlow ဘက်ထရီသည် သတ်မှတ်ထားသော အနိမ့်ဆုံးအဆင့် (${cfg.lowBatteryCutoffThreshold}%) သို့ ရောက်ရှိသွားသဖြင့် အလိုအလျောက် ဖြတ်တောက်ရခြင်း ဖြစ်ပါသည်",
        failedMethod = "AC 220V Inverter ဆက်လက် လည်ပတ်ခြင်းကို ရပ်တန့်ခြင်း",
        alternativeAdvice = "စက်ကို မီးအားသွင်းပါ သို့မဟုတ် DC/USB ပေါက်များကိုသာ ဦးစားပေး အသုံးပြုပါ"
      )

      onStateUpdated(state)
    }

    // 2. Overheat High Temperature Safety Rule
    if (cfg.autoOverheatHaltEnabled && state.temperatureCelsius >= cfg.overheatTempThreshold) {
      if (state.acPortEnabled || state.dcPortEnabled || state.outputWatts > 0) {
        Log.e("EcoFlowEmergency", "Overheat emergency halt triggered at ${state.temperatureCelsius}°C")
        state = state.copy(
          acPortEnabled = false,
          dcPortEnabled = false,
          outputWatts = 0,
          automation = cfg.copy(
            lastTriggeredEvent = "OVERHEAT_SAFETY_HALT",
            lastTriggeredTimestamp = System.currentTimeMillis()
          )
        )

        val haltSpeech = "အရေးပေါ် သတိပေးချက်: EcoFlow အပူချိန် ${state.temperatureCelsius}°C အထိ မြင့်တက်လာပါသဖြင့် အန္တရာယ်ကင်းစေရန် စက်ကို ချက်ချင်း ရပ်တန့်လိုက်ပါသည်!"
        realActionExecutor.speakText(haltSpeech)

        errorLearningEngine.recordFailure(
          actionKey = "ECOFLOW_OVERHEAT_PROTECTION",
          rawCommand = "အပူလွန်ကဲမှု ကာကွယ်ရေး ရပ်တန့်မှု",
          category = ErrorCategory.TECHNICAL,
          reasonDetail = "EcoFlow အပူချိန်သည် ဘေးကင်းလုံခြုံမှု ကန့်သတ်ချက် (${cfg.overheatTempThreshold}°C) ထက် ကျော်လွန်နေပါသည်",
          failedMethod = "High temperature power transmission",
          alternativeAdvice = "စက်ပန်ကာ လေဝင်လေထွက် ရှင်းလင်းပေးပြီး အေးသောနေရာတွင် ထားရှိပါ"
        )

        onStateUpdated(state)
      }
    }

    // 3. Overload Protection Rule (Exceeding Model AC Watts without X-Boost)
    val modelMaxWatts = state.selectedModel.maxAcWatts
    if (cfg.autoOverloadProtectionEnabled && state.outputWatts > modelMaxWatts && !state.xboostEnabled) {
      val overloadMsg = "သတိပေးချက်: လက်ရှိ သုံးစွဲဝပ်အား (${state.outputWatts}W) သည် ${state.selectedModel.modelName} ၏ သတ်မှတ်စွမ်းအား (${modelMaxWatts}W) ထက် ကျော်လွန်နေပါသည်။"
      realActionExecutor.speakText("$overloadMsg X-Boost ကို ဖွင့်ပါ သို့မဟုတ် ဝန်အားလျှော့ပါ။")

      errorLearningEngine.recordFailure(
        actionKey = "ECOFLOW_OVERLOAD_PROTECTION",
        rawCommand = "ဝန်အားလွန်ကဲမှု သတိပေးချက်",
        category = ErrorCategory.TECHNICAL,
        reasonDetail = "${state.selectedModel.modelName} တွင် သတ်မှတ်ထားသော ဝပ်အားထက် ကျော်လွန်သုံးစွဲနေခြင်း",
        failedMethod = "AC Load ${state.outputWatts}W > ${modelMaxWatts}W",
        alternativeAdvice = "X-Boost စနစ်ကို ဖွင့်ပါ သို့မဟုတ် မီးအားသုံးပစ္စည်းများကို ခွဲသုံးပါ"
      )
    }

    // 4. Grid Outage EPS / UPS Auto-Switch Rule
    if (cfg.autoGridOutageUpsEnabled && lastInputWatts > 50 && state.inputWatts == 0) {
      Log.i("EcoFlowEmergency", "Grid outage detected. Auto-switch to Emergency UPS battery mode.")
      realActionExecutor.speakText("ဓာတ်အားလိုင်း မီးပြတ်တောက်သွားပါသဖြင့် EcoFlow အရန်ဘက်ထရီ စနစ်သို့ ချောမွေ့စွာ ပြောင်းလဲပေးလိုက်ပါပြီ ခင်ဗျာ။")
    }

    lastInputWatts = state.inputWatts
    lastBatteryLevel = state.batteryPercentage
  }

  /**
   * Safely triggers a real test automation of the emergency cutoff rule.
   */
  fun runTestEmergencyCutoff(
    currentState: EcoFlowDeviceState,
    onStateUpdated: (EcoFlowDeviceState) -> Unit
  ) {
    scope.launch {
      val updated = currentState.copy(
        acPortEnabled = false,
        automation = _automationConfig.value.copy(
          lastTriggeredEvent = "MANUAL_TEST_EMERGENCY_CUTOFF",
          lastTriggeredTimestamp = System.currentTimeMillis()
        )
      )
      val speech = "အရေးပေါ် ဘက်ထရီအားနည်း AC ဖြတ်တောက်မှု စနစ်ကို စမ်းသပ်လုပ်ဆောင်ပြသလိုက်ပါပြီ ခင်ဗျာ။"
      realActionExecutor.speakText(speech)
      notificationHelper.checkAndNotifyLowBattery(18, updated.remainingMinutes, false)
      onStateUpdated(updated)
    }
  }
}
