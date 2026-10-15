#!/usr/bin/env python3
"""
Unit tests for the Kyaw Gyi V8 Physical Validation Engine.
Runs with python3 -m unittest scripts/tests/test_physical_validation.py
"""

import os
import sys
import unittest

# Add scripts directory to path
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))

from physical_validation import (
    PhysicalValidationEngine,
    EvidenceState,
    FailureCategory,
    BURMESE_REPEAT_WARNING,
    VALID_CONTROLS
)

class TestPhysicalValidation(unittest.TestCase):

    def setUp(self):
        self.engine = PhysicalValidationEngine(
            evidence_dir="evidence/ir",
            report_path="docs/IR_VALIDATION_REPORT.json"
        )

    def test_controls_coverage(self):
        """Must cover all 5 discrete physical controls."""
        expected = ["POWER", "LIGHT", "SPEED", "OSCILLATION", "CENTER"]
        self.assertEqual(sorted(VALID_CONTROLS), sorted(expected))

    def test_sequential_state_transitions(self):
        """Strictly sequential state transition test; skipping states is prohibited."""
        # Valid forward transitions
        self.assertTrue(self.engine.can_transition(EvidenceState.NOT_TESTED, EvidenceState.LEARNING))
        self.assertTrue(self.engine.can_transition(EvidenceState.LEARNING, EvidenceState.LEARNED))
        self.assertTrue(self.engine.can_transition(EvidenceState.LEARNED, EvidenceState.TRANSMITTING))
        self.assertTrue(self.engine.can_transition(EvidenceState.TRANSMITTING, EvidenceState.ACKNOWLEDGED))
        self.assertTrue(self.engine.can_transition(EvidenceState.ACKNOWLEDGED, EvidenceState.OPTICAL_VERIFIED))
        self.assertTrue(self.engine.can_transition(EvidenceState.OPTICAL_VERIFIED, EvidenceState.APPLIANCE_VERIFIED))

        # Invalid skipped transitions (FORBIDDEN)
        self.assertFalse(self.engine.can_transition(EvidenceState.NOT_TESTED, EvidenceState.ACKNOWLEDGED))
        self.assertFalse(self.engine.can_transition(EvidenceState.NOT_TESTED, EvidenceState.APPLIANCE_VERIFIED))
        self.assertFalse(self.engine.can_transition(EvidenceState.ACKNOWLEDGED, EvidenceState.APPLIANCE_VERIFIED))
        self.assertFalse(self.engine.can_transition(EvidenceState.LEARNED, EvidenceState.OPTICAL_VERIFIED))

    def test_esp32_ack_is_not_appliance_verified(self):
        """ESP32 ACK (200 OK) must only yield ACKNOWLEDGED, never APPLIANCE_VERIFIED."""
        res = self.engine.validate_esp32_ack(200)
        self.assertEqual(res, EvidenceState.ACKNOWLEDGED)
        self.assertNotEqual(res, EvidenceState.APPLIANCE_VERIFIED)

    def test_missing_evidence_evaluates_to_not_tested(self):
        """In cloud environment with no evidence files, all controls must be NOT_TESTED."""
        results = self.engine.evaluate_all()
        for c in VALID_CONTROLS:
            self.assertIn(c, results)
            self.assertEqual(results[c]["state"], EvidenceState.NOT_TESTED.value)
            self.assertEqual(results[c]["status"], "NOT VERIFIED")

    def test_error_log_fields(self):
        """Every failure must produce structured log with all mandatory fields."""
        log_entry = self.engine.log_failure(
            action_key="ESP32_DISCOVERY_PROBE",
            category=FailureCategory.NETWORK,
            reason="mDNS multicast blocked by AP isolation",
            evidence="SocketTimeoutException",
            alternative_action="Fallback to Subnet Ping Scanner and Direct IP Presets"
        )
        required_keys = ["requestId", "actionKey", "timestamp", "category", "reason", "evidence", "alternativeAction"]
        for key in required_keys:
            self.assertIn(key, log_entry)
            self.assertTrue(bool(log_entry[key]))

    def test_repeat_failure_warning(self):
        """If an unresolved failure exists for the same method, flag Burmese warning."""
        action = "ESP32_CONNECT_POWER"
        self.engine.log_failure(
            action_key=action,
            category=FailureCategory.TIMEOUT,
            reason="Device unreachable",
            evidence="HTTP 408",
            alternative_action="Check IP and Wi-Fi credentials"
        )
        warning = self.engine.check_unresolved_failure(action)
        self.assertIsNotNone(warning)
        self.assertIn(BURMESE_REPEAT_WARNING, warning)

    def test_report_schema_compliance(self):
        """Validates that docs/IR_VALIDATION_REPORT.json is strictly compliant."""
        self.assertTrue(self.engine.validate_report_file())

if __name__ == "__main__":
    unittest.main()
