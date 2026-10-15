# AI STUDIO EXECUTION PLAN

## Phase 1: Codebase Audit & Governance Baseline (Current)
- [x] Verify Android Toolchain, Gradle configuration, and local JVM unit tests.
- [x] Verify Android Lint checks and suppress/fix verified warnings.
- [x] Verify Node.js simulation and NLU grammar test suite (80/80 passing).
- [x] Establish official governance documentation (`GOOGLE_AI_STUDIO_MASTER_BUILD_PROMPT.md`, `AI_STUDIO_EXECUTION_PLAN.md`, `FINAL_ACCEPTANCE_CHECKLIST.md`, `VERIFICATION_STATUS.md`).

## Phase 2: System Architecture & Subsystem Preservation
- [x] **Voice Control Subsystem**:
  - `AudioRecordWakeWordDetector.kt`: Low-power audio buffering and amplitude/spectral trigger.
  - `MyanmarSttManager.kt` & `MyanmarTtsEngine.kt`: Multi-engine translation and fallback.
  - `OfflineVoiceFallbackTest.kt`: Unit test verification.
- [x] **KENNEDE IR Controller Subsystem**:
  - `KennedeVirtualRemoteControl.kt` & `KennedeFanRemotePad.kt`: UI representation.
  - `Esp32DynamicDiscoveryEngine.kt`: Dual mDNS & Subnet Ping Scanner with manual IP fallback.
  - `HexDisciplineValidator.kt`: Validation of raw timing and hex signals.
- [x] **EcoFlow Subsystem**:
  - `EcoFlowApiClient.kt`, `EcoFlowSigner.kt`, and `EcoFlowBle.ts`: Secure communication and BLE protocol handling.
  - `EcoFlowEmergencyEngine.kt`: Automatic threshold alerts and protective load-shedding.
- [x] **Integrity & Resilience Engine**:
  - `ErrorLearningEngine.kt`: Structured fault tracking, repeat failure blocking (`ယခင်က မအောင်မြင်ခဲ့သော လုပ်ဆောင်ချက် ဖြစ်ပါသည်`), and alternative action suggestion.
  - `AppDatabase.kt` & Room DAOs: Local encrypted Room database persistence.
- [x] **Contacts & Phone Calling Subsystem (ACTION_DIAL)**:
  - `RealDeviceActionExecutor.kt`: Opens system dialer via safe `Intent.ACTION_DIAL` (user reviews number before placing call; never direct unreviewed `ACTION_CALL`).
  - `MyanmarVoiceParser.kt`: Risky calling intents require explicit user confirmation before executing.

## Phase 3: Verification & Evidence Tracking Pipeline
- [x] Build and test automation execution in CI/JVM environment.
- [x] APK build generation and SHA-256 calculation.
- [ ] ESP32 + IR Physical Verification Pipeline:
  - Evidence State Transition: `NOT_TESTED` -> `LEARNING` -> `LEARNED` -> `TRANSMITTING` -> `ACKNOWLEDGED` -> `OPTICAL_VERIFIED` -> `APPLIANCE_VERIFIED`.
  - Buttons to capture & verify:
    1. `POWER`
    2. `LIGHT`
    3. `SPEED`
    4. `OSCILLATION`
    5. `CENTER`
  - *Current Status*: Paused at `NOT_TESTED` pending physical ESP32 and KENNEDE remote connection.

## Phase 4: Production Readiness & Release
- Machine-readable verification output (`verification_report.json`).
- Final sign-off against the `FINAL_ACCEPTANCE_CHECKLIST.md`.
