#!/usr/bin/env python3
"""Repair the known duplicated-components structural corruption in cpay-v2-openapi.yaml.

The file acquired a premature `components:` block in the middle of the path catalogue and lost the
header for the finance-settlement collection path. A later, complete `components:` block is already
present. This script is intentionally narrow and fails if the expected structure is not found.
"""
from pathlib import Path

path = Path("Docs/Api/cpay-v2-openapi.yaml")
text = path.read_text(encoding="utf-8")
marker = "components:\n  securitySchemes:\n"
first = text.find(marker)
second = text.find(marker, first + len(marker)) if first >= 0 else -1

if first < 0:
    raise SystemExit("OpenAPI repair: components marker not found")
if second < 0:
    print("OpenAPI repair: only one components block remains; nothing to repair")
    raise SystemExit(0)

malformed = text.find("      - in: query\n        name: merchantId\n", first, second)
if malformed < 0:
    raise SystemExit("OpenAPI repair: expected orphan settlement parameter block not found")

restored_header = """  /api/v2/admin/finance-operations/settlements:\n    get:\n      summary: List settlement batches\n      operationId: listFinanceSettlementBatches\n      parameters:\n"""

# Preserve all path content after the orphan parameter list, including the collection POST and
# following finance/product/production-maturity paths. Remove only the premature duplicate
# components block; keep the later complete components block untouched.
repaired = text[:first] + restored_header + text[malformed:second] + text[second:]

# Add an operationId to the collection POST if the damaged block predates operationId enforcement.
needle = "  /api/v2/admin/finance-operations/settlements:\n"
start = repaired.index(needle)
end = repaired.index("  /api/v2/admin/finance-operations/settlements/{id}:\n", start)
section = repaired[start:end]
post_needle = "    post:\n      summary: Create a settlement batch\n"
if post_needle in section and "operationId: createFinanceSettlementBatch" not in section:
    section = section.replace(
        post_needle,
        post_needle + "      operationId: createFinanceSettlementBatch\n",
        1,
    )
    repaired = repaired[:start] + section + repaired[end:]

path.write_text(repaired, encoding="utf-8")
print("OpenAPI repair: removed premature components block and restored settlement collection path")
