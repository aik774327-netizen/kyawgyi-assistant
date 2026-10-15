#ifndef IR_CAPTURE_H
#define IR_CAPTURE_H

#include <Arduino.h>
#include <IRremote.hpp>
#include "ir_database.h"

class IrCapture {
public:
  IrCapture();
  void begin(uint8_t receivePin);
  void startLearning(const String &targetButton);
  void stopLearning();
  bool isLearning() const;
  String getCurrentTarget() const;
  bool processLoop();

private:
  uint8_t _receivePin;
  bool _isLearning;
  String _targetButton;
  unsigned long _learningStartTime;
};

extern IrCapture irCapture;

#endif // IR_CAPTURE_H
