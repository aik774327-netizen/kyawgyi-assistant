#!/usr/bin/env python3
"""
Physical IR & Appliance Validation Engine for Kyaw Gyi (ကျော်ကြီး).
Enforces the mandatory 7-stage evidence chain:
NOT_TESTED -> LEARNING -> LEARNED -> TRANSMITTING -> ACKNOWLEDGED -> OPTICAL_VERIFIED -> APPLIANCE_VERIFIED.

Important: ESP32 HTTP ACK (200 OK) != Physical Appliance Success.
"""

import os
import sys
import json
import uuid
import datetime
from enum import Enum
from typing import Dict, List, Optional, Any

VALID_CONTROLS = ["POWER", "LIGHT", "SPEED", "OSCILLATION", "CENTER"]

class EvidenceState(str, Enum):
    NOT_TESTED = "NOT_TESTED"
    LEARNING = "LEARNING"
    LEARNED = "LEARNED"
    TRANSMITTING = "TRANSMITTING"
    ACKNOWLEDGED = "ACKNOWLEDGED"
    OPTICAL_VERIFIED = "OPTICAL_VERIFIED"
    APPLIANCE_VERIFIED = "APPLIANCE_VERIFIED"

STATE_HIERARCHY = [
    EvidenceState.NOT_TESTED,
    EvidenceState.LEARNING,
    EvidenceState.LEARNED,
    EvidenceState.TRANSMITTING,
    EvidenceState.ACKNOWLEDGED,
    EvidenceState.OPTICAL_VERIFIED,
    EvidenceState.APPLIANCE_VERIFIED
]

class FailureCategory(str, Enum):
    NETWORK = "NETWORK"
    HARDWARE = "HARDWARE"
    AUTHENTICATION = "AUTHENTICATION"
    TECHNICAL = "TECHNICAL"
    TIMEOUT = "TIMEOUT"

BURMESE_REPEAT_WARNING = "ယခင်က မအောင်မြင်ခဲ့သော လုပ်ဆောင်ချက် ဖြစ်ပါသည်"

class ErrorLog:
    def __init__(self, action_key: str, category: FailureCategory, reason: str, evidence: str, alternative_action: str):
        self.request_id = str(uuid.uuid4())
        self.action_key = action_key
        self.timestamp = datetime.datetime.now(datetime.timezone.utc).isoformat()
        self.category = category.value if isinstance(category, FailureCategory) else str(category)
        self.reason = reason
        self.evidence = evidence
        self.alternative_action = alternative_action

    def to_dict(self) -> Dict[str, Any]:
        return {
            "requestId": self.request_id,
            "actionKey": self.action_key,
            "timestamp": self.timestamp,
            "category": self.category,
            "reason": self.reason,
            "evidence": self.evidence,
            "alternativeAction": self.alternative_action
        }

class PhysicalValidationEngine:
    def __init__(self, evidence_dir: str = "evidence/ir", report_path: str = "docs/IR_VALIDATION_REPORT.json"):
        self.evidence_dir = evidence_dir
        self.report_path = report_path
        self.error_history: List[Dict[str, Any]] = []

    def can_transition(self, current_state: EvidenceState, next_state: EvidenceState) -> bool:
        """Enforces strictly sequential transitions without skipping."""
        try:
            curr_idx = STATE_HIERARCHY.index(current_state)
            next_idx = STATE_HIERARCHY.index(next_state)
            return next_idx == curr_idx + 1
        except ValueError:
            return False

    def validate_esp32_ack(self, http_status: int) -> EvidenceState:
        """
        Confirms that an HTTP ACK only advances state to ACKNOWLEDGED,
        never to OPTICAL_VERIFIED or APPLIANCE_VERIFIED.
        """
        if http_status == 200:
            return EvidenceState.ACKNOWLEDGED
        raise ValueError(f"ESP32 request failed with status {http_status}")

    def log_failure(self, action_key: str, category: FailureCategory, reason: str, evidence: str, alternative_action: str) -> Dict[str, Any]:
        error = ErrorLog(action_key, category, reason, evidence, alternative_action)
        err_dict = error.to_dict()
        self.error_history.append(err_dict)
        return err_dict

    def check_unresolved_failure(self, action_key: str) -> Optional[str]:
        """If an unresolved failure exists for action_key, returns the Burmese warning."""
        for err in reversed(self.error_history):
            if err.get("actionKey") == action_key:
                return f"{BURMESE_REPEAT_WARNING} (Action: {action_key}, Previous Failure: {err.get('reason')})"
        return None

    def evaluate_control(self, control: str) -> Dict[str, Any]:
        """Evaluates actual evidence on disk for a given control."""
        if control not in VALID_CONTROLS:
            raise ValueError(f"Unknown control: {control}")

        capture_file = os.path.join(self.evidence_dir, f"{control}_capture.json")
        optical_file = os.path.join(self.evidence_dir, f"{control}_optical.csv")
        appliance_file = os.path.join(self.evidence_dir, f"{control}_appliance.json")

        # Check evidence progression
        if not os.path.exists(capture_file):
            return {
                "control": control,
                "state": EvidenceState.NOT_TESTED.value,
                "status": "NOT VERIFIED",
                "reason": "Physical ESP32 receiver has not captured IR pulses."
            }

        if not os.path.exists(optical_file):
            return {
                "control": control,
                "state": EvidenceState.LEARNED.value,
                "status": "NOT VERIFIED",
                "reason": "IR pulses learned, but optical transmission has not been measured."
            }

        if not os.path.exists(appliance_file):
            return {
                "control": control,
                "state": EvidenceState.OPTICAL_VERIFIED.value,
                "status": "NOT VERIFIED",
                "reason": "Optical output verified, but physical appliance movement not confirmed."
            }

        return {
            "control": control,
            "state": EvidenceState.APPLIANCE_VERIFIED.value,
            "status": "VERIFIED",
            "reason": "Physical appliance response verified with evidence."
        }

    def evaluate_all(self) -> Dict[str, Any]:
        results = {}
        for c in VALID_CONTROLS:
            results[c] = self.evaluate_control(c)
        return results

    def validate_report_file(self) -> bool:
        """Validates that the existing IR_VALIDATION_REPORT.json exists and conforms."""
        if not os.path.exists(self.report_path):
            return False
        try:
            with open(self.report_path, "r", encoding="utf-8") as f:
                data = json.load(f)
            required_keys = [
                "timestamp", "project", "build_status", "test_status",
                "subsystem_preservation", "physical_evidence_chain",
                "error_learning_engine_discipline"
            ]
            for k in required_keys:
                if k not in data:
                    return False
            # Verify controls are present
            chain = data.get("physical_evidence_chain", {})
            for c in VALID_CONTROLS:
                if c not in chain:
                    return False
                btn_data = chain[c]
                # If state is NOT_TESTED, status must not be VERIFIED
                if btn_data.get("state") == "NOT_TESTED" and btn_data.get("status") == "VERIFIED":
                    return False
            return True
        except Exception:
            return False

def main():
    engine = PhysicalValidationEngine()
    print("==================================================")
    print(" Kyaw Gyi V8 Physical Validation Engine")
    print("==================================================")

    results = engine.evaluate_all()
    all_not_tested = all(res["state"] == EvidenceState.NOT_TESTED.value for res in results.values())

    for c, res in results.items():
        print(f"[{c}] State: {res['state']} | Status: {res['status']} | Note: {res['reason']}")

    report_valid = engine.validate_report_file()
    print(f"\nReport Schema Integrity ({engine.report_path}): {'VALID' if report_valid else 'INVALID'}")

    if all_not_tested:
        print("\nPhysical Hardware Status: Awaiting connection of ESP32 and KENNEDE remote.")
        print("Enforcing strict discipline: No fake codes or simulated hardware.")

    sys.exit(0 if report_valid else 1)

if __name__ == "__main__":
    main()
