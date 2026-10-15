/**
 * GeminiService - wrapper with circuit-breaker, quota handling and PII scrubbing.
 * Simulator-first: if process.env.SIMULATOR === 'true' the service returns deterministic fixtures.
 *
 * Usage:
 *   const svc = new GeminiService({ enabled: !!process.env.GEMINI_API_KEY });
 *   const res = await svc.classify(utterance, context);
 */
// Use native fetch (Node 18+) or fallback
declare const fetch: any;


type GeminiResponse = {
  intent: string;
  confidence: number;
  entities?: Record<string, any>;
  raw?: any;
};

export class GeminiService {
  enabled: boolean;
  breakerOpenUntil: number | null;
  cooldownMs: number;
  recentErrors: number;

  constructor(opts?: { enabled?: boolean; cooldownMs?: number }) {
    this.enabled = !!(opts?.enabled);
    this.breakerOpenUntil = null;
    this.cooldownMs = opts?.cooldownMs ?? 60 * 60 * 1000; // 1 hour
    this.recentErrors = 0;
  }

  isBreakerOpen() {
    if (!this.breakerOpenUntil) return false;
    return Date.now() < this.breakerOpenUntil;
  }

  openBreaker() {
    this.breakerOpenUntil = Date.now() + this.cooldownMs;
    this.recentErrors = 0;
  }

  scrubPii(text: string) {
    // Scrub phone numbers (Myanmar and standard international format)
    let scrubbed = text.replace(/(?:\+?95[ -]?9?|09)[ -]?\d{7,9}\b/g, '[PHONE]');
    // Scrub national IDs or numerical identification sequences
    scrubbed = scrubbed.replace(/\b\d{6,12}\b/g, '[ID]');
    return scrubbed;
  }

  async callRemote(prompt: string): Promise<GeminiResponse> {
    if (process.env.SIMULATOR === 'true') {
      // Deterministic simulator response
      return {
        intent: 'simulator.intent',
        confidence: 0.8,
        entities: {},
        raw: { simulator: true, text: prompt },
      };
    }

    if (!this.enabled) throw new Error('Gemini disabled');

    if (this.isBreakerOpen()) {
      throw new Error('Circuit breaker open');
    }

    try {
      const cleaned = this.scrubPii(prompt);
      // TODO: Replace URL & auth with real Gemini endpoint & key usage
      const res = await fetch('https://api.gemini.example/v1/parse', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${process.env.GEMINI_API_KEY ?? ''}`,
        },
        body: JSON.stringify({ text: cleaned }),
        timeout: 10000,
      });

      if (res.status === 429) {
        // Quota exhausted -> open circuit
        this.openBreaker();
        throw new Error('Gemini quota exhausted');
      }

      if (!res.ok) {
        this.recentErrors += 1;
        if (this.recentErrors >= 5) this.openBreaker();
        throw new Error(`Gemini error ${res.status}`);
      }

      const json = await res.json();
      // Expected shape: { intent, confidence, entities }
      return {
        intent: json.intent ?? 'unknown',
        confidence: Number(json.confidence ?? 0),
        entities: json.entities ?? {},
        raw: json,
      };
    } catch (err) {
      // propagate wrapped error
      throw err;
    }
  }

  async classify(utterance: string, context?: any): Promise<GeminiResponse> {
    // Circuit breaker check + fallback behavior handled by caller
    return this.callRemote(utterance);
  }
}

export default GeminiService;
