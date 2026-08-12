# v1 → v2 Migration Guide

This guide helps merchants and integrators move from the legacy v1 API (`/api`, `/api/v1`)
to the v2 gateway. v1 remains supported for existing integrations; v2 is where all new
features and hardening land.

## 1. Endpoint mapping

| v1 | v2 |
| --- | --- |
| `POST /api/doMobileMoneyPayIn` | `POST /api/v2/payments/collect` (compat) or `POST /api/v2/native/payments/collect` (adapter-native) |
| `POST /api/doMobileMoneyPayOut` | `POST /api/v2/payments/payout` or `POST /api/v2/native/payments/payout` |
| `POST /api/doTransactionCheckStatus` | `GET /api/v2/payments/{reference}?merchantNumber=...` |
| `POST /api/doGetBalances` | `GET /api/v2/balances?merchantNumber=...` |
| — | `POST /api/v2/accounts/validate` (new) |
| — | `GET /api/v2/statements` (new; JSON/CSV/XLSX) |
| — | `POST /api/v2/payment-links` (new) |
| — | `POST /api/v2/invoices` (new request-to-pay) |

## 2. Field mapping

| v1 field | v2 field | Notes |
| --- | --- | --- |
| `merchant_number` | `merchantNumber` | camelCase body fields |
| `payer_number` (payin) | `payer.value` (type `MSISDN`) | nested party object |
| `payee_number` (payout) | `payee.value` (type `MSISDN`) | nested party object |
| `amount` | `amount` | v2 uses string amounts; same semantics |
| `reference` | `reference` | merchant reference, used for idempotency/status |
| `description` | `description` | same |
| `callback_url` | `callbackUrl` | camelCase; validated (SSRF) in both |
| `currency`, `country` | `currency`, `country` | required in v2 |

## 3. Signing differences

| | v1 | v2 |
| --- | --- | --- |
| Where | `signature` field inside JSON body | `X-CPay-Signature` header |
| Content | `merchant_number + payer/payee + amount + reference + description` | canonical string over headers + body (see `Docs/Api-v2-signing.md`) |
| Key | merchant RSA private key | same, but canonicalization is explicit |
| Nonce/timestamp | not present | `X-CPay-Nonce`, `X-CPay-Timestamp` required |
| Merchant identity | body field | `X-CPay-Merchant-Number` header |

## 4. Callback differences

- v1 callbacks: legacy signed payload, historically inconsistent fields (the old docs had a
  duplicate `amount` and a `created_on_on` typo — the corrected, canonical callback shape is
  defined in `Docs/Webhook-events.md`).
- v2 callbacks: versioned event envelope (`eventId`, `eventType`, `eventVersion`, `createdAt`,
  `merchantNumber`, `reference`, `transactionId`, `status`, `amount`, `currency`, `country`,
  `channel`, `providerReference`, `reasonCode`, `message`, `metadata`), delivered to the
  registered endpoint with signing headers and a retry/backoff policy.
- Verify a callback URL before go-live with the test-callback tool
  (`POST /api/v2/admin/webhooks/merchants/{merchantId}/test-callback`).

## 5. Error model

| | v1 | v2 |
| --- | --- | --- |
| Envelope | `{code, message, error, status, ...}` with `000`-success | `ApiErrorResponse {code, message, traceId}` with HTTP status codes |
| Success signal | `code === "000"` | HTTP 200/201/202 |
| Errors | many stringly codes | typed codes in `Docs/Error-catalog.md` |

## 6. Idempotency improvements

v1 now accepts `Idempotency-Key` / `X-Idempotency-Key` on the money endpoints for
backward-compatible replay protection. v2 formalizes this as `X-CPay-Idempotency-Key`
with:

- same key + same body → original response returned
- same key + different body → rejected

## 7. What else changed under the hood (no client work)

- v2 payouts are risk-controlled: configurable limits and a maker-checker approval queue
  may park a payout (`APPROVAL_PENDING`).
- v2 payouts use reserve-then-capture ledger holds, so concurrent payouts cannot overspend
  the float.
- v2 collects/payouts post double-entry ledger entries.
- v2 routes through provider adapters with channel capability checks and per-provider
  error translation.
- v2 native payment requests can select sandbox or production with `X-CPay-Environment`.
- New hosted-checkout routes create payment links and request-to-pay invoices from signed merchant
  requests, then let customers pay with a tokenized public route.

## 8. Migration steps

1. Generate a key pair; register the merchant public key.
2. Confirm your callback receiver handles the v2 event envelope and signing headers.
3. Run sandbox collect, payout, status, balance, and hosted-checkout scenarios.
4. Switch status checks and balances first (read-only), then collections, then payouts.
5. Keep v1 live until the provider channel is certified and go-live checks pass
   (`Docs/developer-guide.md` section 14).
