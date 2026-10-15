# Physical IR Evidence Directory

This directory stores verified physical evidence files for the 5 KENNEDE fan controls:
- `POWER`
- `LIGHT`
- `SPEED`
- `OSCILLATION`
- `CENTER`

## Evidence Types:
1. `<BUTTON>_capture.json`: Raw pulse timing captured by ESP32 VS1838B receiver.
2. `<BUTTON>_optical.csv` / `<BUTTON>_optical.png`: Oscilloscope / logic analyzer trace verifying 38kHz modulation.
3. `<BUTTON>_appliance.json`: Timestamped log of appliance physical confirmation (audio beep + motor activation).

## Current Status:
- Hardware is currently running in a cloud development container without physical transceivers.
- No physical evidence has been collected yet.
- All controls strictly remain **NOT_TESTED** / **NOT VERIFIED**.
