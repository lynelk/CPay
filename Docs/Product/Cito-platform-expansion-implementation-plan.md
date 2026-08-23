# Cito Platform Expansion Implementation Plan

## Delivery model

The work is delivered in numbered feature slices. Each slice should add the schema, backend domain/service/API, authorization hooks, tests, documentation, and any necessary portal surface. Money-moving features must preserve ledger/reconciliation and idempotency semantics.

## Phase 1: Feature tracks 1-10

### Track 1 - Cito entitlements

Deliver:
- `cito_organizations`
- `cito_service_catalog`
- `cito_service_entitlements`
- `cito_role_templates`
- `cito_access_grants`
- `cito_access_reviews`
- service/permission registry in Java
- tenant entitlement service
- admin APIs for service catalogue/entitlements
- session-facing entitlement summary endpoint
- entitlement checks designed for reuse by later tracks

Acceptance criteria:
- a tenant cannot use a gated feature without an ACTIVE entitlement
- production and sandbox entitlements can differ
- grants and revocations are auditable
- access review records do not silently alter access

### Track 2 - Intelligent routing

Deliver:
- routing policies and policy rules
- provider health/statistics tables
- payment route decisions
- scoring engine
- deterministic simulation endpoint
- execution routing hook for adapter-backed payments
- circuit-breaker/degraded state inputs

Acceptance criteria:
- eligible providers are filtered before scoring
- decisions are explainable
- failover never bypasses idempotency
- every route decision is auditable

### Track 3 - Marketplace/subaccounts/splits

Deliver:
- subaccounts
- split rules and recipients
- split execution snapshots
- settlement allocations
- validation service
- signed merchant APIs and admin operations APIs
- ledger/reconciliation mapping metadata

Acceptance criteria:
- allocations must exactly reconcile to the distributable amount
- immutable split execution snapshot is recorded per payment
- suspended recipients cannot receive new allocations

### Track 4 - Refund/reversal/dispute lifecycle

Deliver:
- refund requests
- refund attempts
- reversal records
- dispute cases/events
- approval threshold support
- financial lifecycle service
- APIs for create/status/approve/reject
- transaction timeline projection

Acceptance criteria:
- cumulative refunds cannot exceed refundable amount
- high-value refunds require maker-checker when configured
- final financial state is traceable to ledger/reconciliation references

### Track 5 - Subscriptions/recurring billing

Deliver:
- recurring plans
- subscriptions
- mandates
- scheduled charges
- dunning attempts
- lifecycle APIs
- due-charge scheduler contract
- provider capability guard

Acceptance criteria:
- no recurring execution without ACTIVE mandate/consent where required
- retries are idempotent per scheduled charge
- pause/cancel prevents future execution

### Track 6 - Merchant analytics

Deliver:
- daily/provider/channel aggregates
- latency and failure aggregates
- webhook/reconciliation metrics projection
- analytics query service
- merchant/admin analytics APIs
- recommendation records

Acceptance criteria:
- data is tenant scoped
- aggregates can be rebuilt from source-of-truth events
- PII is not required for standard analytics responses

### Track 7 - Developer control plane

Deliver:
- developer projects
- project environments
- service accounts
- credential metadata
- API request metadata log
- webhook test events
- developer readiness summary
- merchant portal APIs

Acceptance criteria:
- credentials are scoped to projects and entitlements
- secrets are one-time display or external-secret references
- production project activation requires production eligibility

### Track 8 - Virtual accounts

Deliver:
- virtual account products/providers
- virtual account records
- incoming transfer events
- reconciliation matching metadata
- sandbox issuance
- production issuance guard requiring certified connector configuration

Acceptance criteria:
- sandbox behavior is testable without real bank credentials
- production issuance cannot silently fall back to simulated accounts

### Track 9 - Embedded/white-label Cito

Deliver:
- partner profiles
- brand themes/domains metadata
- embedded onboarding sessions
- delegated merchant relationships
- delegated-service grants
- partner commission metadata

Acceptance criteria:
- delegated administration is constrained by Cito entitlements
- embedded session tokens expire and are single-purpose
- partner isolation is enforced server side

### Track 10 - Integrations marketplace

Deliver:
- connector catalogue
- connector versions
- installations
- mappings
- event subscriptions
- sync jobs/attempts
- health and retry APIs

Acceptance criteria:
- installation cannot exceed tenant entitlements
- credentials are stored by reference/encrypted mechanism, never logged
- jobs have idempotent retry semantics

## Phase 2: Strategic convergence pass

### A. Cito entitlements

Apply entitlement checks to routing policies, refunds, split payments, analytics and developer projects. Add feature discovery so the portal/developer APIs expose only eligible capabilities.

### B. Intelligent routing

Feed provider statistics from payment outcomes. Add merchant-configurable routing preference within entitlement boundaries. Feed routing decisions into analytics.

### C. Refunds/reversals

Integrate split-aware refunds, routing/provider reversal decisions, merchant analytics, developer webhooks, and transaction timeline.

### D. Subaccounts/split payments

Integrate entitlement-aware marketplace access, split-aware settlements/refunds, developer project access, and merchant analytics.

### E. Merchant analytics

Include routing, refund, split, developer API and provider-health metrics. Add actionable recommendation records rather than raw charts only.

### F. Developer control plane

Expose entitlement-aware feature catalogue, route simulation, refund/split APIs, analytics, virtual-account sandbox tooling, embedded onboarding resources and connector installation APIs.

## Migration/versioning

The expansion starts after Flyway V83. New migrations must remain additive and versioned sequentially. Existing payment and merchant tables are treated as source-of-truth unless explicitly migrated by a dedicated backfill.

## Production boundary

Implementation can create software controls, schemas, APIs, tests, sandbox simulators and evidence. It cannot produce:
- bank/provider certification
- regulator approval
- contractual acceptance
- penetration-test signoff
- production secrets
- finance acceptance
- DNS/TLS ownership approval

Those remain explicit launch gates.