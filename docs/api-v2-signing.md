# CPay API v2 Signing

CPay API v2 uses a versioned RSA signature contract.

## Required headers

- `X-CPay-Merchant-Number`
- `X-CPay-Signature-Version`: `v2`
- `X-CPay-Timestamp`: ISO-8601 instant, for example `2026-07-03T08:00:00Z`
- `X-CPay-Nonce`: unique value per merchant request
- `X-CPay-Signature`: base64 RSA SHA256 signature

## Canonical string

```text
METHOD
PATH
TIMESTAMP
NONCE
BODY_SHA256_HEX
```

Example path:

```text
/api/v2/payments/collect
```

The body hash is the SHA-256 hex digest of the exact request body string sent over the wire.

## Replay protection

The initial implementation stores nonces in memory for a short period. That is acceptable for single-node deployments. Clustered production deployments should move nonce storage to Redis or a database table with a TTL.

## Backward compatibility

`/api/v1` keeps the legacy concatenated-field signing contract. `/api/v2` uses this versioned contract.
