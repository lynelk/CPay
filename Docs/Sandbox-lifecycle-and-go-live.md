# CPay Sandbox Lifecycle and Go-Live

This document describes the customer-facing sandbox lifecycle implemented alongside the existing provider sandbox and certification controls.

## Safety boundary

Sandbox activity must not move real money. The platform keeps sandbox synthetic balances, snapshots, refund simulations, batch retry simulations, certification runs and test personas in `sandbox_*` tables. Production channel credentials remain separately provisioned through `merchant_channel_credentials` with a production credential environment.

A sandbox reset never deletes production transactions, ledger entries, production credentials, settlement accounts or live KYC evidence.

## Merchant workbench

The Merchant Portal contains **Sandbox & Go-Live** and exposes tenant-scoped operations under `/api/v2/portal/sandbox`:

| Capability | Endpoint |
|---|---|
| Summary/readiness | `GET /summary` and `GET /readiness` |
| Synthetic wallets | `GET /wallets`, `POST /wallets/top-up` |
| Reset | `POST /reset` |
| Test identities | `GET /personas` |
| Snapshots | `GET/POST /snapshots`, `POST /snapshots/{id}/restore` |
| Certification | `POST /certification/run`, `GET /certification/latest` |
| Environment comparison | `GET /environment-compare` |
| Request production access | `POST /production-access` |
| Go-live status | `GET /production-access/latest` |
| Rollout state | `GET /rollout` |

All merchant endpoints derive the merchant from the authenticated portal session rather than accepting a merchant id from the request.

## Synthetic money

Synthetic wallet balances are stored in `sandbox_wallet_balances`. Merchant users can top up test balances without a support ticket. Top-ups are bounded and cannot affect production balance or ledger tables.

## Deterministic KYC/KYB personas

The sandbox seeds identities for:

- successful KYC;
- identity not found;
- name mismatch;
- date-of-birth mismatch;
- watchlist screening hit;
- expired document;
- manual review;
- biometric mismatch;
- successful KYB;
- beneficial-owner review.

These are synthetic records and must never be interpreted as real identities.

## Refunds and batch payouts

`/api/v2/refunds` and `/api/v2/batch-payouts` historically reach legacy payout internals. They now resolve `X-CPay-Environment` explicitly:

- `SANDBOX` uses sandbox-only refund/batch simulation tables;
- `PRODUCTION` requires the merchant's corresponding production rollout capability before the legacy financial execution path can run.

This prevents a sandbox request from accidentally invoking a live payout.

## Automated certification

A certification run persists its result in `sandbox_certification_runs` and individual checks in `sandbox_certification_checks`.

Certification includes:

1. the merchant-specific readiness checklist;
2. every required provider certification scenario in `provider_certification_requirements`;
3. sandbox environment selection;
4. synthetic-money storage availability;
5. sandbox/production credential separation.

A merchant cannot request production access until the latest certification run passes.

## Go-live workflow

Production access follows these stages:

1. `TECHNICAL_REVIEW`
2. `COMPLIANCE_REVIEW`
3. `RISK_REVIEW`
4. `OPS_REVIEW`
5. `APPROVED`
6. `ACTIVATED`

Admin decisions derive the actor from authenticated Spring Security state. The same administrator cannot approve consecutive review stages.

After approval, activation is blocked until a configuration-promotion record exists for the same merchant and go-live request.

## Configuration promotion

Promotion is deliberately not a secret-copying operation. It validates that production channels have been separately provisioned and records a promotion manifest.

Shared tenant configuration may be reused, including user/role definitions, webhook definitions, reporting preferences, reconciliation configuration, notification templates and feature settings.

The following never promote from sandbox:

- API secrets and private keys;
- synthetic balances;
- sandbox transactions/provider runs;
- synthetic KYC/KYB identities;
- test settlement accounts.

## Progressive activation

Production starts conservatively and can progress through:

- `SANDBOX`
- `COLLECTIONS`
- `REFUNDS`
- `PAYOUTS_LOW_LIMIT`
- `FULL`

Each rollout stage has a per-merchant production daily limit. The v2 payment controllers enforce the stage capability and daily limit before money-moving execution. Existing platform-wide production limits remain an outer safety ceiling.

Payout and full rollout stages require a passing controlled production smoke test.

## Controlled live smoke test

CPay does not create an arbitrary real-money test transaction from the admin console. After an authorized low-value production transaction has been made, operations supplies its reference to the smoke-test verifier. The verifier checks:

- the merchant-owned transaction exists and reached a successful terminal state;
- production provider execution evidence exists within the recent window;
- callback delivery is not failed or parked.

The evidence is persisted in `sandbox_live_smoke_tests`.

## Environment comparison

The Merchant Portal shows sandbox vs production channel counts, webhook/callback configuration, synthetic wallets, production transaction count for the day, and the current production rollout state. This makes environment differences visible before activation.

## Isolation verification

Admins can run `POST /api/v2/admin/sandbox/verify-isolation`. It persists evidence that:

- synthetic balances have sandbox-only storage;
- snapshots have sandbox-only storage;
- provider executions are environment tagged;
- channel credentials carry an explicit credential environment;
- production capabilities are separately staged per merchant.

Infrastructure/runtime isolation must additionally be checked in the deployed hosting environment. Repository/schema verification is not a substitute for verifying deployment variables, service separation and provider endpoint configuration.

## Rollback

Operations can return a merchant to `SANDBOX` rollout, which disables production collection/refund/payout feature flags. Existing financial records remain immutable; rollback prevents new production money movement rather than rewriting history.
