# Developer Experience

This guide captures the merchant-facing developer experience expected for CPay integrations. It is
the checklist for keeping the sandbox, SDKs, OpenAPI contract, Postman collection, and hosted docs
usable by a developer who has never integrated with CPay before.

## SDK Targets

Generated SDKs should be built from `Docs/Api/cpay-v2-openapi.yaml`. The repository now also includes first-party signing helpers in `Sdk/`.

| Language | First Helper |
|---|---|
| Node.js | Canonical request signing and idempotency headers. |
| Python | Canonical request signing and webhook verification. |
| PHP | Canonical request signing for merchant payout integrations. |

The hand-written helpers should stay copy-paste friendly. Full generated clients should stay in the
`Sdk/codegen/` workflow so the OpenAPI contract remains the source of truth.

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

The canonical string rules remain in `Docs/Api-v2-signing.md`.

## Sandbox Onboarding

The merchant portal and `Docs/sandbox-guide.md` should give every new developer enough context to
make the first successful request without private support:

- sandbox and production base URLs from settings
- sample sandbox merchant number
- test MSISDNs for MTN, Airtel, Airtel OpenAPI, Safaricom, and Yo! Payments
- request signing example
- idempotency and retry guidance
- `X-CPay-Environment` examples for `SANDBOX` and `PRODUCTION`
- production transaction cap behavior and who can change it
- payment link and invoice checkout examples
- webhook verification and replay guidance

Production is deliberately constrained while merchants graduate from sandbox: the
`production_transaction_limit_enabled` setting keeps a default daily cap of 10 transactions until an
administrator raises `production_transaction_limit_count` or disables the cap.

## Documentation Portal

The static docs portal entry point is `Docs/site/index.md` (published through GitHub Pages by the
docs workflow). It should include:

- quickstart
- request signing
- idempotency
- collections
- payouts
- refunds
- payment links and hosted checkout
- invoices/request-to-pay
- account validation
- merchant statement export
- webhook verification
- sandbox and production switching
- error catalog

Until a richer generated portal exists, this repository and the published `Docs/` tree are the source
of truth.
