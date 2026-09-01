# Financial Correctness and Data Integrity

This document is the normative financial-correctness policy for Cito. It applies to payment collection, payout, billing, fees, tax, FX, balances, reservations, reconciliation, settlement, invoicing, credit notes, treasury and financial reporting.

Verification note: the September 2026 financial-correctness remediation is accepted for merge only after the exact PR head completes the full Maven/Spotless, billing-convergence, API/documentation, container-build and governance gates. Production activation additionally requires runtime confirmation that Flyway V110 is applied.

## 1. Canonical money policy

Authoritative calculations use `BigDecimal` with four decimal places and `RoundingMode.HALF_UP` unless a documented external protocol requires greater intermediate precision. `MoneyAmount` is the canonical application helper.

Two-decimal output is presentation only. A value must not be rounded to display precision before another fee, tax, FX, ledger, reconciliation or settlement calculation uses it.

Legacy `Double` methods are compatibility boundaries, not calculation APIs. Convert incoming legacy doubles to `BigDecimal` immediately and do not convert back until an external legacy signature requires it.

## 2. Ledger invariant

For every posted transaction and every currency:

```text
sum(debits) = sum(credits)
```

Every entry amount must be positive. Currency is normalized before grouping. A transaction that fails this equality must not be committed.

Ledger history is append-only. A correction posts a new mirror/reversal transaction with a new transaction reference. Never edit an existing financial entry to make a report balance.

Derived balance tables are caches/materialized projections of the ledger and are not independent sources of truth.

## 3. Idempotency invariant

A financial reference can be replayed only with the same commercial meaning. Where the platform accepts an idempotent replay, it must either return the original result or verify all immutable commercial attributes before doing so.

A duplicate reference with different amount, currency, merchant, provider, service or other identity-defining attributes is a conflict and must fail closed.

## 4. Fee invariant

Supported fee methods are currently:

- `FLAT_FEE`
- `PERCENTAGE`

`TIER` is intentionally unsupported until genuine tier bands, thresholds, inclusivity rules and boundary tests exist. It must throw rather than fall back to a flat charge.

Fee schedule rules:

- amount > 0;
- percentage <= 100%;
- effective-dated history is retained;
- merchant-specific active schedules override global schedules;
- the calculation result remains at four decimal places until presentation.

## 5. Tax invariant

Tax is calculated from the approved effective-dated rule applicable to the commercial artifact. The rule/rate evidence is snapshotted so a historical invoice can be reconstructed even after rates change.

Invoice relationship:

```text
total = subtotal + tax
```

At four-decimal precision.

### Credit notes

For a partial credit:

```text
proportional tax credit = gross credit × original invoice tax / original invoice total
```

rounded at four decimals, capped at the remaining original tax.

Cito records each credit note's gross, revenue and tax allocation immutably in `billing_credit_note_allocations` with the database constraint:

```text
gross_amount = revenue_amount + tax_amount
```

If cumulative gross credits reach the original invoice total, the final credit uses the exact remaining original tax rather than another independently rounded proportion. Therefore cumulative tax credits can neither exceed the original tax nor leave a rounding residual after a complete reversal.

## 6. FX invariant

Direct conversion:

```text
target amount = source amount × direct rate
```

If only the inverse pair exists:

```text
derived rate = 1 / inverse rate
```

The FX resolver retains higher precision for rate inversion and rounds monetary output to the canonical money scale. The source rate, provider and effective timestamp are snapshotted against the artifact.

Never reconstruct a historical customer charge using today's FX rate.

## 7. Reservation and available-balance invariant

A merchant reservation is scoped by merchant and currency and serialized through a database lock.

Conceptually:

```text
available = posted merchant liability - active reservations
```

A reservation greater than available funds is rejected. Capture/release is conditional on the reservation still being `RESERVED`.

BaaS charging uses:

```text
available headroom = prepaid balance + credit limit - credit used - reserved amount
```

Commit removes the reservation and allocates the exact gross charge between prepaid funds and credit. Reversal restores those components and reverses linked ledger postings.

## 8. Reconciliation invariant

A merchant reference alone is insufficient evidence for an automatic match.

Automatic matching requires all of:

1. merchant reference equality;
2. amount equality within the narrowly documented tolerance required only because the legacy transaction column contains historical floating-point values;
3. currency equality;
4. an eligible final/successful internal transaction status;
5. exactly one candidate.

If no candidate or multiple candidates satisfy these conditions, the statement row remains unmatched for investigation/manual matching.

Manual matching is an auditable exception workflow, not a mechanism for silently weakening automatic matching.

## 9. Settlement invariant

A settlement batch's provider, channel, currency and expected amount become immutable when the batch is opened.

An identical replay is idempotent. A conflicting replay is rejected before any ledger action, preventing this invalid state:

```text
operational batch amount != posted ledger settlement amount
```

Settlement close remains maker-checker governed; the requester cannot approve the same close.

Corrections after batch creation require an explicit adjustment/reversal workflow rather than mutation of the original commercial record.

## 10. Data movement and traceability

Every monetary outcome should be reconstructible along its chain:

```text
provider/customer event
→ internal payment or usage event
→ price/fee/tax/FX evidence
→ rated/authorized amount
→ ledger posting
→ merchant/customer balance impact
→ reconciliation evidence
→ settlement/invoice/reporting outcome
```

Each boundary must preserve amount, currency, reference and tenant/merchant identity. Any intentional difference must be represented explicitly as a fee, tax, FX difference, provider cost, adjustment or reversal rather than hidden in a changed amount.

## 11. Concurrency controls

Operations that can move or reserve money must use database transactions and suitable locking/idempotency controls. Multi-instance scheduled workers must use shared claim/lock mechanisms such as ShedLock or row-level claims so application replication does not duplicate financial work.

## 12. Required regression tests

Any change to a monetary path must test the corresponding invariant. The baseline suite must cover at least:

- four-decimal canonical money rounding;
- display rounding does not alter calculation values;
- fee percentage and flat formulas;
- unsupported tier pricing fails closed;
- invalid fee schedules are rejected;
- ledger balanced/unbalanced postings;
- ledger reversal and idempotency;
- concurrent reservation safety;
- reconciliation rejects amount/currency/finality/ambiguity mismatches;
- settlement identical replay and conflicting replay;
- tax calculation and snapshot consistency;
- repeated partial credit notes and final residual absorption;
- FX direct/inverse conversion and immutable snapshots;
- invoice payment over-allocation rejection;
- BaaS authorize/commit/release/reverse balance conservation.

## 13. Production acceptance

Do not declare financial correctness solely from a successful HTTP response or green CI. Production acceptance also requires runtime evidence:

- expected Flyway version applied;
- ledger trial balances balanced by currency;
- no unresolved high/critical reconciliation exceptions for the close scope;
- expected provider statements imported;
- settlement evidence agrees with ledger postings;
- schedulers are single-execution under multiple backend replicas;
- backup and restore controls remain operational;
- finance-close maker-checker evidence is present where required.

A balanced ledger does not by itself prove the business day is closed. It proves one important thing, which is refreshingly different from proving everything.
