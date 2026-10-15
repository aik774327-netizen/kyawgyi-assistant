#ifndef IR_TRANSMITTER_H
#define IR_TRANSMITTER_H

#include <Arduino.h>
#include <IRremote.hpp>
#include "ir_database.h"

class IrTransmitter {
public:
  IrTransmitter();
  void begin(uint8_t sendPin, uint8_t carrierKhz = 38);
  bool sendButton(const String &buttonName, String &outLog);

private:
  uint8_t _sendPin;
  uint8_t _carrierKhz;
};

extern IrTransmitter irTransmitter;

#endif // IR_TRANSMITTER_H
