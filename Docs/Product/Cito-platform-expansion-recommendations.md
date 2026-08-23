# Cito Platform Expansion Recommendations

## Purpose

This document turns the current Cito/CPay product position into the next product-development sequence. Cito is the gateway and account/identity front door. CPay is the payments service inside Cito. The goal is to increase merchant adoption, platform value, operational leverage, and monetization without weakening the existing payment, security, ledger, compliance, reconciliation, and provider-adapter foundations.

## Recommended feature sequence

### 1. Cito service catalogue and entitlement engine

Create a first-class Cito organization model that controls which products, capabilities, modules, users, teams, API clients, and environments each tenant can access.

Core capabilities:
- organization/workspace membership
- service catalogue for CPay, Identity & Validation, Communications, Vending, Billing, Developer Services, and future products
- service activation lifecycle: REQUESTED, APPROVED, ACTIVE, SUSPENDED, REVOKED
- plan and feature entitlements
- role templates and permission bundles
- user/service-account grants
- periodic access-review records
- environment-aware entitlements for SANDBOX and PRODUCTION
- audit evidence for grant, revoke, and review actions

### 2. Intelligent payment routing and provider failover

Add policy-driven payment routing above the existing provider adapters.

Core capabilities:
- provider eligibility by country, currency, channel, operation, and merchant
- weighted routing rules
- cost, latency, success-rate, availability, and merchant-preference scoring
- circuit-breaker and degraded-provider state
- safe retry/failover policy with idempotency awareness
- route simulation/explanation endpoint
- provider health statistics and routing audit trail

### 3. Marketplace payments, subaccounts, and transaction splitting

Enable CPay to support marketplace and platform business models.

Core capabilities:
- managed subaccounts/submerchants
- split rules by fixed amount or percentage
- multi-beneficiary allocation
- platform commissions and fee-bearer rules
- settlement allocation
- split validation and immutable execution snapshots
- reconciliation and ledger traceability per split participant

### 4. Refund, reversal, and dispute lifecycle

Make post-payment exception handling a first-class financial lifecycle.

Core capabilities:
- full and partial refunds
- provider reversals and reversal status tracking
- reason codes and supporting evidence
- maker-checker threshold approval
- customer notification hooks
- ledger/reconciliation treatment
- transaction timeline linking original payment, settlement, reversal, and refund
- dispute/case states and escalation

### 5. Subscriptions, recurring billing, and mandates

Create recurring commercial primitives independent of any one provider.

Core capabilities:
- plans and pricing periods
- subscriptions
- mandates/consents
- scheduled charges
- retry and dunning rules
- grace periods
- pause/resume/cancel
- invoice generation
- provider-capability-aware execution
- recurring-payment webhooks and audit

### 6. Merchant intelligence and provider-performance analytics

Turn existing operational data into merchant and operations insight.

Core capabilities:
- payment success/failure funnels
- provider/channel success rates
- P50/P95 latency
- failure taxonomy
- settlement ageing
- float/balance trends
- webhook health
- reconciliation exception trends
- routing recommendations
- exportable analytics and date/merchant/channel filters

### 7. Developer control plane

Create a first-class Cito developer workspace around the existing API, SDK, signing, webhook, and sandbox capabilities.

Core capabilities:
- developer projects
- SANDBOX/PRODUCTION environments
- service accounts and scoped credentials
- signing-key lifecycle and rotation metadata
- API request log/explorer
- webhook inspector/replay/test-event generator
- usage analytics
- OpenAPI/Postman/SDK discovery
- environment comparison and readiness indicators

### 8. Virtual accounts and account-to-account rails

Model bank-transfer collection rails as another CPay payment rail rather than as a separate product silo.

Core capabilities:
- provider-agnostic virtual account abstraction
- permanent and temporary collection accounts
- customer/merchant assignment
- incoming-transfer callbacks
- payment matching and reconciliation
- lifecycle and expiry
- provider capability/configuration model
- no production issuance without a certified banking/provider connector

### 9. Embedded and white-label Cito

Allow partners and platforms to embed Cito services into their own experiences.

Core capabilities:
- partner/tenant branding configuration
- hosted or embedded onboarding sessions
- API-managed downstream merchants
- delegated service activation
- partner commission rules
- callback/webhook isolation
- tenant-safe branding and domain metadata
- auditable delegated administration

### 10. Cito integrations marketplace

Create an installable connector platform for accounting, ERP, e-commerce, POS, workflow, and other third-party systems.

Core capabilities:
- connector catalogue
- versioned connector definitions
- installation lifecycle
- scoped credentials/secrets references
- field mapping and sync direction
- event subscriptions
- job execution state and retries
- installation health/status
- connector permission/entitlement checks

## Cross-cutting requirements

Every new capability must preserve CPay/Cito production controls:
- explicit tenant scoping
- least-privilege authorization
- immutable/auditable financial decisions
- request correlation identifiers
- idempotency for money-moving mutations
- server-side validation
- merchant-safe error messages
- secrets never returned after initial controlled disclosure
- environment separation
- ledger and reconciliation traceability where money moves
- rate limiting for externally accessible mutation endpoints
- maker-checker for privileged or high-risk actions
- no production credential issuance or external provider certification claims without real human/provider approval

## Strategic integration sequence

After all ten feature tracks exist, perform a second integration pass in this order:

1. Cito entitlements
2. Intelligent routing
3. Refunds/reversals
4. Subaccounts/split payments
5. Merchant analytics
6. Developer control plane

The second pass must make these six layers consume each other instead of remaining isolated modules. In particular, entitlements must gate every later feature; routing must emit analytics and explainability data; refunds and splits must produce complete financial traceability; analytics must expose routing/refund/split outcomes; and the developer control plane must surface all eligible capabilities according to tenant entitlements.