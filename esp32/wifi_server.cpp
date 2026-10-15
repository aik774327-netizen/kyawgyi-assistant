#include "wifi_server.h"

WifiServerManager wifiServer;

WifiServerManager::WifiServerManager() : _server(80) {}

void WifiServerManager::sendCorsHeaders() {
  _server.sendHeader("Access-Control-Allow-Origin", "*");
  _server.sendHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
  _server.sendHeader("Access-Control-Allow-Headers", "Content-Type");
}

void WifiServerManager::setupRoutes() {
  // Handle CORS preflight
  _server.onNotFound([this]() {
    if (_server.method() == HTTP_OPTIONS) {
      sendCorsHeaders();
      _server.send(204);
    } else {
      _server.send(404, "text/plain", "Not Found");
    }
  });

  // GET /api/status
  _server.on("/api/status", HTTP_GET, [this]() {
    sendCorsHeaders();
    DynamicJsonDocument doc(512);
    doc["device"] = "KENNEDE KN-5996HRB";
    doc["esp32"] = true;
    doc["receiver"] = true;
    doc["transmitter"] = true;
    doc["carrierFrequency"] = 38000;
    doc["learning"] = irCapture.isLearning();
    doc["learningTarget"] = irCapture.getCurrentTarget();
    doc["ip"] = WiFi.localIP().toString();
    doc["rssi"] = WiFi.RSSI();

    JsonObject learned = doc.createNestedObject("learned");
    learned["power"] = irDb.isCaptured("power");
    learned["light"] = irDb.isCaptured("light");
    learned["speed"] = irDb.isCaptured("speed");
    learned["oscillation"] = irDb.isCaptured("oscillation");
    learned["center"] = irDb.isCaptured("center");

    String res;
    serializeJson(doc, res);
    _server.send(200, "application/json", res);
  });

  // GET /api/device
  _server.on("/api/device", HTTP_GET, [this]() {
    sendCorsHeaders();
    DynamicJsonDocument doc(512);
    doc["device"] = "KENNEDE KN-5996HRB";
    doc["system"] = "KYAW GYI AI IR SMART CONTROLLER";
    doc["receiverPin"] = 15;
    doc["transmitterPin"] = 4;
    doc["carrierKhz"] = 38;
    String res;
    serializeJson(doc, res);
    _server.send(200, "application/json", res);
  });

  // GET /api/codes
  _server.on("/api/codes", HTTP_GET, [this]() {
    sendCorsHeaders();
    String json = irDb.exportJson();
    _server.send(200, "application/json", json);
  });

  // GET /api/raw
  _server.on("/api/raw", HTTP_GET, [this]() {
    sendCorsHeaders();
    String report = irDb.generateReport();
    _server.send(200, "text/plain", report);
  });

  // POST /api/learn/start
  _server.on("/api/learn/start", HTTP_POST, [this]() {
    sendCorsHeaders();
    String button = "power";
    if (_server.hasArg("plain")) {
      DynamicJsonDocument bodyDoc(256);
      deserializeJson(bodyDoc, _server.arg("plain"));
      if (bodyDoc.containsKey("button")) {
        button = bodyDoc["button"].as<String>();
      }
    } else if (_server.hasArg("button")) {
      button = _server.arg("button");
    }

    button.toLowerCase();
    irCapture.startLearning(button);

    DynamicJsonDocument resp(256);
    resp["status"] = "waiting";
    resp["button"] = button;
    resp["message"] = "Point original KN-5996HRB remote at VS1838B and press selected button once.";
    String out;
    serializeJson(resp, out);
    _server.send(200, "application/json", out);
  });

  // POST /api/learn/stop
  _server.on("/api/learn/stop", HTTP_POST, [this]() {
    sendCorsHeaders();
    irCapture.stopLearning();
    DynamicJsonDocument resp(128);
    resp["status"] = "stopped";
    String out;
    serializeJson(resp, out);
    _server.send(200, "application/json", out);
  });

  // Transmission endpoints
  auto handleSend = [this](const String &btnName) {
    sendCorsHeaders();
    String outLog;
    bool ok = irTransmitter.sendButton(btnName, outLog);
    DynamicJsonDocument resp(512);
    resp["device"] = "KENNEDE KN-5996HRB";
    resp["button"] = btnName;
    resp["action"] = "send";
    resp["success"] = ok;
    if (ok) {
      resp["message"] = outLog;
      _server.send(200, "application/json");
    } else {
      resp["error"] = outLog;
      _server.send(400, "application/json");
    }
    String out;
    serializeJson(resp, out);
    _server.sendContent(out);
  };

  _server.on("/api/send/power", HTTP_POST, [handleSend]() { handleSend("power"); });
  _server.on("/api/send/light", HTTP_POST, [handleSend]() { handleSend("light"); });
  _server.on("/api/send/speed", HTTP_POST, [handleSend]() { handleSend("speed"); });
  _server.on("/api/send/oscillation", HTTP_POST, [handleSend]() { handleSend("oscillation"); });
  _server.on("/api/send/center", HTTP_POST, [handleSend]() { handleSend("center"); });

  // GET /api/flash/status
  _server.on("/api/flash/status", HTTP_GET, [this]() {
    sendCorsHeaders();
    DynamicJsonDocument doc(1024);
    doc["flash_chip_size"] = ESP.getFlashChipSize();
    doc["flash_chip_speed"] = ESP.getFlashChipSpeed();
    doc["free_heap"] = ESP.getFreeHeap();
    doc["min_free_heap"] = ESP.getMinFreeHeap();
    doc["partition_scheme"] = "Custom 4MB (24KB NVS, 1.6MB App0, 1.6MB App1, 704KB SPIFFS)";
    doc["buttons_stored"] = irDb.getCapturedCount();
    doc["nvs_status"] = irDb.verifyIntegrity() ? "HEALTHY" : "CORRUPTED_OR_EMPTY";
    
    JsonArray capturedArr = doc.createNestedArray("buttons_captured");
    if (irDb.isCaptured("power")) capturedArr.add("power");
    if (irDb.isCaptured("light")) capturedArr.add("light");
    if (irDb.isCaptured("speed")) capturedArr.add("speed");
    if (irDb.isCaptured("oscillation")) capturedArr.add("oscillation");
    if (irDb.isCaptured("center")) capturedArr.add("center");

    String out;
    serializeJson(doc, out);
    _server.send(200, "application/json", out);
  });

  // POST /api/codes/import
  _server.on("/api/codes/import", HTTP_POST, [this]() {
    sendCorsHeaders();
    if (!_server.hasArg("plain")) {
      _server.send(400, "application/json", "{\"success\":false,\"error_category\":\"TECHNICAL\",\"error\":\"Missing request body\"}");
      return;
    }
    String body = _server.arg("plain");
    if (body.length() < 10) {
      _server.send(400, "application/json", "{\"success\":false,\"error_category\":\"TECHNICAL\",\"error\":\"Payload too short or empty\"}");
      return;
    }
    bool ok = irDb.importJson(body);
    DynamicJsonDocument resp(512);
    if (ok) {
      resp["success"] = true;
      resp["message"] = "Imported codes successfully to ESP32 Flash NVS";
      resp["buttons_stored"] = irDb.getCapturedCount();
      resp["free_heap"] = ESP.getFreeHeap();
      String out;
      serializeJson(resp, out);
      _server.send(200, "application/json", out);
    } else {
      resp["success"] = false;
      resp["error_category"] = "TECHNICAL";
      resp["error"] = "Failed to parse JSON codes or no valid buttons found";
      String out;
      serializeJson(resp, out);
      _server.send(400, "application/json", out);
    }
  });

  // GET & POST /api/codes/export
  auto handleExport = [this]() {
    sendCorsHeaders();
    String json = irDb.exportJson();
    _server.send(200, "application/json", json);
  };
  _server.on("/api/codes/export", HTTP_GET, handleExport);
  _server.on("/api/codes/export", HTTP_POST, handleExport);

  // POST /api/flash/clear
  _server.on("/api/flash/clear", HTTP_POST, [this]() {
    sendCorsHeaders();
    irDb.clearAll();
    _server.send(200, "application/json", "{\"success\":true,\"message\":\"ESP32 Flash storage cleared successfully.\"}");
  });

  // POST /api/reset (Factory reset)
  _server.on("/api/reset", HTTP_POST, [this]() {
    sendCorsHeaders();
    irDb.clearAll();
    _server.send(200, "application/json", "{\"success\":true,\"message\":\"All learned codes cleared.\"}");
  });
}

void WifiServerManager::begin(const char* ssid, const char* password, uint16_t port) {
  Serial.printf("[WIFI] Connecting to SSID: %s ...\n", ssid);
  WiFi.mode(WIFI_STA);
  WiFi.begin(ssid, password);

  unsigned long startAttemptTime = millis();
  while (WiFi.status() != WL_CONNECTED && millis() - startAttemptTime < 15000) {
    delay(500);
    Serial.print(".");
  }
  Serial.println();

  if (WiFi.status() == WL_CONNECTED) {
    Serial.printf("[WIFI CONNECTED] IP Address: %s\n", WiFi.localIP().toString().c_str());
  } else {
    Serial.println("[WIFI ERROR] Connection timeout. Starting AP fallback mode...");
    WiFi.mode(WIFI_AP);
    WiFi.softAP("KYAW_GYI_IR_CONTROLLER", "12345678");
    Serial.printf("[WIFI AP READY] IP: %s\n", WiFi.softAPIP().toString().c_str());
  }

  setupRoutes();
  _server.begin(port);
  Serial.printf("[HTTP SERVER] REST API listening on port %d\n", port);
}

void WifiServerManager::handleClient() {
  _server.handleClient();
}
