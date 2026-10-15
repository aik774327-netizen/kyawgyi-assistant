package com.example.permissions

import androidx.compose.ui.graphics.Color
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Normalized status enum for individual permissions.
 */
enum class PermissionStatus {
  GRANTED,
  DENIED,
  PERMANENTLY_DENIED,
  NOT_SUPPORTED,
  NOT_REQUIRED,
  ACTION_REQUIRED,
  UNKNOWN;

  val isGranted: Boolean
    get() = this == GRANTED
}

/**
 * Normalized status enum for hardware or OS system features.
 */
enum class SystemFeatureStatus {
  ENABLED,
  DISABLED,
  UNAVAILABLE,
  NOT_SUPPORTED,
  UNKNOWN
}

/**
 * Risk classification for permissions according to Android security guidelines.
 */
enum class RiskLevel {
  NORMAL,
  DANGEROUS,
  SPECIAL_ACCESS,
  SYSTEM_SETTING
}

/**
 * Granular states for Bluetooth system stack.
 * Note: Bluetooth permission granted != Bluetooth enabled != GATT connected.
 */
enum class BluetoothReadiness {
  BLUETOOTH_UNSUPPORTED,
  BLUETOOTH_DISABLED,
  BLUETOOTH_PERMISSION_DENIED,
  BLUETOOTH_PERMISSION_GRANTED,
  BLUETOOTH_READY,
  GATT_DISCONNECTED,
  GATT_CONNECTED;

  val isUsable: Boolean
    get() = this == BLUETOOTH_READY || this == GATT_CONNECTED
}

/**
 * Granular states for Microphone voice capture.
 */
enum class MicrophoneReadiness {
  MIC_PERMISSION_GRANTED,
  MIC_PERMISSION_DENIED,
  MIC_PERMISSION_PERMANENTLY_DENIED,
  MIC_HARDWARE_UNAVAILABLE;

  val isGranted: Boolean
    get() = this == MIC_PERMISSION_GRANTED
}

/**
 * Granular states for Notifications.
 */
enum class NotificationReadiness {
  NOTIFICATION_PERMISSION_GRANTED,
  NOTIFICATION_PERMISSION_DENIED,
  NOTIFICATION_CHANNEL_DISABLED;

  val isFullyEnabled: Boolean
    get() = this == NOTIFICATION_PERMISSION_GRANTED
}

/**
 * Granular states for Android Accessibility Service.
 */
enum class AccessibilityReadiness {
  ACCESSIBILITY_SERVICE_ENABLED,
  ACCESSIBILITY_SERVICE_DISABLED;

  val isEnabled: Boolean
    get() = this == ACCESSIBILITY_SERVICE_ENABLED
}

/**
 * Granular states for Overlay (SYSTEM_ALERT_WINDOW).
 */
enum class OverlayReadiness {
  OVERLAY_GRANTED,
  OVERLAY_DENIED;

  val isGranted: Boolean
    get() = this == OVERLAY_GRANTED
}

/**
 * Granular states for Android Battery Optimization (Doze exemption).
 */
enum class BatteryReadiness {
  OPTIMIZED,
  EXEMPTED;

  val isExempted: Boolean
    get() = this == EXEMPTED
}

/**
 * Granular states for Network, Firebase and Cloud endpoints.
 */
enum class NetworkReadiness {
  NETWORK_CONNECTED,
  NETWORK_DISCONNECTED,
  FIREBASE_AVAILABLE,
  FIREBASE_UNAVAILABLE,
  CLOUD_API_AVAILABLE,
  CLOUD_API_UNAVAILABLE
}

/**
 * EcoFlow specific BLE pipeline readiness states.
 */
enum class EcoFlowBleReadiness {
  NOT_SUPPORTED,
  PERMISSION_REQUIRED,
  BLUETOOTH_DISABLED,
  READY_TO_SCAN,
  DEVICE_NOT_FOUND,
  GATT_CONNECTING,
  GATT_DISCOVERING,
  PROTOCOL_DETECTING,
  PROTOCOL_NOT_VERIFIED,
  AUTHENTICATING,
  AUTH_FAILED,
  READY,
  DISCONNECTED,
  RECONNECTING,
  ERROR;

  val isReady: Boolean
    get() = this == READY
}

/**
 * High-level system readiness level.
 */
enum class SystemReadiness(val label: String, val color: Color) {
  GREEN("READY", Color(0xFF10B981)),
  YELLOW("ACTION REQUIRED", Color(0xFFF59E0B)),
  RED("CRITICAL PERMISSION MISSING", Color(0xFFEF4444)),
  GRAY("NOT SUPPORTED", Color(0xFF6B7280))
}

/**
 * Represents an item in the central permission matrix registry.
 */
data class PermissionItem(
  val id: String,
  val name: String,
  val description: String,
  val requiredFor: String,
  val platform: String = "Android",
  val currentStatus: PermissionStatus,
  val required: Boolean,
  val requestable: Boolean,
  val systemSettingsRoute: String,
  val fallbackRoute: String = "AppDetails",
  val verificationMethod: String,
  val riskLevel: RiskLevel,
  val userMessageMyanmar: String,
  val userMessageEnglish: String
)

/**
 * Consolidated snapshot of all device permissions and hardware states.
 */
data class SystemPermissionsState(
  val timestamp: Long = System.currentTimeMillis(),
  val microphoneReadiness: MicrophoneReadiness = MicrophoneReadiness.MIC_PERMISSION_DENIED,
  val bluetoothReadiness: BluetoothReadiness = BluetoothReadiness.BLUETOOTH_DISABLED,
  val notificationReadiness: NotificationReadiness = NotificationReadiness.NOTIFICATION_PERMISSION_DENIED,
  val accessibilityReadiness: AccessibilityReadiness = AccessibilityReadiness.ACCESSIBILITY_SERVICE_DISABLED,
  val overlayReadiness: OverlayReadiness = OverlayReadiness.OVERLAY_DENIED,
  val batteryReadiness: BatteryReadiness = BatteryReadiness.OPTIMIZED,
  val isLocationGranted: Boolean = false,
  val isNetworkConnected: Boolean = false,
  val isFirebaseConfigured: Boolean = true,
  val isEcoFlowCloudConfigured: Boolean = false,
  val ecoFlowBleReadiness: EcoFlowBleReadiness = EcoFlowBleReadiness.PERMISSION_REQUIRED,
  val isVoiceForegroundServiceActive: Boolean = false,
  val overallReadiness: SystemReadiness = SystemReadiness.YELLOW,
  val manufacturer: String = android.os.Build.MANUFACTURER,
  val androidVersion: Int = android.os.Build.VERSION.SDK_INT,
  val items: List<PermissionItem> = emptyList()
)

/**
 * Structure for diagnostic logging of permission and settings operations.
 */
data class DiagnosticLogEntry(
  val timestamp: Long = System.currentTimeMillis(),
  val operation: String,
  val setting: String,
  val previousState: String,
  val newState: String,
  val result: String,
  val error: String? = null,
  val platform: String = "Android API ${android.os.Build.VERSION.SDK_INT}",
  val androidVersion: Int = android.os.Build.VERSION.SDK_INT
) {
  fun formatTime(): String {
    val sdf = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    return sdf.format(Date(timestamp))
  }
}
