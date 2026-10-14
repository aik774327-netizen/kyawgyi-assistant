# KyawGyi Assistant — Flutter + Gemini Myanmar Voice

**KyawGyi** Android voice assistant: Flutter shell over the native bridge,
with full Myanmar voice powered directly by the Google Gemini API
(STT → chat → TTS, all in Burmese). The user's own Gemini API key is stored
securely on-device (Android Keystore) and verified in-app with a
"စစ်ဆေးရန်" button before saving.

> 2026-10-15: the legacy `src/` TanStack web assistant (Lovable gateway) and
> its `tests/` were removed — unused by the APK. Voice now runs 100% on the
> Gemini API from the Flutter app. Model IDs verified 2026-10-15 via web
> research (see `lib/gemini/gemini_config.dart`).

## Layout

```
lib/
  main.dart                  # Flutter shell (demo UI over the bridge)
  native/kyawgyi_bridge.dart # Dart MethodChannel + EventChannel wrapper
  gemini/
    gemini_config.dart       # Verified API endpoints + model IDs
    api_key_manager.dart     # Secure on-device key storage (Keystore)
    gemini_service.dart      # Key validation, STT, chat, TTS, WAV utils
    voice_assistant_screen.dart  # Burmese UI: key entry/verify + voice chat
android/               # Native Android module (mic FGS, accessibility, etc.)
```

## Gemini API key

The app needs the user's own Gemini API key:
1. Get one at Google AI Studio (aistudio.google.com) → Get API Key.
2. In the app: open "🎙️ Gemini မြန်မာအသံ" → paste the key → "စစ်ဆေးရန်".
3. After "✓ API Key မှန်ကန်ပါသည်", tap "သိမ်းရန်".

The key never leaves the device except in HTTPS calls to
`generativelanguage.googleapis.com`, and is never logged.

## Web setup

Prerequisites: the host TanStack Start project with `@/lib` alias, shadcn/ui,
`sonner`, `lucide-react`, `react-markdown`.

```bash
npm install --legacy-peer-deps   # or: bun install
```

Environment variables (server):

| Variable | Required | Purpose |
|---|---|---|
| `LOVABLE_API_KEY` | yes (unless every user brings a key) | Gateway auth |
| `LOVABLE_TTS_MODEL` | no (default `openai/gpt-4o-mini-tts`) | TTS model override |
| `LOVABLE_STT_MODEL` | no (default `openai/gpt-4o-mini-transcribe`) | STT model override |

Model IDs were verified 2026-10-14 against the TanStack `@ai-lovable`
adapter docs (speech default `openai/gpt-4o-mini-tts`, transcription default
`openai/gpt-4o-mini-transcribe`).

### API routes

| Route | Method | In | Out |
|---|---|---|---|
| `/api/angle-chat` | POST | `{ messages[-20], context }` | `text/plain` stream |
| `/api/angle-voice` | POST (JSON) | `{ text, voice }` | audio bytes |
| `/api/angle-voice` | POST (multipart) | `file` | `{ text }` |
| `/api/angle-validate` | GET | — | `{ ok, reason }` |

**BYOK:** the settings panel stores the user's key in
`localStorage["custom_lovable_api_key"]`; the client sends it as the
`X-Custom-Api-Key` header and the server prefers it over the env key
(sanitized, never logged).

Rate limits (per IP, per instance): chat 30/min, voice 20/min, validate 10/min.

## Android setup

Prerequisites: JDK 17, Android SDK (API 34), Flutter SDK.

```bash
# 1. Point at your Flutter SDK (one of):
#    android/local.properties -> flutter.sdk=/path/to/flutter
export FLUTTER_SDK=/path/to/flutter

# 2. Generate the Gradle wrapper (once):
cd android && gradle wrapper --gradle-version 8.7

# 3. Build / analyze:
flutter analyze
flutter build apk --debug
```

Native pieces:
- `VoiceForegroundService` — foreground mic service (16 kHz PCM),
  bounded wake lock, fail-closed without `RECORD_AUDIO`.
- `AudioStreamHandler` — `EventChannel com.kyawgyi.assistant/audio_stream`
  streams raw PCM to Flutter.
- `NativeMethodChannelHandler` — `MethodChannel
  com.kyawgyi.assistant/bridge` (service, STT, device controls, taps…).
- `KyawGyiAccessibilityService` — screen reading (memory-only, never logged)
  + gestures; **the user must enable it manually in system Settings**.
- `DeviceController` — app/URL/volume/brightness allowlists (EN + Burmese).

The Flutter layer must request `RECORD_AUDIO` at runtime (the demo shell
uses `permission_handler`) before `startStt()`.

## Verification status

- TypeScript: `esbuild` syntax PASS on all route/lib/component files.
- Kotlin: brace balance PASS on all 6 files.
- Logic: `node tests/all.mjs` — 14/14 PASS (memory immutability/caps,
  gateway error mapping/BYOK sanitizing/rate limiter).
- **NOT verified here:** Kotlin/Dart/Gradle compilation (no SDKs in this
  environment), live gateway calls, on-device behavior.

## Data & privacy notes

- Memory + chat history + API key live in **browser localStorage only**
  (device-local, no sync). Clearing browser data wipes them.
- The accessibility service can observe all apps (`typeAllMask`, unscoped) —
  keep it explicitly user-enabled; screen text is never logged or transmitted.
