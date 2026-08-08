# ADR 0003: Billing Tenant Model

Date: 2026-08-07
Status: Accepted

## Context

The billing engine (see `Docs/Money-ledger-and-orchestration-roadmap.md` for the underlying
ledger it builds on) needs a scoping key for every billing row. `merchants.id` is the only
scoping key that exists anywhere in the money-movement schema today — there is no `tenant_id`
concept in `ledger`, `reconciliation`, or `compliance` (the one polymorphic-shaped candidate,
`compliance_profiles.entity_type`/`entity_id`, is hardcoded to `entity_type='MERCHANT'` in every
read/write path and was not designed for this). A full Billing-as-a-Service model, where one
merchant could host several billing tenants or resell billing to their own end customers, is a
real long-term goal but not something this build needs to prove out yet.

## Decision

Introduce `billing_tenant_id` as a first-class column across the new billing schema
(`billing_tenants`, `billing_customers`, `billing_accounts`, and every table beneath them), backed
by a real `billing_tenants` table with its own primary key — not a reused `merchants.id` value.
For this build, every `billing_tenants` row maps to exactly one `merchants.id` (1:1, backfilled on
migration), and no UI or API exists yet to create a second billing tenant for the same merchant.
The abstraction is real in the schema from day one specifically so that later multi-tenant BaaS
does not require a breaking migration — only new provisioning code.

## Consequences

- Every new billing table takes `billing_tenant_id`, not `merchant_id`, keeping the abstraction
  consistent even though today's resolution is always 1:1.
- `BillingTenantResolver` is the single place that maps `merchantId -> billing_tenant_id`; nothing
  else in the billing module should read `merchant_id` directly once a tenant is resolved.
- `compliance_profiles` is not touched or repurposed by this decision.
- True multi-tenant BaaS (a merchant provisioning more than one billing tenant, or hosting their
  own customers as separate tenants) is out of scope until a follow-up ADR revisits this.

## Follow-ups

- If BaaS is greenlit, add a tenant-provisioning workflow and revisit whether `billing_tenant_id`
  needs a `merchant_id` foreign key relaxed from 1:1 to 1:many.
- Add cross-tenant isolation tests once more than one tenant per merchant is possible.
