# V8 Physical Validation Package

## 1. Scope & Objective
This package governs the end-to-end hardware and physical verification protocol for the Kyaw Gyi (ကျော်ကြီး) IoT smart home appliance integration, specifically targeting the **KENNEDE KN-5996HRB Rechargeable Fan** and ESP32 IR transceiver module.

## 2. Strict Physical Evidence Chain
Every physical control action must advance sequentially through the evidence states without skipping:

```
NOT_TESTED
   ↓
LEARNING
   ↓
LEARNED
   ↓
TRANSMITTING
   ↓
ACKNOWLEDGED
   ↓
OPTICAL_VERIFIED
   ↓
APPLIANCE_VERIFIED
```

### State Definitions:
1. **NOT_TESTED**: Baseline state. Physical hardware or optical capture not yet initiated.
2. **LEARNING**: ESP32 VS1838B IR receiver is armed in learning mode awaiting carrier signal.
3. **LEARNED**: Signal timings captured into NVS flash memory and validated via HexDisciplineValidator.
4. **TRANSMITTING**: ESP32 IR transmitter diode is driving modulated 38kHz pulses.
5. **ACKNOWLEDGED**: ESP32 firmware returns HTTP 200 JSON acknowledgment. *(Note: ACK != Appliance Success).*
6. **OPTICAL_VERIFIED**: An independent photodiode, logic analyzer, or oscilloscope has verified optical emission from the transmitter.
7. **APPLIANCE_VERIFIED**: The physical appliance (fan motor, oscillation motor, or LED light) responds with audible confirmation beep and kinetic movement.

## 3. Dedicated Physical Controls
The KENNEDE KN-5996HRB fan provides 5 discrete physical controls:
1. **POWER**: Fan power toggle (On/Off)
2. **LIGHT**: LED illumination toggle / brightness control
3. **SPEED**: Fan speed step increment / cycle (Low/Med/High)
4. **OSCILLATION**: Louver / horizontal sweep oscillation toggle
5. **CENTER**: Center breeze directional toggle

## 4. Error Learning Engine & Disciplinary Enforcement
- Every failed operation generates a structured Error Log containing:
  - `requestId`: Unique UUID for tracking
  - `actionKey`: Identifier of action (e.g. `KENNEDE_IR_POWER`)
  - `timestamp`: ISO-8601 timestamp
  - `category`: Failure category (`NETWORK`, `HARDWARE`, `AUTHENTICATION`, `TECHNICAL`, `TIMEOUT`)
  - `reason`: Root cause explanation
  - `evidence`: Raw error trace or status code
  - `alternativeAction`: Suggested fallback route
- **Repeat Failure Prevention**:
  If an unresolved previous failure exists for the same target method, the system displays:
  `ယခင်က မအောင်မြင်ခဲ့သော လုပ်ဆောင်ချက် ဖြစ်ပါသည်`
  and refuses to repeat the failing method automatically.
