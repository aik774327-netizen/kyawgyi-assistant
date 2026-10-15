#ifndef WIFI_SERVER_H
#define WIFI_SERVER_H

#include <Arduino.h>
#include <WiFi.h>
#include <WebServer.h>
#include <ArduinoJson.h>
#include "ir_database.h"
#include "ir_capture.h"
#include "ir_transmitter.h"

class WifiServerManager {
public:
  WifiServerManager();
  void begin(const char* ssid, const char* password, uint16_t port = 80);
  void handleClient();

private:
  WebServer _server;
  void setupRoutes();
  void sendCorsHeaders();
};

extern WifiServerManager wifiServer;

#endif // WIFI_SERVER_H
