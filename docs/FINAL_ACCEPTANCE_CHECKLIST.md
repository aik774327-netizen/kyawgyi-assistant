# FINAL ACCEPTANCE CHECKLIST

## Section 1: Android & JVM Software Integrity
- [x] **Gradle Build**: `:app:assembleDebug` compiles cleanly without errors.
- [x] **Unit Testing**: `:app:testDebugUnitTest` passes (Robolectric, Integrity, Voice, Automation, HexDiscipline).
- [x] **Android Lint**: `:app:lintDebug` reports 0 errors.
- [x] **Node.js Gateway Tests**: `npm test` runs and passes 80/80 test fixtures.
- [x] **Artifact Integrity**: Valid APK produced with verifiable SHA-256 hash.

## Section 2: Core Subsystems Preservation
- [x] **Myanmar Voice Control**:
  - Voice-activity / energy detector ("ကျော်ကြီး" VAD trigger) operational (ML keyword spotting marked NOT DONE — needs model asset).
  - Multi-engine STT (Cloud Gateway / Local SpeechRecognizer) functional.
  - Local Myanmar phonetics TTS functional.
  - Offline Voice Fallback preserved without regressions.
- [x] **KENNEDE Fan Integration**:
  - Dedicated controls for POWER, LIGHT, SPEED, OSCILLATION, CENTER preserved.
  - Dynamic discovery engine supports mDNS, subnet broadcast scanning, and direct IP entry.
  - Sleep timer service and receiver operational.
- [x] **EcoFlow Management**:
  - Direct BLE peripheral connection logic preserved.
  - REST/MQTT API communication preserved.
  - Real-time telemetry monitoring and emergency load-shedding preserved.
- [x] **AI Angel & Studio**:
  - Conversational UI dialogs and Local NLU grammar processor intact.
- [x] **Shortcuts & Integrity Engine**:
  - Room database tables and DAOs functional.
  - Multi-tier error learning engine with failure warning (`ယခင်က မအောင်မြင်ခဲ့သော လုပ်ဆောင်ချက် ဖြစ်ပါသည်`) and alternative action suggestion operational.

## Section 3: ESP32 + KENNEDE IR Physical Evidence Chain
> **Note**: State progression MUST follow:
> `NOT_TESTED` -> `LEARNING` -> `LEARNED` -> `TRANSMITTING` -> `ACKNOWLEDGED` -> `OPTICAL_VERIFIED` -> `APPLIANCE_VERIFIED`
> ESP32 ACK does NOT imply APPLIANCE_VERIFIED.

| Button | Learning State | Transmission State | Optical Verification (Oscilloscope/IR Probe) | Physical Fan Reaction (Beep/Motion) | Overall Status |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **POWER** | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED | **NOT VERIFIED** |
| **LIGHT** | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED | **NOT VERIFIED** |
| **SPEED** | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED | **NOT VERIFIED** |
| **OSCILLATION** | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED | **NOT VERIFIED** |
| **CENTER** | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED | **NOT VERIFIED** |
