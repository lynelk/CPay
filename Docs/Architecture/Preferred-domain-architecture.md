# CPay Preferred Domain Architecture

Status: **Normative target architecture**

This document defines the preferred product and code architecture for CPay. New features and refactors should align to these domains while legacy APIs remain compatibility surfaces during migration.

## Product domains

1. **Home** — dashboard, action centre, alerts, approvals, exceptions and cross-domain read models.
2. **Payments & Transactions** — transactions, collections, payouts, batches, refunds, payout controls and approvals, balances/statements, reconciliation, finance close and settlement close.
3. **KYC & Customer Mgt** — customer/merchant directory, onboarding, KYC/KYB, verification, compliance, limits, accounts, teams and go-live readiness.
4. **Billing** — service catalogue, plans, pricing, metering, rating, charges, invoices, credits, revenue and provider cost attribution. Billing posts accounting effects to the common ledger; it does not create a competing ledger.
5. **Communication** — SMS, WhatsApp, USSD, email/notifications, templates, campaigns, preferences/consent, delivery/retry and communication usage metering.
6. **Developers & Integrations** — payment channels, provider adapters, API credentials, webhooks, callbacks, sandbox/certification and integration health.
7. **Operations** — alerts, incidents, problems, jobs, callback queues, provider/service health, availability, capacity, readiness and service levels.
8. **Administration** — users, roles, permissions, audit, risk configuration, platform settings, policies and feature flags.
9. **Platform Core** — security, audit primitives, events/outbox, observability, configuration, scheduling and shared infrastructure.

## Dependency direction

```text
Portal/API -> Application services -> Domain -> Ports/repositories
                                      |
                                      +-> Platform Core
                                      +-> Integration ports
```

Controllers must not own business workflows, schedulers or direct money manipulation. Provider adapters must not own billing, authorization, reconciliation policy or merchant notification policy.

## Communication module

Communication is a first-class domain, not an SMS helper. The canonical direction is:

```text
Business domain event
      -> communication policy/template
      -> consent/preference evaluation
      -> channel router
      -> SMS | WhatsApp | USSD | Email adapter
      -> delivery status/retry/audit
      -> usage event -> Billing
```

The repository currently implements the initial SMS/email foundation, provider routing and retries. WhatsApp, USSD, templates/campaigns, preference/consent and full usage-to-billing surfaces remain incremental implementation work. Do not expose placeholder portal screens as completed capabilities.

## Money and ledger rules

- New monetary calculations use `MoneyAmount`, `BigDecimal`, or explicit minor units. No new floating-point money arithmetic.
- The double-entry ledger is the accounting source of truth.
- Billing, settlement and reconciliation must not create independent competing financial truths.
- Payment status transitions must be explicit, monotonic where applicable and auditable.
- V1 compatibility should converge on the same application services and controls used by v2 rather than maintaining a second business engine.

## Frontend alignment

Admin and merchant navigation must use the same product vocabulary as the backend target. Existing screens may remain in legacy folders while being incrementally migrated, but new feature ownership should follow:

```text
features/
  home/
  payments-transactions/
  kyc-customer/
  billing/
  communication/
  developers-integrations/
  operations/
  administration/
```

Do not add new top-level navigation concepts that duplicate these domains.

## Architecture guardrails

Preferred automated rules:

- controllers -> application services, not JDBC/provider implementations;
- payment domain -> integration ports, not provider implementation details;
- communication -> communication adapters, not payment tables;
- billing -> ledger posting port, not a second ledger;
- KYC/customer -> owned customer/compliance data, not payment internals;
- provider adapters -> provider translation/execution only;
- operations -> read/monitor/control interfaces with audited writes;
- security-sensitive and financial state changes -> audit evidence.

## ISO-aligned operating intent

This architecture supports, but does not by itself certify, the CPay management system against ISO/IEC 27001, ISO 9001 and ISO/IEC 20000-1. Each domain should have an owner, risks, objectives/KPIs, controlled changes, test/release evidence, incident/problem linkage and continual-improvement records. Organizational certification additionally requires management-system scope, policies, competence, supplier management, internal audits, management reviews and corrective-action evidence.

## Current alignment snapshot

Implemented foundations include signed/versioned APIs, payment adapters, ledger/risk/reconciliation controls, maker-checker workflows, audit/readiness capabilities, billing-engine foundations, `MoneyAmount`, and a Communication domain with SMS/email routing and retry infrastructure.

Known architectural debt remains, including legacy god-class decomposition, v1 control parity, remaining floating-point money paths, full route-based portal navigation, complete Billing portal surfaces, KYC/customer workflow surfaces, role-specific navigation, WhatsApp/USSD communication adapters and broader Communication templates/preferences/metering.

The existence of this target document must not be interpreted as evidence that those gaps are complete.
