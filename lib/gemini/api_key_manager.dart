/// Secure storage for the user's Gemini API key (BYOK).
///
/// The key is stored in the platform secure store (Android Keystore via
/// flutter_secure_storage) — never in plain preferences, never logged,
/// never hardcoded. Mirrors the D9 posture of the Kyaw Gyi AI Studio app.

import 'package:flutter_secure_storage/flutter_secure_storage.dart';

class ApiKeyManager {
  static const String _storageKey = 'gemini_api_key';
  static const FlutterSecureStorage _storage = FlutterSecureStorage();

  /// Saves the key after trimming. Throws [ArgumentError] on empty input.
  Future<void> saveKey(String key) async {
    final k = key.trim();
    if (k.isEmpty) {
      throw ArgumentError('API key is empty');
    }
    await _storage.write(key: _storageKey, value: k);
  }

  Future<String?> getKey() => _storage.read(key: _storageKey);

  Future<bool> hasKey() async {
    final k = await getKey();
    return k != null && k.isNotEmpty;
  }

  Future<void> clearKey() => _storage.delete(key: _storageKey);

  /// Masked display form: ••••1234 (never reveal the full key in UI/logs).
  static String maskKey(String key) {
    if (key.length <= 4) return '••••';
    return '••••${key.substring(key.length - 4)}';
  }
}
