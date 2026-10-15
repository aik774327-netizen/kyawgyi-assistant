import { NextRequest, NextResponse } from "next/server";
import { z } from "zod";
import { CreditSaverCache, RateLimiter, withRetryPolicy } from "../../../src/gateway/creditSaver.ts";

/**
 * -----------------------------------------------------------------------------
 * MULTI-PROVIDER TEXT-TO-SPEECH (TTS) ENGINE WITH CREDIT SAVER CACHE
 * Supported Providers:
 * 1. OpenAI TTS (tts-1, tts-1-hd, voices: alloy, echo, fable, onyx, nova, shimmer)
 * 2. Google Cloud TTS (my-MM-Standard-A, my-MM-Wavenet-A, Neural2)
 * 3. ElevenLabs TTS (eleven_multilingual_v2, eleven_flash_v2, custom voice IDs)
 * 4. Gemini TTS (gemini-2.0-flash multimodal audio, voices: Aoede, Charon, Fenrir, Kore, Puck)
 * -----------------------------------------------------------------------------
 * CREDIT SAVER FEATURES:
 * - Deterministic SHA-256 Audio Cache: Saves 100% of API costs on repeated speech.
 * - Sliding Window Rate Limiting: Safeguards developer account from quota bursts.
 * - Exponential Backoff with Jitter: Resilient retry on transient 429/5xx errors.
 * -----------------------------------------------------------------------------
 */

export type TtsProvider = "openai" | "google" | "elevenlabs" | "gemini";

// Audio caching instance: caches synthesized MP3/WAV audio for 7 days
const ttsAudioCache = new CreditSaverCache<ArrayBuffer>({
  maxEntries: 1000,
  defaultTtlMs: 7 * 24 * 60 * 60 * 1000,
});

// Rate limiter: 35 requests per minute per IP
const rateLimiter = new RateLimiter(35, 60 * 1000);

const warnedProviders = new Set<string>();

// Request body validation schema
const TtsRequestBodySchema = z.object({
  text: z
    .string()
    .trim()
    .min(1, "Text must not be empty")
    .max(10000, "Text exceeds maximum supported length of 10,000 characters"),
  locale: z.string().optional().default("my-MM"),
  provider: z.enum(["openai", "google", "elevenlabs", "gemini"]).optional(),
  voice: z.string().optional(),
  model: z.string().optional(),
});

/**
 * Determine which provider to use based on request parameter, environment
 * default, or available configured API keys.
 */
function resolveProvider(requestedProvider?: TtsProvider): {
  provider: TtsProvider;
  error?: string;
} {
  const openaiKey = process.env.OPENAI_API_KEY;
  const googleKey = process.env.GOOGLE_TTS_API_KEY || process.env.GOOGLE_CLOUD_API_KEY;
  const elevenLabsKey = process.env.ELEVENLABS_API_KEY;
  const geminiKey = process.env.GEMINI_API_KEY;

  if (requestedProvider) {
    switch (requestedProvider) {
      case "openai":
        if (!openaiKey) return { provider: "openai", error: "OPENAI_API_KEY is not configured" };
        return { provider: "openai" };
      case "google":
        if (!googleKey) return { provider: "google", error: "GOOGLE_TTS_API_KEY is not configured" };
        return { provider: "google" };
      case "elevenlabs":
        if (!elevenLabsKey) return { provider: "elevenlabs", error: "ELEVENLABS_API_KEY is not configured" };
        return { provider: "elevenlabs" };
      case "gemini":
        if (!geminiKey) return { provider: "gemini", error: "GEMINI_API_KEY is not configured" };
        return { provider: "gemini" };
    }
  }

  // Check default environment variable
  const envDefault = process.env.DEFAULT_TTS_PROVIDER?.toLowerCase() as TtsProvider | undefined;
  if (envDefault && ["openai", "google", "elevenlabs", "gemini"].includes(envDefault)) {
    const res = resolveProvider(envDefault);
    if (!res.error) return res;
  }

  // Automatic auto-discovery based on available keys
  if (googleKey) return { provider: "google" };
  if (geminiKey) return { provider: "gemini" };
  if (openaiKey) return { provider: "openai" };
  if (elevenLabsKey) return { provider: "elevenlabs" };

  return {
    provider: "google",
    error: "No TTS API keys configured. Set GOOGLE_TTS_API_KEY, GEMINI_API_KEY, OPENAI_API_KEY, or ELEVENLABS_API_KEY.",
  };
}

/**
 * 1. OPENAI TTS PROVIDER
 */
async function synthesizeWithOpenAi(
  text: string,
  model = "tts-1",
  voice = "nova"
): Promise<{ buffer: ArrayBuffer; contentType: string }> {
  return withRetryPolicy(async () => {
    const apiKey = process.env.OPENAI_API_KEY!;
    const response = await fetch("https://api.openai.com/v1/audio/speech", {
      method: "POST",
      headers: {
        Authorization: `Bearer ${apiKey}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        model: model || "tts-1",
        input: text,
        voice: voice || "nova",
        response_format: "mp3",
      }),
      signal: AbortSignal.timeout(10000),
    });

    if (!response.ok) {
      const errorBody = await response.text().catch(() => "Unknown OpenAI error");
      const err: any = new Error(`OpenAI TTS error (${response.status}): ${errorBody}`);
      err.status = response.status;
      throw err;
    }

    const buffer = await response.arrayBuffer();
    return { buffer, contentType: "audio/mpeg" };
  });
}

/**
 * 2. GOOGLE CLOUD TTS PROVIDER
 */
async function synthesizeWithGoogle(
  text: string,
  locale = "my-MM",
  voiceName = "my-MM-Standard-A"
): Promise<{ buffer: ArrayBuffer; contentType: string }> {
  return withRetryPolicy(async () => {
    const apiKey = (process.env.GOOGLE_TTS_API_KEY || process.env.GOOGLE_CLOUD_API_KEY)!;
    const endpoint = `https://texttospeech.googleapis.com/v1/text:synthesize?key=${apiKey}`;

    const response = await fetch(endpoint, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        input: { text },
        voice: {
          languageCode: locale || "my-MM",
          name: voiceName || "my-MM-Standard-A",
        },
        audioConfig: {
          audioEncoding: "MP3",
          pitch: 0.0,
          speakingRate: 0.95,
        },
      }),
      signal: AbortSignal.timeout(10000),
    });

    if (!response.ok) {
      const errorBody = await response.text().catch(() => "Unknown Google TTS error");
      const err: any = new Error(`Google Cloud TTS error (${response.status}): ${errorBody}`);
      err.status = response.status;
      throw err;
    }

    const json = await response.json();
    const audioContent = json.audioContent;
    if (!audioContent) {
      throw new Error("Google Cloud TTS returned no audioContent");
    }

    // Decode Base64 to ArrayBuffer
    const binaryString = atob(audioContent);
    const bytes = new Uint8Array(binaryString.length);
    for (let i = 0; i < binaryString.length; i++) {
      bytes[i] = binaryString.charCodeAt(i);
    }

    return { buffer: bytes.buffer, contentType: "audio/mpeg" };
  });
}

/**
 * 3. ELEVENLABS TTS PROVIDER
 */
async function synthesizeWithElevenLabs(
  text: string,
  model = "eleven_multilingual_v2",
  voiceId?: string
): Promise<{ buffer: ArrayBuffer; contentType: string }> {
  return withRetryPolicy(async () => {
    const apiKey = process.env.ELEVENLABS_API_KEY!;
    const targetVoiceId = voiceId || process.env.ELEVENLABS_VOICE_ID || "21m00Tcm4TlvDq8ikWAM";

    if (!warnedProviders.has("elevenlabs")) {
      console.warn(
        "[TTS: ElevenLabs] Notice: Burmese script is inferred via multilingual context."
      );
      warnedProviders.add("elevenlabs");
    }

    const endpoint = `https://api.elevenlabs.io/v1/text-to-speech/${encodeURIComponent(
      targetVoiceId
    )}?output_format=mp3_44100_128`;

    const response = await fetch(endpoint, {
      method: "POST",
      headers: {
        "xi-api-key": apiKey,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        text,
        model_id: model || "eleven_multilingual_v2",
      }),
      signal: AbortSignal.timeout(10000),
    });

    if (!response.ok) {
      const errorBody = await response.text().catch(() => "Unknown ElevenLabs error");
      const err: any = new Error(`ElevenLabs TTS error (${response.status}): ${errorBody}`);
      err.status = response.status;
      throw err;
    }

    const buffer = await response.arrayBuffer();
    return { buffer, contentType: "audio/mpeg" };
  });
}

/**
 * 4. GEMINI TTS PROVIDER
 */
async function synthesizeWithGemini(
  text: string,
  voice = "Aoede"
): Promise<{ buffer: ArrayBuffer; contentType: string }> {
  return withRetryPolicy(async () => {
    const apiKey = process.env.GEMINI_API_KEY!;
    const endpoint = `https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=${apiKey}`;

    const response = await fetch(endpoint, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        contents: [
          {
            parts: [
              {
                text: `Please read aloud the following text in Burmese naturally and clearly, with proper tones. Do not include any explanations or intro:\n\n${text}`,
              },
            ],
          },
        ],
        generationConfig: {
          responseModalities: ["AUDIO"],
          speechConfig: {
            voiceConfig: {
              prebuiltVoiceConfig: {
                voiceName: voice || "Aoede",
              },
            },
          },
        },
      }),
      signal: AbortSignal.timeout(10000),
    });

    if (!response.ok) {
      const errorBody = await response.text().catch(() => "Unknown Gemini error");
      const err: any = new Error(`Gemini TTS error (${response.status}): ${errorBody}`);
      err.status = response.status;
      throw err;
    }

    const json = await response.json();
    const inlineData = json?.candidates?.[0]?.content?.parts?.[0]?.inlineData;
    if (!inlineData?.data) {
      throw new Error("Gemini returned no inline audio data");
    }

    const binaryString = atob(inlineData.data);
    const bytes = new Uint8Array(binaryString.length);
    for (let i = 0; i < binaryString.length; i++) {
      bytes[i] = binaryString.charCodeAt(i);
    }

    const mimeType = inlineData.mimeType || "audio/wav";
    return { buffer: bytes.buffer, contentType: mimeType };
  });
}

/**
 * GET Handler — Returns status & credit saving metrics
 */
export async function GET(): Promise<NextResponse> {
  const currentDefault = resolveProvider().provider;
  const stats = ttsAudioCache.getStats();

  return NextResponse.json({
    activeDefault: currentDefault,
    creditSaverStats: stats,
    supportedProviders: ["openai", "google", "elevenlabs", "gemini"],
  });
}

/**
 * POST Handler — Synthesizes speech with Credit Saving Cache
 */
export async function POST(req: NextRequest): Promise<NextResponse> {
  // 1. Authentication Check
  const authHeader = req.headers.get("authorization");
  const apiKeyHeader = req.headers.get("x-api-key");
  const clientSecret = process.env.INTERNAL_TTS_SECRET;

  if (clientSecret) {
    const bearerToken = authHeader?.replace(/^Bearer\s+/i, "");
    if (bearerToken !== clientSecret && apiKeyHeader !== clientSecret) {
      return NextResponse.json({ error: "Unauthorized" }, { status: 401 });
    }
  }

  // 2. Rate Limiter (Credit Protection)
  const clientIdentifier =
    req.headers.get("x-forwarded-for")?.split(",")[0]?.trim() ||
    req.headers.get("x-real-ip") ||
    "anonymous_client";

  const rateCheck = rateLimiter.check(clientIdentifier);
  if (!rateCheck.allowed) {
    return NextResponse.json(
      { error: "Too many requests. Credit protection active.", retryAfterMs: rateCheck.retryAfterMs },
      { status: 429, headers: { "Retry-After": Math.ceil(rateCheck.retryAfterMs / 1000).toString() } }
    );
  }

  // 3. Request Body Validation
  let parsedBody: z.infer<typeof TtsRequestBodySchema>;
  try {
    const json = await req.json();
    parsedBody = TtsRequestBodySchema.parse(json);
  } catch {
    return NextResponse.json({ error: "Invalid request payload" }, { status: 400 });
  }

  const { text, locale, provider: requestedProvider, voice, model } = parsedBody;

  // 4. Resolve Provider
  const { provider, error: providerConfigError } = resolveProvider(requestedProvider);
  if (providerConfigError) {
    return NextResponse.json({ error: providerConfigError }, { status: 500 });
  }

  // 5. Check Credit Saver Cache
  const cacheKey = CreditSaverCache.hashKey(`tts:${provider}:${model || ""}:${voice || ""}:${text}`);
  const cached = ttsAudioCache.get(cacheKey);
  if (cached.hit) {
    return new NextResponse(cached.value, {
      status: 200,
      headers: {
        "Content-Type": cached.contentType || "audio/mpeg",
        "Cache-Control": "public, max-age=604800",
        "Content-Length": cached.value.byteLength.toString(),
        "X-TTS-Provider": provider,
        "X-Cache-Status": "HIT_SAVED_CREDIT",
      },
    });
  }

  // 6. Synthesize Audio with Selected Provider
  try {
    let result: { buffer: ArrayBuffer; contentType: string };

    switch (provider) {
      case "openai":
        result = await synthesizeWithOpenAi(text, model, voice);
        break;
      case "google":
        result = await synthesizeWithGoogle(text, locale, voice);
        break;
      case "elevenlabs":
        result = await synthesizeWithElevenLabs(text, model, voice);
        break;
      case "gemini":
        result = await synthesizeWithGemini(text, voice);
        break;
    }

    // Save into cache for future requests
    ttsAudioCache.set(cacheKey, result.buffer, { contentType: result.contentType });

    return new NextResponse(result.buffer, {
      status: 200,
      headers: {
        "Content-Type": result.contentType,
        "Cache-Control": "public, max-age=604800",
        "Content-Length": result.buffer.byteLength.toString(),
        "X-TTS-Provider": provider,
        "X-Cache-Status": "MISS_STORED",
      },
    });
  } catch (error: any) {
    console.error(`[TTS Service: ${provider}] Error during synthesis:`, error?.message || error);
    return NextResponse.json({ error: "Audio synthesis failed", detail: error?.message }, { status: 502 });
  }
}
