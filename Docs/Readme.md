# CPay Documentation Index

Use this index as the front door for CPay documentation. The root `Readme.md` explains the product
and local setup at a high level; this folder contains the architecture, API, operations, security,
merchant, and provider-specific detail.

## Start Here

| Document | Use it for |
|---|---|
| `Architecture/Overview.md` | System context, package map, main flows, ERD, and operational contracts. |
| `site/index.md` | Lightweight developer-docs landing page for merchant integrators. |
| `Api/cpay-v2-openapi.yaml` | Machine-readable v2 API contract. |
| `Api-v2-signing.md` | Required signing headers and canonical request format. |
| `Api-v2-examples.md` | Copyable collect, payout, maker-checker, webhook, and admin examples. |
| `sandbox-guide.md` | Sandbox credentials, test scenarios, environment switching, and production-limit behavior. |
| `Merchant-self-service.md` | Merchant signup, channel setup, webhooks, batch payouts, payment links, and invoices. |

## Current Product Areas

| Area | Main docs |
|---|---|
| Payment channels | `Gateway-adapter-guide.md`, `Payment-channel-schema.md`, `Provider-integration-roadmap.md` |
| Yo! Payments | `Gateway-adapter-guide.md`, `Provider-integration-roadmap.md`, `Runbooks/Provider-certification-checklist.md` |
| Payment links and hosted checkout | `Merchant-facing-features-roadmap.md`, `Api-v2-examples.md`, OpenAPI spec |
| Invoices/request-to-pay | `Merchant-facing-features-roadmap.md`, `Webhook-events.md`, OpenAPI spec |
| Merchant sandbox | `sandbox-guide.md`, `developer-guide.md`, `Developer-experience.md` |
| Webhooks | `Webhook-events.md`, `Runbooks/Callback-security-and-requeue.md` |
| Reconciliation and settlement | `Process-flow-controls.md`, `Runbooks/Reconciliation-finance-daily-close.md` |
| Ledger and billing | `Money-ledger-and-orchestration-roadmap.md`, `Adr/0003-billing-tenant-model.md`, `Adr/0004-billing-ledger-integration.md`, `Adr/0005-billing-outbox-design.md` |
| Compliance and KYB | `Compliance-risk-controls.md`, `Security-authentication-roadmap.md` |
| Provider certification | `Runbooks/Provider-certification-checklist.md`, `Runbooks/Provider-sandbox-and-statement-validation.md` |
| Treasury and balance monitoring | `Architecture/Overview.md`, `Production-code-controls.md` |
| Communication delivery | `Production-code-controls.md`, `Data-retention.md`, `Observability.md` |
| Vending/ChargeNow | `Vending-platform.md`, `ChargeNow-OEM-sandbox-setup.md` |

## Operations And Release Readiness

| Document | Use it for |
|---|---|
| `Production-code-controls.md` | What production-supporting code controls exist today. |
| `Readiness/Market-readiness-gates.md` | Launch-readiness checklist and manual signoff gates. |
| `Reliability-scale-runbook.md` | HA, graceful shutdown, backups, and scaling expectations. |
| `Data-retention.md` | Retention classes and cleanup/archival behavior. |
| `Observability.md` | Logging, metrics, alerting, tracing, and operations-dashboard expectations. |
| `Runbooks/Production-incident-response.md` | Incident severity and response workflow. |
| `Runbooks/Operations-alerts.md` | Alert-specific triage steps. |
| `Runbooks/Security-and-access-control.md` | Admin, merchant, callback, and access-value controls. |
| `Runbooks/Backup-restore-dr.md` | Backup cadence, restore drill, and RPO/RTO expectations. |

## Architecture Decisions

Architecture decisions live under `Adr/`:

- `0001-gateway-adapter-boundary.md`
- `0002-cpay-system-of-record.md`
- `0003-billing-tenant-model.md`
- `0004-billing-ledger-integration.md`
- `0005-billing-outbox-design.md`
- `0006-webhook-catalog-schema-per-type.md`

Add a new ADR when a change affects money movement, callback semantics, ledger/accounting behavior,
provider boundaries, retention, security posture, or externally visible API contracts.

## Maintenance Rules

- Update this index when adding a major document, route family, module, or operational workflow.
- Update `Api/cpay-v2-openapi.yaml` and `Api-v2-examples.md` when merchant-facing v2 routes change.
- Update `sandbox-guide.md` when test credentials, environment switching, or production caps change.
- Update `Production-code-controls.md` and `Readiness/Market-readiness-gates.md` when a readiness
  recommendation moves from roadmap to working code.
- Regenerate schema snapshots under `Schema/snapshots/` from a freshly migrated database before
  tagging a release.
