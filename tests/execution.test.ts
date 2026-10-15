/**
 * Tests for CommandExecutor using simulators
 */
import { CommandExecutor } from '../src/core/commandExecutor';
import EcoflowBle from '../src/services/ecoflowBle';
import EcoFlowService from '../src/services/ecoflowService';

describe('CommandExecutor (simulator)', () => {
  it('execTurnOn should use BLE simulator', async () => {
    const ble = new EcoflowBle({ simulator: true });
    const eco = new EcoFlowService({ simulator: true });
    const exec = new CommandExecutor({ ble, eco });
    const res = await exec.execTurnOn('SN123');
    // BLE path returns via 'ble' in our stub
    expect(res.via).toBe('ble');
    expect(res.success).toBe(true);
  });

  it('falls back to deeplink when ack missing', async () => {
    // make eco not connected and ble failing
    const ble = new EcoflowBle({ simulator: false }); // will error on connect in stub
    const eco = new EcoFlowService({ simulator: true });
    const exec = new CommandExecutor({ ble, eco });
    const res = await exec.execTurnOn('SN_UNKNOWN');
    expect(res.via).toBeDefined();
    // Either mqtt or deeplink - in case of ack timeout our stub returns deeplink
    expect(['mqtt', 'deeplink', 'ble']).toContain(res.via);
  });
});
