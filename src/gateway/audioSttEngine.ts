import { CreditSaverCache, withRetryPolicy } from './creditSaver.ts';
import { MyanmarGrammarEngine, defaultGrammarEngine, type GrammarCorrectionResult } from './myanmarGrammarEngine.ts';

export interface SttProcessingResult {
  rawTranscript: string;
  grammarResult: GrammarCorrectionResult;
  finalText: string;
  confidence: number;
  durationSec: number;
  cached: boolean;
  model: string;
  audioHash: string;
}

/**
 * Audio STT Engine with Credit Saver Caching & Multi-provider AI Gateway
 */
export class AudioSttEngine {
  private cache: CreditSaverCache<SttProcessingResult>;
  private grammarEngine: MyanmarGrammarEngine;

  constructor(options?: {
    cache?: CreditSaverCache<SttProcessingResult>;
    grammarEngine?: MyanmarGrammarEngine;
  }) {
    this.cache = options?.cache ?? new CreditSaverCache<SttProcessingResult>({ maxEntries: 500, defaultTtlMs: 24 * 60 * 60 * 1000 });
    this.grammarEngine = options?.grammarEngine ?? defaultGrammarEngine;
  }

  /**
   * Validate RIFF WAV header and calculate duration
   */
  static parseWavHeader(buffer: Buffer): { isValidWav: boolean; sampleRate: number; channels: number; durationSec: number } {
    if (buffer.length < 44) {
      return { isValidWav: false, sampleRate: 0, channels: 0, durationSec: 0 };
    }

    const riff = buffer.toString('ascii', 0, 4);
    const wave = buffer.toString('ascii', 8, 12);
    if (riff !== 'RIFF' || wave !== 'WAVE') {
      return { isValidWav: false, sampleRate: 0, channels: 0, durationSec: 0 };
    }

    const channels = buffer.readUInt16LE(22);
    const sampleRate = buffer.readUInt32LE(24);
    const byteRate = buffer.readUInt32LE(28);

    const dataSize = buffer.length - 44;
    const durationSec = byteRate > 0 ? dataSize / byteRate : 0;

    return { isValidWav: true, sampleRate, channels, durationSec: Math.max(0, durationSec) };
  }

  /**
   * Transcribes WAV audio with Caching, Rate Limiting & Gemini Multimodal Audio
   */
  async transcribeWav(
    audioBuffer: Buffer,
    options?: {
      context?: string;
      language?: string;
    }
  ): Promise<SttProcessingResult> {
    const audioHash = CreditSaverCache.hashKey(audioBuffer);

    // 1. Credit Saver: Check Cache
    const cached = this.cache.get(audioHash);
    if (cached.hit) {
      return {
        ...cached.value,
        cached: true,
      };
    }

    const wavInfo = AudioSttEngine.parseWavHeader(audioBuffer);
    const apiKey = process.env.GEMINI_API_KEY;

    // Simulator / offline fallback
    if (!apiKey || process.env.SIMULATOR === 'true') {
      const simulatedTranscript = 'မင်္ဂလာပါ EcoFlow ကို ဖွင့်ပေးပါ';
      const grammarRes = await this.grammarEngine.correct(simulatedTranscript, options?.context);
      const result: SttProcessingResult = {
        rawTranscript: simulatedTranscript,
        grammarResult: grammarRes,
        finalText: grammarRes.correctedText,
        confidence: 0.98,
        durationSec: wavInfo.durationSec || 1.5,
        cached: false,
        model: 'simulator-stt',
        audioHash,
      };
      this.cache.set(audioHash, result);
      return result;
    }

    // 2. Call Gemini Multimodal Audio STT via AI Gateway
    try {
      const result = await withRetryPolicy(async () => {
        const base64Audio = audioBuffer.toString('base64');
        const prompt = `Listen to this Burmese spoken audio carefully and transcribe it exactly as spoken into Myanmar script (Burmese Unicode).
Do not translate to English.
Return strict JSON in this format:
{
  "transcript": "မြန်မာစာသား",
  "confidence": 0.95
}`;

        const endpoint = `https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=${apiKey}`;
        const response = await fetch(endpoint, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          signal: AbortSignal.timeout(6000),
          body: JSON.stringify({
            contents: [
              {
                parts: [
                  { text: prompt },
                  {
                    inlineData: {
                      mimeType: 'audio/wav',
                      data: base64Audio,
                    },
                  },
                ],
              },
            ],
            generationConfig: {
              responseMimeType: 'application/json',
              temperature: 0.0,
            },
          }),
        });

        if (!response.ok) {
          const errText = await response.text().catch(() => 'Unknown error');
          const error: any = new Error(`Gemini STT error (${response.status}): ${errText}`);
          error.status = response.status;
          throw error;
        }

        const json = await response.json();
        const jsonText = json?.candidates?.[0]?.content?.parts?.[0]?.text;
        if (!jsonText) throw new Error('No transcript in Gemini STT response');

        const parsed = JSON.parse(jsonText);
        return {
          transcript: parsed.transcript || '',
          confidence: Number(parsed.confidence ?? 0.95),
        };
      });

      // 3. Pass raw transcript through Myanmar Grammar & Orthography Engine
      const grammarRes = await this.grammarEngine.correct(result.transcript, options?.context);

      const processingResult: SttProcessingResult = {
        rawTranscript: result.transcript,
        grammarResult: grammarRes,
        finalText: grammarRes.correctedText,
        confidence: result.confidence,
        durationSec: wavInfo.durationSec,
        cached: false,
        model: 'gemini-2.5-flash-audio',
        audioHash,
      };

      this.cache.set(audioHash, processingResult);
      return processingResult;
    } catch (error: any) {
      console.error('[AudioSttEngine] Error in STT synthesis:', error.message);
      throw error;
    }
  }

  getCacheStats() {
    return this.cache.getStats();
  }
}

export const defaultSttEngine = new AudioSttEngine();
