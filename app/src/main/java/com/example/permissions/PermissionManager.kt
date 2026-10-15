package com.example.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Central Permission and System Architecture Manager for Kyaw Gyi AI.
 * Coordinates specialized permission managers, validates actual hardware and OS states,
 * and maintains continuous state synchronization across lifecycle transitions.
 */
class PermissionManager(
  private val context: Context,
  private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {

  // Specialized Sub-Managers
  val bluetoothManager = BluetoothPermissionManager(context)
  val microphoneManager = MicrophonePermissionManager(context)
  val notificationManager = NotificationPermissionManager(context)
  val batteryManager = BatteryOptimizationManager(context)
  val accessibilityManager = DeviceAccessibilityManager(context)
  val overlayManager = OverlayPermissionManager(context)
  val backgroundManager = BackgroundExecutionManager(context)
  val firebaseManager = FirebasePermissionManager(context)
  val exactAlarmManager = ExactAlarmPermissionManager(context)

  private val _state = MutableStateFlow(SystemPermissionsState())
  val state: StateFlow<SystemPermissionsState> = _state.asStateFlow()

  init {
    refreshAll()
  }

  /**
   * Re-evaluates all permissions, hardware states, and OS system settings.
   * Invoked automatically on onResume() and after returning from Android Settings.
   */
  fun refreshAll(
    gattConnected: Boolean = false,
    protocolVerified: Boolean = false,
    authenticated: Boolean = false
  ) {
    scope.launch {
      val micState = microphoneManager.checkReadiness()
      val btState = bluetoothManager.checkReadiness(gattConnected)
      val notifState = notificationManager.checkReadiness()
      val accessState = accessibilityManager.checkReadiness()
      val overlayState = overlayManager.checkReadiness()
      val batteryState = batteryManager.checkReadiness()
      val locationGranted = checkLocation()
      val networkConnected = firebaseManager.isNetworkConnected()
      val firebaseAvailable = firebaseManager.isFirebaseConfigured()

      val ecoFlowBleState = calculateEcoFlowBleReadiness(
        btState = btState,
        gattConnected = gattConnected,
        protocolVerified = protocolVerified,
        authenticated = authenticated
      )

      val items = buildPermissionMatrix(
        micState = micState,
        btState = btState,
        notifState = notifState,
        accessState = accessState,
        overlayState = overlayState,
        batteryState = batteryState,
        locationGranted = locationGranted,
        networkConnected = networkConnected
      )

      val overall = calculateOverallReadiness(
        micState = micState,
        btState = btState,
        notifState = notifState,
        batteryState = batteryState
      )

      val previousState = _state.value
      val newState = SystemPermissionsState(
        microphoneReadiness = micState,
        bluetoothReadiness = btState,
        notificationReadiness = notifState,
        accessibilityReadiness = accessState,
        overlayReadiness = overlayState,
        batteryReadiness = batteryState,
        isLocationGranted = locationGranted,
        isNetworkConnected = networkConnected,
        isFirebaseConfigured = firebaseAvailable,
        ecoFlowBleReadiness = ecoFlowBleState,
        overallReadiness = overall,
        items = items
      )

      _state.value = newState

      DiagnosticLogger.logOperation(
        operation = "PERMISSION_REFRESH",
        setting = "SystemOverall",
        previousState = previousState.overallReadiness.name,
        newState = overall.name,
        result = "MIC=${micState.name}, BT=${btState.name}, NOTIF=${notifState.name}"
      )
    }
  }

  // ==========================================
  // LOCATION (OPTIONAL / WEATHER / LEGACY BLE)
  // ==========================================
  fun checkLocation(): Boolean {
    val fine = ContextCompat.checkSelfPermission(
      context,
      Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    val coarse = ContextCompat.checkSelfPermission(
      context,
      Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    return fine || coarse
  }

  // ==========================================
  // ECOFLOW BLE READINESS PIPELINE
  // ==========================================
  fun calculateEcoFlowBleReadiness(
    btState: BluetoothReadiness,
    gattConnected: Boolean,
    protocolVerified: Boolean,
    authenticated: Boolean
  ): EcoFlowBleReadiness {
    return when (btState) {
      BluetoothReadiness.BLUETOOTH_UNSUPPORTED -> EcoFlowBleReadiness.NOT_SUPPORTED
      BluetoothReadiness.BLUETOOTH_PERMISSION_DENIED -> EcoFlowBleReadiness.PERMISSION_REQUIRED
      BluetoothReadiness.BLUETOOTH_DISABLED -> EcoFlowBleReadiness.BLUETOOTH_DISABLED
      BluetoothReadiness.BLUETOOTH_PERMISSION_GRANTED,
      BluetoothReadiness.BLUETOOTH_READY,
      BluetoothReadiness.GATT_DISCONNECTED -> {
        if (!gattConnected) {
          EcoFlowBleReadiness.READY_TO_SCAN
        } else if (!protocolVerified) {
          EcoFlowBleReadiness.PROTOCOL_DETECTING
        } else if (!authenticated) {
          EcoFlowBleReadiness.AUTHENTICATING
        } else {
          EcoFlowBleReadiness.READY
        }
      }
      BluetoothReadiness.GATT_CONNECTED -> {
        if (authenticated && protocolVerified) {
          EcoFlowBleReadiness.READY
        } else if (protocolVerified) {
          EcoFlowBleReadiness.AUTHENTICATING
        } else {
          EcoFlowBleReadiness.PROTOCOL_DETECTING
        }
      }
    }
  }

  // ==========================================
  // SYSTEM READINESS SCORE
  // ==========================================
  fun calculateOverallReadiness(
    micState: MicrophoneReadiness,
    btState: BluetoothReadiness,
    notifState: NotificationReadiness,
    batteryState: BatteryReadiness
  ): SystemReadiness {
    // Critical failure: Mic is required for Kyaw Gyi voice assistant
    if (!micState.isGranted) {
      return SystemReadiness.RED
    }

    // Degraded state: Bluetooth off, or notifications disabled, or battery optimized
    if (btState == BluetoothReadiness.BLUETOOTH_DISABLED ||
      btState == BluetoothReadiness.BLUETOOTH_PERMISSION_DENIED ||
      !notifState.isFullyEnabled ||
      batteryState == BatteryReadiness.OPTIMIZED
    ) {
      return SystemReadiness.YELLOW
    }

    return SystemReadiness.GREEN
  }

  // ==========================================
  // PERMISSION REGISTRY MATRIX
  // ==========================================
  private fun buildPermissionMatrix(
    micState: MicrophoneReadiness,
    btState: BluetoothReadiness,
    notifState: NotificationReadiness,
    accessState: AccessibilityReadiness,
    overlayState: OverlayReadiness,
    batteryState: BatteryReadiness,
    locationGranted: Boolean,
    networkConnected: Boolean
  ): List<PermissionItem> {
    val items = mutableListOf<PermissionItem>()

    // 1. Microphone
    items.add(
      PermissionItem(
        id = "RECORD_AUDIO",
        name = "Microphone Permission",
        description = "Voice recognition & live audio sensing for Kyaw Gyi commands",
        requiredFor = "Kyaw Gyi Burmese Voice Assistant",
        currentStatus = if (micState.isGranted) PermissionStatus.GRANTED else PermissionStatus.DENIED,
        required = true,
        requestable = true,
        systemSettingsRoute = "AppDetails",
        verificationMethod = "ContextCompat.checkSelfPermission(RECORD_AUDIO)",
        riskLevel = RiskLevel.DANGEROUS,
        userMessageMyanmar = MicrophonePermissionManager.MYANMAR_MESSAGE,
        userMessageEnglish = MicrophonePermissionManager.ENGLISH_MESSAGE
      )
    )

    // 2. Nearby Devices (Bluetooth Scan / Connect)
    val isBtPermGranted = btState != BluetoothReadiness.BLUETOOTH_PERMISSION_DENIED &&
      btState != BluetoothReadiness.BLUETOOTH_UNSUPPORTED
    items.add(
      PermissionItem(
        id = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) "BLUETOOTH_SCAN_CONNECT" else "BLUETOOTH_ADMIN",
        name = "Nearby Devices (Bluetooth)",
        description = "Discovery & connection to EcoFlow Power Stations and IR ESP32",
        requiredFor = "EcoFlow BLE & Hardware Controller",
        currentStatus = if (isBtPermGranted) PermissionStatus.GRANTED else PermissionStatus.DENIED,
        required = true,
        requestable = true,
        systemSettingsRoute = "Bluetooth",
        verificationMethod = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) "BLUETOOTH_SCAN + BLUETOOTH_CONNECT" else "BLUETOOTH + BLUETOOTH_ADMIN",
        riskLevel = RiskLevel.DANGEROUS,
        userMessageMyanmar = "EcoFlow ဘက်ထရီနှင့် ချိတ်ဆက်ရန် အနီးရှိ စက်ပစ္စည်း (Bluetooth) ခွင့်ပြုချက် လိုအပ်ပါသည်။",
        userMessageEnglish = "Nearby devices permission is required to discover and communicate with EcoFlow."
      )
    )

    // 3. Notifications
    items.add(
      PermissionItem(
        id = "POST_NOTIFICATIONS",
        name = "Notifications",
        description = "Ongoing status for voice listening service & battery alerts",
        requiredFor = "Persistent Background Listening & Alarms",
        currentStatus = if (notifState.isFullyEnabled) PermissionStatus.GRANTED else PermissionStatus.DENIED,
        required = false,
        requestable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU,
        systemSettingsRoute = "Notifications",
        verificationMethod = "NotificationManagerCompat.areNotificationsEnabled()",
        riskLevel = RiskLevel.NORMAL,
        userMessageMyanmar = "နောက်ခံအသံဖမ်းယူမှု အခြေအနေနှင့် အရေးပေါ်အသိပေးချက်များ ရရှိရန် ခွင့်ပြုချက် လိုအပ်ပါသည်။",
        userMessageEnglish = "Notification permission ensures the background service status and alerts remain visible."
      )
    )

    // 4. Battery Optimization
    items.add(
      PermissionItem(
        id = "REQUEST_IGNORE_BATTERY_OPTIMIZATIONS",
        name = "Battery Optimization Exemption",
        description = "Prevents Android Doze from killing background audio sensing",
        requiredFor = "Always-on Background Voice & BLE Reconnect",
        currentStatus = if (batteryState == BatteryReadiness.EXEMPTED) PermissionStatus.GRANTED else PermissionStatus.ACTION_REQUIRED,
        required = false,
        requestable = false,
        systemSettingsRoute = "BatteryOptimization",
        verificationMethod = "PowerManager.isIgnoringBatteryOptimizations()",
        riskLevel = RiskLevel.SPECIAL_ACCESS,
        userMessageMyanmar = BatteryOptimizationManager.MYANMAR_MESSAGE,
        userMessageEnglish = BatteryOptimizationManager.ENGLISH_MESSAGE
      )
    )

    // 5. System Overlay (Optional)
    items.add(
      PermissionItem(
        id = "SYSTEM_ALERT_WINDOW",
        name = "Floating Assistant Overlay",
        description = "Display Kyaw Gyi voice indicator over other apps",
        requiredFor = "Floating Voice Widget (Optional)",
        currentStatus = if (overlayState == OverlayReadiness.OVERLAY_GRANTED) PermissionStatus.GRANTED else PermissionStatus.DENIED,
        required = false,
        requestable = false,
        systemSettingsRoute = "Overlay",
        verificationMethod = "Settings.canDrawOverlays()",
        riskLevel = RiskLevel.SPECIAL_ACCESS,
        userMessageMyanmar = "အခြားအက်ပ်များအပေါ်တွင် ကျော်ကြီး အသံခလုတ် အလွယ်တကူ ပြသရန် ခွင့်ပြုချက် ဖြစ်ပါသည်။",
        userMessageEnglish = "Overlay permission displays a floating voice indicator above other apps."
      )
    )

    // 6. Accessibility (Optional)
    items.add(
      PermissionItem(
        id = "ACCESSIBILITY_SERVICE",
        name = "Accessibility Service",
        description = "Deep system actions and automation support",
        requiredFor = "Device Action Automation (Optional)",
        currentStatus = if (accessState == AccessibilityReadiness.ACCESSIBILITY_SERVICE_ENABLED) PermissionStatus.GRANTED else PermissionStatus.DENIED,
        required = false,
        requestable = false,
        systemSettingsRoute = "Accessibility",
        verificationMethod = "Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES",
        riskLevel = RiskLevel.SPECIAL_ACCESS,
        userMessageMyanmar = "စနစ်အတွင်း လိုအပ်သော လုပ်ဆောင်ချက်များကို အသံဖြင့် တိုက်ရိုက်ခိုင်းစေရန် ဖြစ်ပါသည်။",
        userMessageEnglish = "Accessibility allows voice-directed automation of device actions."
      )
    )

    // 7. Location (Optional / Weather / Legacy BLE)
    items.add(
      PermissionItem(
        id = "ACCESS_FINE_LOCATION",
        name = "Location Permission (Optional)",
        description = "Local weather reports and legacy BLE scanning on Android 11 and below",
        requiredFor = "Weather Reports & Legacy BLE Discovery",
        currentStatus = if (locationGranted) PermissionStatus.GRANTED else PermissionStatus.DENIED,
        required = false,
        requestable = true,
        systemSettingsRoute = "Location",
        verificationMethod = "ContextCompat.checkSelfPermission(ACCESS_FINE_LOCATION)",
        riskLevel = RiskLevel.DANGEROUS,
        userMessageMyanmar = "ရာသီဥတုအခြေအနေ မေးမြန်းခြင်းနှင့် Bluetooth စကင်ဖတ်ခြင်း (Android အဟောင်းများ) အတွက် ဖြစ်ပါသည်။",
        userMessageEnglish = "Location is used for local weather queries and legacy BLE discovery on older devices."
      )
    )

    // 8. Exact Alarm (Optional / Appliance Sleep Timer)
    val exactAlarmGranted = exactAlarmManager.canScheduleExactAlarms()
    items.add(
      PermissionItem(
        id = "SCHEDULE_EXACT_ALARM",
        name = "Exact Alarm (Sleep Timer)",
        description = "Precise hardware alarm timer for appliance scheduled turn-off",
        requiredFor = "Appliance Sleep Timer Automation",
        currentStatus = if (exactAlarmGranted) PermissionStatus.GRANTED else PermissionStatus.ACTION_REQUIRED,
        required = false,
        requestable = false,
        systemSettingsRoute = "Alarms",
        verificationMethod = "AlarmManager.canScheduleExactAlarms()",
        riskLevel = RiskLevel.SPECIAL_ACCESS,
        userMessageMyanmar = ExactAlarmPermissionManager.MYANMAR_RATIONALE,
        userMessageEnglish = ExactAlarmPermissionManager.ENGLISH_RATIONALE
      )
    )

    return items
  }
}
