#ifndef IR_DATABASE_H
#define IR_DATABASE_H

#include <Arduino.h>
#include <Preferences.h>
#include <ArduinoJson.h>

#define MAX_RAW_TIMINGS 300

struct IrButtonData {
  bool captured;
  String protocol;
  uint32_t address;
  uint32_t command;
  uint32_t rawData;
  uint16_t bits;
  uint8_t repeatCount;
  uint16_t rawLength;
  uint16_t rawTiming[MAX_RAW_TIMINGS];
};

class IrDatabase {
public:
  IrDatabase();
  void begin();
  bool saveButton(const String &buttonName, const IrButtonData &data);
  bool loadButton(const String &buttonName, IrButtonData &data);
  bool isCaptured(const String &buttonName);
  void clearAll();
  String exportJson();
  bool importJson(const String &jsonString);
  String generateReport();
  uint8_t getCapturedCount();
  bool verifyIntegrity();
  static String getShortPrefix(const String &buttonName);

  IrButtonData power;
  IrButtonData light;
  IrButtonData speed;
  IrButtonData oscillation;
  IrButtonData center;

private:
  Preferences preferences;
  void loadAll();
};

extern IrDatabase irDb;

#endif // IR_DATABASE_H
