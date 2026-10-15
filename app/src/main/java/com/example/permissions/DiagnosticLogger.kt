package com.example.permissions

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Thread-safe diagnostic logger for permission lifecycle, hardware events,
 * and system settings transitions. Never logs credentials or private data.
 */
object DiagnosticLogger {
  private const val MAX_LOGS = 150
  private val logList = CopyOnWriteArrayList<DiagnosticLogEntry>()
  private val _logsFlow = MutableStateFlow<List<DiagnosticLogEntry>>(emptyList())
  val logsFlow: StateFlow<List<DiagnosticLogEntry>> = _logsFlow.asStateFlow()

  fun log(entry: DiagnosticLogEntry) {
    logList.add(0, entry)
    while (logList.size > MAX_LOGS) {
      logList.removeAt(logList.lastIndex)
    }
    _logsFlow.value = logList.toList()
  }

  fun logOperation(
    operation: String,
    setting: String,
    previousState: String,
    newState: String,
    result: String,
    error: String? = null
  ) {
    val entry = DiagnosticLogEntry(
      operation = operation,
      setting = setting,
      previousState = previousState,
      newState = newState,
      result = result,
      error = error
    )
    log(entry)
  }

  fun logSettingsOpen(settingName: String, intentAction: String) {
    logOperation(
      operation = "SETTINGS_OPEN",
      setting = settingName,
      previousState = "APP_ACTIVE",
      newState = "LAUNCHING_SETTINGS",
      result = "INTENT: $intentAction"
    )
  }

  fun logSettingsReturn(settingName: String) {
    logOperation(
      operation = "SETTINGS_RETURN",
      setting = settingName,
      previousState = "SETTINGS_ACTIVE",
      newState = "APP_RESUMED",
      result = "TRIGGER_RECHECK"
    )
  }

  fun clearLogs() {
    logList.clear()
    _logsFlow.value = emptyList()
  }
}
