/*
 ============================================================================
  KYAW GYI AI - KENNEDE KN-5996HRB IR CONTROLLER FIRMWARE
  Hardware: ESP32 Development Board
  IR Receiver: VS1838B 38 kHz (GPIO 15)
  IR Transmitter: 38 kHz IR LED Driver (GPIO 4)
  Library: Arduino-IRremote v4+ (#include <IRremote.hpp>)
 ============================================================================
*/

#define IR_RECEIVE_PIN 15
#define IR_SEND_PIN 4
#define IR_CARRIER_KHZ 38

// WiFi Credentials - Replace with your network credentials
const char* WIFI_SSID = "YOUR_WIFI_SSID";
const char* WIFI_PASSWORD = "YOUR_WIFI_PASSWORD";

#include <Arduino.h>
#include <IRremote.hpp>
#include "ir_database.h"
#include "ir_capture.h"
#include "ir_transmitter.h"
#include "wifi_server.h"

void printStartupBanner() {
  Serial.println("========================================");
  Serial.println("KYAW GYI AI");
  Serial.println("KENNEDE KN-5996HRB IR CONTROLLER");
  Serial.println("========================================");
  Serial.println();
  Serial.printf("IR Receiver : GPIO %d\n", IR_RECEIVE_PIN);
  Serial.printf("IR Sender   : GPIO %d\n", IR_SEND_PIN);
  Serial.printf("Carrier     : %d kHz\n", IR_CARRIER_KHZ);
  Serial.println();
}

void setup() {
  Serial.begin(115200);
  delay(1000);

  printStartupBanner();

  // 1. Initialize Nonvolatile Database
  irDb.begin();
  Serial.println("[DATABASE] Nonvolatile IR storage loaded.");

  // 2. Initialize Hardware Peripherals
  irCapture.begin(IR_RECEIVE_PIN);
  irTransmitter.begin(IR_SEND_PIN, IR_CARRIER_KHZ);

  // 3. Initialize Wi-Fi & REST API Server
  wifiServer.begin(WIFI_SSID, WIFI_PASSWORD);

  Serial.println("\nWiFi:");
  if (WiFi.status() == WL_CONNECTED) {
    Serial.printf("IP: %s\n", WiFi.localIP().toString().c_str());
  } else {
    Serial.printf("IP: %s (SoftAP Mode)\n", WiFi.softAPIP().toString().c_str());
  }
  Serial.println("\nReady.\n");
}

void loop() {
  // Handle HTTP REST API clients
  wifiServer.handleClient();

  // Process IR signal capture loop if currently learning
  if (irCapture.isLearning()) {
    irCapture.processLoop();
  }

  delay(2);
}
