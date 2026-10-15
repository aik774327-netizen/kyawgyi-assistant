/**
 * EcoFlowService - MQTT/REST wrapper supporting simulator mode.
 *
 * Simulator mode activated when process.env.SIMULATOR === 'true'.
 * Real mode should use mqtt.js or other MQTT client (TODO in real integration).
 *
 * API:
 *   const svc = new EcoFlowService({ accessKey, secret, simulator });
 *   await svc.connect();
 *   await svc.publishCmd(sn, payload);
 *   svc.subscribeState(sn, callback);
 */
import { EventEmitter } from 'events';

export type CmdAck = { success: boolean; reason?: string };

export class EcoFlowService {
  simulator: boolean;
  connected = false;
  emitter: EventEmitter;

  constructor(opts?: { simulator?: boolean }) {
    this.simulator = opts?.simulator ?? process.env.SIMULATOR === 'true';
    this.emitter = new EventEmitter();
  }

  async connect() {
    if (this.simulator) {
      // Simulate connection delay
      await new Promise((res) => setTimeout(res, 50));
      this.connected = true;
      return;
    }

    // TODO: Implement real MQTT connection using mqtt or AWS IoT or broker credentials
    // For now, throw to avoid silent failures
    throw new Error('Real EcoFlow MQTT not implemented in stub - enable simulator or implement connector');
  }

  async publishCmd(sn: string, payload: any): Promise<CmdAck> {
    if (!this.connected) await this.connect();
    if (this.simulator) {
      // immediate ack simulation
      setTimeout(() => {
        this.emitter.emit(`ack:${sn}`, { success: true });
        this.emitter.emit(`state:${sn}`, { battery: Math.floor(Math.random() * 100), payload });
      }, 200);
      return { success: true };
    }
    // TODO: publish via MQTT - return ack after publish
    throw new Error('Publish to real broker not implemented');
  }

  subscribeState(sn: string, cb: (state: any) => void) {
    this.emitter.on(`state:${sn}`, cb);
  }

  onAck(sn: string, cb: (ack: CmdAck) => void) {
    this.emitter.on(`ack:${sn}`, cb);
  }

  disconnect() {
    this.connected = false;
    // teardown real connections if implemented
  }
}

export default EcoFlowService;
