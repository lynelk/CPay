# Money Model, Ledger, and Orchestration

Cito now has an implemented normalized double-entry ledger alongside compatibility transaction records. This document describes the current model and the remaining retirement work for legacy monetary storage. The normative arithmetic rules live in [`Financial-correctness-and-data-integrity.md`](Financial-correctness-and-data-integrity.md).

## Current ledger model

| Concept | Current rule |
|---|---|
| Account | Merchant, provider float, suspense, fees, billing, settlement and other purposes are represented as ledger accounts. |
| Entry | Every authoritative ledger movement is a positive debit or credit with currency and source/reference evidence. |
| Transaction | A group of entries is accepted only if debits equal credits independently for every currency. |
| Balance | Balances are derived from ledger entries or refreshed into a ledger-backed read model. |
| Currency | Currency is explicit data, normalized before financial grouping. |
| Amount | Authoritative arithmetic uses `BigDecimal`, scale 4, `HALF_UP`; floating-point values are compatibility boundaries only. |
| Correction | Posted history is not mutated. Corrections use a new reversing/mirror transaction. |
| Idempotency | Replaying a financial reference returns/reuses the original result only where its immutable commercial attributes are unchanged. |

## Required invariants

- Total debits equal total credits for each transaction group and currency.
- Trial balances are evaluated by currency.
- Provider statement imports never mutate posted ledger entries directly.
- Insufficient-funds checks use ledger-derived available balance before provider payout calls.
- Reservation creation serializes by merchant and currency through `ledger_reservation_controls`.
- Ledger account identity is scoped by owner, owner id, currency and account code.
- Settlement batches cannot change provider, channel, currency or expected amount after opening.
- Reconciliation cannot declare a match from merchant reference alone; amount, currency, finality and candidate uniqueness are required.
- Display rounding must never feed back into authoritative fee, tax, FX, settlement or ledger calculations.

## Orchestration states

The payment orchestration model distinguishes accepted/reserved/provider-sent/final and ambiguous states. Provider uncertainty must remain explicit rather than being coerced into success or failure merely to make a dashboard tidier.

Representative states include:

| State | Meaning |
|---|---|
| `PENDING` | Request accepted and persisted. |
| `RESERVED` | Funds reserved before an external money-moving call. |
| `SENT_TO_PROVIDER` | Provider call attempted. |
| `SUCCESSFUL` / equivalent final-success state | Provider evidence confirms success and accounting is complete. |
| `FAILED` | Final failure; reservations are released where appropriate. |
| `UNDETERMINED` | Outcome needs provider status/reconciliation/operator resolution. |

Invalid state transitions must fail rather than overwrite a final outcome.

## Implemented controls

The repository now includes:

1. normalized ledger accounts, transactions and entries;
2. balanced-posting validation and idempotent transaction references;
3. append-only ledger reversal support;
4. merchant/currency reservation serialization and ledger-derived availability checks;
5. owner/currency-scoped ledger account identity;
6. ledger-backed balance read models and trial-balance controls;
7. billing account templates linking usage/invoices/payments/credits back to ledger transactions;
8. effective-dated tax and FX evidence;
9. governed billing invoice/payment/credit-note flows;
10. multi-factor reconciliation and immutable settlement commercial attributes;
11. four-decimal canonical `MoneyAmount` policy shared by new financial calculations;
12. V110 credit-note allocation evidence to prevent cumulative tax-rounding drift.

## Remaining legacy-retirement work

The remaining work is cleanup/migration, not permission to create another parallel money model:

1. continue replacing legacy `Double`/floating-point compatibility fields at persistence/API boundaries with decimal schema/contracts where backward compatibility permits;
2. prove parity before retiring legacy transaction/statement-derived balance paths;
3. remove obsolete provider-specific balance columns only after ledger-backed reports and operational controls have signed-off parity;
4. keep reconciliation/provider evidence capable of tracing old transactions while legacy data remains in retention scope;
5. perform schema changes as forward-only Flyway migrations rather than rewriting historical migrations.

Any future change that alters these rules must update the normative financial-correctness document, regression tests and relevant runbooks in the same pull request.
