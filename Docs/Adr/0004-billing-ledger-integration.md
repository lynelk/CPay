# ADR 0004: Billing Ledger Integration

Date: 2026-08-07
Status: Accepted

## Context

`DoubleEntryLedgerService` (`net.citotech.cito.ledger`) is CPay's tested, `BigDecimal`-based,
currency-balanced ledger, already posting real entries for every payment via
`PaymentOrchestrationService.postLedgerEntries()`, with a generic `ledger_accounts` model
(`account_code`, `account_type`, `owner_type`, `owner_id`, `currency`) that is not hardcoded to
merchants. `Docs/Money-ledger-and-orchestration-roadmap.md` already establishes this as the
target financial source of truth and states explicitly that "provider statement imports propose
matched corrections rather than mutating ledger entries directly." The billing engine needs a way
to post charges, tax, provider cost, prepaid consumption, and BaaS platform fees without violating
that rule or duplicating the ledger.

## Decision

Billing does not build a second ledger. It extends the existing one:

- `owner_type` gains a new accepted value, `BILLING_TENANT` (alongside the existing `MERCHANT`/
  `PROVIDER`/`SYSTEM`), with `owner_id` = `billing_tenant_id`. This is a free-form string column
  already, so no migration is needed to add the value.
- Account codes follow the existing `merchant:{id}:{ccy}:{purpose}` / `provider:{gatewayId}:{ccy}:
  {purpose}` / `cpay:{ccy}:{purpose}` convention, extended with a `billing:{tenantId}:{ccy}:
  {purpose}` namespace (`ar`, `stored_value_liability`, `tax_payable`) plus `cpay:{ccy}:
  billing_revenue` and `cpay:{ccy}:baas_platform_fee_revenue`, kept segregated from a merchant's
  own revenue accounts.
- `DoubleEntryLedgerService` gets one additive method, `reverse(originalReference, newReference,
  reason)`, that posts a mirror-image balanced group under a new reference. No existing method
  signature changes. Corrections are always a new reversal transaction, never an edit — this
  satisfies the roadmap's "propose, never mutate" rule by construction.
- A new `billing_ledger_links` table records `ledger_transaction_id` / `billing_tenant_id` /
  `link_type` (CHARGE/INVOICE/PAYMENT/COST/REVERSAL) / `billing_reference`, populated by the
  billing module immediately after every post — this is how billing traces back to the ledger
  without touching `ledger_transactions`/`ledger_entries` itself.
- All billing calls into `DoubleEntryLedgerService.post()` go through one new
  `BillingLedgerAccountTemplateService`, so posting conventions for the standard patterns
  (postpaid charge, tax, cost accrual, prepaid top-up/consumption, invoice payment, credit/refund,
  BaaS fee) live in one reviewable place instead of being scattered across the billing module.
- A `ledger_period_locks` table plus an additive check inside `post()`/`reverse()` rejects postings
  into a locked period. This defaults **fail-open** (no configured lock = always allowed), since
  `post()` already has 9 real production dependents and an accidental lock must not silently halt
  payment processing platform-wide.

## Consequences

- `DoubleEntryLedgerService` stays the single financial source of truth; billing is strictly an
  additive consumer of its existing account/transaction/entry model.
- Any billing code that wants to post money must go through `BillingLedgerAccountTemplateService` —
  reviewers can check ledger-posting correctness in one file.
- The period-lock check is new failure-mode behavior on a heavily-depended-on method; it ships with
  a full re-run of the existing ledger test suite, not just new tests, and defaults to a no-op
  until locks are actually configured.

## Follow-ups

- Add `CUSTOMER`/`TAX_AUTHORITY` owner-type usage once tax/customer-level billing lands (Phase 3).
- Consider a DB-level immutability guard (trigger or revoked grants) on `ledger_entries` once the
  application-level append-only convention has proven itself under the new billing write volume.
