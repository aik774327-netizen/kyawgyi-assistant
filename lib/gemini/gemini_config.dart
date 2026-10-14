/// Gemini API configuration for the KyawGyi voice assistant.
///
/// Model IDs VERIFIED 2026-10-15 via web research (multiple independent sources):
/// - TTS: `gemini-2.5-flash-preview-tts` — generateContent + responseModalities
///   ["AUDIO"], documented by Google AI docs mirrors and 3 community sources.
///   Response audio MIME: audio/L16;codec=pcm;rate=24000 (raw PCM s16le 24kHz
///   mono) — must be WAV-wrapped before playback.
/// - STT/transcription: `gemini-3.5-flash` — native generateContent with inline
///   audio part + transcription instruction; candidate text is the transcript.
///   Documented in-use for transcription via the native v1beta API.
/// - Chat: `gemini-3.5-flash` (same family).
/// - Endpoint: POST https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent
/// - Auth: ?key= query parameter (documented pattern).
/// - Key check: GET https://generativelanguage.googleapis.com/v1beta/models?key={key}
///   (200 = valid).
///
/// Gotcha (verified): WITHOUT responseModalities ["AUDIO"] the TTS call
/// returns HTTP 400. Always include it.

class GeminiConfig {
  static const String baseUrl =
      'https://generativelanguage.googleapis.com/v1beta';

  /// Text-to-speech model (verified 2026-10-15).
  static const String ttsModel = 'gemini-2.5-flash-preview-tts';

  /// Speech-to-text model: audio understanding via generateContent (verified in use).
  static const String sttModel = 'gemini-3.5-flash';

  /// Conversational reply model.
  static const String chatModel = 'gemini-3.5-flash';

  /// Default prebuilt TTS voice. ~30 prebuilt voices exist (Kore, Puck,
  /// Charon, Fenrir, Aoede, Leda, ...). Kore = clear, calming female.
  static const String defaultVoice = 'Kore';

  /// System instruction for the assistant's spoken replies.
  static const String systemInstruction = 'You are "Kyaw Gyi", a warm and '
      'friendly Myanmar voice assistant. Always reply in Myanmar (Burmese) '
      'language, using natural spoken style as if talking to a friend. Keep '
      'replies short: 2 to 4 sentences. Never use markdown, lists, or symbols '
      '— plain speech only, because your reply will be read aloud.';

  /// Instruction prepended to STT audio parts.
  static const String sttInstruction =
      'Transcribe the following Myanmar (Burmese) speech into Myanmar text '
      'exactly as spoken. Clean up background noise and music automatically. '
      'Output ONLY the transcription, no explanation.';

  static String generateContentUrl(String model) =>
      '$baseUrl/models/$model:generateContent';

  static String modelsUrl(String apiKey) => '$baseUrl/models?key=$apiKey';
}
