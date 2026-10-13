package com.kyawgyi.assistant.channel

import com.kyawgyi.assistant.service.VoiceForegroundService
import io.flutter.plugin.common.EventChannel

/**
 * Bridges the native mic PCM stream to Flutter.
 *
 * Channel: "com.kyawgyi.assistant/audio_stream"
 * Payload: 16-bit mono PCM @ 16 kHz as byte[] (delivered as Uint8List in Dart),
 * one event per AudioRecord read (~4096 bytes, ~8 events/sec).
 *
 * Flow: Dart subscribes -> onListen sets [VoiceForegroundService.pcmTap] ->
 * the service drain loop forwards each chunk. onCancel clears the tap so the
 * service goes back to drain-and-discard. EventSink is thread-safe, so calls
 * from the IO drain coroutine are fine.
 *
 * FIX (mic/stt/tts audit #3): the native mic path previously drained to
 * nowhere — startStt produced no audio for Flutter. Register in MainActivity.
 *
 * NOTE: RECORD_AUDIO must be granted at runtime by the Flutter layer
 * (e.g. permission_handler) before startStt; the service fails closed without it.
 *
 * Compile status: NOT VERIFIED (no JDK/Android SDK in this environment, ER-006).
 */
class AudioStreamHandler : EventChannel.StreamHandler {

    companion object {
        const val CHANNEL_NAME = "com.kyawgyi.assistant/audio_stream"
    }

    override fun onListen(args: Any?, events: EventChannel.EventSink) {
        VoiceForegroundService.pcmTap = { bytes, len ->
            try {
                // copyOf: the drain buffer is reused across reads
                events.success(bytes.copyOf(len))
            } catch (e: Exception) {
                // sink gone (cancel raced) — ignore, tap cleared on cancel
            }
        }
    }

    override fun onCancel(args: Any?) {
        VoiceForegroundService.pcmTap = null
    }
}
