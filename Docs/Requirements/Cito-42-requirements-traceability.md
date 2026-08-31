# Cito Universal Billing and Monetization: 42-Requirement Traceability Matrix

**Status:** FROZEN baseline  
**Baseline branch:** `main`  
**Baseline date:** 31 August 2026  
**Change policy:** requirement IDs and intent are immutable. Clarifications may be appended, but a requirement may not be silently renumbered, deleted, or weakened. New requirements receive new IDs above `CITO-BILL-042`.

## Evidence states

- **IMPLEMENTED**: executable code/persistence exists for the principal requirement.
- **VERIFIED**: focused automated tests prove the principal behaviour and negative paths.
- **PRODUCTION-PROVEN**: production telemetry/runbook evidence demonstrates the control under real operation.
- **PARTIAL**: a material implementation exists, but one or more required product/control surfaces remain incomplete.
- **MISSING**: no material implementation exists in the current baseline.

A requirement is considered release-complete only when every applicable evidence column is present. A table, migration, class, or endpoint by itself is not completion evidence.

## Frozen requirements

| ID | Requirement | Current state | Principal repository evidence | Remaining closure evidence |
|---|---|---|---|---|
| CITO-BILL-001 | Universal tenant, customer and billing-account hierarchy | PARTIAL | `V38__billing_tenancy_core.sql`; `V100__billing_baas_control_plane.sql`; `billing/tenancy`; BaaS customer/account APIs | Parent/child accounts, approved multi-tenant merchant hierarchy, lifecycle/export/offboarding tests |
| CITO-BILL-002 | Universal service catalog | PARTIAL | `V39__billing_service_catalog.sql`; `V87__cito_entitlements.sql` service catalog | Full catalog publication/versioning API, unit/dimension registry, delegated ownership and contract tests |
| CITO-BILL-003 | Canonical immutable usage events | VERIFIED foundation | `V40__billing_usage_events.sql`; `billing/usage`; outbox handlers and tests | Public BaaS ingestion/batch contract, quarantine/correction/replay administration and retention proof |
| CITO-BILL-004 | Transactional outbox and idempotent processing | VERIFIED foundation | `V41`; `billing/outbox`; relay/rollback/Testcontainers tests | Dead-letter/replay operating API, SLOs, backlog dashboards and scale evidence |
| CITO-BILL-005 | Payment event production | PARTIAL | payment usage outbox hook/handlers; payment-to-billing pipeline | 100% collect/payout/refund/reversal/timeout/settlement/legacy coverage proof in shadow production |
| CITO-BILL-006 | General meter aggregation | PARTIAL | `billing/metering`; meter schema and aggregation services | Durable windows, timezone/late-data rules, unique/delta/weighted/time-integrated acceptance tests |
| CITO-BILL-007 | Versioned price books | PARTIAL | `V43`; `PriceBookRepository`; `PriceBookAuthoringService`; admin API | Draft/review/approve/schedule/retire/rollback governance and maker-checker publication |
| CITO-BILL-008 | Flat, percentage, minimum/maximum, tiered and hybrid pricing | VERIFIED core / PARTIAL universal | `RatingEngine`; `TierCalculator`; pricing tests | Allowances, commitments, recurring/overage, promotions, cost-plus/revenue-share and invoice-level minima |
| CITO-BILL-009 | Effective-dated prices | IMPLEMENTED foundation | `billing_price_book_versions`; `PriceResolver` | Published-version immutability, overlap protection, scheduled activation and retroactive/rerating governance |
| CITO-BILL-010 | Provider cost separated from customer price | IMPLEMENTED | `PROVIDER_COST` price books; `ProviderCostOutboxHandler`; rated-charge records | Provider-contract import, estimated/actual cost reconciliation and margin-control acceptance tests |
| CITO-BILL-011 | Provider-specific adapters outside billing core | PARTIAL | gateway adapters; statement parsers; normalized billing cost handler | Billing adapter certification for communications, identity, fiscal, cloud/storage/AI and future services |
| CITO-BILL-012 | Deterministic rated-charge records | VERIFIED foundation | `V44`; `RatingEngine`; `RatedChargeRepository`; rating tests | Retain contract/tax/FX/entitlement/dimension snapshots and explicit rerating/reversal workflow |
| CITO-BILL-013 | Shadow comparison before cutover | VERIFIED service / PARTIAL product | `ChargeShadowComparisonService`; unit/Testcontainers tests | Secured simulation API, thresholds, reports and release-gate enforcement |
| CITO-BILL-014 | General online charging service | IMPLEMENTED foundation | `V100`; `BillingBaasChargingService`; authorize/commit/release/reverse API | Extend/partial-commit contract, latency/concurrency/load proof and operational alerts |
| CITO-BILL-015 | Prepaid billing balances and quotas | IMPLEMENTED foundation | charging accounts/reservations; quota policies in `V100`; concurrency-safe service logic | Quota bucket lifecycle, top-up/reconciliation API and overspend/load proofs |
| CITO-BILL-016 | SaaS subscriptions | IMPLEMENTED foundation | `billing_subscriptions`; BaaS create/activate/pause/cancel APIs | Trial/grace/proration/renewal/co-termination/add-ons and recurring billing acceptance |
| CITO-BILL-017 | Entitlements | IMPLEMENTED foundation | `V87`; `billing_entitlement_grants/usage`; subscription entitlement APIs | One policy-enforcement boundary across every Cito module plus negative cross-service tests |
| CITO-BILL-018 | Customer contracts and commitments | IMPLEMENTED foundation | `billing_contracts`; submit/approve/activate lifecycle | Minimum commitment/take-or-pay/reserved-capacity/revenue-share modelling and version governance |
| CITO-BILL-019 | Tax rule engine | PARTIAL | `V99` effective-dated tax rules/snapshots; `BillingTaxSnapshot`; `V101` | Complete resolver/calculation API, inclusive/exclusive/exemption/jurisdiction tests and fiscal adapter evidence |
| CITO-BILL-020 | Effective-dated FX for billing | PARTIAL | `V99` billing FX snapshots; existing platform FX controls | Billing FX resolver/source adapter, triangulation/markup policy and invoice reproducibility tests |
| CITO-BILL-021 | Immutable double-entry ledger reuse | VERIFIED foundation | platform ledger; `billing_ledger_links`; V103/V105 financial-integrity hardening | All billing correction types, period-close interaction and billing trial-balance release tests |
| CITO-BILL-022 | Financial period locks | PARTIAL | `V46__ledger_period_locks.sql` | Authenticated close/reopen workflow, late-event/rerating restrictions, UI and negative tests |
| CITO-BILL-023 | Periodic billing invoice aggregate | PARTIAL | `V47`; invoicing services; V99 invoice lifecycle fields | Automated billing runs, delivery/PDF/overdue/dispute/write-off/debit-note and multi-currency product surface |
| CITO-BILL-024 | Usage-completeness gate | IMPLEMENTED / VERIFIED foundation | `V49`; `V99` source watermarks/exceptions; `BillingCompletenessGateService` | SLO/dashboard, source registry and production watermark/waiver evidence |
| CITO-BILL-025 | Credit notes and payment allocations | PARTIAL | `V48`; V99 credit lifecycle fields | Complete APIs/workflows, debit notes, reallocations/refunds and immutable financial tests |
| CITO-BILL-026 | Invoice-to-event traceability | VERIFIED foundation | `BillingTraceChainService`; trace tests; ledger links | Tenant-secured external trace/export API and operator UI including tax/FX/cost/authorization evidence |
| CITO-BILL-027 | Invoice generation as a billable service | PARTIAL | periodic invoice domain and generic usage/rating pipeline | Emit and rate invoice/PDF/fiscalization/delivery/storage/reminder/reconciliation service usage |
| CITO-BILL-028 | Merchant BaaS tenant provisioning | IMPLEMENTED foundation | `billing_baas_tenant_profiles`; `BillingBaasAdminService`; legal/commercial/tax/funds-flow gates | Full self-service/sandbox promotion/offboarding/export and legal-operating evidence |
| CITO-BILL-029 | Merchant BaaS control-plane APIs | PARTIAL | `BillingBaasController`; customers/accounts/contracts/subscriptions/entitlements/charging/protected actions | Usage, invoices, credits/payments, catalog/pricing, reports, keys/webhooks and complete OpenAPI surface |
| CITO-BILL-030 | Merchant Billing Center UI | PARTIAL / evidence required | Cito portal modules and service-entitlement surfaces | Full Billing Overview, Customers, Catalog, Pricing, Usage, Subscriptions, Invoices, Credits, Reports, Developers |
| CITO-BILL-031 | Billing RBAC/ABAC and maker-checker | PARTIAL | Cito role templates/access grants; protected-action approvals; admin controls | Central billing authorization policy, step-up MFA matrix and protected-action coverage tests |
| CITO-BILL-032 | Tenant isolation throughout runtime | PARTIAL / release blocker | tenant IDs/context; BaaS context; tenant-scoped SQL; hardening tests | Adversarial API/job/export/cache/ledger tests across every Cito module, executed as mandatory CI gate |
| CITO-BILL-033 | BaaS API keys, scopes, limits and webhooks | PARTIAL | `BillingBaasApiKeyService`; quota policies; BaaS webhook subscriptions; existing webhook delivery | Public management APIs, schema catalog, rotation/revocation and OpenAPI/SDK contract tests |
| CITO-BILL-034 | Full reconciliation control chain | PARTIAL | usage/payment reconciliation; platform finance reconciliation; completeness controls | Event→meter→charge→ledger→invoice→payment→settlement and provider-cost→supplier controls in one certification view |
| CITO-BILL-035 | Revenue-leakage detection | PARTIAL | watermarks/exceptions; shadow deltas; reconciliation | Unified work queue for unrated/unbilled/duplicate/missing/negative-margin/tax/commission/allocation/settlement exceptions |
| CITO-BILL-036 | FOCUS-compatible import/export | MISSING | trace export package exists, but no FOCUS exporter | Implement versioned FOCUS-compatible cost/usage export, validation and API contract |
| CITO-BILL-037 | Customer usage, bill estimate, quota and explanation | PARTIAL data / product gap | usage, charges, entitlements, quota data exist | Tenant API/UI for current usage, estimate, remaining allowance, forecast and pricing explanation |
| CITO-BILL-038 | Non-payment Cito service coverage | PARTIAL | communications/vending/validation service domains and communication billing meters | End-to-end service adapters, meter/price templates and acceptance tests for each enabled product |
| CITO-BILL-039 | Advisory AI with protected-action controls | PARTIAL governance | protected-action service exists | Explicit AI governance/provenance schema and tests proving AI cannot execute protected financial actions |
| CITO-BILL-040 | Operational observability and SLOs | PARTIAL | metrics, readiness, scheduler locks, operational runbooks | Billing RED/SLO dashboards, rating lag, watermark lag, duplicate, charging latency, margin/leakage alerts |
| CITO-BILL-041 | High-volume scalability and resilience | PARTIAL | DB outbox, idempotency, retries, Testcontainers, production ops | Load/capacity tests, backpressure, partition/broker thresholds, replay/failure/DR drills and RPO/RTO evidence |
| CITO-BILL-042 | OpenAPI, SDK and developer documentation | PARTIAL | payment OpenAPI; developer control plane; BaaS controller | Publish complete Cito Billing/BaaS OpenAPI, examples, webhooks, sandbox guide, SDK-generation contract and deprecation policy |

## Release blockers

The following IDs are mandatory blockers for a Billing/BaaS production release until they reach **VERIFIED** and have explicit CI evidence:

`CITO-BILL-008`, `012`, `014`, `015`, `019`, `020`, `021`, `024`, `026`, `029`, `031`, `032`, `033`, `034`, `035`, `036`, `039`, `042`.

## Traceability rule

Every requirement PR must reference one or more IDs above. CI must fail when this matrix is absent, renumbered, contains duplicate IDs, or contains fewer/more than exactly 42 frozen baseline IDs. Functional closure is demonstrated through behaviour tests, not by changing a status label in this document.
