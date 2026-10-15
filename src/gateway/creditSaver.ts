import crypto from 'node:crypto';

export interface CacheEntry<T> {
  data: T;
  contentType?: string;
  createdAt: number;
  expiresAt: number;
  hitCount: number;
}

export interface CreditSaverStats {
  cacheHits: number;
  cacheMisses: number;
  savedCallsCount: number;
  rateLimitedCount: number;
  estimatedCreditsSavedUsd: number;
}

/**
 * High-performance In-Memory & Digest Caching with TTL
 * Saves API credits by caching identical audio (STT) and text (Grammar/TTS).
 */
export class CreditSaverCache<T> {
  private cache = new Map<string, CacheEntry<T>>();
  private maxEntries: number;
  private defaultTtlMs: number;
  private stats: CreditSaverStats = {
    cacheHits: 0,
    cacheMisses: 0,
    savedCallsCount: 0,
    rateLimitedCount: 0,
    estimatedCreditsSavedUsd: 0,
  };

  constructor(options?: { maxEntries?: number; defaultTtlMs?: number }) {
    this.maxEntries = options?.maxEntries ?? 500;
    this.defaultTtlMs = options?.defaultTtlMs ?? 24 * 60 * 60 * 1000; // 24 hours
  }

  /**
   * Generates a deterministic SHA-256 hash from string, Buffer, or Uint8Array
   */
  static hashKey(input: string | Buffer | Uint8Array | ArrayBuffer): string {
    const hash = crypto.createHash('sha256');
    if (typeof input === 'string') {
      hash.update(input, 'utf8');
    } else if (Buffer.isBuffer(input)) {
      hash.update(input);
    } else if (input instanceof ArrayBuffer) {
      hash.update(Buffer.from(input));
    } else {
      hash.update(Buffer.from(input.buffer, input.byteOffset, input.byteLength));
    }
    return hash.digest('hex');
  }

  get(key: string): { hit: true; value: T; contentType?: string } | { hit: false } {
    const entry = this.cache.get(key);
    if (!entry) {
      this.stats.cacheMisses++;
      return { hit: false };
    }

    if (Date.now() > entry.expiresAt) {
      this.cache.delete(key);
      this.stats.cacheMisses++;
      return { hit: false };
    }

    entry.hitCount++;
    this.stats.cacheHits++;
    this.stats.savedCallsCount++;
    this.stats.estimatedCreditsSavedUsd += 0.005; // Average API call savings estimate
    return { hit: true, value: entry.data, contentType: entry.contentType };
  }

  set(key: string, data: T, options?: { ttlMs?: number; contentType?: string }) {
    // Evict oldest entry if size exceeds maxEntries
    if (this.cache.size >= this.maxEntries) {
      const firstKey = this.cache.keys().next().value;
      if (firstKey) this.cache.delete(firstKey);
    }

    const ttl = options?.ttlMs ?? this.defaultTtlMs;
    const now = Date.now();
    this.cache.set(key, {
      data,
      contentType: options?.contentType,
      createdAt: now,
      expiresAt: now + ttl,
      hitCount: 0,
    });
  }

  getStats(): CreditSaverStats {
    return { ...this.stats };
  }

  clear() {
    this.cache.clear();
  }
}

/**
 * Token Bucket & Sliding Window Rate Limiter
 */
export class RateLimiter {
  private requests = new Map<string, number[]>();
  private limit: number;
  private windowMs: number;

  constructor(limit = 30, windowMs = 60 * 1000) {
    this.limit = limit;
    this.windowMs = windowMs;
  }

  check(clientId: string): { allowed: boolean; remaining: number; retryAfterMs: number } {
    const now = Date.now();
    const timestamps = this.requests.get(clientId) || [];
    const validTimestamps = timestamps.filter((t) => now - t < this.windowMs);

    if (validTimestamps.length >= this.limit) {
      const oldest = validTimestamps[0];
      const retryAfterMs = Math.max(0, oldest + this.windowMs - now);
      this.requests.set(clientId, validTimestamps);
      return { allowed: false, remaining: 0, retryAfterMs };
    }

    validTimestamps.push(now);
    this.requests.set(clientId, validTimestamps);
    return {
      allowed: true,
      remaining: this.limit - validTimestamps.length,
      retryAfterMs: 0,
    };
  }
}

/**
 * Exponential Backoff Retry Policy with Jitter
 */
export async function withRetryPolicy<T>(
  fn: () => Promise<T>,
  options?: {
    maxRetries?: number;
    initialDelayMs?: number;
    maxDelayMs?: number;
    shouldRetry?: (error: any) => boolean;
  }
): Promise<T> {
  const maxRetries = options?.maxRetries ?? 3;
  const initialDelay = options?.initialDelayMs ?? 400;
  const maxDelay = options?.maxDelayMs ?? 3000;

  let attempt = 0;
  while (true) {
    try {
      return await fn();
    } catch (error: any) {
      attempt++;
      if (attempt > maxRetries) {
        throw error;
      }

      const shouldRetry = options?.shouldRetry
        ? options.shouldRetry(error)
        : isTransientError(error);

      if (!shouldRetry) {
        throw error;
      }

      const backoff = Math.min(initialDelay * Math.pow(2, attempt - 1), maxDelay);
      const jitter = Math.random() * 200;
      const delayMs = backoff + jitter;

      await new Promise((resolve) => setTimeout(resolve, delayMs));
    }
  }
}

function isTransientError(error: any): boolean {
  if (!error) return false;
  const status = error.status || error.statusCode || error.response?.status;
  if (status === 429 || status === 500 || status === 502 || status === 503 || status === 504) {
    return true;
  }
  const msg = (error.message || '').toLowerCase();
  return (
    msg.includes('timeout') ||
    msg.includes('econnreset') ||
    msg.includes('network') ||
    msg.includes('fetch failed') ||
    msg.includes('rate limit')
  );
}
