# Cito unified product experience

## Product architecture

Cito is the customer gateway and control plane. Customer-facing product names are:

- **Cito Payments** — collections, payouts, refunds, disputes, reconciliation and settlement.
- **Cito Billing** — catalogue, pricing, metering, rating, invoices, credits and allocations.
- **Cito Operations** — treasury, provider operations, risk, compliance, support and incidents.
- **Cito Platform** — organizations, entitlements, access, communications and shared services.
- **Cito Developer Platform** — applications, credentials, API activity, webhooks, sandbox and go-live.

`CPay`, legacy route names, table names and API headers remain compatibility identifiers. New public copy must not present CPay as a separate customer-facing product.

## Canonical information architecture

The admin workspace groups existing operational modules under Home, Merchants & Accounts, Money Operations, Treasury, Risk & Compliance, Providers & Integrations, Platform, Administration and Engineering / Internal. The merchant workspace uses Home, Payments, Balances & Settlements, Customers, Developers, Services, Reports, Business, Help and Settings. URLs are authoritative; refresh, deep links and browser navigation retain context.

Admin workspaces are prioritized for operations, finance, compliance, customer care and platform administration. Merchant service-specific navigation is shown only when explicit entitlement metadata permits it. Server-side authorization remains authoritative.

## Trust contract

- Production screens never substitute sample amounts, counts, success rates, incidents, customers or providers.
- Every asynchronous surface distinguishes loading, empty, error and loaded states.
- Sandbox data is labelled synthetic. Production data is sourced from live records only.
- `X-Cito-Environment` communicates the persistent selected environment. Moving to production requires explicit confirmation.
- Transaction detail exposes finality, references, provider transitions, reconciliation and settlement evidence. It never recommends a blind retry.
- High-risk production actions must use the shared confirmation pattern and retain server-side maker-checker/idempotency controls.

## Authoritative merchant activation lifecycle

`merchant_activation_lifecycles` and `merchant_activation_steps` are the cross-team source of truth. The 15 ordered steps are account creation, email verification, business profile, ownership, documents, KYB review, risk review, commercial approval, service selection, sandbox configuration, integration testing, provider certification, settlement configuration, go-live approval and production activation.

Each step records status, responsible party, guidance, blocker and completion evidence. The lifecycle summary records the current status, owner, blocker, due date and next action. Existing onboarding, sandbox and go-live tables remain compatible workflow evidence; new portal, admin, support and sales surfaces read the canonical lifecycle API.

## Experience domain APIs

The versioned experience contract is documented in `Docs/Api/cito-platform-v2-openapi.yaml`:

- merchant overview and lifecycle;
- canonical transaction detail, timeline and support context;
- role- and tenant-scoped search;
- contextual support cases with SLA timestamps;
- recipient-scoped notifications;
- provider incidents and public status;
- allowlisted product analytics events and consented sales enquiries.

All data-access failures return an explicit `503 DATA_TEMPORARILY_UNAVAILABLE`; the server does not manufacture zeros or empty lists to hide retrieval failures.

## Delivery traceability

| Guide outcome | Implementation evidence |
|---|---|
| Product coherence and naming | Public product routes, landing copy, this architecture contract |
| Hierarchical admin and merchant IA | Route-driven layouts and canonical grouped navigation |
| Role, tenant and entitlement context | Session access context, merchant-scoped SQL, entitlement-aware merchant menu |
| Activation and go-live | V114 canonical lifecycle plus existing sandbox/certification engines |
| Payments and finance operations | Existing payment, payout, reconciliation, settlement, treasury and finance-close modules under canonical routes |
| Transaction source of truth | `/transactions/{reference}`, `/timeline`, `/support-context` |
| Search, support and Merchant 360 context | `/search`, `/support/cases`, merchant overview and lifecycle |
| Notifications and preferences | Recipient notification centre plus existing merchant delivery preferences |
| Provider certification and incidents | Existing certification module, provider incident domain, public status |
| Developer journey | Existing developer control plane/sandbox plus Developer Platform route |
| Service catalogue and entitlements | Existing Cito catalogue/entitlement engine and navigation filtering |
| Marketing, conversion and SEO | Product/about/security/contact/status pages, enquiry handoff, metadata, schema, sitemap and robots |
| Analytics | Allowlisted, hashed-reference funnel event storage |
| Accessibility and responsive design | Semantic shared primitives, reduced-motion behavior, automated axe regression |
| Security UX | Persistent environment context and reusable high-risk confirmation |
| Observability and operational KPIs | Live status and KPI/runbook definitions; existing actuator/operations alerts |
| Support and incident readiness | First-class case/incident tables and runbooks |
| Testing and release gates | Backend tests, frontend unit/accessibility tests, OpenAPI drift checks, clean MySQL Flyway gate and Compose gate |
| Railway and deployment safety | Existing branch promotion and health gates; no replica, CPU, RAM or database topology change in this release |

## Definition of done

A journey is complete only when its route survives refresh/deep-linking, authorization is enforced server-side, empty/error/environment states are explicit, records are auditable, API contracts are updated, tenant isolation and financial invariants pass, and production health is verified after migration.
