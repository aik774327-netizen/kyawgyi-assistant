/**
 * In-memory Memory Engine with TTL and simple LRU cleanup.
 * For production swap in Redis or other persistent store via adapter.
 */

type MemoryItem = {
  id: string;
  sessionId: string;
  key: string;
  value: any;
  expiresAt?: number;
  createdAt: number;
};

export class MemoryEngine {
  items: Map<string, MemoryItem> = new Map();

  constructor() {
    // Periodic clean
    const timer = setInterval(() => this.cleanupExpired(), 60 * 1000);
    if (timer && typeof timer.unref === 'function') {
      timer.unref();
    }
  }

  createItem(sessionId: string, key: string, value: any, ttlSeconds?: number) {
    const id = `${sessionId}:${key}`;
    const now = Date.now();
    const expiresAt = ttlSeconds ? now + ttlSeconds * 1000 : undefined;
    const item: MemoryItem = { id, sessionId, key, value, createdAt: now, expiresAt };
    this.items.set(id, item);
    return item;
  }

  getItem(sessionId: string, key: string) {
    const id = `${sessionId}:${key}`;
    const it = this.items.get(id);
    if (!it) return null;
    if (it.expiresAt && Date.now() > it.expiresAt) {
      this.items.delete(id);
      return null;
    }
    return it.value;
  }

  deleteItem(sessionId: string, key: string) {
    const id = `${sessionId}:${key}`;
    return this.items.delete(id);
  }

  cleanupExpired() {
    const now = Date.now();
    for (const [k, v] of this.items.entries()) {
      if (v.expiresAt && now > v.expiresAt) this.items.delete(k);
    }
  }
}
