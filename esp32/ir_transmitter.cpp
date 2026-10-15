#include "ir_transmitter.h"

IrTransmitter irTransmitter;

IrTransmitter::IrTransmitter() : _sendPin(4), _carrierKhz(38) {}

void IrTransmitter::begin(uint8_t sendPin, uint8_t carrierKhz) {
  _sendPin = sendPin;
  _carrierKhz = carrierKhz;
  IrSender.begin(_sendPin);
  Serial.printf("[IR TRANSMITTER] Initialized on GPIO %d (Carrier %d kHz)\n", _sendPin, _carrierKhz);
}

bool IrTransmitter::sendButton(const String &buttonName, String &outLog) {
  IrButtonData btn;
  if (!irDb.loadButton(buttonName, btn) || !btn.captured) {
    outLog = "IR CODE NOT LEARNED for " + buttonName;
    Serial.printf("[IR TRANSMIT] %s\n", outLog.c_str());
    return false;
  }

  Serial.printf("[IR TRANSMIT] Sending '%s' (Protocol: %s)...\n", buttonName.c_str(), btn.protocol.c_str());

  // Check known protocols
  if (btn.protocol.equalsIgnoreCase("NEC")) {
    IrSender.sendNEC(btn.address, btn.command, btn.repeatCount);
    outLog = "Transmitted NEC (Address: 0x" + String(btn.address, HEX) + ", Command: 0x" + String(btn.command, HEX) + ")";
  } else if (btn.protocol.equalsIgnoreCase("Samsung")) {
    IrSender.sendSamsung(btn.address, btn.command, btn.repeatCount);
    outLog = "Transmitted Samsung (Address: 0x" + String(btn.address, HEX) + ", Command: 0x" + String(btn.command, HEX) + ")";
  } else if (btn.protocol.equalsIgnoreCase("Sony")) {
    IrSender.sendSony(btn.address, btn.command, btn.repeatCount, btn.bits);
    outLog = "Transmitted Sony (Command: 0x" + String(btn.command, HEX) + ")";
  } else if (btn.protocol.equalsIgnoreCase("RC5")) {
    IrSender.sendRC5(btn.address, btn.command, btn.repeatCount);
    outLog = "Transmitted RC5 (Command: 0x" + String(btn.command, HEX) + ")";
  } else if (btn.protocol.equalsIgnoreCase("RC6")) {
    IrSender.sendRC6(btn.address, btn.command, btn.repeatCount);
    outLog = "Transmitted RC6 (Command: 0x" + String(btn.command, HEX) + ")";
  } else if (btn.protocol.equalsIgnoreCase("Panasonic")) {
    IrSender.sendPanasonic(btn.address, btn.command, btn.repeatCount);
    outLog = "Transmitted Panasonic";
  } else if (btn.protocol.equalsIgnoreCase("Kaseikyo")) {
    IrSender.sendKaseikyo(btn.address, btn.command, btn.repeatCount, 0x1234);
    outLog = "Transmitted Kaseikyo";
  } else {
    // UNKNOWN PROTOCOL or RAW TIMING
    if (btn.rawLength > 0) {
      IrSender.sendRaw(btn.rawTiming, btn.rawLength, _carrierKhz);
      outLog = "UNKNOWN PROTOCOL — RAW TRANSMISSION (" + String(btn.rawLength) + " ticks @ " + String(_carrierKhz) + " kHz)";
    } else {
      outLog = "CAPTURE_FAILED: No valid raw timings found";
      return false;
    }
  }

  Serial.printf("[IR TRANSMIT SUCCESS] %s\n", outLog.c_str());
  return true;
}
