#include "ir_database.h"

IrDatabase irDb;

IrDatabase::IrDatabase() {
  memset(&power, 0, sizeof(IrButtonData));
  memset(&light, 0, sizeof(IrButtonData));
  memset(&speed, 0, sizeof(IrButtonData));
  memset(&oscillation, 0, sizeof(IrButtonData));
  memset(&center, 0, sizeof(IrButtonData));
}

void IrDatabase::begin() {
  preferences.begin("kennede_ir", false);
  loadAll();
}

void IrDatabase::loadAll() {
  loadButton("power", power);
  loadButton("light", light);
  loadButton("speed", speed);
  loadButton("oscillation", oscillation);
  loadButton("center", center);
}

String IrDatabase::getShortPrefix(const String &buttonName) {
  if (buttonName == "power") return "pwr_";
  if (buttonName == "light") return "lit_";
  if (buttonName == "speed") return "spd_";
  if (buttonName == "oscillation") return "osc_";
  if (buttonName == "center") return "ctr_";
  return buttonName.substring(0, 4) + "_";
}

bool IrDatabase::saveButton(const String &buttonName, const IrButtonData &data) {
  String prefix = getShortPrefix(buttonName);
  preferences.putBool((prefix + "cap").c_str(), data.captured);
  preferences.putString((prefix + "pro").c_str(), data.protocol);
  preferences.putUInt((prefix + "add").c_str(), data.address);
  preferences.putUInt((prefix + "cmd").c_str(), data.command);
  preferences.putUInt((prefix + "raw").c_str(), data.rawData);
  preferences.putUShort((prefix + "bit").c_str(), data.bits);
  preferences.putUChar((prefix + "rep").c_str(), data.repeatCount);
  preferences.putUShort((prefix + "len").c_str(), data.rawLength);
  
  if (data.rawLength > 0 && data.rawLength <= MAX_RAW_TIMINGS) {
    preferences.putBytes((prefix + "tim").c_str(), data.rawTiming, data.rawLength * sizeof(uint16_t));
  }

  // Update memory cache
  if (buttonName == "power") power = data;
  else if (buttonName == "light") light = data;
  else if (buttonName == "speed") speed = data;
  else if (buttonName == "oscillation") oscillation = data;
  else if (buttonName == "center") center = data;

  return true;
}

bool IrDatabase::loadButton(const String &buttonName, IrButtonData &data) {
  String prefix = getShortPrefix(buttonName);
  data.captured = preferences.getBool((prefix + "cap").c_str(), false);
  if (!data.captured) {
    // Fallback: check legacy non-short prefix for backwards compatibility
    String legacyPrefix = buttonName + "_";
    data.captured = preferences.getBool((legacyPrefix + "cap").c_str(), false);
    if (data.captured) {
      prefix = legacyPrefix;
    } else {
      data.protocol = "";
      data.address = 0;
      data.command = 0;
      data.rawData = 0;
      data.bits = 0;
      data.repeatCount = 0;
      data.rawLength = 0;
      return false;
    }
  }

  data.protocol = preferences.getString((prefix + "pro").c_str(), "");
  data.address = preferences.getUInt((prefix + "add").c_str(), 0);
  data.command = preferences.getUInt((prefix + "cmd").c_str(), 0);
  data.rawData = preferences.getUInt((prefix + "raw").c_str(), 0);
  data.bits = preferences.getUShort((prefix + "bit").c_str(), 0);
  data.repeatCount = preferences.getUChar((prefix + "rep").c_str(), 0);
  data.rawLength = preferences.getUShort((prefix + "len").c_str(), 0);

  if (data.rawLength > 0 && data.rawLength <= MAX_RAW_TIMINGS) {
    preferences.getBytes((prefix + "tim").c_str(), data.rawTiming, data.rawLength * sizeof(uint16_t));
  }
  return true;
}

bool IrDatabase::isCaptured(const String &buttonName) {
  if (buttonName == "power") return power.captured;
  if (buttonName == "light") return light.captured;
  if (buttonName == "speed") return speed.captured;
  if (buttonName == "oscillation") return oscillation.captured;
  if (buttonName == "center") return center.captured;
  return false;
}

void IrDatabase::clearAll() {
  preferences.clear();
  memset(&power, 0, sizeof(IrButtonData));
  memset(&light, 0, sizeof(IrButtonData));
  memset(&speed, 0, sizeof(IrButtonData));
  memset(&oscillation, 0, sizeof(IrButtonData));
  memset(&center, 0, sizeof(IrButtonData));
}

String IrDatabase::exportJson() {
  DynamicJsonDocument doc(16384);
  doc["device"] = "KENNEDE KN-5996HRB";
  doc["carrierFrequency"] = 38000;
  doc["carrier_frequency_assumed"] = 38000;

  JsonObject buttons = doc.createNestedObject("buttons");

  auto addButton = [&](const char* name, const IrButtonData &b) {
    JsonObject obj = buttons.createNestedObject(name);
    obj["captured"] = b.captured;
    if (b.captured) {
      obj["protocol"] = b.protocol;
      char addrBuf[12];
      sprintf(addrBuf, "0x%X", b.address);
      obj["address"] = addrBuf;
      char cmdBuf[12];
      sprintf(cmdBuf, "0x%X", b.command);
      obj["command"] = cmdBuf;
      char rawBuf[12];
      sprintf(rawBuf, "0x%X", b.rawData);
      obj["rawData"] = rawBuf;
      obj["bits"] = b.bits;
      obj["repeatCount"] = b.repeatCount;
      JsonArray timings = obj.createNestedArray("rawTiming");
      for (uint16_t i = 0; i < b.rawLength; i++) {
        timings.add(b.rawTiming[i]);
      }
    } else {
      obj["protocol"] = (char*)NULL;
      obj["address"] = (char*)NULL;
      obj["command"] = (char*)NULL;
      obj["rawData"] = (char*)NULL;
      obj["bits"] = (char*)NULL;
      obj.createNestedArray("rawTiming");
      obj["repeatCount"] = 0;
    }
  };

  addButton("power", power);
  addButton("light", light);
  addButton("speed", speed);
  addButton("oscillation", oscillation);
  addButton("center", center);

  String output;
  serializeJson(doc, output);
  return output;
}

bool IrDatabase::importJson(const String &jsonString) {
  DynamicJsonDocument doc(16384);
  DeserializationError err = deserializeJson(doc, jsonString);
  if (err) return false;

  JsonObject buttons = doc["buttons"];
  if (buttons.isNull()) return false;

  uint8_t importedCount = 0;
  auto importBtn = [&](const char* name) {
    JsonObject b = buttons[name];
    if (b.isNull()) return;
    IrButtonData data;
    memset(&data, 0, sizeof(IrButtonData));
    data.captured = b["captured"] | false;
    if (data.captured) {
      data.protocol = b["protocol"].as<String>();
      const char* addrStr = b["address"];
      data.address = addrStr ? strtoul(addrStr, NULL, 0) : 0;
      const char* cmdStr = b["command"];
      data.command = cmdStr ? strtoul(cmdStr, NULL, 0) : 0;
      const char* rawStr = b["rawData"];
      data.rawData = rawStr ? strtoul(rawStr, NULL, 0) : 0;
      data.bits = b["bits"] | 0;
      data.repeatCount = b["repeatCount"] | 0;

      JsonArray timings = b["rawTiming"];
      data.rawLength = min((size_t)timings.size(), (size_t)MAX_RAW_TIMINGS);
      for (uint16_t i = 0; i < data.rawLength; i++) {
        data.rawTiming[i] = timings[i].as<uint16_t>();
      }
    }
    saveButton(name, data);
    importedCount++;
  };

  importBtn("power");
  importBtn("light");
  importBtn("speed");
  importBtn("oscillation");
  importBtn("center");
  return (importedCount > 0);
}

uint8_t IrDatabase::getCapturedCount() {
  uint8_t count = 0;
  if (power.captured) count++;
  if (light.captured) count++;
  if (speed.captured) count++;
  if (oscillation.captured) count++;
  if (center.captured) count++;
  return count;
}

bool IrDatabase::verifyIntegrity() {
  if (power.captured && power.rawLength == 0 && power.protocol.length() == 0) return false;
  if (light.captured && light.rawLength == 0 && light.protocol.length() == 0) return false;
  if (speed.captured && speed.rawLength == 0 && speed.protocol.length() == 0) return false;
  if (oscillation.captured && oscillation.rawLength == 0 && oscillation.protocol.length() == 0) return false;
  if (center.captured && center.rawLength == 0 && center.protocol.length() == 0) return false;
  return true;
}

String IrDatabase::generateReport() {
  String report = "========================================\n";
  report += "KENNEDE KN-5996HRB IR PROFILE\n";
  report += "========================================\n\n";

  auto appendButtonReport = [&](const char* label, const IrButtonData &b) {
    report += String(label) + "\n";
    if (b.captured) {
      report += "Status  : LEARNED\n";
      report += "Protocol: " + b.protocol + "\n";
      char buf[32];
      sprintf(buf, "Address : 0x%X\nCommand : 0x%X\nRawData : 0x%X\n", b.address, b.command, b.rawData);
      report += String(buf);
      report += "Bits    : " + String(b.bits) + "\n";
      report += "Carrier : 38 kHz\n";
      report += "Repeats : " + String(b.repeatCount) + "\n";
      report += "Raw Len : " + String(b.rawLength) + " samples\n";
    } else {
      report += "Status  : IR CODE NOT LEARNED\n";
    }
    report += "\n";
  };

  appendButtonReport("POWER", power);
  appendButtonReport("LIGHT", light);
  appendButtonReport("SPEED", speed);
  appendButtonReport("OSCILLATION", oscillation);
  appendButtonReport("CENTER", center);

  return report;
}
