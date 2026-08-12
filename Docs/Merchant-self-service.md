# Merchant Self-Service Onboarding and Channel Setup

This document explains the merchant self-service features in CPay.

## Purpose

Merchants can begin onboarding without an administrator creating the account manually. After registration, a merchant can log in and configure supported payment channels from the merchant portal.

## Merchant signup

Public signup is available through the merchant portal at:

```text
/signup
```

The signup form captures:

- business name
- short name
- primary contact name
- email address
- phone number
- password

After registration, the merchant receives an account number and the account is created in a pending approval state. Production activity should remain blocked until business approval is completed.

Merchant signup is protected by database-backed rate limiting to reduce abuse and repeated automated registration attempts.

## Merchant payment channel setup

Logged-in merchants can open:

```text
Merchant Dashboard -> Payment Channels
```

The page lists supported channels such as:

- MTN MoMo
- Airtel Money
- Airtel OpenAPI
- Safaricom M-Pesa
- Yo! Payments

For each channel, the merchant can:

1. enter setup values for the provider channel
2. enter sandbox endpoint URLs for collect and payout flows
3. save the channel setup
4. run a sandbox readiness check
5. submit the channel for approval

Saved values are stored in the backend and returned to the user only as masked values.

## Sandbox and production mode

The merchant portal exposes a developer sandbox guide and the merchant/user's active environment.
Merchants should start with `SANDBOX` credentials and switch to `PRODUCTION` only after channel
setup, provider evidence, callback verification, and business approval are complete.

API callers may also send:

```text
X-CPay-Environment: SANDBOX
X-CPay-Environment: PRODUCTION
```

Production is capped by default at 10 transactions per day through the
`production_transaction_limit_count` setting while `production_transaction_limit_enabled=true`.
Administrators can raise or disable this cap from the admin settings flow as a merchant graduates
from sandbox.

## Payment links and invoices

Merchants can create customer-facing payment links and invoice/request-to-pay objects through signed
v2 API calls:

```text
POST /api/v2/payment-links
POST /api/v2/invoices
GET  /api/v2/invoices?merchantNumber=...
POST /api/v2/invoices/{reference}/send
POST /api/v2/invoices/{reference}/cancel
```

Customers pay through tokenized hosted checkout routes:

```text
POST /api/v2/checkout/{token}/pay
GET  /api/v2/invoices/pay/{token}
POST /api/v2/invoices/pay/{token}
```

The merchant-created routes require v2 signing; the public hosted routes rely on the unguessable
payment token and route back through the same payment orchestration controls.

## Required channel setup values

Every channel must include:

- `collectUrl`
- `payoutUrl`
- channel-specific setup values

Optional request header fields are also available where a sandbox endpoint requires them:

- `authHeaderName`
- `authHeaderValue`

## Merchant webhook manager

Logged-in merchants can open:

```text
Merchant Dashboard -> Webhooks
```

The page has two panels:

1. **Endpoints** — list registered webhook endpoints, register or update an endpoint for a catalog event type (`payment.pending`, `payment.completed`, `payout.failed`, etc.), and rotate an endpoint's signing secret. Secrets are shown exactly once with a "copy it now" notice after registration or rotation.
2. **Deliveries** — see recent delivery attempts (status, attempt count, last HTTP status), expand a delivery for per-attempt detail (reference, response summary, next attempt), and replay a failed or delivered delivery.

All routes are merchant-session-scoped, so one merchant can never read, rotate, or replay another merchant's webhook data. The same routes are also covered by the portal session-authorization filter, which returns the standard 107 envelope when no portal session is present.

## Backend endpoints

```text
POST /api/v2/merchant-self-service/signup
GET  /api/v2/merchant-self-service/channels
POST /api/v2/merchant-self-service/channels/save
POST /api/v2/merchant-self-service/channels/test
POST /api/v2/merchant-self-service/channels/submit

POST /api/v2/payment-links
POST /api/v2/invoices
GET  /api/v2/invoices?merchantNumber=...
POST /api/v2/invoices/{reference}/send
POST /api/v2/invoices/{reference}/cancel

GET  /api/v2/merchant-self-service/webhooks
POST /api/v2/merchant-self-service/webhooks
POST /api/v2/merchant-self-service/webhooks/{endpointId}/rotate-secret
GET  /api/v2/merchant-self-service/webhooks/deliveries
POST /api/v2/merchant-self-service/webhooks/deliveries/{deliveryId}/replay
```

## Gateway integration

Native v2 payments check whether the merchant has configured and tested the selected channel before allowing collect or payout execution through that channel.

When a native v2 request is processed, the gateway loads the merchant channel setup values and passes them into adapter execution metadata. Adapter-backed channels can then call the configured endpoint URLs through `ProviderEndpointClient`.

In production mode, missing endpoint URLs are rejected and the gateway loads active `PRODUCTION` merchant channel credentials. In sandbox mode, the channel can still be used for controlled setup and certification preparation with sandbox channel setup.

## Security and control measures

The implementation adds the following controls:

- merchant-owned channel records
- encrypted storage for channel setup values
- masked display values
- merchant session ownership checks
- audit records for channel actions
- sandbox readiness status
- approval status before production enablement
- explicit environment selection
- configurable production transaction cap
- signup rate limiting
- trusted-origin API access controls

## Remaining manual checks

The software flow is implemented, but production activation still requires real-world checks:

- provider sandbox validation
- provider approval
- merchant callback verification
- finance signoff where applicable
- production monitoring setup
- regulator and compliance signoff where required
