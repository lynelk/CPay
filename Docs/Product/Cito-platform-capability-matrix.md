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

| # | Feature | Primary owner | Tenant scoped | Money movement | Human approval possible | Sandbox support |
|---|---|---|---|---|---|---|
| 1 | Service catalogue & entitlements | Cito | Yes | No | Yes | Yes |
| 2 | Intelligent routing | CPay | Yes | Yes | Policy approval | Yes |
| 3 | Subaccounts & splits | CPay | Yes | Yes | Yes | Yes |
| 4 | Refunds/reversals/disputes | CPay | Yes | Yes | Yes | Yes |
| 5 | Subscriptions/mandates | CPay | Yes | Yes | Yes | Yes |
| 6 | Merchant intelligence | CPay/Cito | Yes | No | No | Yes |
| 7 | Developer control plane | Cito | Yes | Indirect | Production activation | Yes |
| 8 | Virtual accounts | CPay | Yes | Yes | Provider activation | Yes |
| 9 | Embedded/white-label | Cito | Yes | Indirect | Partner approval | Yes |
| 10 | Integrations marketplace | Cito | Yes | Indirect | Connector approval | Yes |

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
- secrets encrypted or stored as external secret references
- audit role, entitlement, configuration and money-lifecycle mutations
- maker-checker for configurable high-risk thresholds
- environment isolation enforced server side, not only by the portal UI

## Launch definition

A software feature is considered implemented when schema, backend logic, authorization, APIs, tests, documentation and a usable control surface or API workflow exist. It is considered production-activated only after any required external provider, regulator, security, finance or operational approval is complete.