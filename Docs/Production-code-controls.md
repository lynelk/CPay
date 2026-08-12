# Production Code Controls

This document records the code controls added to support production operation, scaling, and regulated readiness.

## Scope

These controls improve the software posture of CPay. They do not replace legal review, provider certification, finance signoff, external security review, or formal production approval.

## Implemented controls

| Area | Code support |
|---|---|
| Provider connection | Adapter-backed channels can call configured provider endpoint URLs through `ProviderEndpointClient`. |
| Merchant channel setup | Channel setup now requires endpoint URLs and channel-specific setup values before readiness can be marked. |
| Sandbox and production mode | Merchant/user environment selection is explicit; production calls are capped by configurable settings during graduation from sandbox. |
| Native v2 processing | Native v2 requests load merchant channel setup values into adapter metadata. |
| Production credential isolation | `CUSTOM_GATEWAYSTATE=PRODUCTION` loads active `PRODUCTION` merchant channel credentials rather than sandbox credentials. |
| Payment links and hosted checkout | Signed v2 payment-link and invoice creation feed tokenized customer payment routes through the orchestration path. |
| Callback scaling | Callback delivery uses `callback_task_claims` so workers claim tasks before processing. |
| Signup protection | Merchant signup uses database-backed request-window tracking through `api_rate_limits`. |
| Origin control | API routes use configured trusted origins. |
| Browser request protection | Browser routes use CSRF tokens from `/auth/csrf`; legacy/API route groups are exempted specifically where integration compatibility requires it. |
| Session resilience | Admin and merchant portal sessions are stored through Spring Session JDBC. |
| Operating oversight | Admin users can review open operating-control counts through `/api/v2/admin/operating-controls/summary`. |
| Webhook self-service | Merchants manage webhook endpoints, signing secrets, and delivery replay from `Merchant Dashboard -> Webhooks`, scoped to their own `merchant_id` and gated by the portal session-authorization filter. |
| Compliance and KYB | Admin users can review compliance summaries, cases, profiles, beneficial owners, and KYC/KYB documents. |
| Provider certification | Admin users can review and approve provider sandbox/callback/statement evidence before live enablement. |
| Treasury and balance monitoring | Admin users can review treasury positions, normalized channel balances, and balance-monitoring summaries. |
| Communication operations | SMS/email delivery, routing, credentials, provider policies, templates, preferences, campaigns, delivery logs, and billing usage metering are first-class modules. |
| Vending operations | Merchant-hosted vending locations, devices, rentals, and ChargeNow/OEM connector setup are managed through dedicated services and portal modules. |

## Provider endpoint execution

Provider adapters for MTN, Airtel, Airtel OpenAPI, Safaricom, and Yo! Payments now call the shared
provider execution path. The endpoint client/execution layer:

- reads endpoint URLs from merchant channel setup
- applies connection and read timeouts
- sends a structured JSON request
- records the HTTP status in the gateway response
- rejects missing endpoint URLs in production mode
- selects production merchant channel credentials when the gateway runs in production mode
- verifies signed Yo! Payments provider responses before trusting them

Real provider sandbox and production certification are still required before live traffic.

## Merchant channel setup

Each merchant channel setup must include:

- `collectUrl`
- `payoutUrl`
- channel-specific setup values
- optional request header name and value where a sandbox requires one

Stored setup values are encrypted server-side and returned to the portal only as masked values.

## Sandbox and production limits

`merchant_environment_preferences` stores the active environment per merchant/user/channel. Merchant
API callers can also use `X-CPay-Environment` to select `SANDBOX` or `PRODUCTION` for a request.

Production usage is capped by settings while a merchant is graduating from sandbox:

| Setting | Default |
|---|---|
| `production_transaction_limit_enabled` | `true` |
| `production_transaction_limit_count` | `10` |

The cap can be raised or disabled by administrators without redeploying.

## Admin operation surfaces

The current admin portal includes operational modules for dashboarding, merchants, transactions,
settlements, audit trail, settings, compliance, KYB review, provider certification, treasury and
balance monitoring, communication routing/security/delivery, vending, finance close, settlement
close, payout approvals, and webhook operations. New admin modules should use typed API hooks,
loading/error/empty states, and page-level refresh instead of full application reloads.

## Callback processing at scale

Callback workers now claim due callback tasks before delivery. The claim flow reduces duplicate processing when more than one application worker is running.

## Signup protection

Merchant signup is protected by database-backed request counting. The current signup limit is five attempts per minute per client source.

## Operating-control visibility

The admin operating-control summary endpoint reports open high, medium, low, and total operating-control events.

## Production note

The codebase now supports stronger production controls, but final launch still requires provider, security, compliance, finance, and regulator signoff. Code can support evidence. It cannot issue approvals.
