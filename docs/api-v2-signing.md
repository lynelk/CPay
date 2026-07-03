# CPay API v2 Signing

CPay API v2 uses a versioned RSA signature contract.

## Required headers

- `X-CPay-Merchant-Number`
- `X-CPay-Signature-Version`: `v2`
- `X-CPay-Timestamp`: ISO-8601 instant, for example `2026-07-03T08:00:00Z`
- `X-CPay-Nonce`: unique value per merchant request
- `X-CPay-Signature`: base64 RSA SHA256 signature

## Optional idempotency header

- `X-CPay-Idempotency-Key`: unique merchant-generated key for collect and payout requests

When the same key is reused with the same request body, CPay returns the stored result. When the same key is reused with a different body, CPay rejects the request.

## Canonical string

```text
METHOD
PATH
CANONICAL_QUERY
TIMESTAMP
NONCE
BODY_SHA256_HEX
```

Example path:

```text
/api/v2/payments/collect
```

For a request such as:

```text
GET /api/v2/balances?merchantNumber=123
```

The canonical query line is:

```text
merchantNumber=123
```

The body hash is the SHA-256 hex digest of the exact request body string sent over the wire. For GET requests with no body, hash an empty string.

## Replay protection

The default local implementation stores nonces in memory. Production deployments can switch to JDBC nonce storage with:

```text
cpay.security.nonce-store=jdbc
```

Clustered production deployments should use the JDBC nonce table or a Redis-backed implementation so all application instances share replay state.

## Backward compatibility

`/api/v1` keeps the legacy concatenated-field signing contract. `/api/v2` uses this versioned query-aware contract.
