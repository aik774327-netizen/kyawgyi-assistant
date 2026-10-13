package com.kyawgyi.assistant.controller

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * DeviceController — local device controls via Android Intents and system services.
 *
 * PLATFORM RESTRICTIONS (honest):
 *  - Wi-Fi toggle: Android 10+ (API 29+) forbids direct toggling by third-party
 *    apps. We open Settings.Panel / Wi-Fi settings for the user instead.
 *  - Bluetooth toggle: API 33+ needs BLUETOOTH_CONNECT + system dialog; we open
 *    Bluetooth settings rather than pretending to toggle.
 *  - Brightness: writing Settings.System needs WRITE_SETTINGS (special
 *    app-access). Without it we open Display settings instead of failing silently.
 *
 * Every startActivity call is guarded — a missing handler returns false
 * instead of crashing the app.
 *
 * Compile status: NOT VERIFIED (no JDK/Android SDK in this environment, ER-006).
 */
class DeviceController(private val context: Context) {

    /** Friendly names for common apps — avoids raw package strings from voice input. */
    private val appAllowlist = mapOf(
        "phone" to "com.android.dialer",
        "dialer" to "com.android.dialer",
        // FIX #11: Burmese aliases — the assistant is Burmese-first.
        "ဖုန်း" to "com.android.dialer",
        "messages" to "com.google.android.apps.messaging",
        "sms" to "com.google.android.apps.messaging",
        "မက်ဆေ့ချ်" to "com.google.android.apps.messaging",
        "စာတို" to "com.google.android.apps.messaging",
        "camera" to "com.android.camera",
        "ကင်မရာ" to "com.android.camera",
        "gallery" to "com.google.android.apps.photos",
        "photos" to "com.google.android.apps.photos",
        "ဓာတ်ပုံ" to "com.google.android.apps.photos",
        "maps" to "com.google.android.apps.maps",
        "မြေပုံ" to "com.google.android.apps.maps",
        "chrome" to "com.android.chrome",
        "browser" to "com.android.chrome",
        "ဘရောက်ဇာ" to "com.android.chrome",
        "settings" to "com.android.settings",
        "ဆက်တင်" to "com.android.settings",
        "clock" to "com.google.android.deskclock",
        "နာရီ" to "com.google.android.deskclock",
        "calendar" to "com.google.android.calendar",
        "ပြက္ခဒိန်" to "com.google.android.calendar"
    )

    // ------------------------------------------------------------------ Wi-Fi / Bluetooth

    /**
     * Opens Wi-Fi controls. Returns true if a settings UI was launched.
     * (Direct toggle is impossible on API 29+ — we do not fake it.)
     */
    fun toggleWifi(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return try {
                val panelIntent = Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(panelIntent)
                true
            } catch (e: Exception) {
                openSettingsPage(Settings.ACTION_WIFI_SETTINGS)
            }
        } else {
            @Suppress("DEPRECATION")
            return try {
                val wifiManager = context.applicationContext
                    .getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
                    ?: return false
                @Suppress("DEPRECATION")
                wifiManager.isWifiEnabled = !wifiManager.isWifiEnabled
                true
            } catch (e: SecurityException) {
                openSettingsPage(Settings.ACTION_WIFI_SETTINGS)
            } catch (e: Exception) {
                false
            }
        }
    }

    /** Opens Bluetooth settings (direct toggle needs system dialog on API 33+). */
    fun toggleBluetooth(): Boolean =
        openSettingsPage(Settings.ACTION_BLUETOOTH_SETTINGS)

    /** Opens Display settings. */
    fun openDisplaySettings(): Boolean =
        openSettingsPage(Settings.ACTION_DISPLAY_SETTINGS)

    // ------------------------------------------------------------------ apps & URLs

    /**
     * Opens an app by friendly name ("phone") or package ("com.android.dialer").
     * Unknown names return false — never launches an arbitrary component.
     */
    fun openApp(nameOrPackage: String): Boolean {
        val key = nameOrPackage.trim().lowercase()
        if (key.isEmpty()) return false
        val pkg = appAllowlist[key] ?: if (isValidPackageName(key)) key else return false
        return try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(pkg)
                ?: return false
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
            true
        } catch (e: ActivityNotFoundException) {
            false
        } catch (e: SecurityException) {
            false
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Opens a URL. Only http/https schemes are allowed — anything else
     * (tel:, intent:, file:, …) is rejected to prevent intent abuse.
     */
    fun openUrl(url: String): Boolean {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return false
        val uri = try {
            Uri.parse(trimmed)
        } catch (e: Exception) {
            return false
        }
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return false
        return try {
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            false
        } catch (e: SecurityException) {
            false
        } catch (e: Exception) {
            false
        }
    }

    // ------------------------------------------------------------------ volume & brightness

    /**
     * Sets a volume stream level. [level01] is 0.0–1.0; values are clamped.
     * No special permission required for standard streams.
     */
    fun setVolume(streamType: Int, level01: Float): Boolean {
        return try {
            val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                ?: return false
            val max = audio.getStreamMaxVolume(streamType)
            if (max <= 0) return false
            val level = (level01.coerceIn(0f, 1f) * max).toInt().coerceIn(0, max)
            audio.setStreamVolume(streamType, level, 0)
            true
        } catch (e: SecurityException) {
            false
        } catch (e: Exception) {
            false
        }
    }

    /** Convenience: music/media stream volume 0.0–1.0. */
    fun setMediaVolume(level01: Float): Boolean =
        setVolume(AudioManager.STREAM_MUSIC, level01)

    /**
     * Sets screen brightness 0–255. Needs WRITE_SETTINGS (special app access);
     * without it we open Display settings instead of failing silently.
     */
    fun setBrightness(brightness01: Float): Boolean {
        val value = (brightness01.coerceIn(0f, 1f) * 255).toInt().coerceIn(1, 255)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            !Settings.System.canWrite(context)
        ) {
            openDisplaySettings()
            return false
        }
        return try {
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                value
            )
            true
        } catch (e: SecurityException) {
            openDisplaySettings()
            false
        } catch (e: Exception) {
            false
        }
    }

    // ------------------------------------------------------------------ helpers

    private fun openSettingsPage(action: String): Boolean {
        return try {
            val intent = Intent(action).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            false
        } catch (e: SecurityException) {
            false
        } catch (e: Exception) {
            false
        }
    }

    private fun isValidPackageName(name: String): Boolean {
        // Conservative package-name shape: segments of [a-zA-Z0-9_], dots between.
        return name.matches(Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+$")) &&
            name.length <= 255
    }
}
