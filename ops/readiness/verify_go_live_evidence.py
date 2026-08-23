#!/usr/bin/env python3
"""Fail closed unless every required production go-live evidence gate is verified."""

from __future__ import annotations

import json
import sys
from datetime import datetime
from pathlib import Path

ALLOWED_STATUSES = {"PENDING_MANUAL", "BLOCKED", "VERIFIED"}


def _valid_timestamp(value: object) -> bool:
    if not isinstance(value, str) or not value.strip():
        return False
    try:
        datetime.fromisoformat(value.replace("Z", "+00:00"))
        return True
    except ValueError:
        return False


def main() -> int:
    path = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).with_name("go-live-evidence.json")
    try:
        payload = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        print(f"ERROR: unable to read go-live evidence register {path}: {exc}", file=sys.stderr)
        return 2

    gates = payload.get("gates")
    if not isinstance(gates, list) or not gates:
        print("ERROR: evidence register must contain a non-empty gates list.", file=sys.stderr)
        return 2

    structural_errors: list[str] = []
    blockers: list[str] = []
    seen_ids: set[str] = set()

    for index, gate in enumerate(gates):
        if not isinstance(gate, dict):
            structural_errors.append(f"gate[{index}] is not an object")
            continue

        gate_id = gate.get("id")
        if not isinstance(gate_id, str) or not gate_id.strip():
            structural_errors.append(f"gate[{index}] has no id")
            continue
        if gate_id in seen_ids:
            structural_errors.append(f"duplicate gate id: {gate_id}")
        seen_ids.add(gate_id)

        status = gate.get("status")
        if status not in ALLOWED_STATUSES:
            structural_errors.append(f"{gate_id}: invalid status {status!r}")
            continue

        if not gate.get("required", False):
            continue

        if status != "VERIFIED":
            blockers.append(f"{gate_id}: {status}")
            continue

        verified_by = gate.get("verifiedBy")
        verified_at = gate.get("verifiedAt")
        evidence = gate.get("evidence")
        if not isinstance(verified_by, str) or not verified_by.strip():
            structural_errors.append(f"{gate_id}: VERIFIED gate requires verifiedBy")
        if not _valid_timestamp(verified_at):
            structural_errors.append(f"{gate_id}: VERIFIED gate requires ISO-8601 verifiedAt")
        if not isinstance(evidence, list) or not evidence or not all(isinstance(item, str) and item.strip() for item in evidence):
            structural_errors.append(f"{gate_id}: VERIFIED gate requires one or more evidence references")

    if structural_errors:
        print("Go-live evidence register is invalid:", file=sys.stderr)
        for item in structural_errors:
            print(f"  - {item}", file=sys.stderr)
        return 2

    if blockers:
        print("PRODUCTION GO-LIVE BLOCKED. Required manual gates remain:")
        for item in blockers:
            print(f"  - {item}")
        return 1

    print("PRODUCTION GO-LIVE EVIDENCE COMPLETE. All required gates are VERIFIED.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
