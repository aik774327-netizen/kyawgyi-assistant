/**
 * Jest tests for memoryEngine
 */
const { MemoryEngine } = require('../src/storage/memoryEngine');

describe('MemoryEngine', () => {
  jest.setTimeout(10000);
  it('creates and retrieves item with TTL and expires', async () => {
    const m = new MemoryEngine();
    m.createItem('s1', 'k1', { foo: 'bar' }, 1); // 1 second TTL
    const v1 = m.getItem('s1', 'k1');
    expect(v1).toEqual({ foo: 'bar' });
    await new Promise((r) => setTimeout(r, 1200));
    const v2 = m.getItem('s1', 'k1');
    expect(v2).toBeNull();
  });

  it('deletes items', () => {
    const m = new MemoryEngine();
    m.createItem('s2', 'k2', 123);
    const got = m.getItem('s2', 'k2');
    expect(got).toBe(123);
    m.deleteItem('s2', 'k2');
    expect(m.getItem('s2', 'k2')).toBeNull();
  });
});
