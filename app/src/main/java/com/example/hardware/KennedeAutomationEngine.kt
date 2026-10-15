package com.example.hardware

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.database.FanAutomationConfigDao
import com.example.database.FanAutomationConfigEntity
import com.example.models.KennedeButton
import com.example.models.KennedeFanAutomationState
import com.example.notification.NotificationManagementHelper
import com.example.realworld.RealDeviceActionExecutor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Item 6: Smart Fan Automation, Scheduling & EcoFlow Power Linkage Engine.
 *
 * Provides:
 * 1. Sleep Countdown Timers with automatic IR power-off and Burmese voice status.
 * 2. EcoFlow Smart Battery Linkage: Automatic light-off and speed-reduction when power station battery is low.
 * 3. Quiet Night Mode: Gentle airflow and zero glare for restful sleep.
 * 4. 10-Button Batch Calibration Wizard: Step-by-step guided physical remote learning.
 * 5. Persistent State in Room Database (Item 1).
 * 6. Android Foreground Service & Hardware AlarmManager Wake-Lock (Item 2).
 */
class KennedeAutomationEngine(
  private val context: Context,
  private val notificationHelper: NotificationManagementHelper,
  private val actionExecutor: RealDeviceActionExecutor,
  private val externalScope: CoroutineScope,
  private val fanAutomationConfigDao: FanAutomationConfigDao? = null
) {
  companion object {
    const val ALARM_REQUEST_CODE = 8899

    @Volatile
    var activeInstance: KennedeAutomationEngine? = null
  }

  private val tag = "KennedeAutomation"
  private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

  private val _state = MutableStateFlow(KennedeFanAutomationState())
  val state: StateFlow<KennedeFanAutomationState> = _state.asStateFlow()

  private var timerJob: Job? = null
  private var lastCheckedBatteryPercent: Int? = null

  // Callback to dispatch IR signal via MainViewModel
  var sendIrCallback: ((KennedeButton, (Boolean, String) -> Unit) -> Unit)? = null
  var startLearningCallback: ((KennedeButton) -> Unit)? = null

  init {
    activeInstance = this

    // Item 1: Restore persisted automation settings from Room Database on launch
    externalScope.launch(Dispatchers.IO) {
      try {
        val saved = fanAutomationConfigDao?.getConfigDirect()
        if (saved != null) {
          _state.value = _state.value.copy(
            ecoFlowPowerLinkEnabled = saved.ecoFlowPowerLinkEnabled,
            lowBatteryThreshold = saved.lowBatteryThreshold,
            autoTurnOffLightOnLowBattery = saved.autoTurnOffLightOnLowBattery,
            autoReduceSpeedOnLowBattery = saved.autoReduceSpeedOnLowBattery,
            quietNightModeActive = saved.quietNightModeActive,
            sleepTimerMinutes = if (saved.defaultSleepTimerMinutes > 0) saved.defaultSleepTimerMinutes else 0
          )
          Log.i(tag, "Loaded persisted fan automation config from Room: threshold=${saved.lowBatteryThreshold}%, powerLink=${saved.ecoFlowPowerLinkEnabled}")
        }
      } catch (e: Exception) {
        Log.e(tag, "Failed to load saved fan automation config", e)
      }
    }
  }

  private fun persistConfig() {
    val cur = _state.value
    val entity = FanAutomationConfigEntity(
      id = 1,
      ecoFlowPowerLinkEnabled = cur.ecoFlowPowerLinkEnabled,
      lowBatteryThreshold = cur.lowBatteryThreshold,
      autoTurnOffLightOnLowBattery = cur.autoTurnOffLightOnLowBattery,
      autoReduceSpeedOnLowBattery = cur.autoReduceSpeedOnLowBattery,
      quietNightModeActive = cur.quietNightModeActive,
      defaultSleepTimerMinutes = cur.sleepTimerMinutes.takeIf { it > 0 } ?: 30,
      updatedAt = System.currentTimeMillis()
    )
    externalScope.launch(Dispatchers.IO) {
      try {
        fanAutomationConfigDao?.saveConfig(entity)
      } catch (e: Exception) {
        Log.e(tag, "Failed to persist fan automation config to Room", e)
      }
    }
  }

  private val calibrationButtonList = listOf(
    KennedeButton.POWER,
    KennedeButton.SPEED,
    KennedeButton.LIGHT,
    KennedeButton.TIMER,
    KennedeButton.OSCILLATION,
    KennedeButton.MODE,
    KennedeButton.VOLUME_UP,
    KennedeButton.VOLUME_DOWN,
    KennedeButton.MUTE,
    KennedeButton.CENTER
  )

  /**
   * Item 2: Starts or updates a sleep countdown timer in minutes (15, 30, 45, 60, 120, etc.).
   * Runs an Android Foreground Service with live lockscreen notification and schedules
   * an exact hardware alarm in AlarmManager with WakeLock so that the device CPU wakes up
   * and dispatches the IR turn-off signal even if the screen is locked or phone is in Doze sleep.
   */
  fun startSleepTimer(minutes: Int) {
    if (minutes <= 0) {
      cancelSleepTimer()
      return
    }

    timerJob?.cancel()
    val totalSeconds = minutes * 60

    _state.value = _state.value.copy(
      sleepTimerMinutes = minutes,
      sleepTimerRemainingSeconds = totalSeconds,
      sleepTimerActive = true,
      lastAutomationEvent = "Sleep Timer $minutes မိနစ် သတ်မှတ်လိုက်ပါပြီ",
      lastAutomationTimestamp = System.currentTimeMillis()
    )

    actionExecutor.speakText("KENNEDE ပန်ကာကို $minutes မိနစ်ကြာလျှင် အလိုအလျောက် ပိတ်ရန် အချိန်မှတ်လိုက်ပါပြီ ခင်ဗျာ။")
    persistConfig()

    // Item 2: Schedule hardware exact alarm via AlarmManager
    scheduleAlarm(totalSeconds)

    // Item 2: Start Android Foreground Service for lock screen countdown
    FanSleepTimerService.start(context, minutes)

    timerJob = externalScope.launch(Dispatchers.Default) {
      var remaining = totalSeconds
      while (isActive && remaining > 0) {
        delay(1000L)
        remaining--
        _state.value = _state.value.copy(sleepTimerRemainingSeconds = remaining)
      }

      if (isActive && remaining <= 0) {
        onSleepTimerExpired()
      }
    }
  }

  private fun scheduleAlarm(totalSeconds: Int) {
    if (alarmManager == null) return
    val triggerAtMillis = System.currentTimeMillis() + (totalSeconds * 1000L)
    val intent = Intent(context, FanSleepTimerReceiver::class.java).apply {
      action = FanSleepTimerReceiver.ACTION_TIMER_EXPIRED
    }
    val pendingIntent = PendingIntent.getBroadcast(
      context,
      ALARM_REQUEST_CODE,
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        alarmManager.setExactAndAllowWhileIdle(
          AlarmManager.RTC_WAKEUP,
          triggerAtMillis,
          pendingIntent
        )
      } else {
        alarmManager.setExact(
          AlarmManager.RTC_WAKEUP,
          triggerAtMillis,
          pendingIntent
        )
      }
      Log.i(tag, "Scheduled exact hardware alarm for sleep timer at $triggerAtMillis ($totalSeconds s)")
    } catch (e: SecurityException) {
      Log.w(tag, "Exact alarm restricted (${com.example.permissions.ExactAlarmPermissionManager.MYANMAR_DENIED_FALLBACK}), falling back to standard alarm", e)
      try {
        alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
      } catch (ex: Exception) {
        Log.e(tag, "Failed to schedule fallback alarm", ex)
      }
    }
  }

  private fun cancelAlarm() {
    if (alarmManager == null) return
    val intent = Intent(context, FanSleepTimerReceiver::class.java).apply {
      action = FanSleepTimerReceiver.ACTION_TIMER_EXPIRED
    }
    val pendingIntent = PendingIntent.getBroadcast(
      context,
      ALARM_REQUEST_CODE,
      intent,
      PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
    )
    if (pendingIntent != null) {
      try {
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
      } catch (e: Exception) {
        Log.w(tag, "Error canceling sleep timer alarm", e)
      }
    }
  }

  /**
   * Cancels the active sleep countdown timer, stops foreground service, and clears hardware alarm.
   */
  fun cancelSleepTimer() {
    timerJob?.cancel()
    timerJob = null
    cancelAlarm()
    FanSleepTimerService.stop(context)

    _state.value = _state.value.copy(
      sleepTimerMinutes = 0,
      sleepTimerRemainingSeconds = 0,
      sleepTimerActive = false,
      lastAutomationEvent = "Sleep Timer ဖျက်သိမ်းပြီးပါပြီ",
      lastAutomationTimestamp = System.currentTimeMillis()
    )
    actionExecutor.speakText("ပန်ကာ အချိန်မှတ်စနစ်ကို ပယ်ဖျက်လိုက်ပါပြီ ခင်ဗျာ။")
  }

  /**
   * Handles timer expiration: safely shuts off the fan via IR and alerts user.
   * Can be called directly or from FanSleepTimerReceiver when screen was locked.
   */
  fun onSleepTimerExpired() {
    timerJob?.cancel()
    timerJob = null
    cancelAlarm()
    FanSleepTimerService.stop(context)

    _state.value = _state.value.copy(
      sleepTimerMinutes = 0,
      sleepTimerRemainingSeconds = 0,
      sleepTimerActive = false,
      lastAutomationEvent = "အချိန်ပြည့်သဖြင့် ပန်ကာပါဝါ ပိတ်ပေးလိုက်ပါပြီ",
      lastAutomationTimestamp = System.currentTimeMillis()
    )

    sendIrCallback?.invoke(KennedeButton.POWER) { success, _ ->
      Log.i(tag, "Sleep timer expired. Power off result: $success")
    }

    actionExecutor.speakText("အချိန်ပြည့်သွားပါပြီ။ KENNEDE ပန်ကာကို အလိုအလျောက် ပါဝါပိတ်ပေးလိုက်ပါပြီ ခင်ဗျာ။")
    notificationHelper.notifyIrCommandExecuted(
      deviceName = "KENNEDE KN-5996HRB",
      buttonLabel = "POWER OFF (SLEEP TIMER)",
      burmeseLabel = "အချိန်ကိုက် အလိုအလျောက် ပိတ်ခြင်း"
    )
  }

  /**
   * Evaluates EcoFlow power station battery and automatically triggers power saving.
   */
  fun evaluateEcoFlowBattery(currentBatteryPercent: Int) {
    val current = _state.value
    if (!current.ecoFlowPowerLinkEnabled) return

    val prev = lastCheckedBatteryPercent
    lastCheckedBatteryPercent = currentBatteryPercent

    // Trigger only when crossing below or at the threshold from a higher level
    val threshold = current.lowBatteryThreshold
    if (currentBatteryPercent <= threshold && (prev == null || prev > threshold)) {
      triggerLowBatteryPowerSave(currentBatteryPercent)
    }
  }

  fun triggerLowBatteryPowerSave(batteryPercent: Int) {
    val current = _state.value
    _state.value = current.copy(
      lastAutomationEvent = "EcoFlow ဘက်ထရီ $batteryPercent% သို့ ရောက်ရှိသဖြင့် စွမ်းအင်ချွေတာရေး စတင်ပါသည်",
      lastAutomationTimestamp = System.currentTimeMillis()
    )

    externalScope.launch(Dispatchers.Main) {
      if (current.autoTurnOffLightOnLowBattery) {
        delay(200L)
        sendIrCallback?.invoke(KennedeButton.LIGHT) { _, _ -> }
      }

      if (current.autoReduceSpeedOnLowBattery) {
        delay(1200L)
        // Send Speed command to cycle down to lowest speed
        sendIrCallback?.invoke(KennedeButton.SPEED) { _, _ -> }
      }

      val announcement = "သတိပေးချက် - EcoFlow ဘက်ထရီ $batteryPercent ရာခိုင်နှုန်းအောက် ရောက်ရှိသဖြင့် KENNEDE ပန်ကာမီးကို ပိတ်ပြီး စွမ်းအင်ချွေတာရေး စနစ်ကို စတင်လိုက်ပါပြီ ခင်ဗျာ။"
      actionExecutor.speakText(announcement)

      notificationHelper.notifyIrCommandExecuted(
        deviceName = "KENNEDE KN-5996HRB",
        buttonLabel = "ECOFLOW POWER LINK",
        burmeseLabel = "ဘက်ထရီ ချွေတာရေး စနစ် စတင်ခြင်း"
      )
    }
  }

  /**
   * Toggles Quiet Night Mode.
   */
  fun toggleQuietNightMode(enabled: Boolean) {
    _state.value = _state.value.copy(
      quietNightModeActive = enabled,
      lastAutomationEvent = if (enabled) "ညဘက် အသံတိတ် မုဒ် စတင်ပါသည်" else "ညဘက် မုဒ် ပိတ်လိုက်ပါပြီ",
      lastAutomationTimestamp = System.currentTimeMillis()
    )

    if (enabled) {
      externalScope.launch(Dispatchers.Main) {
        actionExecutor.speakText("ညဘက် အိပ်စက်ချိန် အသံတိတ်စနစ်ကို ဖွင့်လှစ်ပေးလိုက်ပါပြီ ခင်ဗျာ။")
        // Set fan speed to low/gentle and turn off fan decorative light
        sendIrCallback?.invoke(KennedeButton.SPEED) { _, _ -> }
        delay(800L)
        sendIrCallback?.invoke(KennedeButton.LIGHT) { _, _ -> }
      }
    } else {
      actionExecutor.speakText("ညဘက် အသံတိတ်စနစ်ကို ပိတ်လိုက်ပါပြီ ခင်ဗျာ။")
    }
    persistConfig()
  }

  fun setEcoFlowPowerLink(enabled: Boolean) {
    _state.value = _state.value.copy(ecoFlowPowerLinkEnabled = enabled)
    val msg = if (enabled) "EcoFlow ချိတ်ဆက် စွမ်းအင်ချွေတာရေး စနစ်ကို ဖွင့်ထားပါသည်" else "EcoFlow ချိတ်ဆက်မှု ပိတ်ထားပါသည်"
    actionExecutor.speakText(msg)
    persistConfig()
  }

  fun setLowBatteryThreshold(threshold: Int) {
    _state.value = _state.value.copy(lowBatteryThreshold = threshold.coerceIn(5, 50))
    persistConfig()
  }

  fun toggleAutoTurnOffLight(enabled: Boolean) {
    _state.value = _state.value.copy(autoTurnOffLightOnLowBattery = enabled)
    persistConfig()
  }

  fun toggleAutoReduceSpeed(enabled: Boolean) {
    _state.value = _state.value.copy(autoReduceSpeedOnLowBattery = enabled)
    persistConfig()
  }

  // ---------------------------------------------------------------------------
  // Step-by-Step 10-Button Batch Calibration Wizard
  // ---------------------------------------------------------------------------

  /**
   * Starts the 10-button batch calibration wizard.
   */
  fun startBatchCalibration() {
    _state.value = _state.value.copy(
      batchCalibrationActive = true,
      batchCalibrationStep = 1,
      batchCalibrationTotal = calibrationButtonList.size,
      batchCalibrationTarget = calibrationButtonList[0],
      lastAutomationEvent = "Batch Calibration စတင်ပါသည်",
      lastAutomationTimestamp = System.currentTimeMillis()
    )

    promptCalibrationStep(1)
  }

  /**
   * Advances or completes batch calibration when a button is successfully captured.
   */
  fun onButtonCaptured(capturedButton: KennedeButton) {
    val current = _state.value
    if (!current.batchCalibrationActive) return

    val currentTarget = current.batchCalibrationTarget
    if (currentTarget != capturedButton) return

    val nextStep = current.batchCalibrationStep + 1
    if (nextStep <= calibrationButtonList.size) {
      val nextButton = calibrationButtonList[nextStep - 1]
      _state.value = current.copy(
        batchCalibrationStep = nextStep,
        batchCalibrationTarget = nextButton,
        lastAutomationEvent = "${capturedButton.label} အောင်မြင်စွာ ဖမ်းယူပြီးပါပြီ",
        lastAutomationTimestamp = System.currentTimeMillis()
      )
      promptCalibrationStep(nextStep)
    } else {
      // Completed all buttons
      _state.value = current.copy(
        batchCalibrationActive = false,
        batchCalibrationStep = calibrationButtonList.size,
        batchCalibrationTarget = null,
        lastAutomationEvent = "ခလုတ် ၁၀ ခုစလုံး အောင်မြင်စွာ Calibration ပြီးစီးပါပြီ",
        lastAutomationTimestamp = System.currentTimeMillis()
      )
      actionExecutor.speakText("ဂုဏ်ယူပါသည်ခင်ဗျာ။ KENNEDE KN-5996HRB မူရင်းရီမုဒ် ခလုတ် ၁၀ ခုစလုံး အောင်မြင်စွာ ဖမ်းယူစစ်ဆေးပြီးစီးပါပြီ ခင်ဗျာ။")
    }
  }

  fun cancelBatchCalibration() {
    _state.value = _state.value.copy(
      batchCalibrationActive = false,
      batchCalibrationStep = 0,
      batchCalibrationTarget = null,
      lastAutomationEvent = "Batch Calibration ဖျက်သိမ်းလိုက်ပါသည်",
      lastAutomationTimestamp = System.currentTimeMillis()
    )
    actionExecutor.speakText("Calibration လုပ်ဆောင်မှုကို ရပ်တန့်လိုက်ပါပြီ။")
  }

  private fun promptCalibrationStep(step: Int) {
    if (step < 1 || step > calibrationButtonList.size) return
    val btn = calibrationButtonList[step - 1]

    externalScope.launch(Dispatchers.Main) {
      startLearningCallback?.invoke(btn)
      val speech = "အဆင့် $step/${calibrationButtonList.size} - မူရင်းရီမုဒ်မှ ${btn.label} (${btn.burmeseLabel}) ခလုတ်ကို ESP32 သို့ ချိန်၍ ၁ ကြိမ် နှိပ်ပါ ခင်ဗျာ။"
      actionExecutor.speakText(speech)
    }
  }
}
