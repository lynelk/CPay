# Cito Platform Capability Matrix

## Product hierarchy

| Layer | Product purpose |
|---|---|
| Cito Gateway | Identity, organizations, entitlements, service discovery, developer access, embedded access, integrations |
| CPay | Payments, routing, collections, payouts, refunds, splits, subscriptions, virtual accounts, settlement, reconciliation |
| Identity & Validation | KYC/KYB and configurable identity checks |
| Communications | SMS/email routing, templates, consent, campaigns, delivery logs and billing metering |
| Vending | Merchant-hosted vending/device/rental operations |
| Billing | Usage, rating, invoicing and commercial metering across Cito services |

## Expansion feature matrix

| # | Feature | Primary owner | Tenant scoped | Money movement | Human approval possible | Sandbox support | Software status |
|---|---|---|---|---|---|---|---|
| 1 | Service catalogue & entitlements | Cito | Yes | No | Yes | Yes | Implemented |
| 2 | Intelligent routing | CPay | Yes | Yes | Policy approval | Yes | Implemented & converged |
| 3 | Subaccounts & splits | CPay | Yes | Yes | Yes | Yes | Implemented & converged |
| 4 | Refunds/reversals/disputes | CPay | Yes | Yes | Yes | Yes | Implemented & converged |
| 5 | Subscriptions/mandates | CPay | Yes | Yes | Yes | Yes | Implemented & entitlement-gated |
| 6 | Merchant intelligence | CPay/Cito | Yes | No | No | Yes | Implemented & converged |
| 7 | Developer control plane | Cito | Yes | Indirect | Production activation | Yes | Implemented & scope-aware |
| 8 | Virtual accounts | CPay | Yes | Yes | Provider activation | Yes | Implemented; production provider required |
| 9 | Embedded/white-label | Cito | Yes | Indirect | Partner approval | Yes | Implemented & entitlement-gated |
| 10 | Integrations marketplace | Cito | Yes | Indirect | Connector approval | Yes | Implemented & entitlement-gated |

## Current software convergence

The expansion is implemented as one Cito platform rather than ten disconnected endpoint families:

- `CitoFeatureAccessService` and `CitoMerchantFeatureAuthorizationFilter` provide common environment-aware merchant entitlement enforcement.
- Automatic native payment routing requires the `INTELLIGENT_ROUTING` entitlement for the payment's resolved environment. Explicit channel selection remains available under the existing CPay payment entitlement/privilege model.
- Routing policies and decision records are environment-specific and feed provider-performance analytics.
- Marketplace split preview is side-effect free. A preview never creates a financial execution.
- Split-enabled collections persist a deterministic `platform_feature_events` outbox intent before the provider call. Successful payments activate split capture; terminal failures cancel it; pending/ambiguous outcomes remain recoverable until CPay has a final transaction status.
- Split executions are immutable financial snapshots and partial refunds proportionally reverse their original subaccount allocations through `marketplace_split_refund_allocations`.
- Refunds, split-refund allocations and disputes feed the financial timeline without re-reading today's split configuration.
- Recurring charges execute through the same native payment path and independently re-check the `RECURRING_PAYMENTS` entitlement in the mandate/payment environment before money movement.
- Merchant analytics aggregates payments, routing, refunds, splits and recurring activity. `CitoPlatformOverviewService` adds cross-feature health for all ten expansion areas.
- Developer service-account scopes are mapped to owning Cito products. Production project activation is refused when active project scopes require products that are not production-entitled.
- Virtual-account issuance is environment-entitled. Sandbox accounts are internal test objects; production issuance refuses to fabricate bank details and requires an active certified provider configuration.
- Embedded Cito can delegate only services that the partner merchant itself is entitled to in the delegated environment.
- Integration installation requires both `INTEGRATIONS_MARKETPLACE` and the connector's own required service entitlement.
- The merchant portal exposes the ten areas through one `Cito Services` workspace with sandbox/production switching, service status, health metrics and common operational actions.

## Required cross-feature relationships

- Entitlements gate all premium or privileged feature use.
- Routing decisions are recorded for analytics and refund/reversal explainability.
- Split executions are immutable snapshots and are referenced by refunds and settlements.
- Refunds reference original transactions and, where applicable, split allocation state.
- Analytics reads routing, payment, refund, split, webhook, settlement and reconciliation outcomes.
- Developer projects expose only entitled APIs and environment capabilities.
- Virtual-account issuance is exposed through developer projects only when the tenant and environment are entitled.
- Embedded partners can grant only the subset of services delegated to them.
- Marketplace connector installation requires an active connector entitlement and tenant-authorized scopes.

## Status vocabulary

Use shared status language where practical:

- lifecycle: `REQUESTED`, `PENDING`, `APPROVED`, `ACTIVE`, `SUSPENDED`, `REVOKED`, `CLOSED`
- execution: `CREATED`, `QUEUED`, `PROCESSING`, `SUCCEEDED`, `FAILED`, `CANCELLED`
- review: `PENDING_REVIEW`, `APPROVED`, `REJECTED`
- environments: `SANDBOX`, `PRODUCTION`

Individual domains may add states, but APIs should avoid inventing synonyms for the same business condition.

## API conventions

- Merchant-facing signed APIs: `/api/v2/**`
- Cito account/session APIs: `/api/v2/cito/**`
- Merchant portal session APIs: `/api/v2/merchant-self-service/**`
- Admin/operations APIs: `/api/v2/admin/**`
- Public tokenized/anonymous APIs: narrowly scoped `/api/public/**` or token routes with dedicated security configuration
- Every new money-moving API requires idempotency and a request/correlation identifier.

## Security conventions

- deny by default
- tenant scope derived server side where a session provides it
- no trust in client-supplied role/tenant identifiers without authorization checks
- no public privilege self-assignment
- secrets encrypted, hashed where only verification is needed, or stored as external secret references
- audit role, entitlement, configuration and money-lifecycle mutations
- maker-checker for configurable high-risk thresholds
- environment isolation enforced server side, not only by the portal UI
- asynchronous money-adjacent follow-up uses durable recovery state rather than unsafe provider re-submission

## Automated verification added for convergence

Focused regression coverage includes:

- Cito merchant feature authorization and environment selection
- side-effect-free marketplace split simulation
- pending payment split-event recovery semantics
- developer scope-to-service entitlement mapping
- unified Cito Services merchant workspace rendering and tab loading

These supplement the repository's existing backend, frontend, migration and security test suites. CI remains authoritative for the branch before merge.

## Production activation boundary

The code intentionally does **not** convert a software implementation into an external approval. Production activation still requires applicable provider certification/credentials, production entitlements, security and finance sign-off, regulatory/compliance approval, staging/UAT evidence, monitoring/on-call readiness and deployment authorization.

## Launch definition

A software feature is considered implemented when schema, backend logic, authorization, APIs, tests, documentation and a usable control surface or API workflow exist. It is considered production-activated only after any required external provider, regulator, security, finance or operational approval is complete.
