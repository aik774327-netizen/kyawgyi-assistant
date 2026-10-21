package com.kyawgyi.voice

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Supported microphone audio input routes for KyawGyi Voice Assistant.
 */
enum class AudioInputRoute(val displayLabel: String, val defaultSampleRateHz: Int) {
    BUILTIN_MIC("Built-in Phone Mic (16kHz)", 16000),
    BLUETOOTH_SCO("Bluetooth Headset SCO (8kHz/16kHz)", 16000),
    BLUETOOTH_BLE("Bluetooth LE Audio Headset (16kHz/24kHz)", 16000),
    WIRED_HEADSET("Wired Headset Mic (48kHz/16kHz)", 16000),
    USB_HEADSET("USB Audio Mic (48kHz/16kHz)", 16000)
}

/**
 * Immutable thread-safe snapshot of the current audio input routing state.
 */
data class AudioInputRouteState(
    val activeRoute: AudioInputRoute = AudioInputRoute.BUILTIN_MIC,
    val preferredRoute: AudioInputRoute? = null,
    val availableRoutes: List<AudioInputRoute> = listOf(AudioInputRoute.BUILTIN_MIC),
    val activeDeviceName: String = "Built-in Microphone",
    val hardwareSampleRateHz: Int = 16000,
    val requiresResamplingTo16k: Boolean = false,
    val isBluetoothScoActive: Boolean = false
)

/**
 * AudioInputRouter (Requirements 9 — Dynamic Bluetooth SCO / BLE / Wired / Built-in Mic Router)
 *
 * Automatically detects connected audio input peripherals via [AudioDeviceCallback] and routes
 * voice assistant microphone capture using [AudioManager.setCommunicationDevice] on API 31+
 * or legacy [AudioManager.startBluetoothSco] on older Android versions.
 */
class AudioInputRouter(context: Context) {

    companion object {
        private const val TAG = "AudioInputRouter"
        const val TARGET_STT_SAMPLE_RATE_HZ = 16000
    }

    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _routeState = MutableStateFlow(AudioInputRouteState())
    val routeState: StateFlow<AudioInputRouteState> = _routeState.asStateFlow()

    private var deviceCallbackRegistered = false

    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            refreshAvailableRoutes()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            refreshAvailableRoutes()
        }
    }

    init {
        registerDeviceCallback()
        refreshAvailableRoutes()
    }

    fun registerDeviceCallback() {
        val am = audioManager ?: return
        if (!deviceCallbackRegistered) {
            runCatching {
                am.registerAudioDeviceCallback(audioDeviceCallback, mainHandler)
                deviceCallbackRegistered = true
            }.onFailure { e ->
                Log.w(TAG, "Unable to register AudioDeviceCallback: ${e.message}")
            }
        }
    }

    /**
     * Scans connected audio input devices and updates [routeState] atomically.
     */
    fun refreshAvailableRoutes(): AudioInputRouteState {
        val am = audioManager
        if (am == null) {
            return _routeState.value
        }

        val inputDevices = runCatching {
            am.getDevices(AudioManager.GET_DEVICES_INPUTS).toList()
        }.getOrDefault(emptyList())

        val detectedRoutes = linkedSetOf(AudioInputRoute.BUILTIN_MIC)
        var bestDeviceName = "Built-in Microphone"
        var detectedHardwareRate = TARGET_STT_SAMPLE_RATE_HZ

        for (device in inputDevices) {
            when (device.type) {
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> {
                    detectedRoutes.add(AudioInputRoute.BLUETOOTH_SCO)
                    bestDeviceName = device.productName?.toString()?.takeIf { it.isNotBlank() } ?: "Bluetooth SCO Mic"
                    detectedHardwareRate = pickBestHardwareRate(device.sampleRates, 8000)
                }
                AudioDeviceInfo.TYPE_BLE_HEADSET -> {
                    detectedRoutes.add(AudioInputRoute.BLUETOOTH_BLE)
                    bestDeviceName = device.productName?.toString()?.takeIf { it.isNotBlank() } ?: "BLE Audio Headset"
                    detectedHardwareRate = pickBestHardwareRate(device.sampleRates, 16000)
                }
                AudioDeviceInfo.TYPE_WIRED_HEADSET -> {
                    detectedRoutes.add(AudioInputRoute.WIRED_HEADSET)
                    bestDeviceName = device.productName?.toString()?.takeIf { it.isNotBlank() } ?: "Wired Headset Mic"
                    detectedHardwareRate = pickBestHardwareRate(device.sampleRates, 48000)
                }
                AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE -> {
                    detectedRoutes.add(AudioInputRoute.USB_HEADSET)
                    bestDeviceName = device.productName?.toString()?.takeIf { it.isNotBlank() } ?: "USB Audio Mic"
                    detectedHardwareRate = pickBestHardwareRate(device.sampleRates, 48000)
                }
            }
        }

        val currentPreferred = _routeState.value.preferredRoute
        val resolvedActive = when {
            currentPreferred != null && detectedRoutes.contains(currentPreferred) -> currentPreferred
            detectedRoutes.contains(AudioInputRoute.BLUETOOTH_BLE) -> AudioInputRoute.BLUETOOTH_BLE
            detectedRoutes.contains(AudioInputRoute.BLUETOOTH_SCO) -> AudioInputRoute.BLUETOOTH_SCO
            detectedRoutes.contains(AudioInputRoute.USB_HEADSET) -> AudioInputRoute.USB_HEADSET
            detectedRoutes.contains(AudioInputRoute.WIRED_HEADSET) -> AudioInputRoute.WIRED_HEADSET
            else -> AudioInputRoute.BUILTIN_MIC
        }

        if (resolvedActive == AudioInputRoute.BUILTIN_MIC) {
            bestDeviceName = "Built-in Microphone"
            detectedHardwareRate = TARGET_STT_SAMPLE_RATE_HZ
        }

        _routeState.update { prev ->
            prev.copy(
                activeRoute = resolvedActive,
                availableRoutes = detectedRoutes.toList(),
                activeDeviceName = bestDeviceName,
                hardwareSampleRateHz = detectedHardwareRate,
                requiresResamplingTo16k = detectedHardwareRate != TARGET_STT_SAMPLE_RATE_HZ
            )
        }
        return _routeState.value
    }

    /**
     * Activates the requested [route] (or auto-selects the highest priority headset mic if null).
     */
    fun activateRoute(route: AudioInputRoute? = null): Boolean {
        val am = audioManager
        val stateAfterScan = refreshAvailableRoutes()
        val targetRoute = route ?: stateAfterScan.activeRoute

        if (am == null) {
            _routeState.update {
                it.copy(
                    activeRoute = targetRoute,
                    preferredRoute = route,
                    hardwareSampleRateHz = targetRoute.defaultSampleRateHz,
                    requiresResamplingTo16k = targetRoute.defaultSampleRateHz != TARGET_STT_SAMPLE_RATE_HZ
                )
            }
            return true
        }

        var scoEnabled = false
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val devices = am.availableCommunicationDevices
                val matchingDevice = devices.firstOrNull { dev ->
                    when (targetRoute) {
                        AudioInputRoute.BLUETOOTH_BLE -> dev.type == AudioDeviceInfo.TYPE_BLE_HEADSET
                        AudioInputRoute.BLUETOOTH_SCO -> dev.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                        AudioInputRoute.WIRED_HEADSET -> dev.type == AudioDeviceInfo.TYPE_WIRED_HEADSET
                        AudioInputRoute.USB_HEADSET ->
                            dev.type == AudioDeviceInfo.TYPE_USB_HEADSET || dev.type == AudioDeviceInfo.TYPE_USB_DEVICE
                        AudioInputRoute.BUILTIN_MIC -> dev.type == AudioDeviceInfo.TYPE_BUILTIN_MIC
                    }
                }
                if (matchingDevice != null) {
                    scoEnabled = am.setCommunicationDevice(matchingDevice)
                } else if (targetRoute == AudioInputRoute.BUILTIN_MIC) {
                    am.clearCommunicationDevice()
                    scoEnabled = false
                }
            } else {
                @Suppress("DEPRECATION")
                if (targetRoute == AudioInputRoute.BLUETOOTH_SCO) {
                    am.mode = AudioManager.MODE_IN_COMMUNICATION
                    am.startBluetoothSco()
                    am.isBluetoothScoOn = true
                    scoEnabled = true
                } else {
                    am.isBluetoothScoOn = false
                    am.stopBluetoothSco()
                }
            }
        }.onFailure { e ->
            Log.w(TAG, "Failed to switch communication device to $targetRoute: ${e.message}")
        }

        val effectiveRate = when (targetRoute) {
            AudioInputRoute.BLUETOOTH_SCO -> if (_routeState.value.hardwareSampleRateHz == 8000) 8000 else 16000
            else -> _routeState.value.hardwareSampleRateHz.takeIf { it > 0 } ?: TARGET_STT_SAMPLE_RATE_HZ
        }

        _routeState.update { prev ->
            prev.copy(
                activeRoute = targetRoute,
                preferredRoute = route,
                hardwareSampleRateHz = effectiveRate,
                requiresResamplingTo16k = effectiveRate != TARGET_STT_SAMPLE_RATE_HZ,
                isBluetoothScoActive = scoEnabled || targetRoute == AudioInputRoute.BLUETOOTH_SCO
            )
        }
        return true
    }

    /**
     * Releases any active Bluetooth SCO or communication device lock and reverts to normal mode.
     */
    fun releaseRoute() {
        val am = audioManager ?: return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                am.clearCommunicationDevice()
            } else {
                @Suppress("DEPRECATION")
                if (am.isBluetoothScoOn) {
                    am.isBluetoothScoOn = false
                    am.stopBluetoothSco()
                }
            }
        }
        _routeState.update { prev ->
            prev.copy(
                activeRoute = AudioInputRoute.BUILTIN_MIC,
                isBluetoothScoActive = false,
                hardwareSampleRateHz = TARGET_STT_SAMPLE_RATE_HZ,
                requiresResamplingTo16k = false
            )
        }
    }

    fun close() {
        releaseRoute()
        val am = audioManager ?: return
        if (deviceCallbackRegistered) {
            runCatching { am.unregisterAudioDeviceCallback(audioDeviceCallback) }
            deviceCallbackRegistered = false
        }
    }

    private fun pickBestHardwareRate(supportedRates: IntArray?, fallbackRate: Int): Int {
        if (supportedRates == null || supportedRates.isEmpty()) return fallbackRate
        if (supportedRates.contains(TARGET_STT_SAMPLE_RATE_HZ)) return TARGET_STT_SAMPLE_RATE_HZ
        if (supportedRates.contains(48000)) return 48000
        if (supportedRates.contains(44100)) return 44100
        if (supportedRates.contains(24000)) return 24000
        if (supportedRates.contains(8000)) return 8000
        return supportedRates.first()
    }
}
