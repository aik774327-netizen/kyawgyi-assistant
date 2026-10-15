/**
 * EcoFlowService tests for simulator publish/subscribe
 */
import EcoFlowService from '../../src/services/ecoflowService';

describe('EcoFlowService (simulator)', () => {
  it('connects and publishes command then receives ack', async () => {
    const s = new EcoFlowService({ simulator: true });
    await s.connect();
    const ackPromise = new Promise((res) => {
      s.onAck('SN1', (ack) => res(ack));
    });
    await s.publishCmd('SN1', { cmd: 'turn_on' });
    const ack: any = await ackPromise;
    expect(ack.success).toBe(true);
  });

  it('state subscription works', async () => {
    const s = new EcoFlowService({ simulator: true });
    await s.connect();
    const statePromise = new Promise((res) => {
      s.subscribeState('SN1', (st) => res(st));
    });
    await s.publishCmd('SN1', { cmd: 'status' });
    const st: any = await statePromise;
    expect(st).toHaveProperty('battery');
  });
});
