/**
 * Command execution core with 3-tier strategy:
 *  - BLE direct (if connected)
 *  - MQTT / EcoFlow cloud with ACK wait (5s)
 *  - Deep-link fallback (returns URI)
 *
 * This is a stub: adapt to your domain command schemas and authorization.
 */

import EcoflowBle from '../services/ecoflowBle.ts';
import EcoFlowService from '../services/ecoflowService.ts';

export type ExecResult = { success: boolean; via?: 'ble' | 'mqtt' | 'deeplink'; info?: any };

export class CommandExecutor {
  ble: EcoflowBle;
  eco: EcoFlowService;
  ackTimeoutMs = 5000;

  constructor(opts?: { ble?: EcoflowBle; eco?: EcoFlowService }) {
    this.ble = opts?.ble ?? new EcoflowBle();
    this.eco = opts?.eco ?? new EcoFlowService();
  }

  async execTurnOn(sn?: string): Promise<ExecResult> {
    // 1) Try BLE
    try {
      if (this.ble.simulator) {
        await this.ble.connect();
        await this.ble.writeChar('ecoflow_service', 'command', new Uint8Array([0x01]));
        await this.ble.disconnect();
        return { success: true, via: 'ble' };
      }
    } catch (e) {
      // proceed to next tier
    }

    // 2) Try Cloud/MQTT with ACK wait
    try {
      await this.eco.connect();
      const ackPromise = new Promise<ExecResult>((resolve) => {
        const timer = setTimeout(() => resolve({ success: false, via: 'mqtt', info: 'ack_timeout' }), this.ackTimeoutMs);
        this.eco.onAck(sn ?? 'unknown', (ack: any) => {
          clearTimeout(timer);
          resolve({ success: !!ack.success, via: 'mqtt', info: ack });
        });
      });
      await this.eco.publishCmd(sn ?? 'unknown', { cmd: 'turn_on' });
      const ack = await ackPromise;
      if (ack.success) return ack;
    } catch (e) {
      // fallback to deeplink
    }

    // 3) Deep-link fallback
    const uri = `ecoflow://device/${sn ?? 'unknown'}/turn_on`;
    return { success: false, via: 'deeplink', info: { uri } };
  }
}
