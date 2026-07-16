# CPay SDK Helpers

These SDK helpers implement the merchant-facing CPay API v2 signing contract from `docs/api-v2-signing.md`.

They are intentionally small and dependency-light so merchants can copy them into existing integrations or package them into their own build system.

## Helpers

| Runtime | File | Purpose |
|---|---|---|
| Node.js | `node/cpay-signing.js` | Canonical request signing and idempotency headers. |
| Python | `python/cpay_signing.py` | Canonical request signing and webhook verification primitives. |
| PHP | `php/CPaySigning.php` | Canonical request signing for merchant integrations. |

All helpers return the required v2 headers:

- `X-CPay-Merchant-Number`
- `X-CPay-Signature-Version`
- `X-CPay-Timestamp`
- `X-CPay-Nonce`
- `X-CPay-Signature`
- `X-CPay-Idempotency-Key` when supplied or generated
