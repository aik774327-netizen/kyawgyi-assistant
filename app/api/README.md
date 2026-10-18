# app/api — Reference server implementation (NOT bundled in the APK)

These Next.js API routes (`gateway/`, `stt/`, `tts/fallback/`) are the **reference
implementation of the optional AI Gateway backend** (audit finding #3, D9).

- They are **TypeScript** and are **never compiled into the Android APK**.
- The Android app does not require them: `AiGatewaySttClient` degrades gracefully
  (direct Gemini → local Myanmar rules) when no backend URL is configured.
- To use a self-hosted gateway: deploy this directory as a Next.js app (see
  `docker-compose.simulator.yml` at repo root), then set the backend URL in the
  Android app via `AiGatewaySttClient.setBackendUrl("https://your-host")`.
- Default backend URL is **empty (disabled)** — the previous hardcoded
  `http://10.0.2.2:3000` emulator loopback is no longer attempted on devices
  (audit finding #16).

Do not delete: this is the D9 server-proxy blueprint for future deployment.
