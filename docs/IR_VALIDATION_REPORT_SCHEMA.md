# IR Validation Report Schema Specification

## Overview
This document specifies the standard JSON schema for the machine-readable verification report (`docs/IR_VALIDATION_REPORT.json`) within the Kyaw Gyi (ကျော်ကြီး) IoT smart home ecosystem.

## Strict Rules
1. **Never Skip States**: The physical control evidence chain must follow:
   `NOT_TESTED` ➔ `LEARNING` ➔ `LEARNED` ➔ `TRANSMITTING` ➔ `ACKNOWLEDGED` ➔ `OPTICAL_VERIFIED` ➔ `APPLIANCE_VERIFIED`.
2. **ESP32 ACK != Appliance Success**: An HTTP 200 / ACK response from the ESP32 transmitter indicates successful network packet receipt only. It does **NOT** constitute optical or physical appliance verification.
3. **No Fabrication**: Unverified controls must strictly remain `NOT_TESTED` / `NOT VERIFIED`.

## JSON Schema Specification

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "title": "KyawGyiIrValidationReport",
  "type": "object",
  "required": [
    "timestamp",
    "project",
    "build_status",
    "test_status",
    "subsystem_preservation",
    "physical_evidence_chain",
    "error_learning_engine_discipline"
  ],
  "properties": {
    "timestamp": {
      "type": "string",
      "format": "date-time"
    },
    "project": {
      "type": "string"
    },
    "build_status": {
      "type": "object",
      "required": ["compile_applet", "gradle_assemble_debug", "apk_path", "apk_sha256"],
      "properties": {
        "compile_applet": { "type": "string", "enum": ["VERIFIED", "NOT VERIFIED", "FAILED"] },
        "gradle_assemble_debug": { "type": "string", "enum": ["VERIFIED", "NOT VERIFIED", "FAILED"] },
        "apk_path": { "type": "string" },
        "apk_size_bytes": { "type": "integer" },
        "apk_sha256": { "type": "string" }
      }
    },
    "test_status": {
      "type": "object",
      "required": ["android_unit_tests", "android_lint", "node_gateway_tests"],
      "properties": {
        "android_unit_tests": {
          "type": "object",
          "required": ["command", "status", "failed_tests"],
          "properties": {
            "command": { "type": "string" },
            "status": { "type": "string", "enum": ["VERIFIED", "NOT VERIFIED", "FAILED"] },
            "failed_tests": { "type": "integer" }
          }
        },
        "android_lint": {
          "type": "object",
          "required": ["command", "status", "errors"],
          "properties": {
            "command": { "type": "string" },
            "status": { "type": "string", "enum": ["VERIFIED", "NOT VERIFIED", "FAILED"] },
            "errors": { "type": "integer" }
          }
        },
        "node_gateway_tests": {
          "type": "object",
          "required": ["command", "status", "total_tests", "passed_tests", "failed_tests"],
          "properties": {
            "command": { "type": "string" },
            "status": { "type": "string", "enum": ["VERIFIED", "NOT VERIFIED", "FAILED"] },
            "total_tests": { "type": "integer" },
            "passed_tests": { "type": "integer" },
            "failed_tests": { "type": "integer" }
          }
        }
      }
    },
    "subsystem_preservation": {
      "type": "object",
      "required": [
        "myanmar_voice_control",
        "speech_to_text",
        "text_to_speech",
        "wake_word_detector",
        "offline_voice_fallback",
        "kennede_ir_remote",
        "ecoflow_integration",
        "ai_angel",
        "studio",
        "shortcuts",
        "integrity_engine",
        "room_database",
        "esp32_integration"
      ],
      "additionalProperties": { "type": "string", "enum": ["VERIFIED", "NOT VERIFIED", "FAILED"] }
    },
    "physical_evidence_chain": {
      "type": "object",
      "required": ["POWER", "LIGHT", "SPEED", "OSCILLATION", "CENTER"],
      "properties": {
        "POWER": { "$ref": "#/definitions/ButtonVerification" },
        "LIGHT": { "$ref": "#/definitions/ButtonVerification" },
        "SPEED": { "$ref": "#/definitions/ButtonVerification" },
        "OSCILLATION": { "$ref": "#/definitions/ButtonVerification" },
        "CENTER": { "$ref": "#/definitions/ButtonVerification" }
      }
    },
    "error_learning_engine_discipline": {
      "type": "object",
      "required": ["repeat_failure_guard", "burmese_warning", "alternative_action_routing", "failure_categories_tracked"]
    }
  },
  "definitions": {
    "ButtonVerification": {
      "type": "object",
      "required": ["state", "status", "learning", "transmission", "acknowledged", "optical_verified", "appliance_verified"],
      "properties": {
        "state": {
          "type": "string",
          "enum": ["NOT_TESTED", "LEARNING", "LEARNED", "TRANSMITTING", "ACKNOWLEDGED", "OPTICAL_VERIFIED", "APPLIANCE_VERIFIED"]
        },
        "status": {
          "type": "string",
          "enum": ["NOT VERIFIED", "VERIFIED", "FAILED", "UNKNOWN"]
        },
        "learning": { "type": "string" },
        "transmission": { "type": "string" },
        "acknowledged": { "type": "string" },
        "optical_verified": { "type": "string" },
        "appliance_verified": { "type": "string" },
        "reason": { "type": "string" }
      }
    }
  }
}
```
