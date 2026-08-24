#!/usr/bin/env python3
"""Remove one known incomplete duplicate finance-settlement path block from CPay OpenAPI."""
from pathlib import Path

SPEC = Path("Docs/Api/cpay-v2-openapi.yaml")
text = SPEC.read_text(encoding="utf-8")
route = "  /api/v2/admin/finance-operations/settlements:\n"
expected_fragment = """  /api/v2/admin/finance-operations/settlements:
    get:
      summary: List settlement batches
      parameters:
      - in: query
        name: status
        schema:
"""

if text.count(route) != 2:
    raise SystemExit(f"Expected exactly two settlement path keys before repair, found {text.count(route)}")
if text.count(expected_fragment) != 1:
    raise SystemExit("Known incomplete duplicate settlement fragment was not found exactly once")

text = text.replace(expected_fragment, "", 1)
if text.count(route) != 1:
    raise SystemExit("Settlement path repair did not leave exactly one canonical path")

SPEC.write_text(text, encoding="utf-8")
print("Removed incomplete duplicate finance settlement path block")
