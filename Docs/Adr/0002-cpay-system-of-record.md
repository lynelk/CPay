# ADR 0002: CPay as the System of Record for CitoConnect Features

Date: 2026-08-06
Status: Accepted

## Context

CitoConnect is an integration layer that brings merchant onboarding, identity
verification, KYC/KYB review, regulatory reporting, and an operations console to
the CPay platform. CPay already owns the authoritative transaction, merchant,
ledger, payout-control, and webhook state (money movement, maker-checker
approvals, reconciliation, finance close). If CitoConnect components import
their own copies of that state, the platform would have two sources of truth for
money movement and compliance events, which is unacceptable for an audited
payments platform.

## Decision

CPay is the system of record for every shared entity. All money paths stay in
CPay. When CitoConnect components are absorbed, they are registered in CPay as
bounded features:

- A feature is enabled per environment and per merchant via a single feature
  registry (the "@Owner" of the V35 baseline contract), never by ad-hoc code
  paths.
- CitoConnect components read CPay state through CPay repositories/services and
  write only through CPay-approved flows (the same outbox/journaling rules every
  other internal consumer follows).
- Every schema addition for a CitoConnect feature ships as a Flyway migration
  under `db/migration` (next version V36+), never as an inline DDL executed by
  application code.
- The v1 API surface remains ABI-stable. CitoConnect work is exposed on
  `/api/v2/**` unless a v1 gap is explicitly closed (e.g. v1 payout-control
  parity, PII masking of provider callback logs).

## Consequences

- A single audit trail and a single set of maker-checker controls cover both
  platform-native and CitoConnect-absorbed features.
- Feature-registry gates make rollouts reversible per merchant without code
  deploys.
- Component teams must keep state transitions inside CPay transactions,
  preventing drift between CitoConnect tables and CPay authoritative tables.
- Legacy routes can be retired gradually after merchants migrate, exactly as
  documented in ADR 0001.

## Follow-ups

- Land the feature registry (global + per-merchant) and the migration DDL as
  V36 (W0-W1 of the hardening roadmap).
- Land the outbox and idempotent-consumer pattern for any CitoConnect async
  flows so event delivery is exactly-once from CPay's perspective.
- Expand the OpenAPI/Postman contracts so CitoConnect's operations console
  consumes only documented endpoints (admin finance close, settlement close,
  payout approvals, webhook ops, treasury, regulator reporting).
- Keep the money-path rule enforced: no new money movement outside
  `Common`/`PaymentOrchestrationService` with a `skipRiskCheck` seam (v1 risk
  is already wired via `RiskDecisionRegistry`; v1 payout controls now ride
  `PayoutControlService` before reserve/execute).
