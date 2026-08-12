# CPay Developer Guide

This guide is the onboarding path for a third-party developer integrating with CPay's
v2 gateway: collections, payouts, hosted checkout, payment links, request-to-pay
invoices, status checks, balances, callbacks, statements, and cross-border transfers.
Detailed references live in the linked documents; this page is the 30-minute version.

## 1. Overview

CPay is a mobile-money payments gateway for MTN MoMo, Airtel Money, Airtel OpenAPI,
Safaricom M-Pesa, and Yo! Payments. Two API generations run side-by-side:

| Generation | Base path | Auth | Status |
| --- | --- | --- | --- |
| v1 (legacy) | `/api` / `/api/v1` | RSA signature in JSON body | Supported, stable, no new features |
| **v2 (recommended)** | `/api/v2` | RSA signature over canonical headers | New integrations go here |

Always build new integrations against v2. v1 exists for existing merchants.

## 2. Environments

| Environment | Base URL | Purpose |
| --- | --- | --- |
| Sandbox | `https://sandbox.cpay.example/api/v2` | Testing with provider sandboxes or controlled CPay simulations |
| Production | `https://api.cpay.example/api/v2` | Live traffic after channel certification and approval |

Sandbox behaviour is documented in `sandbox-guide.md`. The merchant portal also exposes
the configured sandbox URL, sample merchant number, test MSISDNs, idempotency window,
and currently selected environment.

Use the optional environment header when a merchant has both sandbox and production
configuration:

```http
X-CPay-Environment: SANDBOX
X-CPay-Environment: PRODUCTION
```

Production calls are capped by default at 10 transactions per day while the
`production_transaction_limit_enabled` setting is true. An administrator can raise the
`production_transaction_limit_count` value or disable the cap when the merchant is ready.

## 3. Authentication (v2 signing)

Every request except the public checkout and admin surfaces is signed with the
merchant's RSA private key. The canonical string and header format are specified in
`Docs/Api-v2-signing.md` and implemented in the SDKs (`Sdk/Node`, `Sdk/Php`, `Sdk/Python`).

Required headers:

- `X-CPay-Merchant-Number` — merchant account number
- `X-CPay-Signature-Version` — currently `v2`
- `X-CPay-Timestamp` — ISO-8601 instant
- `X-CPay-Nonce` — unique per-request nonce
- `X-CPay-Signature` — base64 RSA-SHA256 signature over the canonical string

## 4. Idempotency

Financial write endpoints (collect, payout, refund) accept an optional idempotency key:

```
X-CPay-Idempotency-Key: <opaque-client-supplied-key>
```

- Same key + same body → the original response is returned (no re-execution).
- Same key + different body → `400` with an idempotency-conflict error.

The legacy v1 money endpoints (`/api/doMobileMoneyPayIn`, `/api/doMobileMoneyPayOut`)
accept the same header (`Idempotency-Key` / `X-Idempotency-Key`) for backward-compatible
replay protection.

## 5. Collect

`POST /api/v2/native/payments/collect`

See `Docs/Api-v2-examples.md` for a full body. Flow summary:

1. Merchant submits the request with payer MSISDN, amount, currency, country, channel.
2. CPay validates, signs, routes to the provider adapter, and returns `202` with a
   transaction reference.
3. Final state arrives via the callback URL (section 7).

## 6. Payout

`POST /api/v2/native/payments/payout`

Payouts are risk-controlled. Depending on the merchant's configuration, a payout may:

- execute immediately (within configured limits), or
- be parked for maker-checker approval in the admin **Payout Approvals** queue.

Duplicate references are rejected safely; limits (per-transaction, daily, monthly,
beneficiary velocity) are enforced per control configuration.

## 7. Status and balances

- `GET /api/v2/payments/{reference}?merchantNumber=...` — transaction status
- `GET /api/v2/balances?merchantNumber=...` — available balances

## 8. Payment links and invoices

Payment links and invoices are signed merchant API calls that return tokenized customer
checkout routes.

| Action | Endpoint |
| --- | --- |
| Create a payment link | `POST /api/v2/payment-links` |
| Pay a payment link | `POST /api/v2/checkout/{token}/pay` |
| Create an invoice/request-to-pay | `POST /api/v2/invoices` |
| List invoices | `GET /api/v2/invoices?merchantNumber=...` |
| Send an invoice | `POST /api/v2/invoices/{reference}/send` |
| Cancel an invoice | `POST /api/v2/invoices/{reference}/cancel` |
| Pay an invoice | `POST /api/v2/invoices/pay/{token}` |

## 9. Callbacks / webhooks

Callbacks are delivered to the merchant's registered callback URL and signed. Event
types and the envelope are specified in `Docs/Webhook-events.md`.

Verification tooling:

- Register an endpoint via the merchant portal or `POST /api/v2/admin/webhooks/merchants/{merchantId}/test-callback`
  (admin) to queue a synthetic `TEST` event before go-live.
- Retry/backoff: first attempt immediately, exponential backoff, max 5 attempts, then parked.
- Replay: `POST /api/v2/merchant-self-service/webhooks/deliveries/{id}/replay` (merchant) or
  the admin replay endpoint.

## 10. Errors

All v2 errors use the `ApiErrorResponse` shape (`code`, `message`, `traceId`). Error
codes and recovery guidance are catalogued in `Docs/Error-catalog.md`. Never retry a
request that failed with a validation or idempotency-conflict error without changing payload.

## 11. Retry behaviour

| Case | Behaviour |
| --- | --- |
| Provider timeout/unavailable | Async retry; transaction stays `PENDING` |
| Callback delivery failure | Backoff (5 attempts), then parked for manual replay |
| Client network failure | Use idempotency key to retry safely |

## 12. Reconciliation basics

Provider statements are validated and imported through the admin Reconciliation
workbench; daily close is a maker-checker flow (`/api/v2/admin/recon-finance/close` →
`close/approve`). See `Docs/Runbooks/Reconciliation-finance-daily-close.md`.

## 13. Cross-border transfers

EAC corridors (UG→KE, UG→TZ, UG→RW) are supported: FX quote → transfer intent →
compliance + treasury reservation → delivery.

- `POST /api/v2/fx/quotes`
- `POST /api/v2/cross-border/transfers`
- Treasury positions: `GET /api/v2/admin/treasury/positions`

## 14. Go-live checklist

A merchant may not take production traffic until every item below is complete:

- [ ] RSA key pair generated; merchant public key registered
- [ ] Callback URL verified via the test-callback tool
- [ ] Sandbox collect, payout, status, and balance scenarios passed
- [ ] Provider channel certified (sandbox + statement evidence) or approved exception
- [ ] Hosted checkout, payment link, and invoice flows tested if enabled for the merchant
- [ ] Reconciliation tested: statement import, matching, daily-close dry run
- [ ] Production credentials configured; IP allow-list set
- [ ] Production transaction cap reviewed and set to the approved launch limit
- [ ] Business approval + production activation granted in the admin portal

## References

- Signing: `Docs/Api-v2-signing.md`
- Examples: `Docs/Api-v2-examples.md`
- OpenAPI: `Docs/Api/cpay-v2-openapi.yaml`
- Webhooks: `Docs/Webhook-events.md`
- Errors: `Docs/Error-catalog.md`
- Sandbox: `Docs/sandbox-guide.md`
- Migration (v1 users): `Docs/v1-to-v2-migration.md`
