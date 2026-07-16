# Developer Experience

This guide captures the minimum merchant-facing developer experience expected for CPay integrations.

## SDK Targets

Generated SDKs should be built from `docs/api/cpay-v2-openapi.yaml`. The repository now also includes first-party signing helpers in `sdk/`.

| Language | First Helper |
|---|---|
| Node.js | Canonical request signing and idempotency headers. |
| Python | Canonical request signing and webhook verification. |
| PHP | Canonical request signing for merchant payout integrations. |

## Signing Helper Contract

SDKs should expose one helper that returns:

```json
{
  "X-CPay-Merchant-Number": "merchant_123",
  "X-CPay-Signature-Version": "v2",
  "X-CPay-Timestamp": "2026-07-16T09:30:00Z",
  "X-CPay-Nonce": "nonce_123",
  "X-CPay-Signature": "base64-signature",
  "X-CPay-Idempotency-Key": "idem_123"
}
```

The canonical string rules remain in `docs/api-v2-signing.md`.

## Documentation Portal

The public docs portal should include:

- quickstart
- request signing
- idempotency
- collections
- payouts
- refunds
- account validation
- merchant statement export
- webhook verification
- test mode
- error catalog

Until a portal is published, this repository is the source of truth.
