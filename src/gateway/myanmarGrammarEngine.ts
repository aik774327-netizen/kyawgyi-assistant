import { CreditSaverCache, withRetryPolicy } from './creditSaver.ts';

export interface GrammarCorrectionDetail {
  original: string;
  corrected: string;
  category: 'spelling' | 'tone' | 'grammar' | 'phonetic' | 'slang';
  reason: string;
}

export interface GrammarCorrectionResult {
  originalText: string;
  correctedText: string;
  corrections: GrammarCorrectionDetail[];
  confidence: number;
  cached: boolean;
  model: string;
}

/**
 * Common Myanmar Orthography Normalization Dictionary (မြန်မာစာအဖွဲ့ သတ်ပုံကျမ်း စံနှုန်းများ)
 */
const COMMON_TYPOS_MAP: Record<string, { corrected: string; reason: string; category: GrammarCorrectionDetail['category'] }> = {
  'ဖွင့်ပေးပါအုံး': { corrected: 'ဖွင့်ပေးပါဦး', reason: 'စကားအဆုံး စာလုံးပေါင်း "ဦး" ကို သုံးရမည်', category: 'spelling' },
  'ဖွင့်ပေးပါအုံး': { corrected: 'ဖွင့်ပေးပါဦး', reason: 'စကားအဆုံး စာလုံးပေါင်း "ဦး" ကို သုံးရမည်', category: 'spelling' },
  'လုက်ပါ': { corrected: 'လုပ်ပါ', reason: 'ကြိယာ "လုပ်" ၏ မူရင်းသတ်ပုံ', category: 'spelling' },
  'လုပ္ပါ': { corrected: 'လုပ်ပါ', reason: 'ယူနီကုဒ် စံသတ်ပုံ "လုပ်ပါ"', category: 'spelling' },
  'ပိက်ပါ': { corrected: 'ပိတ်ပါ', reason: 'ကြိယာ "ပိတ်" ၏ စာလုံးပေါင်းမှန်', category: 'spelling' },
  'ခေါ်ဖို': { corrected: 'ခေါ်ဖို့', reason: 'ရှေ့ပြေးကြိယာနောက်တွင် အောက်မြစ် "ဖို့" ဖြစ်ရမည်', category: 'tone' },
  'ခေါ်ပီး': { corrected: 'ခေါ်ပြီး', reason: 'အချိန်ပြ "ပြီး" စာလုံးပေါင်းမှန်', category: 'spelling' },
  'စစ်ဆေပါ': { corrected: 'စစ်ဆေးပါ', reason: '"စစ်ဆေး" ဝသသတ်ပုံမှန်', category: 'spelling' },
  'စစ်ဆီးပါ': { corrected: 'စစ်ဆေးပါ', reason: '"စစ်ဆေး" ဝသသတ်ပုံမှန်', category: 'spelling' },
  'ဘယ်လောက်လဲ': { corrected: 'မည်မျှရှိပါသနည်း', reason: 'ယဉ်ကျေးသော သဒ္ဒါစနစ် သတ်မှတ်မှု', category: 'grammar' },
  'မေးမေး': { corrected: 'မေမေ', reason: 'အသံထွက်အမှား ပြင်ဆင်ခြင်း', category: 'phonetic' },
  'ဖေဖေဆီဖုန်းခေါ်': { corrected: 'ဖေဖေ့ဆီ ဖုန်းခေါ်ပါ', reason: 'ပိုင်ဆိုင်မှုပြ အောက်မြစ်နှင့် ယဉ်ကျေးမှုကြိယာ ထည့်သွင်းခြင်း', category: 'grammar' },
  'မေမေဆီဖုန်းခေါ်': { corrected: 'မေမေ့ဆီ ဖုန်းခေါ်ပါ', reason: 'ပိုင်ဆိုင်မှုပြ အောက်မြစ်နှင့် ယဉ်ကျေးမှုကြိယာ ထည့်သွင်းခြင်း', category: 'grammar' },
  'အခုဖွင့်': { corrected: 'ယခု ဖွင့်ပေးပါ', reason: 'တရားဝင် သဒ္ဒါ အသုံးအနှုန်းသို့ ပြင်ဆင်ခြင်း', category: 'grammar' },
  'ဘက်ထရီအားသွင်းနှုန်း': { corrected: 'ဘက်ထရီ အားသွင်းနှုန်း', reason: 'ပုဒ်ဖြတ် ပုဒ်ရပ် စည်းဝါးညီညွတ်စေခြင်း', category: 'spelling' },
};

/**
 * AI Gateway Myanmar Grammar & Orthography Engine
 */
export class MyanmarGrammarEngine {
  private cache: CreditSaverCache<GrammarCorrectionResult>;

  constructor(cache?: CreditSaverCache<GrammarCorrectionResult>) {
    this.cache = cache ?? new CreditSaverCache<GrammarCorrectionResult>({ maxEntries: 1000, defaultTtlMs: 7 * 24 * 3600 * 1000 });
  }

  /**
   * Fast rule-based normalizer (Tier 1)
   */
  normalizeLocal(input: string): { normalizedText: string; corrections: GrammarCorrectionDetail[] } {
    let text = input.trim();
    const corrections: GrammarCorrectionDetail[] = [];

    // Normalize Unicode inconsistencies (Zawgyi-like or malformed tones)
    const originalBeforeUnicode = text;
    text = text.replace(/([က-အ])([့])([်])/g, '$1$3$2'); // tone mark order
    text = text.replace(/၀/g, '0'); // digit 0 vs letter ဝ normalization when in context of numbers
    text = text.replace(/(?<=\d)ဝ(?=\d)/g, '0');

    if (text !== originalBeforeUnicode) {
      corrections.push({
        original: originalBeforeUnicode,
        corrected: text,
        category: 'spelling',
        reason: 'ယူနီကုဒ် အက္ခရာအစဉ်အတိုင်း ပြန်လည်စီစဉ်ခြင်း',
      });
    }

    // Replace ending colloquial 'အုံး' with standard 'ဦး'
    if (/(?:ပါ|ပေးပါ|ပြောပါ|လုပ်ပါ)အုံး/.test(text)) {
      const before = text;
      text = text.replace(/(ပါ|ပေးပါ|ပြောပါ|လုပ်ပါ)အုံး/g, '$1ဦး');
      corrections.push({
        original: 'အုံး',
        corrected: 'ဦး',
        category: 'spelling',
        reason: 'စကားအဆုံး စာလုံးပေါင်း "ဦး" ကို သုံးရမည်',
      });
    }

    // Check vocabulary replacements
    for (const [typoRaw, fix] of Object.entries(COMMON_TYPOS_MAP)) {
      const typoNorm = typoRaw.replace(/([က-အ])([့])([်])/g, '$1$3$2');
      if (text.includes(typoNorm) || text.includes(typoRaw)) {
        text = text.replace(new RegExp(typoNorm, 'g'), fix.corrected);
        text = text.replace(new RegExp(typoRaw, 'g'), fix.corrected);
        corrections.push({
          original: typoRaw,
          corrected: fix.corrected,
          category: fix.category,
          reason: fix.reason,
        });
      }
    }

    return { normalizedText: text, corrections };
  }

  /**
   * Deep AI Gateway Grammar and Orthography Refinement using Gemini
   */
  async correct(
    rawText: string,
    context?: string
  ): Promise<GrammarCorrectionResult> {
    const trimmed = rawText.trim();
    if (!trimmed) {
      return {
        originalText: '',
        correctedText: '',
        corrections: [],
        confidence: 1.0,
        cached: false,
        model: 'local-rule',
      };
    }

    // 1. Check Caching to save credits
    const cacheKey = CreditSaverCache.hashKey(`grammar:${trimmed}:${context || 'general'}`);
    const cachedEntry = this.cache.get(cacheKey);
    if (cachedEntry.hit) {
      return {
        ...cachedEntry.value,
        cached: true,
      };
    }

    // 2. Perform Tier 1 Rule Normalization
    const localResult = this.normalizeLocal(trimmed);

    // If Gemini API Key is not available or in simulator, return normalized local result
    const apiKey = process.env.GEMINI_API_KEY;
    if (!apiKey || process.env.SIMULATOR === 'true') {
      const result: GrammarCorrectionResult = {
        originalText: trimmed,
        correctedText: localResult.normalizedText,
        corrections: localResult.corrections,
        confidence: 0.95,
        cached: false,
        model: 'local-rule-engine',
      };
      this.cache.set(cacheKey, result);
      return result;
    }

    // 3. Tier 2: Call Gemini API via AI Gateway
    try {
      const aiResult = await withRetryPolicy(async () => {
        const prompt = `You are the official Myanmar Language Commission (မြန်မာစာအဖွဲ့) Orthography & Grammar Engine.
Task:
Take this raw Speech-to-Text (STT) transcription in Burmese, and output the grammatically correct, impeccably spelled standard Myanmar text.

Context: ${context || 'Smart Home & Assistant Voice Command'}
Original STT: "${localResult.normalizedText}"

Rules:
1. Strict adherence to official Myanmar Orthography (မြန်မာစာလုံးပေါင်းသတ်ပုံကျမ်း).
2. Fix tone mark misplacements (့, း, ်) and colloquial phonetic errors (e.g., ဦး/အုန်း, လုပ်/လုက်, ပိတ်/ပိက်, ဖို့/ဖို).
3. Do NOT translate to English.
4. Keep technical parameters intact (e.g., EcoFlow, 1400W, USB, AC, DC, Kennede, Viber, Telegram, KBZPay).
5. Output strict JSON format with this schema:
{
  "correctedText": "သဒ္ဒါနှင့် စာလုံးပေါင်းမှန်ကန်သော စာသား",
  "corrections": [
    {
      "original": "မှားယွင်းသောစကားလုံး",
      "corrected": "မှန်ကန်သောစကားလုံး",
      "category": "spelling|grammar|tone|phonetic",
      "reason": "ပြင်ဆင်ရသည့် အကြောင်းပြချက်"
    }
  ],
  "confidence": 0.98
}`;

        const endpoint = `https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=${apiKey}`;
        const response = await fetch(endpoint, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          signal: AbortSignal.timeout(5000),
          body: JSON.stringify({
            contents: [{ parts: [{ text: prompt }] }],
            generationConfig: {
              responseMimeType: 'application/json',
              temperature: 0.1,
            },
          }),
        });

        if (!response.ok) {
          const errText = await response.text().catch(() => 'Unknown error');
          const error: any = new Error(`Gemini API error (${response.status}): ${errText}`);
          error.status = response.status;
          throw error;
        }

        const json = await response.json();
        const rawJsonText = json?.candidates?.[0]?.content?.parts?.[0]?.text;
        if (!rawJsonText) throw new Error('Empty Gemini response content');

        const parsed = JSON.parse(rawJsonText);
        return parsed;
      });

      const allCorrections = [
        ...localResult.corrections,
        ...(Array.isArray(aiResult.corrections) ? aiResult.corrections : []),
      ];

      const result: GrammarCorrectionResult = {
        originalText: trimmed,
        correctedText: aiResult.correctedText || localResult.normalizedText,
        corrections: allCorrections,
        confidence: Number(aiResult.confidence ?? 0.96),
        cached: false,
        model: 'gemini-2.5-flash',
      };

      this.cache.set(cacheKey, result);
      return result;
    } catch (err: any) {
      console.warn('[MyanmarGrammarEngine] AI Gateway fallback to local rules:', err.message);
      const fallbackResult: GrammarCorrectionResult = {
        originalText: trimmed,
        correctedText: localResult.normalizedText,
        corrections: localResult.corrections,
        confidence: 0.9,
        cached: false,
        model: 'local-fallback',
      };
      this.cache.set(cacheKey, fallbackResult);
      return fallbackResult;
    }
  }
}

export const defaultGrammarEngine = new MyanmarGrammarEngine();
