# GOOGLE AI STUDIO MASTER BUILD PROMPT & ARCHITECTURE SPECIFICATION

## 1. Executive System Purpose
Kyaw Gyi (ကျော်ကြီး) is an intelligent, voice-directed Myanmar smart home and appliance control orchestrator designed for Android and IoT edge devices. The system bridges edge-level voice recognition, IoT appliances (specifically KENNEDE rechargeable fans and EcoFlow portable power stations), and multi-layer resilience integrity engines.

## 2. Core Operational Pillars
1. **Myanmar Natural Voice Processing**:
   - Audio Record Wake Word Detector ("ကျော်ကြီး" / "မင်္ဂလာပါ").
   - 3-tier Speech-to-Text: AI Gateway Audio STT -> Local Android SpeechRecognizer -> Local Whisper/Wav STT.
   - Dual-tier TTS: AI Voice Studio & Local Android TTS with Myanmar phonetics fallback.
   - Offline speech fallback with zero-cloud degradation.
2. **KENNEDE IR Control Architecture**:
   - ESP32 hardware bridge with REST API (`/api/ir/button`, `/api/ir/capture`, `/api/ir/send`).
   - Strict 7-step Evidence Verification Chain:
     `NOT_TESTED` -> `LEARNING` -> `LEARNED` -> `TRANSMITTING` -> `ACKNOWLEDGED` -> `OPTICAL_VERIFIED` -> `APPLIANCE_VERIFIED`.
   - Discrete 5-button control layout: `POWER`, `LIGHT`, `SPEED`, `OSCILLATION`, `CENTER`.
   - Subnet scanning (`/24`), TCP ping probing, and Direct IP manual input presets for mDNS isolation tolerance.
   - Hex discipline and timing integrity enforcement.
3. **EcoFlow Integration**:
   - Bluetooth Low Energy (BLE) direct peripheral protocol with CRC/Protobuf framing.
   - Cloud MQTT / REST API synchronization.
   - Real-time battery telemetry, historic power metrics line graphs, and automated low-power emergency load shedding.
4. **Resilience & Integrity Engine**:
   - `ErrorLearningEngine`: Categorizes faults into `NETWORK`, `HARDWARE`, `AUTHENTICATION`, `TECHNICAL`, and `TIMEOUT`.
   - Error logging requirements: `requestId`, `actionKey`, `timestamp`, `category`, `reason`, `evidence`, and `alternativeAction`.
   - Repetition Avoidance Rule: If an unresolved previous failure exists for the same method, notify `"ယခင်က မအောင်မြင်ခဲ့သော လုပ်ဆောင်ချက် ဖြစ်ပါသည်"` and automatically propose alternative routing.
   - Cross-system state reconciliation and local Room database persistence.
5. **Telephony & Contact Calling Architecture (ACTION_DIAL)**:
   - Safe Android Intent: Dispatches via `Intent.ACTION_DIAL` (opens phone dialer with prepopulated number for explicit user review, never direct `ACTION_CALL` without user verification).
   - Risky Action Gate: Requires explicit user confirmation in Burmese before dialer dispatch.

## 3. Physical Verification Rigor
- ESP32 HTTP ACK (200 OK) represents transmission acknowledgment only, NOT appliance execution verification.
- Code structures strictly forbid synthetic IR code fabrication or fabricated physical testing results.
- Unverified physical appliances remain marked as `NOT_TESTED` or `NOT VERIFIED` until real hardware verification is performed.
