package com.kyawgyi.assistant

import com.kyawgyi.assistant.channel.AudioStreamHandler
import com.kyawgyi.assistant.channel.NativeMethodChannelHandler
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodChannel

/**
 * MainActivity — hosts the Flutter UI and registers the native bridge.
 *
 * Bridge channel: "com.kyawgyi.assistant/bridge"
 * (see NativeMethodChannelHandler for the full method list).
 *
 * Compile status: NOT VERIFIED (no JDK/Android SDK in this environment, ER-006).
 */
class MainActivity : FlutterActivity() {

    private var bridgeChannel: MethodChannel? = null
    private var audioStreamChannel: EventChannel? = null

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        bridgeChannel = MethodChannel(
            flutterEngine.dartExecutor.binaryMessenger,
            NativeMethodChannelHandler.CHANNEL_NAME
        ).also { channel ->
            channel.setMethodCallHandler(NativeMethodChannelHandler(this))
        }
        // FIX (mic/stt/tts audit #3): PCM stream for native STT consumers.
        audioStreamChannel = EventChannel(
            flutterEngine.dartExecutor.binaryMessenger,
            AudioStreamHandler.CHANNEL_NAME
        ).also { channel ->
            channel.setStreamHandler(AudioStreamHandler())
        }
    }

    override fun cleanUpFlutterEngine(flutterEngine: FlutterEngine) {
        try {
            bridgeChannel?.setMethodCallHandler(null)
            // FIX #19: clearing the stream handler forces onCancel, which
            // clears VoiceForegroundService.pcmTap — otherwise the service
            // would keep invoking a dead EventSink.
            audioStreamChannel?.setStreamHandler(null)
        } catch (e: Exception) {
            // ignore — engine is going away
        } finally {
            bridgeChannel = null
            audioStreamChannel = null
        }
        super.cleanUpFlutterEngine(flutterEngine)
    }
}
