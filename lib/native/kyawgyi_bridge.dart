import 'package:flutter/services.dart';

/// Dart counterpart of [NativeMethodChannelHandler]
/// (Kotlin, channel: "com.kyawgyi.assistant/bridge").
///
/// Every method mirrors a native handler case 1:1. Methods returning `bool`
/// report whether the native side actually performed the action.
///
/// NOTE (mic/stt/tts audit #4): the Flutter app MUST request RECORD_AUDIO at
/// runtime (e.g. via permission_handler) before [startStt]/[startVoiceService];
/// the native service fails closed without it.
///
/// Compile status: NOT VERIFIED (no Flutter SDK in this environment).
class KyawGyiBridge {
  KyawGyiBridge._();

  static const MethodChannel _channel =
      MethodChannel('com.kyawgyi.assistant/bridge');

  /// Raw mic PCM stream (16-bit mono @ 16 kHz, ~8 chunks/sec).
  /// Subscribe AFTER [startStt]; cancel to stop streaming.
  /// FIX (mic/stt/tts audit #3): native audio previously drained to nowhere.
  static const EventChannel _audioStream =
      EventChannel('com.kyawgyi.assistant/audio_stream');

  static Stream<Uint8List> get pcmStream =>
      _audioStream.receiveBroadcastStream().map((e) => e as Uint8List);

  static Future<bool> _callBool(String method,
      [Map<String, dynamic>? args]) async {
    try {
      final result = await _channel.invokeMethod<bool>(method, args);
      return result ?? false;
    } on PlatformException {
      return false;
    } on MissingPluginException {
      // FIX #20: native side not registered (wrong engine / desktop) —
      // must not crash the Dart isolate.
      return false;
    }
  }

  // ------------------------------------------------ foreground service
  static Future<bool> startVoiceService() => _callBool('startVoiceService');
  static Future<bool> stopVoiceService() => _callBool('stopVoiceService');

  static Future<bool> isVoiceServiceRunning() async {
    try {
      return await _channel.invokeMethod<bool>('isVoiceServiceRunning') ??
          false;
    } on PlatformException {
      return false;
    } on MissingPluginException {
      return false; // FIX #20
    }
  }

  // ------------------------------------------------ STT session triggers
  static Future<bool> startStt() => _callBool('startStt');
  static Future<bool> stopStt() => _callBool('stopStt');

  /// Pollable listening state — verify startStt actually took effect.
  /// FIX (audit #14): startStt is fire-and-forget; don't trust it blindly.
  static Future<bool> isListening() async {
    try {
      return await _channel.invokeMethod<bool>('isListening') ?? false;
    } on PlatformException {
      return false;
    } on MissingPluginException {
      return false; // FIX #20
    }
  }

  // ------------------------------------------------ device controls
  static Future<bool> toggleWifi() => _callBool('toggleWifi');
  static Future<bool> toggleBluetooth() => _callBool('toggleBluetooth');
  static Future<bool> openDisplaySettings() =>
      _callBool('openDisplaySettings');

  /// [nameOrPackage]: friendly name ("phone") or package ("com.android.dialer").
  static Future<bool> openApp(String nameOrPackage) =>
      _callBool('openApp', {'name': nameOrPackage});

  /// Only http/https URLs are accepted natively.
  static Future<bool> openUrl(String url) =>
      _callBool('openUrl', {'url': url});

  /// [level]: 0.0 - 1.0. [stream]: "music" | "call" | "alarm" | "notification".
  static Future<bool> setVolume(double level, {String stream = 'music'}) =>
      _callBool('setVolume', {
        'level': level.clamp(0.0, 1.0),
        'stream': stream,
      });

  /// [level]: 0.0 - 1.0.
  static Future<bool> setBrightness(double level) =>
      _callBool('setBrightness', {'level': level.clamp(0.0, 1.0)});

  // ------------------------------------------------ accessibility
  /// Requires the user-enabled accessibility service; false otherwise.
  static Future<bool> performTap(double x, double y) =>
      _callBool('performTap', {'x': x, 'y': y});

  static Future<bool> goBack() => _callBool('goBack');
  static Future<bool> goHome() => _callBool('goHome');

  /// Recent apps overview. FIX (audit #15): was native-only, now bridged.
  static Future<bool> openRecents() => _callBool('openRecents');

  /// Returns visible on-screen texts (D5: memory-only, never logged).
  static Future<List<String>> readScreen() async {
    try {
      final result =
          await _channel.invokeListMethod<String>('readScreen');
      return result ?? <String>[];
    } on PlatformException {
      return <String>[];
    } on MissingPluginException {
      return <String>[]; // FIX #20
    }
  }
}
