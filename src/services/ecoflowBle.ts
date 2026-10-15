/**
 * Web Bluetooth wrapper with guardrails and simulator fallback.
 * Exports a class EcoflowBle with connect(), disconnect(), readChar, writeChar, onNotify.
 *
 * Runtime guard:
 *   - If inside iframe (window.top !== window.self) do NOT call requestDevice().
 *   - If navigator.bluetooth.getAvailability() === false, do not attempt scans; instruct user.
 *
 * Simulator:
 *   - If process.env.SIMULATOR === 'true' this returns a simulator object implementing same API.
 */

export type NotifyCallback = (data: Uint8Array) => void;

class BleSimulator {
  connected = false;
  notifiers: Record<string, NotifyCallback[]> = {};

  async connect() {
    await new Promise((r) => setTimeout(r, 50));
    this.connected = true;
  }

  async disconnect() {
    this.connected = false;
  }

  async readChar(_service: string, _char: string) {
    // deterministic simulated value
    return new Uint8Array([0x01, 0x02]);
  }

  async writeChar(_service: string, _char: string, _data: Uint8Array) {
    // simulate notify to listeners
    setTimeout(() => {
      const key = `${_service}:${_char}`;
      (this.notifiers[key] || []).forEach((cb) => cb(_data));
    }, 30);
  }

  onNotify(service: string, char: string, cb: NotifyCallback) {
    const key = `${service}:${char}`;
    this.notifiers[key] = this.notifiers[key] || [];
    this.notifiers[key].push(cb);
  }
}

export class EcoflowBle {
  simulator: boolean;
  device: any | null = null;
  reconnectAttempts = 0;
  maxReconnect = 5;

  constructor(opts?: { simulator?: boolean }) {
    this.simulator = opts?.simulator ?? process.env.SIMULATOR === 'true';
  }

  async ensureRadioAvailable(): Promise<boolean> {
    if (this.simulator) return true;
    if (typeof navigator === 'undefined' || !('bluetooth' in navigator)) return false;
    // @ts-ignore
    if (typeof navigator.bluetooth.getAvailability === 'function') {
      // @ts-ignore
      return await navigator.bluetooth.getAvailability();
    }
    return false;
  }

  inIframe() {
    try {
      // top/self check can throw in some sandboxed envs
      return typeof window !== 'undefined' && window.top !== window.self;
    } catch (e) {
      return true;
    }
  }

  showOpenStandaloneButton() {
    // The real UI should render a button to open in new tab/window and guide user.
    console.warn('[EcoflowBle] Running in iframe. Show "Open in standalone tab" button instead of requestDevice().');
  }

  async connect() {
    if (this.simulator) {
      this.device = new BleSimulator();
      await this.device.connect();
      return;
    }

    if (this.inIframe()) {
      this.showOpenStandaloneButton();
      throw new Error('Cannot requestDevice from iframe - instruct user to open standalone tab');
    }

    if (!(await this.ensureRadioAvailable())) {
      throw new Error('Bluetooth radio not available');
    }

    // Real connection flow:
    // const device = await navigator.bluetooth.requestDevice({ filters: [{ services: ['ecoflow_service_uuid'] }] });
    // const server = await device.gatt.connect();
    // this.device = { device, server }
    throw new Error('Real WebBluetooth path not implemented in stub');
  }

  async disconnect() {
    if (!this.device) return;
    try {
      if (this.simulator) {
        await this.device.disconnect();
      } else {
        // disconnect real device/ server
      }
    } finally {
      this.device = null;
    }
  }

  async readChar(service: string, char: string): Promise<Uint8Array> {
    if (!this.device) throw new Error('Not connected');
    return this.device.readChar(service, char);
  }

  async writeChar(service: string, char: string, data: Uint8Array): Promise<void> {
    if (!this.device) throw new Error('Not connected');
    await this.device.writeChar(service, char, data);
  }

  onNotify(service: string, char: string, cb: NotifyCallback) {
    if (!this.device) throw new Error('Not connected');
    this.device.onNotify(service, char, cb);
  }
}

export default EcoflowBle;
