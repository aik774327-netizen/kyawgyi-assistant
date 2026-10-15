# Physical Validation Execution Runbook

## Hardware Prerequisites
1. **ESP32 DevKit V1** programmed with `esp32/kennede_ir_controller.ino`.
2. **VS1838B IR Receiver** connected to GPIO 15.
3. **940nm IR LED Transmitter** with NPN transistor driver connected to GPIO 4.
4. **Physical KENNEDE KN-5996HRB Remote Control**.
5. **Target KENNEDE KN-5996HRB Rechargeable Fan**.

## Execution Procedure

### Step 1: Network & Device Association
1. Power up ESP32 module.
2. Confirm mDNS broadcast `esp32-ir.local` or scan subnet `/24` using the Android App's Subnet Ping Scanner.
3. Verify endpoint health:
   ```bash
   curl -s http://<ESP32_IP>/api/status
   ```

### Step 2: IR Code Learning Sequence
For each control (`POWER`, `LIGHT`, `SPEED`, `OSCILLATION`, `CENTER`):
1. Send learning arm command:
   ```bash
   curl -X POST http://<ESP32_IP>/api/learn/start -d "button=POWER"
   ```
   *State*: `LEARNING`
2. Point original KENNEDE remote at VS1838B receiver and press button.
3. Fetch captured raw timing:
   ```bash
   curl -s http://<ESP32_IP>/api/raw?button=POWER
   ```
   *State*: `LEARNED`

### Step 3: Optical & Appliance Verification
1. Trigger transmission:
   ```bash
   curl -X POST http://<ESP32_IP>/api/send/POWER
   ```
   *State*: `TRANSMITTING` ➔ `ACKNOWLEDGED`
2. Inspect IR diode emission with IR receiver probe / phone camera.
   *State*: `OPTICAL_VERIFIED`
3. Observe physical fan motor starting / audible beep from fan buzzer.
   *State*: `APPLIANCE_VERIFIED`
4. Record proof into `evidence/ir/<BUTTON>_evidence.json`.
5. Run automated physical validation check:
   ```bash
   python3 scripts/physical_validation.py --check
   ```
