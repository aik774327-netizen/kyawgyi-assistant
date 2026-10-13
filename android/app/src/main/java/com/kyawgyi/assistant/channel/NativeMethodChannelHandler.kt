package com.kyawgyi.assistant.channel

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.kyawgyi.assistant.controller.DeviceController
import com.kyawgyi.assistant.service.KyawGyiAccessibilityService
import com.kyawgyi.assistant.service.VoiceForegroundService
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel

/**
 * NativeMethodChannelHandler — Flutter <-> Kotlin bridge.
 *
 * Channel: "com.kyawgyi.assistant/bridge" (per Enterprise Architecture Blueprint).
 *
 * Security notes:
 *  - All arguments are validated before use (no raw strings reach Intents).
 *  - performTap requires the user-enabled AccessibilityService; otherwise a
 *    structured error is returned (never a silent no-op).
 *  - startStt/stopStt only manage the native listening session; the actual
 *    speech-to-text engine lives in the Flutter layer.
 *
 * Compile status: NOT VERIFIED (no JDK/Android SDK in this environment, ER-006).
 */
class NativeMethodChannelHandler(private val context: Context) : MethodChannel.MethodCallHandler {

    companion object {
        /** Blueprint-specified channel name. */
        const val CHANNEL_NAME = "com.kyawgyi.assistant/bridge"
    }

    private val deviceController = DeviceController(context)

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            // ------------------------------------------------ foreground service
            "startVoiceService" -> {
                startServiceWithAction(VoiceForegroundService.ACTION_START)
                result.success(true)
            }
            "stopVoiceService" -> {
                startServiceWithAction(VoiceForegroundService.ACTION_STOP)
                result.success(true)
            }
            "isVoiceServiceRunning" -> {
                result.success(VoiceForegroundService.isServiceRunning)
            }

            // ------------------------------------------------ STT session triggers
            "startStt" -> {
                // FIX #14: honest start — a Service cannot show the permission
                // dialog, so without RECORD_AUDIO the service would fail closed
                // silently while we reported success. Check here instead.
                if (ContextCompat.checkSelfPermission(
                        context,
                        android.Manifest.permission.RECORD_AUDIO
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    result.error(
                        "PERMISSION_DENIED",
                        "RECORD_AUDIO not granted — request it in the Flutter layer first",
                        null
                    )
                } else {
                    // Starts mic capture in the foreground service. The Flutter STT
                    // engine consumes audio via its own pipeline.
                    startServiceWithAction(VoiceForegroundService.ACTION_START_LISTENING)
                    result.success(true)
                }
            }
            "stopStt" -> {
                startServiceWithAction(VoiceForegroundService.ACTION_STOP_LISTENING)
                result.success(true)
            }
            // FIX #14: lets Flutter verify the listening state instead of
            // trusting the fire-and-forget startStt success.
            "isListening" -> {
                result.success(VoiceForegroundService.isListening)
            }

            // ------------------------------------------------ device controls
            "toggleWifi" -> result.success(deviceController.toggleWifi())
            "toggleBluetooth" -> result.success(deviceController.toggleBluetooth())
            "openDisplaySettings" -> result.success(deviceController.openDisplaySettings())

            "openApp" -> {
                val name = call.argument<String>("name")
                if (name.isNullOrBlank()) {
                    result.error("INVALID_ARGUMENT", "Missing 'name' argument", null)
                } else {
                    result.success(deviceController.openApp(name))
                }
            }
            "openUrl" -> {
                val url = call.argument<String>("url")
                if (url.isNullOrBlank()) {
                    result.error("INVALID_ARGUMENT", "Missing 'url' argument", null)
                } else {
                    result.success(deviceController.openUrl(url))
                }
            }
            "setVolume" -> {
                // level: 0.0–1.0 ; stream: "music" | "call" | "alarm" | "notification"
                val level = call.argument<Double>("level")
                val stream = when (call.argument<String>("stream")?.lowercase()) {
                    "call" -> AudioManager.STREAM_VOICE_CALL
                    "alarm" -> AudioManager.STREAM_ALARM
                    "notification" -> AudioManager.STREAM_NOTIFICATION
                    else -> AudioManager.STREAM_MUSIC
                }
                if (level == null) {
                    result.error("INVALID_ARGUMENT", "Missing 'level' argument", null)
                } else {
                    result.success(deviceController.setVolume(stream, level.toFloat()))
                }
            }
            "setBrightness" -> {
                val level = call.argument<Double>("level")
                if (level == null) {
                    result.error("INVALID_ARGUMENT", "Missing 'level' argument", null)
                } else {
                    result.success(deviceController.setBrightness(level.toFloat()))
                }
            }

            // ------------------------------------------------ accessibility
            "performTap" -> {
                val x = call.argument<Double>("x")?.toFloat()
                val y = call.argument<Double>("y")?.toFloat()
                if (x == null || y == null) {
                    result.error("INVALID_ARGUMENT", "Missing 'x'/'y' arguments", null)
                    return
                }
                val service = KyawGyiAccessibilityService.getInstance()
                if (service != null) {
                    service.performTap(x, y) { success -> result.success(success) }
                } else {
                    result.error(
                        "ACCESSIBILITY_NOT_RUNNING",
                        "KyawGyiAccessibilityService is not enabled by the user",
                        null
                    )
                }
            }
            "goBack" -> {
                val service = KyawGyiAccessibilityService.getInstance()
                if (service != null) result.success(service.goBack())
                else result.error("ACCESSIBILITY_NOT_RUNNING", "Accessibility service not enabled", null)
            }
            "goHome" -> {
                val service = KyawGyiAccessibilityService.getInstance()
                if (service != null) result.success(service.goHome())
                else result.error("ACCESSIBILITY_NOT_RUNNING", "Accessibility service not enabled", null)
            }
            // FIX #15: openRecents() existed natively but had no bridge method.
            "openRecents" -> {
                val service = KyawGyiAccessibilityService.getInstance()
                if (service != null) result.success(service.openRecents())
                else result.error("ACCESSIBILITY_NOT_RUNNING", "Accessibility service not enabled", null)
            }
            "readScreen" -> {
                val service = KyawGyiAccessibilityService.getInstance()
                if (service != null) {
                    // D5: text stays in memory, handed to Flutter only — never logged.
                    service.readVisibleText { texts -> result.success(texts) }
                } else {
                    result.error("ACCESSIBILITY_NOT_RUNNING", "Accessibility service not enabled", null)
                }
            }

            else -> result.notImplemented()
        }
    }

    private fun startServiceWithAction(action: String) {
        val intent = Intent(context, VoiceForegroundService::class.java).apply {
            this.action = action
        }
        try {
            // FIX #13: stopping needs no foreground start — startService avoids
            // ForegroundServiceStartNotAllowedException on Android 12+ when the
            // app is in the background.
            if (action == VoiceForegroundService.ACTION_STOP ||
                Build.VERSION.SDK_INT < Build.VERSION_CODES.O
            ) {
                @Suppress("DEPRECATION")
                context.startService(intent)
            } else {
                context.startForegroundService(intent)
            }
        } catch (e: Exception) {
            // ForegroundServiceStartNotAllowedException etc. — caller sees
            // isVoiceServiceRunning == false; Flutter layer handles the UX.
        }
    }
}
