# CPay Production Maturity Implementation Guide

## Purpose

This guide converts the CPay seriousness review into an executable engineering roadmap. It is intended for maintainers, developers, and AI coding agents working on `lynelk/CPay`.

The goal is to move CPay from a feature-rich gateway into a provable payment infrastructure platform with clear controls, settlement discipline, compliance workflows, and operational evidence.

## Scope

This guide covers five delivery tracks:

- P0 - Trust and safety controls
- P1 - Finance and operations maturity
- P2 - Compliance and KYB/KYC maturity
- P4 - Product polish and developer experience
- P3 - Regional expansion and cross-border payments

The requested order is intentional: P3 regional expansion should come after the trust, finance, compliance, and product foundations are stable. Cross-border payments multiply risk. They should not be layered on top of weak settlement, weak compliance, or unclear audit controls.

## Operating Principles

### 1. Financial correctness first

No feature should move money unless it has:

- an explicit lifecycle state model
- idempotency protection where duplicate execution is dangerous
- audit records for initiation, approval, execution, failure, and reversal
- reconciliation hooks
- settlement implications
- a documented error model

### 2. Evidence over claims

Readiness claims must be backed by evidence records. A provider is not production-ready because an adapter exists. A merchant is not production-ready because a signup succeeded. A settlement is not closed because a button was clicked.

### 3. Maker-checker for sensitive actions

Sensitive operations must require separation of duties. These include provider production enablement, merchant production activation, settlement approval, finance adjustments, high-value payout release, callback secret rotation, and compliance case decisions.

### 4. No silent state changes

Every privileged or financial state transition must record:

- actor
- previous state
- new state
- reason
- timestamp
- request/correlation ID where available
- evidence reference where applicable

### 5. APIs and portals must agree

Any workflow exposed in the portal must be backed by an API with the same authorization, validation, and audit behavior. UI hiding is not security.

## Phase Sequencing

### P0 - Trust and Safety Controls

P0 establishes the minimum safety posture required before broad usage.

Expected outputs:

- provider certification evidence workflow
- admin RBAC and maker-checker controls
- payout risk controls
- webhook retry/replay hardening
- money correctness and ledger guardrails
- production observability baselines

### P1 - Finance and Operations

P1 turns reconciliation and daily close into operational finance workflows.

Expected outputs:

- settlement batch lifecycle
- treasury position tracking
- reconciliation exception ownership
- finance daily-close enforcement
- reports and exports
- incident management

### P2 - Compliance

P2 formalizes KYB/KYC and compliance controls.

Expected outputs:

- KYB/KYC profile workflows
- beneficial ownership capture
- compliance case management
- transaction monitoring rules
- screening integration boundary
- regulatory evidence pack

### P4 - Product Polish

P4 improves the merchant, admin, finance, compliance, and developer experience.

Expected outputs:

- merchant onboarding workflow UI
- developer portal polish
- hosted checkout and invoice lifecycle improvements
- mobile/USSD/WhatsApp journey definitions
- operations dashboards

### P3 - Regional Expansion

P3 adds cross-border and regional payment capabilities after the operating core is stable.

Expected outputs:

- corridors
- beneficiaries
- FX quotes
- cross-border transfer lifecycle
- corridor settlement
- cross-border compliance and reporting

## Delivery Rules for AI Agents

When using this guide to implement changes:

1. Start each phase by reading the existing docs and code for that domain.
2. Preserve existing v1 and v2 API compatibility unless explicitly instructed otherwise.
3. Prefer additive migrations over destructive schema changes.
4. Add or update OpenAPI definitions for every public or admin API change.
5. Add tests for state transitions, authorization, idempotency, and failure paths.
6. Update readiness gates when a workflow becomes enforceable.
7. Do not mark an external dependency as complete unless evidence exists.
8. Do not invent provider URLs, credentials, signatures, or private partner contracts.
9. Treat sandbox and production behavior separately.
10. Commit each workstream separately with a clear message.

## Recommended Repository Artifacts

Each phase should update or add the following where applicable:

- backend entities/services/controllers
- database migrations
- React portal screens/hooks
- OpenAPI contract
- API examples
- runbooks
- readiness gates
- tests
- audit records
- reporting exports

## Implementation Definition of Done

A phase is not complete until:

- the code compiles locally or in CI
- tests cover success, failure, duplicate, authorization, and edge cases
- documentation is updated
- readiness gates reflect the change
- migrations are additive and named consistently
- sensitive operations are audited
- operational users can see and act on the workflow from the portal or admin API

## Current Limitation Note

This guide does not replace provider certification, legal review, regulator engagement, finance signoff, production monitoring setup, or external security review. Code can support evidence. It cannot manufacture approval.