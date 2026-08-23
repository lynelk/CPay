# CPay Developer Guide

This guide is the onboarding path for a third-party developer integrating with CPay's
v2 gateway: collections, payouts, hosted checkout, payment links, request-to-pay
invoices, status checks, balances, callbacks, statements, cross-border transfers,
and merchant self-service capabilities. Detailed references live in the linked
documents; this page is the 30-minute version.

> Documentation freshness: API documentation is now managed as code. Any API-affecting
> pull request is required by CI to update the OpenAPI/reference material in the same
> change. See `Docs/Api/AUTO_UPDATE_POLICY.md`.

## 1. Overview

CPay is a multi-tenant payments and channel-management gateway for MTN MoMo, Airtel
Money, Airtel OpenAPI, Safaricom M-Pesa, Yo! Payments, communications, vending, and
identity/validation integrations. Two payment API generations run side-by-side:

| Generation | Base path | Auth | Status |
| --- | --- | --- | --- |
| v1 (legacy) | `/api` / `/api/v1` | RSA signature in JSON body | Supported, stable, no new payment features |
| **v2 (recommended)** | `/api/v2` | RSA signature over canonical request data | New payment integrations go here |

Always build new payment integrations against v2 unless an endpoint is explicitly
available only on the legacy surface. CPay also contains merchant-session, public-token,
provider-callback, and internal/admin APIs; those surfaces use different security models
and must not be treated as interchangeable with signed merchant APIs.

## 2. Environments

| Environment | Base URL | Purpose |
| --- | --- | --- |
| Sandbox | deployment-provided sandbox URL | Testing with provider sandboxes or controlled CPay simulations |
| Production | deployment-provided production URL | Live traffic after channel certification and approval |

Do not hard-code example hostnames from documentation into production clients. Obtain the
current environment URLs from the merchant onboarding material or portal.

Sandbox behaviour is documented in `Docs/sandbox-guide.md`. The merchant portal also
exposes configured sandbox guidance, test data, idempotency expectations, and environment
selection where enabled.

Use the optional environment header where the merchant deployment supports both sandbox
and production configuration:

```http
X-CPay-Environment: SANDBOX
```

Production transaction controls, channel readiness, risk controls, and merchant feature
activation can restrict what a merchant may execute even when the endpoint exists in the
OpenAPI contract.

## 3. Authentication (v2 signing)

Signed v2 requests are verified using the merchant's RSA public key registered with CPay.
The client keeps the private key and uses it to sign the canonical request. The canonical
format is specified in `Docs/Api-v2-signing.md` and implemented in the SDK examples.

The current verification implementation reads these headers:

- `X-CPay-Signature-Version` - currently `v2`
- `X-CPay-Timestamp` - ISO-8601 instant
- `X-CPay-Nonce` - unique per merchant request
- `X-CPay-Signature` - base64 RSA-SHA256 signature over the canonical request

The merchant identity is supplied to request verification from the API request body or
query parameter as required by the operation. Do not rely on an additional merchant-number
header unless a specific endpoint contract explicitly defines one.

Canonical request input:

```text
METHOD
PATH
CANONICAL_QUERY
TIMESTAMP
NONCE
BODY_SHA256_HEX
```

For GET requests, include query parameters in canonical order and hash the empty body.
For POST requests, hash the exact raw request body sent on the wire. Reformatting JSON
after signing changes the body hash.

## 4. Idempotency

Financial write endpoints such as collect, payout, and refund should use an idempotency
key:

```http
X-CPay-Idempotency-Key: <opaque-client-supplied-key>
```

- Same key + same request body -> the original result is returned without re-execution.
- Same key + different request body -> treat the response as an idempotency conflict and
  do not automatically create a replacement transaction.
- Reuse the same merchant reference and idempotency key when retrying after a client-side
  network failure whose server outcome is unknown.

Legacy v1 money routes retain their backward-compatible replay-protection behavior where
enabled. New clients should use the v2 contract.

## 5. Collect

`POST /api/v2/native/payments/collect`

Flow summary:

1. Merchant submits payer MSISDN, amount, currency, country, channel, reference, and
   callback information.
2. CPay validates the merchant, request signature, nonce/timestamp, channel readiness,
   merchant controls, and provider route.
3. CPay returns an accepted/pending result for asynchronous processing.
4. Final state arrives through the configured callback/webhook; status polling is a
   recovery mechanism rather than the primary completion signal.

See `Docs/Api-v2-examples.md` for request-signing examples.

## 6. Payout

`POST /api/v2/native/payments/payout`

Payouts are risk-controlled. Depending on merchant configuration, a payout can execute
immediately or be parked for maker-checker approval. A response such as
`APPROVAL_PENDING` is non-terminal: retain the original reference/idempotency key and wait
for approval and subsequent execution. Do not create a replacement payout merely because
money has not moved yet.

Per-transaction, daily/monthly, beneficiary-velocity, balance/reservation, compliance, and
other configured controls may be evaluated before provider execution.

## 7. Status, balances, and statements

- `GET /api/v2/payments/{reference}?merchantNumber=...` - transaction status
- `GET /api/v2/balances?merchantNumber=...` - available balances
- `GET /api/v2/statements?...` - statement/export API where enabled

List/export endpoints must be integrated using their documented limit/cursor rules rather
than assuming an unbounded result set. Treat balances and statements as sensitive merchant
data.

## 8. Payment links and invoices

Payment links and invoices return tokenized customer-facing payment routes.

| Action | Endpoint |
| --- | --- |
| Create a payment link | `POST /api/v2/payment-links` |
| Pay a payment link | `POST /api/v2/checkout/{token}/pay` |
| Create an invoice/request-to-pay | `POST /api/v2/invoices` |
| List invoices | `GET /api/v2/invoices?merchantNumber=...` |
| Send an invoice | `POST /api/v2/invoices/{reference}/send` |
| Cancel an invoice | `POST /api/v2/invoices/{reference}/cancel` |
| Pay an invoice | `POST /api/v2/invoices/pay/{token}` |

Public token endpoints do not use merchant RSA signing. Treat payment/invoice tokens as
secrets: do not log or expose them unnecessarily.

## 9. Communications and validation capabilities

CPay now contains provider-neutral communications and identity/validation infrastructure.
The merchant contract is capability-oriented: merchants activate CPay capabilities such as
a communication channel or a validation check, while provider selection, credentials,
health routing, retries, and failover remain internal platform concerns.

Communication processing includes durable outbox delivery, provider health controls,
retry/failover handling, SMS compatibility, and WhatsApp provider support. Identification
and validation includes provider routing, versioned policy evaluation, tenant-scoped
verified-profile handling, metering, provider health, and validation webhook events.

Only use an endpoint for these capabilities when it is present in the current committed
OpenAPI contract and enabled for the merchant. Do not couple a client to an underlying
provider credential or provider-specific response shape unless CPay explicitly exposes it
as part of the public contract.

## 10. Callbacks / webhooks

Callbacks are delivered to registered merchant endpoints and must be verified before the
merchant changes business state or fulfills an order. Event types and envelope details are
specified in `Docs/Webhook-events.md`.

Integration rules:

- verify the documented signature before processing;
- enforce timestamp/replay rules where the callback contract includes them;
- make webhook consumption idempotent because duplicate deliveries are normal in reliable
  delivery systems;
- return a successful 2xx response only after the event has been durably accepted;
- use delivery logs/replay tooling instead of manually fabricating replacement events.

## 11. Errors

The preferred v2 baseline is the `ApiErrorResponse` shape containing stable `code`, a
safe `message`, and `traceId`. Some older/internal controllers still use simpler response
maps; the OpenAPI operation is authoritative for the surface being integrated.

Do not branch business logic on human-readable message text. Use stable error/status codes,
HTTP status, and documented `retryable` semantics where present. Error recovery guidance is
catalogued in `Docs/Error-catalog.md`.

## 12. Retry behaviour

| Case | Behaviour |
| --- | --- |
| Client network failure with unknown server outcome | Retry with the same idempotency key and exact request body |
| Provider timeout/unavailable | Follow returned transaction status and callback/status guidance; do not invent a second reference |
| Callback delivery failure | CPay delivery/replay handling applies; merchant receiver remains idempotent |
| Validation/input error | Correct the request; blind retry is not useful |
| Authentication/replay rejection | Rebuild timestamp/nonce/signature; never reuse a nonce |

## 13. Reconciliation basics

Provider statements are validated and imported through the reconciliation workbench.
Finance close and reconciliation approval are privileged maker-checker operations and are
not public merchant automation targets. See
`Docs/Runbooks/Reconciliation-finance-daily-close.md`.

## 14. Cross-border transfers

Where enabled, the flow is FX quote -> transfer intent -> compliance/treasury controls ->
delivery.

- `POST /api/v2/fx/quotes`
- `POST /api/v2/cross-border/transfers`
- internal treasury operations are intentionally separate from the merchant quickstart

A quote does not itself move money. Creating/authorizing a transfer is a high-impact action
and should require explicit user approval in AI/agent integrations.

## 15. AI and automated clients

Automated clients should read the OpenAPI operation schemas and honor CPay risk metadata.
Recommended policy:

| Risk class | Examples | Autonomous execution |
| --- | --- | --- |
| `read_only` | health, channels, status | allowed within authorization scope |
| `read_only_sensitive` | balances, statements | scoped access and audit trail |
| `quote_only` | FX quote | may be automated when requested |
| `communication_send` | billable SMS/WhatsApp/customer contact | require user/business authorization |
| `validation_check` | identity/KYC checks | require lawful purpose, consent/policy controls and scoped access |
| `money_movement` | payout, refund, cross-border transfer | explicit human approval before execution |
| `secret_management` | webhook/provider secret rotation | privileged human workflow only |
| `finance_close` / `admin_control` | reconciliation approval, close, repair | never general autonomous execution |

Never provide an AI agent with unrestricted production private keys or privileged admin
credentials. Give it the minimum credential scope needed for the intended workflow.

## 16. Go-live checklist

A merchant should not take production traffic until every applicable item below is
complete:

- [ ] RSA key pair generated; merchant public key registered; private key stored securely
- [ ] Deterministic signing fixture passes
- [ ] Callback URL verified and duplicate/replay handling tested
- [ ] Sandbox collect, payout, status, balance, and failure scenarios passed
- [ ] Provider/channel capability certified or explicitly approved
- [ ] Hosted checkout, payment links, invoices, communications, validation, vending, or
      cross-border flows tested if those capabilities are enabled
- [ ] Idempotency behavior tested under client timeout/retry conditions
- [ ] Reconciliation and statement handling tested where the integration settles money
- [ ] Production credentials configured; IP/network controls applied where required
- [ ] Production transaction limits and approval controls reviewed
- [ ] Monitoring covers provider failures, webhook backlog, reconciliation exceptions, and
      critical asynchronous queues
- [ ] Business/compliance approval and production activation completed

## 17. Documentation lifecycle

The repository automatically validates the OpenAPI contract and generates a browsable API
reference whenever API-affecting code or documentation changes. Pull requests that modify
API-facing controllers, DTOs, security, webhook, communication, identity, compliance,
cross-border, or vending code must include matching API documentation changes. CI fails
when that requirement is not met.

This removes the need for a separate manual request to refresh documentation after each
incremental application improvement. The developer making an intentional API change still
owns the semantics of that change; automation validates, detects drift, and rebuilds the
reference rather than guessing business behavior.

See `Docs/Api/AUTO_UPDATE_POLICY.md` for the release gates and implementation rules.

## References

- OpenAPI source of truth: `Docs/Api/cpay-v2-openapi.yaml`
- Documentation auto-update policy: `Docs/Api/AUTO_UPDATE_POLICY.md`
- Signing: `Docs/Api-v2-signing.md`
- Examples: `Docs/Api-v2-examples.md`
- Webhooks: `Docs/Webhook-events.md`
- Errors: `Docs/Error-catalog.md`
- Sandbox: `Docs/sandbox-guide.md`
- Migration for v1 users: `Docs/v1-to-v2-migration.md`
