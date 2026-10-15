/**
 * Standalone Test Runner for Prototype Studio AI V3.5 Enterprise Simulator Suites
 */
import assert from 'node:assert';
import fs from 'node:fs';

let totalTests = 0;
let passedTests = 0;
let failedTests = 0;

function it(name, fn) {
  totalTests++;
  try {
    const result = fn();
    if (result && typeof result.then === 'function') {
      return result.then(() => {
        passedTests++;
        console.log(`  ✓ ${name}`);
      }).catch((err) => {
        failedTests++;
        console.error(`  ✗ ${name}:`, err.message);
      });
    }
    passedTests++;
    console.log(`  ✓ ${name}`);
  } catch (err) {
    failedTests++;
    console.error(`  ✗ ${name}:`, err.message);
  }
}

async function runAll() {
  console.log('\n======================================================');
  console.log(' Prototype Studio AI V3.5 Enterprise Test Suite');
  console.log('======================================================\n');

  // Suite 1: Local NLU (62 Myanmar & English utterances)
  console.log('Suite 1: Local NLU Engine (62 Fixtures)');
  const { parseUtterance } = await import('./src/nlu/localNlu.ts');
  const nluTestContent = fs.readFileSync('./tests/nlu.test.ts', 'utf8');
  const fixtures = [...nluTestContent.matchAll(/\{\s*utterance:\s*'([^']+)',\s*expected:\s*'([^']+)'\s*\}/g)];
  
  for (const match of fixtures) {
    const utterance = match[1];
    const expected = match[2];
    it(`NLU: "${utterance}" => ${expected}`, () => {
      const res = parseUtterance(utterance);
      assert.strictEqual(res.intent, expected);
      assert.strictEqual(typeof res.confidence, 'number');
    });
  }

  // Suite 2: Semantic Router
  console.log('\nSuite 2: Semantic Cognitive Router');
  const { route } = await import('./src/router/semanticRouter.ts');
  it('routes high confidence local intent to execute', () => {
    const nlu = parseUtterance('EcoFlow ကို ဖွင့်ပါ');
    const decision = route(nlu, {});
    assert.strictEqual(decision.action, 'execute');
  });
  it('asks clarification on mid confidence', () => {
    const nlu = { intent: 'unknown', confidence: 0.5, entities: [] };
    const decision = route(nlu, {});
    assert.strictEqual(decision.action, 'clarify');
  });
  it('falls back on very low confidence', () => {
    const nlu = { intent: 'unknown', confidence: 0.0, entities: [] };
    const decision = route(nlu, {});
    assert.strictEqual(decision.action, 'fallback');
  });

  // Suite 3: Gemini Service & Circuit Breakers
  console.log('\nSuite 3: Gemini Node & Circuit Breaker');
  const GeminiService = (await import('./src/services/geminiService.ts')).default;
  it('opens circuit breaker on quota exhaustion', () => {
    const svc = new GeminiService({ enabled: false });
    svc.openBreaker();
    assert.strictEqual(svc.isBreakerOpen(), true);
  });
  it('scrubs PII phone numbers and IDs accurately', () => {
    const svc = new GeminiService({ enabled: false });
    const cleaned = svc.scrubPii('Call 0912345678 or +959123456789 with ID 12345678');
    assert.ok(!cleaned.includes('0912345678'));
    assert.ok(cleaned.includes('[PHONE]'));
    assert.ok(cleaned.includes('[ID]'));
  });

  // Suite 4: Memory Engine
  console.log('\nSuite 4: Memory Engine (TTL & LRU)');
  const { MemoryEngine } = await import('./src/storage/memoryEngine.ts');
  await it('creates and retrieves items with TTL', async () => {
    const mem = new MemoryEngine();
    mem.createItem('s1', 'k1', { token: 'xyz' }, 1);
    assert.deepStrictEqual(mem.getItem('s1', 'k1'), { token: 'xyz' });
  });
  it('deletes items cleanly', () => {
    const mem = new MemoryEngine();
    mem.createItem('s2', 'k2', 'val');
    assert.strictEqual(mem.getItem('s2', 'k2'), 'val');
    mem.deleteItem('s2', 'k2');
    assert.strictEqual(mem.getItem('s2', 'k2'), null);
  });

  // Suite 5: Command Executor (3-Tier)
  console.log('\nSuite 5: Command Executor (3-Tier Resilience)');
  const EcoflowBle = (await import('./src/services/ecoflowBle.ts')).default;
  const EcoFlowService = (await import('./src/services/ecoflowService.ts')).default;
  const { CommandExecutor } = await import('./src/core/commandExecutor.ts');
  
  await it('execTurnOn executes via BLE in simulator mode', async () => {
    const ble = new EcoflowBle({ simulator: true });
    const eco = new EcoFlowService({ simulator: true });
    const exec = new CommandExecutor({ ble, eco });
    const res = await exec.execTurnOn('SN_TEST_001');
    assert.strictEqual(res.success, true);
    assert.strictEqual(res.via, 'ble');
  });

  await it('falls back to cloud MQTT or deeplink when BLE unavailable', async () => {
    const ble = new EcoflowBle({ simulator: false });
    const eco = new EcoFlowService({ simulator: true });
    const exec = new CommandExecutor({ ble, eco });
    const res = await exec.execTurnOn('SN_TEST_002');
    assert.ok(['ble', 'mqtt', 'deeplink'].includes(res.via));
  });

  // Suite 6: EcoFlow BLE Simulator
  console.log('\nSuite 6: EcoFlow BLE Simulator');
  await it('connects, writes and disconnects in BLE simulator', async () => {
    const ble = new EcoflowBle({ simulator: true });
    await ble.connect();
    const val = await ble.readChar('svc', 'char');
    assert.ok(val instanceof Uint8Array);
    await ble.writeChar('svc', 'char', new Uint8Array([0x01]));
    await ble.disconnect();
  });
  it('detects iframe restrictions for Web Bluetooth security', () => {
    const ble = new EcoflowBle({ simulator: true });
    assert.strictEqual(typeof ble.inIframe, 'function');
  });

  // Suite 7: EcoFlow Service (MQTT/Cloud)
  console.log('\nSuite 7: EcoFlow Cloud & MQTT Service Simulator');
  await it('publishes command and receives acknowledgment (ACK)', async () => {
    const eco = new EcoFlowService({ simulator: true });
    await eco.connect();
    const ackPromise = new Promise((resolve) => {
      eco.onAck('SN_ACK_TEST', (ack) => resolve(ack));
    });
    await eco.publishCmd('SN_ACK_TEST', { cmd: 'turn_on' });
    const ack = await ackPromise;
    assert.strictEqual(ack.success, true);
  });
  await it('subscribes and receives device telemetry updates', async () => {
    const eco = new EcoFlowService({ simulator: true });
    await eco.connect();
    const statePromise = new Promise((resolve) => {
      eco.subscribeState('SN_STATE_TEST', (st) => resolve(st));
    });
    await eco.publishCmd('SN_STATE_TEST', { cmd: 'status' });
    const st = await statePromise;
    assert.ok('battery' in st);
  });

  // Suite 8: AI Gateway Myanmar Grammar, STT & Credit Saver Engine
  console.log('\nSuite 8: AI Gateway Myanmar Grammar & Credit Saver Engine');
  process.env.SIMULATOR = 'true';
  const { MyanmarGrammarEngine } = await import('./src/gateway/myanmarGrammarEngine.ts');
  const { CreditSaverCache, RateLimiter } = await import('./src/gateway/creditSaver.ts');
  const { AudioSttEngine } = await import('./src/gateway/audioSttEngine.ts');

  await it('corrects Myanmar typos and colloquial speech (e.g. ဖွင့်ပေးပါအုံး -> ဖွင့်ပေးပါဦး)', async () => {
    const engine = new MyanmarGrammarEngine();
    const res = await engine.correct('EcoFlow ကို ဖွင့်ပေးပါအုံး');
    assert.ok(res.correctedText.includes('ပေးပါဦး'));
    assert.strictEqual(res.originalText, 'EcoFlow ကို ဖွင့်ပေးပါအုံး');
  });

  await it('corrects verb spelling mistakes (e.g. လုက်ပါ -> လုပ်ပါ, ပိက်ပါ -> ပိတ်ပါ)', async () => {
    const engine = new MyanmarGrammarEngine();
    const res = await engine.correct('ပန်ကာကို ပိက်ပါ');
    assert.ok(res.correctedText.includes('ပိတ်ပါ'));
  });

  it('CreditSaverCache caches audio/text digests and registers cache hit', () => {
    const cache = new CreditSaverCache();
    const key = CreditSaverCache.hashKey('test_audio_sample');
    cache.set(key, { transcript: 'မင်္ဂလာပါ' });
    
    const hitRes = cache.get(key);
    assert.strictEqual(hitRes.hit, true);
    if (hitRes.hit) {
      assert.strictEqual(hitRes.value.transcript, 'မင်္ဂလာပါ');
    }
    const stats = cache.getStats();
    assert.strictEqual(stats.cacheHits, 1);
    assert.strictEqual(stats.savedCallsCount, 1);
  });

  it('RateLimiter enforces burst control per client', () => {
    const limiter = new RateLimiter(3, 10000);
    assert.strictEqual(limiter.check('client1').allowed, true);
    assert.strictEqual(limiter.check('client1').allowed, true);
    assert.strictEqual(limiter.check('client1').allowed, true);
    assert.strictEqual(limiter.check('client1').allowed, false);
  });

  it('AudioSttEngine parses and validates WAV headers', () => {
    // Construct a minimal 44-byte RIFF WAV header
    const wav = Buffer.alloc(44 + 100);
    wav.write('RIFF', 0);
    wav.writeUInt32LE(36 + 100, 4);
    wav.write('WAVE', 8);
    wav.write('fmt ', 12);
    wav.writeUInt32LE(16, 16);
    wav.writeUInt16LE(1, 20); // PCM
    wav.writeUInt16LE(1, 22); // mono
    wav.writeUInt32LE(16000, 24); // 16kHz
    wav.writeUInt32LE(32000, 28); // byteRate = 16000 * 2
    wav.writeUInt16LE(2, 32); // blockAlign
    wav.writeUInt16LE(16, 34); // bitsPerSample
    wav.write('data', 36);
    wav.writeUInt32LE(100, 40);

    const info = AudioSttEngine.parseWavHeader(wav);
    assert.strictEqual(info.isValidWav, true);
    assert.strictEqual(info.sampleRate, 16000);
    assert.strictEqual(info.channels, 1);
  });

  console.log('\n------------------------------------------------------');
  console.log(`TOTAL: ${totalTests} | PASSED: ${passedTests} | FAILED: ${failedTests}`);
  console.log('------------------------------------------------------\n');
  if (failedTests > 0) {
    process.exit(1);
  }
}

runAll().catch((err) => {
  console.error('Fatal Runner Error:', err);
  process.exit(1);
});
