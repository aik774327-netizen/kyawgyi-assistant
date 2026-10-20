import { NextRequest, NextResponse } from 'next/server';
import { z } from 'zod';
import { defaultGrammarEngine } from '../../../../src/gateway/myanmarGrammarEngine.ts';
import { RateLimiter } from '../../../../src/gateway/creditSaver.ts';

const GrammarRequestSchema = z.object({
  text: z.string().trim().min(1, 'Text cannot be empty').max(5000),
  context: z.string().optional().default('Voice Assistant Command'),
});

const rateLimiter = new RateLimiter(40, 60 * 1000); // 40 requests/min

export async function POST(req: NextRequest): Promise<NextResponse> {
  const clientIdentifier =
    req.headers.get('x-forwarded-for')?.split(',')[0]?.trim() ||
    req.headers.get('x-real-ip') ||
    'anonymous_client';

  // Rate Limiting
  const rateLimitCheck = rateLimiter.check(clientIdentifier);
  if (!rateLimitCheck.allowed) {
    return NextResponse.json(
      { error: 'Rate limit exceeded. Credit protection active.', retryAfterMs: rateLimitCheck.retryAfterMs },
      { status: 429, headers: { 'Retry-After': Math.ceil(rateLimitCheck.retryAfterMs / 1000).toString() } }
    );
  }

  // Parse Body
  let body: z.infer<typeof GrammarRequestSchema>;
  try {
    const json = await req.json();
    body = GrammarRequestSchema.parse(json);
  } catch {
    return NextResponse.json({ error: 'Invalid payload. Text is required.' }, { status: 400 });
  }

  try {
    const startTime = Date.now();
    const result = await defaultGrammarEngine.correct(body.text, body.context);
    const durationMs = Date.now() - startTime;

    return NextResponse.json({
      success: true,
      originalText: result.originalText,
      correctedText: result.correctedText,
      corrections: result.corrections,
      confidence: result.confidence,
      cached: result.cached,
      model: result.model,
      processingTimeMs: durationMs,
    });
  } catch (error: any) {
    console.error('[API /api/gateway/grammar] Error:', error.message);
    return NextResponse.json(
      { error: 'Grammar correction failed', detail: error.message },
      { status: 500 }
    );
  }
}
