#include "ir_capture.h"

IrCapture irCapture;

IrCapture::IrCapture() : _receivePin(15), _isLearning(false), _targetButton(""), _learningStartTime(0) {}

void IrCapture::begin(uint8_t receivePin) {
  _receivePin = receivePin;
  IrReceiver.begin(_receivePin, ENABLE_LED_FEEDBACK);
  Serial.printf("[IR RECEIVER] Initialized on GPIO %d (VS1838B 38kHz)\n", _receivePin);
}

void IrCapture::startLearning(const String &targetButton) {
  _targetButton = targetButton;
  _isLearning = true;
  _learningStartTime = millis();
  IrReceiver.resume(); // Ensure receiver is listening
  Serial.printf("[LEARNING MODE] Started for button: %s. Point KN-5996HRB remote at VS1838B.\n", targetButton.c_str());
}

void IrCapture::stopLearning() {
  _isLearning = false;
  _targetButton = "";
  Serial.println("[LEARNING MODE] Stopped.");
}

bool IrCapture::isLearning() const {
  return _isLearning;
}

String IrCapture::getCurrentTarget() const {
  return _targetButton;
}

bool IrCapture::processLoop() {
  if (!_isLearning) return false;

  // Optional 60-second timeout for learning
  if (millis() - _learningStartTime > 60000) {
    Serial.println("[LEARNING MODE] Timeout reached (60s). Stopping.");
    stopLearning();
    return false;
  }

  if (IrReceiver.decode()) {
    Serial.println("========================================");
    Serial.printf("[IR CAPTURE] Signal detected for: %s\n", _targetButton.c_str());
    IrReceiver.printIRResultShort(&Serial);
    IrReceiver.printIRSendUsage(&Serial);

    IrButtonData btn;
    memset(&btn, 0, sizeof(IrButtonData));
    btn.captured = true;

    // Protocol identification
    String protoName = getProtocolString(IrReceiver.decodedIRData.protocol);
    btn.protocol = protoName;
    btn.address = IrReceiver.decodedIRData.address;
    btn.command = IrReceiver.decodedIRData.command;
    btn.rawData = IrReceiver.decodedIRData.decodedRawData;
    btn.bits = IrReceiver.decodedIRData.numberOfBits;
    btn.repeatCount = (IrReceiver.decodedIRData.flags & IRDATA_FLAGS_IS_REPEAT) ? 1 : 0;

    // Capture raw timings
    uint16_t rawLen = IrReceiver.decodedIRData.rawDataPtr->rawlen - 1; // Exclude first dummy space
    btn.rawLength = min((size_t)rawLen, (size_t)MAX_RAW_TIMINGS);
    
    // Copy timings (converted to microseconds)
    for (uint16_t i = 0; i < btn.rawLength; i++) {
      btn.rawTiming[i] = IrReceiver.decodedIRData.rawDataPtr->rawbuf[i + 1] * MICROS_PER_TICK;
    }

    Serial.printf("Protocol: %s | Addr: 0x%X | Cmd: 0x%X | Bits: %d | RawLen: %d\n",
                  btn.protocol.c_str(), btn.address, btn.command, btn.bits, btn.rawLength);
    Serial.println("========================================");

    // Save into database
    irDb.saveButton(_targetButton, btn);

    // Stop learning after capturing exactly one button press
    stopLearning();
    IrReceiver.resume();
    return true;
  }

  return false;
}
