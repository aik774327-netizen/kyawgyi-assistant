import { createFileRoute } from "@tanstack/react-router";
import {
  resolveApiKey,
  burmeseError,
  clientIp,
  createRateLimiter,
} from "../../lib/gateway";

/**
 * POST /api/angle-voice — dual-purpose voice route (Lovable AI Gateway).
 *
 *  - TTS: JSON body { text, voice }            -> audio bytes (blob)
 *  - STT: multipart/form-data { file }        -> JSON { text }
 *
 * Content-Type dispatch: multipart => STT, otherwise JSON => TTS.
 *
 * Model IDs VERIFIED 2026-10-14 via TanStack @ai-lovable adapter docs
 * (speech default "openai/gpt-4o-mini-tts", transcription default
 * "openai/gpt-4o-mini-transcribe") + a production Lovable project using
 * gpt-4o-mini-transcribe. Env overrides (LOVABLE_TTS_MODEL /
 * LOVABLE_STT_MODEL) still win.
 * Compile status: NOT VERIFIED (no project deps here).
 */

const GATEWAY = "https://ai.gateway.lovable.dev/v1";
const TTS_MODEL = process.env["LOVABLE_TTS_MODEL"] || "openai/gpt-4o-mini-tts";
const STT_MODEL = process.env["LOVABLE_STT_MODEL"] || "openai/gpt-4o-mini-transcribe";

// NOTE: the old list used Gemini voice names (Kore/Aoede/...) but the gateway
// TTS model is OpenAI's — those names would fail. OpenAI voices only.
const TTS_VOICES = ["alloy", "echo", "fable", "onyx", "nova", "shimmer"];
const DEFAULT_VOICE = "nova";
const MAX_TEXT_CHARS = 4000;
const MAX_AUDIO_BYTES = 10 * 1024 * 1024; // 10 MB (matches client cap)

// FIX #8: per-IP token bucket (20/min) — audio endpoints are the most
// expensive, so the limit is tighter here (best-effort, per instance).
const voiceRateLimit = createRateLimiter();

export const Route = createFileRoute("/api/angle-voice")({
  server: {
    handlers: {
      POST: async ({ request }) => {
        // BYOK: user's own key (x-custom-api-key header) wins over env.
        const key = resolveApiKey(request);
        if (!key) return Response.json({ error: "AI key မရှိပါ" }, { status: 500 });

        if (voiceRateLimit(clientIp(request), 20, 60_000)) {
          return Response.json(
            { error: "တောင်းဆိုမှု များနေပါသည်၊ ခဏနေမှ ပြန်ကြိုးစားပါ" },
            { status: 429 }
          );
        }

        const contentType = request.headers.get("content-type") || "";

        // ------------------------------------------------ STT branch
        if (contentType.includes("multipart/form-data")) {
          let form: FormData;
          try {
            form = await request.formData();
          } catch {
            return Response.json({ error: "Invalid form data" }, { status: 400 });
          }
          const file = form.get("file");
          if (!(file instanceof Blob) || file.size === 0) {
            return Response.json({ error: "အသံဖိုင် မပါရှိပါ" }, { status: 400 });
          }
          if (file.size > MAX_AUDIO_BYTES) {
            return Response.json({ error: "အသံဖိုင် ကြီးလွန်းပါသည် (10MB အထိ)" }, { status: 413 });
          }

          const fd = new FormData();
          fd.append("file", file, (file as File).name || "voice.webm");
          fd.append("model", STT_MODEL);

          const upstream = await fetch(`${GATEWAY}/audio/transcriptions`, {
            method: "POST",
            headers: {
              Authorization: `Bearer ${key}`,
              "Lovable-API-Key": key,
            },
            body: fd,
            signal: request.signal,
          });

          if (!upstream.ok) {
            const t = await upstream.text().catch(() => "");
            console.error("angle-voice STT upstream error:", upstream.status, t.slice(0, 300));
            return Response.json({ error: burmeseError(upstream.status) }, { status: upstream.status });
          }
          const j = (await upstream.json().catch(() => ({}))) as { text?: string };
          const text = (j.text || "").trim();
          if (!text) return Response.json({ error: "စကားသံ မကြားရပါ" }, { status: 502 });
          return Response.json({ text });
        }

        // ------------------------------------------------ TTS branch
        let body: { text?: string; voice?: string };
        try {
          body = await request.json();
        } catch {
          return Response.json({ error: "Invalid JSON format" }, { status: 400 });
        }
        const text = (body.text || "").trim();
        if (!text) return Response.json({ error: "စာသား မပါရှိပါ" }, { status: 400 });
        if (text.length > MAX_TEXT_CHARS) {
          return Response.json(
            { error: `စာသား ရှည်လွန်းပါသည် (${MAX_TEXT_CHARS} အထိ)` },
            { status: 413 }
          );
        }
        const voice = TTS_VOICES.includes(body.voice || "") ? body.voice! : DEFAULT_VOICE;

        const upstream = await fetch(`${GATEWAY}/audio/speech`, {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            Authorization: `Bearer ${key}`,
            "Lovable-API-Key": key,
          },
          body: JSON.stringify({ model: TTS_MODEL, input: text, voice }),
          signal: request.signal,
        });

        if (!upstream.ok || !upstream.body) {
          const t = await upstream.text().catch(() => "");
          console.error("angle-voice TTS upstream error:", upstream.status, t.slice(0, 300));
          return Response.json({ error: burmeseError(upstream.status) }, { status: upstream.status });
        }
        return new Response(upstream.body, {
          headers: {
            "Content-Type": upstream.headers.get("content-type") || "audio/mpeg",
            "Cache-Control": "no-store",
          },
        });
      },
    },
  },
});
