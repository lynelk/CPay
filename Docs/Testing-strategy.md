# Testing Strategy

The highest-risk behavior in CPay is money movement. Test coverage should grow around the payment state machine, ledger invariants, provider adapters, and callback lifecycle.

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
| Provider simulators | WireMock fixtures for MTN, Airtel, Airtel OpenAPI, Safaricom, and SMS providers. |
| End-to-end path | One golden path from payin to provider callback to statement/reconciliation evidence. |
| Frontend module tests | React Testing Library coverage as the large modules are converted to smaller components. |
| Load baseline | k6 or Gatling scenarios for collection, payout, callback, and dashboard reads. |

## Money-Movement Invariants

- Idempotent retries must not double-disburse.
- Provider callbacks must be deduplicated.
- Ledger debits and credits must balance per account and currency.
- Callback retry failures must park work visibly.
- Reconciliation corrections must leave an audit trail.
