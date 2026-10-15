# Runbook: EcoFlow NLU + WebBLE Service

## Typical incidents & steps

### 1) WebBluetooth SecurityError / Permission Denied in Browser
Symptoms:
- Users report "SecurityError" or inability to pair device.
- Metrics: ble_permission_denied_total increase.

Steps:
1. Verify whether user is in an iframe. If so, instruct user to press "Open in standalone tab" button.
2. Check frontend logs for `inIframe()` flag and show instructions in UI.
3. If still fails, ask user to confirm they are using Chromium-based browser and HTTPS.
4. If problem persists, collect browser & OS details and escalate.

### 2) Gemini Quota Exhaustion (429)
Symptoms:
- gemini_errors_total increases; circuit breaker opens.
- Users observe degraded intent parsing (fallback to local rules).

Steps:
1. Check Prometheus alert.
2. Inspect GeminiService logs for 429 events and circuit open timestamp.
3. Circuit breaker auto-cools (1 hour by default). Consider:
   - Enabling secondary key
   - Temporarily increasing local rule coverage
4. If prolonged, request quota increase from provider.

### 3) EcoFlow Cloud/MQTT Failures
Symptoms:
- ecoflow_command_ack_timeout_total rises
- Devices not responding via cloud.

Steps:
1. Confirm MQTT broker health (EMQX or Mosquitto).
2. Check connection & auth logs for token expiry.
3. Switch affected sessions to simulator mode if needed and alert ops.
4. Rotate keys/tokens as needed and document.

## Operational notes
- Simulator mode (SIMULATOR=true) should be default in PR CI to ensure deterministic testing.
- Do not commit secrets to repo. Use GitHub Actions secrets or Vault.
