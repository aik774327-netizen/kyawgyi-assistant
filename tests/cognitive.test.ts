/**
 * Cognitive tests simulate Gemini fallback and circuit-breaker behavior.
 * We don't call the real Gemini API in tests; instead we assert behavior around circuit-breaker.
 */
import GeminiService from '../src/services/geminiService';

describe('GeminiService circuit breaker (sim)', () => {
  it('opens breaker after openBreaker()', () => {
    const s = new GeminiService({ enabled: false });
    s.openBreaker();
    expect(s.isBreakerOpen()).toBeTruthy();
  });

  it('scrubs phone numbers', () => {
    const s = new GeminiService({ enabled: false });
    const scrubbed = s.scrubPii('Call 0912345678 please');
    expect(scrubbed).not.toContain('0912345678');
    expect(scrubbed).toContain('[PHONE]');
  });
});
