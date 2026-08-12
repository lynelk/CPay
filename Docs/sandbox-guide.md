# CPay Sandbox Guide

The sandbox lets merchants and integrators exercise the v2 gateway without moving real
money. It routes to provider sandboxes or controlled CPay simulations, records run
evidence for provider certification, and gives novice developers copyable credentials,
test numbers, and retry/idempotency rules before they touch production.

## 1. Base URL

All sandbox endpoints are relative to the configured sandbox base URL:

```
https://sandbox.cpay.example/api/v2
```

The displayed value comes from the `developer_sandbox_base_url` setting. Production examples use
`developer_production_base_url`. Both are settings-table values managed through the admin settings
surfaces rather than environment variables.

Credentials, merchant numbers, and signing keys are provisioned per merchant. Production must never
be used for testing (`Docs/developer-guide.md` section 2).

## 2. Environment selection

Merchant calls can explicitly select the target environment:

```text
X-CPay-Environment: SANDBOX
X-CPay-Environment: PRODUCTION
```

If omitted, CPay resolves the active merchant/user environment from
`merchant_environment_preferences`. The merchant portal exposes the current environment and the
developer sandbox guide through `/api/v2/portal/session` and `/api/v2/portal/dashboard/summary`.

Production mode is intentionally constrained while merchants graduate from sandbox:

| Setting | Default | Meaning |
|---|---|---|
| `production_transaction_limit_enabled` | `true` | Enforces the daily cap on production-environment calls. |
| `production_transaction_limit_count` | `10` | Default daily production transaction count per merchant. |

An administrator can raise or disable this cap in the settings portal after channel evidence and
business approval are complete.

## 3. Test credentials

- Merchant account numbers use the merchant's sandbox account (format identical to
  production: numeric account number).
- RSA signing uses the sandbox key pair; the merchant public key must be registered for
  the sandbox environment.
- Set `X-CPay-Idempotency-Key` on money requests to make retries safe.
- Use the first-party signing helpers in `Sdk/` or generate clients from
  `Docs/Api/cpay-v2-openapi.yaml` through `Sdk/codegen/README.md`.

## 4. Test MSISDNs

Use provider-appropriate EAC test numbers. Conventional sandbox MSISDNs:

| Provider | Test MSISDN |
| --- | --- |
| MTN MoMo | `256771000001` |
| Airtel Money | `256751000001` |
| Safaricom M-Pesa | `254700000001` |
| Yo! Payments | `256700000001` |

Confirm exact test numbers with the provider sandbox documentation; these are the CPay
example set.

## 5. Available scenarios

The sandbox supports scenario scripts that are recorded as evidence:

- Collect accepted
- Payout accepted
- Status check returns expected state
- Provider callback received and mapped
- Duplicate merchant reference handled safely
- Invalid account rejected
- Insufficient funds mapped
- Provider timeout handled
- Provider unavailable handled

Admin-run evidence endpoints (see `Docs/Api/cpay-v2-openapi.yaml`):

- `POST /api/v2/admin/provider-sandbox/run?channel=...` — record + run a sandbox scenario
- `POST /api/v2/admin/statements/check?provider=...` — validate a provider statement file
- `GET /api/v2/admin/provider-certification/summary` — evidence coverage

## 6. Success scenario (collect)

`POST /api/v2/native/payments/collect` with a payer test MSISDN and a positive amount
returns `202` with a transaction reference. The callback arrives at the registered
callback URL with the canonical event envelope (`Docs/Webhook-events.md`).

## 7. Failure scenario (invalid account)

Submit a collect/payout with an invalid account such as an empty or malformed MSISDN.
Expect a mapped rejection (see `Docs/Error-catalog.md`) — no money movement, no pending
state.

## 8. Pending scenario

Use a provider sandbox test value that leaves the transaction in `PENDING`: the status
check (`GET /api/v2/payments/{reference}?merchantNumber=...`) returns `PENDING`, and the
provider callback resolves the final state.

## 9. Timeout scenario

Point the request at a sandbox endpoint configured to time out, or use the provider
sandbox's delay simulation. The gateway marks the transaction `PENDING`, schedules an
async retry, and records the timeout in the provider endpoint run log. The merchant can
retry safely using the same idempotency key.

## 10. Payment links and invoices

Sandbox also covers hosted customer-payment flows:

| Flow | Endpoint |
|---|---|
| Create payment link | `POST /api/v2/payment-links` |
| Pay a payment link | `POST /api/v2/checkout/{token}/pay` |
| Create invoice/request-to-pay | `POST /api/v2/invoices` |
| View invoice payment token | `GET /api/v2/invoices/pay/{token}` |
| Pay invoice | `POST /api/v2/invoices/pay/{token}` |

Creation routes are signed v2 merchant API calls. Public pay routes are token-based hosted checkout
routes and should only be exercised with sandbox channels until the merchant is approved.

## 11. Provider statement validation

- `POST /api/v2/admin/statements/check?provider=MTN` — validates a statement file
  (supported format, required columns, references, duplicates, amount/currency validity).
- Import the statement in the Reconciliation workbench, then review auto-match results.

## 12. What sandbox evidence is used for

Sandbox run ids, statement validation run ids, and callback evidence are retained in
`provider_certification_*` records. Per `Docs/Runbooks/Provider-sandbox-and-statement-validation.md`,
a channel is not enabled for live traffic until that evidence is approved (or an approved
exception exists).
