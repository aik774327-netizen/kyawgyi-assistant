# KENNEDE KN-5996HRB IR Smart Controller for KYAW GYI AI

Production-grade Infrared (IR) learning, database persistence, and Wi-Fi transmission system specifically engineered for the **KENNEDE KN-5996HRB** Fan with full integration into the **KYAW GYI AI** Myanmar Voice Assistant.

---

## 1. System Architecture

```
KN-5996HRB ORIGINAL REMOTE
        │
        ▼
VS1838B IR RECEIVER (38 kHz)
        │
        ▼
ESP32 (GPIO 15)
        │
        ├── Arduino-IRremote (Modern v4+ API)
        ├── Protocol Identification (NEC, Sony, Samsung, RC5, etc.)
        ├── UNKNOWN Protocol RAW Timing Capture
        ├── Preferences (Nonvolatile NVS storage)
        └── REST API Web Server (Port 80)
        ▲
        │ Wi-Fi HTTP API
        ▼
KYAW GYI AI ANDROID APP
  (Jetpack Compose M3, Real-time Visualizer, Burmese Voice Engine)
        │
        ▼
ESP32 IR TRANSMITTER (GPIO 4 -> NPN/MOSFET Driver Stage)
        │
        ▼
KENNEDE KN-5996HRB FAN
```

---

## 2. Hardware Pin Configuration & Wiring

### ESP32 Development Board

#### IR Receiver (VS1838B 38 kHz)
* **VCC** ➔ ESP32 **3.3V** (or 5V depending on receiver module board)
* **GND** ➔ ESP32 **GND**
* **OUT / Signal** ➔ ESP32 **GPIO 15**

#### IR Transmitter (High-Power IR LED Driver Stage)
> **CRITICAL SAFETY NOTE**: Do NOT drive high-power 38 kHz IR LEDs directly from ESP32 GPIOs (which max out at ~12mA–40mA). Use a transistor driver circuit:

```
                  +5V (External or ESP32 VIN)
                     │
                     ▲
                 [IR LED] (e.g. 940nm / 38kHz)
                     │
                    [R2: 10Ω - 33Ω, 0.5W Current Limiting]
                     │
                     ├── Collector (or Drain for N-Ch MOSFET)
ESP32 GPIO 4 ──[R1: 1kΩ]── Base (or Gate)   [2N2222 / 2N3904 / 2N7000]
                     │
                     └── Emitter (or Source) ── GND
```

---

## 3. ESP32 REST API Specification

| Endpoint | Method | Payload / Params | Description |
|---|---|---|---|
| `/api/status` | `GET` | None | Device status, receiver & transmitter readiness, learning state, and learned button flags |
| `/api/codes` | `GET` | None | Full JSON export of learned buttons with timings, addresses, commands |
| `/api/device` | `GET` | None | Hardware pins, carrier frequency (38 kHz), firmware identity |
| `/api/raw` | `GET` | None | Human-readable text report of all learned waveforms and protocols |
| `/api/learn/start` | `POST` | `{"button": "power"}` | Enters single-button capture mode for specified key (`power`, `light`, `speed`, `oscillation`, `center`) |
| `/api/learn/stop` | `POST` | None | Exits learning mode |
| `/api/send/power` | `POST` | None | Transmits the learned `power` signal (known protocol or RAW timing) |
| `/api/send/light` | `POST` | None | Transmits the learned `light` signal |
| `/api/send/speed` | `POST` | None | Transmits the learned `speed` signal |
| `/api/send/oscillation` | `POST` | None | Transmits the learned `oscillation` signal |
| `/api/send/center` | `POST` | None | Transmits the learned `center` signal |
| `/api/codes/import` | `POST` | Raw JSON | Restores backup codes into ESP32 flash |
| `/api/codes/export` | `POST` | None | Exports current codes as JSON |
| `/api/reset` | `POST` | None | Factory resets learned codes |

---

## 4. Burmese Voice Commands (KYAW GYI AI Integration)

| Burmese Voice Command | Mapped Action | API Dispatch |
|---|---|---|
| **"ကျော်ကြီး ပန်ကာဖွင့်"** | Fan Power On / Toggle | `POST /api/send/power` |
| **"ကျော်ကြီး ပန်ကာပိတ်"** | Fan Power Off / Toggle | `POST /api/send/power` |
| **"ကျော်ကြီး မီးဖွင့်"** | Fan Light On / Toggle | `POST /api/send/light` |
| **"ကျော်ကြီး မီးပိတ်"** | Fan Light Off / Toggle | `POST /api/send/light` |
| **"ကျော်ကြီး ပန်ကာအရှိန်မြှင့်"** | Speed Up | `POST /api/send/speed` |
| **"ကျော်ကြီး ပန်ကာအရှိန်လျှော့"** | Speed Down / Cycle | `POST /api/send/speed` |
| **"ကျော်ကြီး ပန်ကာလှည့်"** | Oscillation On | `POST /api/send/oscillation` |
| **"ကျော်ကြီး ပန်ကာမလှည့်တော့နဲ့"**| Oscillation Off | `POST /api/send/oscillation` |

---

## 5. Absolute Hex Discipline
The system strictly enforces zero-hallucination policies:
- No synthetic default IR codes like `0x20DF10EF` or `0x00FF00FF` are generated.
- All buttons default to `captured = false` until real physical signals are recorded from the physical KENNEDE KN-5996HRB remote.
- If a button has not been learned, transmission is prevented and the UI reports `IR CODE NOT LEARNED`.
