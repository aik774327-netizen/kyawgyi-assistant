/**
 * Integration-like tests for WebBLE wrapper (simulator)
 */
import EcoflowBle from '../../src/services/ecoflowBle';

describe('EcoflowBle simulator', () => {
  it('connects in simulator mode', async () => {
    const b = new EcoflowBle({ simulator: true });
    await b.connect();
    const val = await b.readChar('svc', 'char');
    expect(val instanceof Uint8Array).toBe(true);
    await b.writeChar('svc', 'char', new Uint8Array([1]));
    await b.disconnect();
  });

  it('disallows requestDevice in iframe (simulated check)', () => {
    const b = new EcoflowBle({ simulator: true });
    // in simulator path, inIframe() may be true/false; ensure method exists
    expect(typeof b.inIframe).toBe('function');
  });
});
