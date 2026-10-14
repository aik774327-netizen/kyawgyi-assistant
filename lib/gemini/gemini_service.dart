/// Google Gemini API client: key validation, Myanmar STT, chat, Myanmar TTS.
///
/// All calls use the user's own API key (BYOK) via the `?key=` query
/// parameter (documented pattern). The key is never logged.
///
/// Audio formats:
/// - STT input: WAV (16-bit PCM mono). The native mic stream delivers
///   16 kHz mono s16le — [pcmToWav] wraps it with a RIFF header.
/// - TTS output: the 2.5 TTS models return raw PCM s16le 24 kHz mono
///   (MIME audio/L16;codec=pcm;rate=24000) — [pcmToWav] wraps it so the
///   bytes are directly playable. Newer models may return WAV already;
///   [speak] detects this from the MIME type.

import 'dart:convert';
import 'dart:typed_data';

import 'package:http/http.dart' as http;

import 'gemini_config.dart';

/// API error with a user-facing Burmese message.
class GeminiApiException implements Exception {
  final String message; // Burmese, safe to show in UI
  final int? statusCode;
  const GeminiApiException(this.message, [this.statusCode]);

  @override
  String toString() => message;
}

class GeminiService {
  final http.Client _client;
  GeminiService({http.Client? client}) : _client = client ?? http.Client();

  void dispose() => _client.close();

  // ---------------------------------------------------------------- key check

  /// Lightweight key validation: GET /v1beta/models.
  /// Returns true on HTTP 200, false on 400/401/403. Throws on network errors.
  Future<bool> validateKey(String apiKey) async {
    final key = apiKey.trim();
    if (key.isEmpty) {
      throw const GeminiApiException('API key အရင်ထည့်သွင်းပါ');
    }
    late http.Response r;
    try {
      r = await _client
          .get(Uri.parse(GeminiConfig.modelsUrl(key)))
          .timeout(const Duration(seconds: 20));
    } catch (_) {
      throw const GeminiApiException(
          'စစ်ဆေး၍မရပါ — အကြောင်းရင်း: ကွန်ယက်ချိတ်ဆက်မှု အမှား');
    }
    if (r.statusCode == 200) return true;
    if (r.statusCode == 400 ||
        r.statusCode == 401 ||
        r.statusCode == 403) {
      return false;
    }
    if (r.statusCode == 429) {
      throw const GeminiApiException(
          'စစ်ဆေး၍မရပါ — အကြောင်းရင်း: API quota ကုန်နေပါသည်');
    }
    throw GeminiApiException('စစ်ဆေး၍မရပါ — အကြောင်းရင်း: HTTP ${r.statusCode}');
  }

  // ------------------------------------------------------------------- STT

  /// Transcribes WAV audio bytes to Myanmar text.
  /// Throws [GeminiApiException] with a Burmese message on failure.
  Future<String> transcribeWav(String apiKey, Uint8List wavBytes) async {
    if (wavBytes.isEmpty) {
      throw const GeminiApiException('အသံဖိုင် မရှိပါ');
    }
    final body = jsonEncode({
      'contents': [
        {
          'parts': [
            {'text': GeminiConfig.sttInstruction},
            {
              'inlineData': {
                'mimeType': 'audio/wav',
                'data': base64Encode(wavBytes),
              }
            },
          ]
        }
      ],
    });
    final text = await _generateContent(
      apiKey: apiKey,
      model: GeminiConfig.sttModel,
      body: body,
      timeoutSeconds: 90,
    );
    final t = text.trim();
    if (t.isEmpty) {
      throw const GeminiApiException('စကားသံ မကြားရပါ');
    }
    return t;
  }

  // ------------------------------------------------------------------ chat

  /// Generates the assistant's Myanmar reply for [userText].
  Future<String> chatReply(String apiKey, String userText) async {
    final body = jsonEncode({
      'systemInstruction': {
        'parts': [
          {'text': GeminiConfig.systemInstruction}
        ]
      },
      'contents': [
        {
          'parts': [
            {'text': userText}
          ]
        }
      ],
    });
    final text = await _generateContent(
      apiKey: apiKey,
      model: GeminiConfig.chatModel,
      body: body,
      timeoutSeconds: 60,
    );
    final t = text.trim();
    if (t.isEmpty) {
      throw const GeminiApiException('အဖြေ မရရှိပါ');
    }
    return t;
  }

  // ------------------------------------------------------------------- TTS

  /// Converts Myanmar [text] to spoken audio. Returns WAV bytes ready to play.
  Future<Uint8List> speak(String apiKey, String text, {String? voice}) async {
    final clean = text.trim();
    if (clean.isEmpty) {
      throw const GeminiApiException('စာသား မရှိပါ');
    }
    final body = jsonEncode({
      'contents': [
        {
          'parts': [
            {'text': clean}
          ]
        }
      ],
      'generationConfig': {
        // REQUIRED — without this the API returns HTTP 400.
        'responseModalities': ['AUDIO'],
        'speechConfig': {
          'voiceConfig': {
            'prebuiltVoiceConfig': {
              'voiceName': voice ?? GeminiConfig.defaultVoice,
            }
          }
        },
      },
    });

    late http.Response r;
    try {
      r = await _client
          .post(
            Uri.parse(
                '${GeminiConfig.generateContentUrl(GeminiConfig.ttsModel)}?key=${apiKey.trim()}'),
            headers: {'Content-Type': 'application/json'},
            body: body,
          )
          .timeout(const Duration(seconds: 90));
    } catch (_) {
      throw const GeminiApiException('အသံ ထုတ်မရပါ — ကွန်ယက်အမှား');
    }
    if (r.statusCode != 200) {
      throw _httpError(r.statusCode, 'အသံ ထုတ်မရပါ');
    }
    final decoded = _decodeJson(r.body);
    final part = _firstInlineAudioPart(decoded);
    if (part == null) {
      throw const GeminiApiException('အသံ ထုတ်မရပါ — audio ပြန်မလာပါ');
    }
    final data = base64Decode(part['data'] as String);
    final mime = (part['mimeType'] as String?) ?? '';
    // 2.5 TTS models return raw PCM s16le 24kHz mono → WAV-wrap it.
    if (mime.contains('L16') || mime.contains('pcm')) {
      return pcmToWav(data, sampleRate: 24000);
    }
    return data; // already WAV (newer models)
  }

  // -------------------------------------------------------------- internals

  /// Shared generateContent POST for text responses (STT transcript / chat).
  Future<String> _generateContent({
    required String apiKey,
    required String model,
    required String body,
    required int timeoutSeconds,
  }) async {
    final key = apiKey.trim();
    if (key.isEmpty) {
      throw const GeminiApiException('API key အရင်ထည့်သွင်းပါ');
    }
    late http.Response r;
    try {
      r = await _client
          .post(
            Uri.parse(
                '${GeminiConfig.generateContentUrl(model)}?key=$key'),
            headers: {'Content-Type': 'application/json'},
            body: body,
          )
          .timeout(Duration(seconds: timeoutSeconds));
    } catch (_) {
      throw const GeminiApiException('ကွန်ယက်ချိတ်ဆက်မှု အမှား');
    }
    if (r.statusCode != 200) {
      throw _httpError(r.statusCode, 'AI တုံ့ပြန်မှု မအောင်မြင်ပါ');
    }
    final decoded = _decodeJson(r.body);
    final text = _firstTextPart(decoded);
    return text ?? '';
  }

  Map<String, dynamic> _decodeJson(String body) {
    try {
      return jsonDecode(body) as Map<String, dynamic>;
    } catch (_) {
      throw const GeminiApiException('AI တုံ့ပြန်မှု ဖတ်မရပါ');
    }
  }

  /// First text part of candidates[0].content.parts, or null.
  String? _firstTextPart(Map<String, dynamic> decoded) {
    try {
      final candidates = decoded['candidates'] as List;
      final content = (candidates.first as Map)['content'] as Map;
      for (final p in (content['parts'] as List)) {
        final t = (p as Map)['text'];
        if (t is String && t.isNotEmpty) return t;
      }
    } catch (_) {/* fall through */}
    return null;
  }

  /// First inlineData audio part, or null.
  Map<String, dynamic>? _firstInlineAudioPart(Map<String, dynamic> decoded) {
    try {
      final candidates = decoded['candidates'] as List;
      final content = (candidates.first as Map)['content'] as Map;
      for (final p in (content['parts'] as List)) {
        final inline = (p as Map)['inlineData'];
        if (inline is Map<String, dynamic>) {
          return inline;
        }
      }
    } catch (_) {/* fall through */}
    return null;
  }

  GeminiApiException _httpError(int status, String fallback) {
    if (status == 400 || status == 401 || status == 403) {
      return const GeminiApiException('✗ API Key မမှန်ကန်ပါ');
    }
    if (status == 429) {
      return const GeminiApiException('API quota ကုန်နေပါသည်');
    }
    return GeminiApiException('$fallback (HTTP $status)');
  }

  // ------------------------------------------------------------ WAV utils

  /// Wraps raw 16-bit mono PCM bytes in a 44-byte RIFF/WAV header.
  /// [sampleRate] e.g. 16000 (mic stream) or 24000 (Gemini TTS output).
  static Uint8List pcmToWav(Uint8List pcm, {required int sampleRate}) {
    const int numChannels = 1;
    const int bitsPerSample = 16;
    final int byteRate = sampleRate * numChannels * bitsPerSample ~/ 8;
    final int blockAlign = numChannels * bitsPerSample ~/ 8;
    final int dataLen = pcm.length;
    final int totalLen = 44 + dataLen;

    final out = Uint8List(totalLen);
    final bd = ByteData.sublistView(out);

    void writeAscii(int offset, String s) {
      for (var i = 0; i < s.length; i++) {
        out[offset + i] = s.codeUnitAt(i);
      }
    }

    writeAscii(0, 'RIFF');
    bd.setUint32(4, totalLen - 8, Endian.little); // chunk size
    writeAscii(8, 'WAVE');
    writeAscii(12, 'fmt ');
    bd.setUint32(16, 16, Endian.little); // fmt chunk size
    bd.setUint16(20, 1, Endian.little); // PCM
    bd.setUint16(22, numChannels, Endian.little);
    bd.setUint32(24, sampleRate, Endian.little);
    bd.setUint32(28, byteRate, Endian.little);
    bd.setUint16(32, blockAlign, Endian.little);
    bd.setUint16(34, bitsPerSample, Endian.little);
    writeAscii(36, 'data');
    bd.setUint32(40, dataLen, Endian.little);
    out.setRange(44, totalLen, pcm);

    return out;
  }
}
