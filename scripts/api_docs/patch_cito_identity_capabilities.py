#!/usr/bin/env python3
"""One-time, fail-closed patch for the merchant identity capabilities API contract."""
from pathlib import Path

SPEC = Path("Docs/Api/cito-platform-v2-openapi.yaml")
text = SPEC.read_text(encoding="utf-8")

old_description = (
    "  description: >-\n"
    "    Session-authenticated merchant platform APIs that sit alongside the CPay payments\n"
    "    contract. Cito is the platform; CPay is the payments capability within it.\n"
)
new_description = (
    "  description: >-\n"
    "    Merchant platform APIs that sit alongside the CPay payments contract. Operations use\n"
    "    the authentication mechanism declared by the contract, including Cito merchant sessions\n"
    "    and signed merchant requests where explicitly specified. Cito is the platform; CPay is\n"
    "    the payments capability within it.\n"
)
if old_description not in text:
    raise SystemExit("Expected Cito API description marker not found")
text = text.replace(old_description, new_description, 1)

tag_marker = "tags:\n  - name: Cito Services\n"
if tag_marker not in text:
    raise SystemExit("Expected tag marker not found")
text = text.replace(tag_marker, "tags:\n  - name: Identity & Validation\n  - name: Cito Services\n", 1)

path_block = """  /api/v2/identity/capabilities:
    get:
      tags: [Identity & Validation]
      summary: List merchant-visible identity provider capabilities
      operationId: getMerchantIdentityCapabilities
      security:
        - CPayV2Signature: []
      parameters:
        - {name: merchantNumber, in: query, required: true, schema: {type: string}}
      responses:
        '200': {description: Identity provider capabilities without PII}
        '400': {$ref: '#/components/responses/BadRequest'}
        '401': {$ref: '#/components/responses/UnauthorizedSignedRequest'}
"""
paths_marker = "paths:\n"
if "/api/v2/identity/capabilities:" in text:
    raise SystemExit("Identity capabilities path already present; one-time patch must not run twice")
if paths_marker not in text:
    raise SystemExit("Expected paths marker not found")
text = text.replace(paths_marker, paths_marker + path_block, 1)

security_marker = "  securitySchemes:\n    MerchantSession:\n"
security_insert = """  securitySchemes:
    CPayV2Signature:
      type: apiKey
      in: header
      name: X-CPay-Signature
      description: Signature-based merchant authentication enforced by the CPay/Cito v2 request security service.
    MerchantSession:
"""
if security_marker not in text:
    raise SystemExit("Expected securitySchemes marker not found")
text = text.replace(security_marker, security_insert, 1)

unauthorized_marker = """    Unauthorized:
      description: A valid Cito merchant session is required
      content:
        application/json:
          schema: {$ref: '#/components/schemas/ErrorResponse'}
"""
unauthorized_insert = unauthorized_marker + """    UnauthorizedSignedRequest:
      description: A valid signed merchant request is required
      content:
        application/json:
          schema: {$ref: '#/components/schemas/ErrorResponse'}
"""
if unauthorized_marker not in text:
    raise SystemExit("Expected Unauthorized response marker not found")
text = text.replace(unauthorized_marker, unauthorized_insert, 1)

SPEC.write_text(text, encoding="utf-8")
print("Patched Cito OpenAPI identity capabilities route")
