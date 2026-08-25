from pathlib import Path
import re

PATH = Path("Docs/Api/cpay-v2-openapi.yaml")
text = PATH.read_text(encoding="utf-8")


def replace_once(old: str, new: str, label: str) -> None:
    global text
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one match, found {count}")
    text = text.replace(old, new, 1)


replace_once(
    "  version: 1.9.0\n",
    "  version: 1.9.1\n",
    "API version",
)

replace_once(
    "security:\n- CPayV2Signature: []\npaths:\n",
    "servers:\n"
    "  - url: /\n"
    "    description: Current CPay deployment host\n"
    "security:\n"
    "- CPayV2Signature: []\n"
    "paths:\n",
    "root server declaration",
)

replace_once(
    "  /api/v2/invoices/{reference}/send:\n",
    "  /api/v2/invoices/{reference}/actions/send:\n",
    "invoice send route",
)

replace_once(
    "  /api/v2/admin/webhooks/{endpointId}/rotate-secret:\n",
    "  /api/v2/admin/webhooks/endpoints/{endpointId}/rotate-secret:\n",
    "webhook secret rotation route",
)

legacy_case_pattern = re.compile(
    r"  /api/v2/admin/compliance/cases/\{id\}/decision:\n"
    r".*?"
    r"(?=  /api/v2/admin/provider-certification/summary:\n)",
    re.DOTALL,
)
text, removed = legacy_case_pattern.subn("", text, count=1)
if removed != 1:
    raise SystemExit(
        f"legacy compliance decision route: expected exactly one block, removed {removed}"
    )

replace_once(
    "      summary: List open compliance cases\n"
    "      operationId: listComplianceCases\n"
    "      security:\n"
    "      - AdminBasicAuth: []\n"
    "      responses:\n",
    "      summary: List compliance cases\n"
    "      operationId: listComplianceCases\n"
    "      security:\n"
    "      - AdminBasicAuth: []\n"
    "      parameters:\n"
    "      - name: status\n"
    "        in: query\n"
    "        required: false\n"
    "        schema:\n"
    "          type: string\n"
    "      - name: subjectReference\n"
    "        in: query\n"
    "        required: false\n"
    "        schema:\n"
    "          type: string\n"
    "      responses:\n",
    "compliance case list parameters",
)

replace_once(
    "      operationId: createComplianceCase\n"
    "      requestBody:\n",
    "      operationId: createComplianceCase\n"
    "      security:\n"
    "      - AdminBasicAuth: []\n"
    "      requestBody:\n",
    "compliance case create security",
)

replace_once(
    "      operationId: getComplianceCase\n"
    "      parameters:\n",
    "      operationId: getComplianceCase\n"
    "      security:\n"
    "      - AdminBasicAuth: []\n"
    "      parameters:\n",
    "compliance case read security",
)

replace_once(
    "      operationId: decideComplianceCaseByReference\n"
    "      parameters:\n",
    "      operationId: decideComplianceCaseByReference\n"
    "      security:\n"
    "      - AdminBasicAuth: []\n"
    "      parameters:\n",
    "compliance case decision security",
)

for stale in (
    "/api/v2/invoices/{reference}/send:",
    "/api/v2/admin/webhooks/{endpointId}/rotate-secret:",
    "/api/v2/admin/compliance/cases/{id}/decision:",
):
    if stale in text:
        raise SystemExit(f"stale route remains after patch: {stale}")

PATH.write_text(text, encoding="utf-8")
print("CPay OpenAPI contract hardening patch applied successfully.")
