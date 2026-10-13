/**
 * Shared server helpers for the /api/angle-* routes (Lovable AI Gateway).
 *
 * Centralizes: BYOK key resolution, Burmese error mapping, client IP
 * extraction, and a tiny per-IP token-bucket rate limiter.
 * (Fixes audit #4 — the rate limiter was copy-pasted across 3 route files —
 * and #5 — routes no longer import from each other.)
 *
 * Security: resolveApiKey sanitizes the header value (length + control-char
 * check against header injection) and the key is NEVER logged in any route.
 */

export const CUSTOM_KEY_HEADER = "x-custom-api-key";
const MAX_KEY_LENGTH = 200;

/**
 * BYOK key resolution: the user's own key (browser localStorage ->
 * `x-custom-api-key` request header) takes priority over the server env key.
 */
export function resolveApiKey(request: Request): string | null {
  const h = request.headers.get(CUSTOM_KEY_HEADER);
  if (h) {
    const k = h.trim();
    if (k && k.length <= MAX_KEY_LENGTH && !/[\r\n\x00-\x1f\x7f]/.test(k)) {
      return k;
    }
  }
  return process.env["LOVABLE_API_KEY"] || null;
}

/** Maps gateway HTTP statuses to user-facing Burmese messages. */
export function burmeseError(status: number): string {
  if (status === 401) return "API Key မမှန်ကန်ပါ";
  if (status === 402) return "AI credits ကုန်သွားပါပြီ";
  if (status === 429) return "တောင်းဆိုမှု များနေပါသည်၊ ခဏနေမှ ပြန်ကြိုးစားပါ";
  return `AI error (${status})`;
}

export function clientIp(request: Request): string {
  return (
    request.headers.get("x-forwarded-for")?.split(",")[0]?.trim() || "unknown"
  );
}

/**
 * Per-IP token bucket (best-effort, per server instance).
 * Returns a checker: (ip, max, windowMs) => true when the request is limited.
 */
export function createRateLimiter() {
  const buckets = new Map<string, { count: number; reset: number }>();
  return function rateLimited(
    ip: string,
    max: number,
    windowMs: number
  ): boolean {
    const now = Date.now();
    const b = buckets.get(ip);
    if (!b || now > b.reset) {
      buckets.set(ip, { count: 1, reset: now + windowMs });
      if (buckets.size > 5000) buckets.clear(); // memory safety
      return false;
    }
    if (b.count >= max) return true;
    b.count++;
    return false;
  };
}
