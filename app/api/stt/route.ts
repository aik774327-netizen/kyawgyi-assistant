import { NextRequest, NextResponse } from 'next/server';
import { defaultSttEngine } from '../../../src/gateway/audioSttEngine.ts';
import { RateLimiter } from '../../../src/gateway/creditSaver.ts';

// Rate limiter: 20 STT requests/minute per client IP
const rateLimiter = new RateLimiter(20, 60 * 1000);

export async function POST(req: NextRequest): Promise<NextResponse> {
  const clientIdentifier =
    req.headers.get('x-forwarded-for')?.split(',')[0]?.trim() ||
    req.headers.get('x-real-ip') ||
    'anonymous_client';

  // 1. Rate Limit Check
  const rateLimitCheck = rateLimiter.check(clientIdentifier);
  if (!rateLimitCheck.allowed) {
    return NextResponse.json(
      {
        error: 'Too many STT requests. Credit protection limit reached.',
        retryAfterMs: rateLimitCheck.retryAfterMs,
      },
      {
        status: 429,
        headers: {
          'Retry-After': Math.ceil(rateLimitCheck.retryAfterMs / 1000).toString(),
        },
      }
    );
  }

  // 2. Audio Payload Extraction
  let audioBuffer: Buffer;
  const contentType = req.headers.get('content-type') || '';

  try {
    if (contentType.includes('application/json')) {
      const json = await req.json();
      const base64Audio = json.audioBase64 || json.audio;
      if (!base64Audio) {
        return NextResponse.json(
          { error: 'Missing audioBase64 field in request JSON' },
          { status: 400 }
        );
      }
      audioBuffer = Buffer.from(base64Audio, 'base64');
    } else if (contentType.includes('multipart/form-data')) {
      const formData = await req.formData();
      const file = formData.get('audio') as File | null;
      if (!file) {
        return NextResponse.json(
          { error: 'Missing "audio" file field in multipart form-data' },
          { status: 400 }
        );
      }
      const arrayBuffer = await file.arrayBuffer();
      audioBuffer = Buffer.from(arrayBuffer);
    } else {
      // Raw binary audio stream
      const arrayBuffer = await req.arrayBuffer();
      audioBuffer = Buffer.from(arrayBuffer);
    }

    if (audioBuffer.length < 44) {
      return NextResponse.json(
        { error: 'Audio data is too small or invalid WAV format' },
        { status: 400 }
      );
    }
  } catch (parseError: any) {
    return NextResponse.json(
      { error: `Failed to parse audio input: ${parseError.message}` },
      { status: 400 }
    );
  }

  // 3. Process STT + Myanmar Grammar Correction via AI Gateway
  try {
    const startTime = Date.now();
    const result = await defaultSttEngine.transcribeWav(audioBuffer);
    const durationMs = Date.now() - startTime;

    return NextResponse.json({
      success: true,
      rawTranscript: result.rawTranscript,
      correctedText: result.finalText,
      corrections: result.grammarResult.corrections,
      confidence: result.confidence,
      durationSec: result.durationSec,
      processingTimeMs: durationMs,
      cached: result.cached,
      model: result.model,
      audioHash: result.audioHash,
    });
  } catch (error: any) {
    console.error('[API /api/stt] Error:', error.message);
    return NextResponse.json(
      { error: 'Speech-to-text synthesis failed', detail: error.message },
      { status: 502 }
    );
  }
}

export async function GET(): Promise<NextResponse> {
  const stats = defaultSttEngine.getCacheStats();
  return NextResponse.json({
    service: 'Myanmar Audio STT & AI Gateway Engine',
    supportedFormat: 'audio/wav (16-bit PCM, 16000Hz mono recommended)',
    cacheStats: stats,
  });
}
