# Testing Strategy

The highest-risk behavior in CPay is money movement. Test coverage should grow around the payment
state machine, ledger invariants, provider adapters, hosted checkout, communication delivery, vending
callbacks, and callback lifecycle.

## Current Baseline

Run:

```powershell
mvn test
npm run build
```

The backend test suite includes controller, security, SQL safety, settings, signing, and merchant signup coverage.

## Required Next Layers

| Layer | Target |
|---|---|
| Integration DB tests | Testcontainers MySQL for payin, payout, refund, statement, and reconciliation flows. |
| Provider simulators | WireMock fixtures for MTN, Airtel, Airtel OpenAPI, Safaricom, Yo! Payments, SMS, email, and vending connectors. |
| End-to-end path | One golden path from payment link/invoice/pay-in to provider callback to statement/reconciliation evidence. |
| Frontend module tests | React Testing Library coverage as the large modules are converted to smaller components. |
| Load baseline | k6 or Gatling scenarios for collection, payout, hosted checkout, callback, and dashboard reads. |

## Money-Movement Invariants

- Idempotent retries must not double-disburse.
- Concurrent payout reservations must serialize by merchant/currency so only one UGX 80,000 reservation can consume a UGX 100,000 available balance; the Docker-tagged MySQL Testcontainers ledger test proves this opt-in path.
- Provider callbacks must be deduplicated.
- Ledger debits and credits must balance per account and currency.
- Callback retry failures must park work visibly.
- Reconciliation corrections must leave an audit trail.
- Payment-link and invoice checkout attempts must be idempotent and traceable to the originating merchant request.
- Sandbox/production environment selection must never mix credentials or bypass the production transaction cap.
- Communication and vending side effects must not block authoritative payment state transitions.
