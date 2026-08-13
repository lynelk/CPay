# Money Model, Ledger, and Orchestration Roadmap

The current code stores balances and movement state across merchant transaction rows, statement rows, hardcoded balance columns, and provider-specific code paths. The target model is a normalized ledger with a small orchestration state machine.

## Target Ledger

| Concept | Rule |
|---|---|
| Account | Merchant, provider float, suspense, fee, SMS credit, and settlement accounts are rows. |
| Entry | Every money movement is a debit or credit with account, currency, minor units, direction, and reference. |
| Balance | Balances are derived from entries or refreshed into a materialized read model. |
| Currency | Currency is data on the account and entry, not a literal in code. |
| Amount | Use minor-unit longs or `BigDecimal` consistently; no floating-point money. |

## Required Invariants

- Total debits equal total credits for each transaction group.
- Trial balance runs daily by account and currency.
- Provider statement imports cannot mutate ledger entries directly; they propose matched corrections.
- Insufficient-funds checks use ledger-derived available balance (`posted merchant liability - active reservations`) before provider calls and release on failure.
- Reservation creation serializes by merchant and currency through `ledger_reservation_controls` so concurrent payouts cannot both consume the same available balance.

## Orchestration State Machine

Target states:

| State | Meaning |
|---|---|
| `PENDING` | Request accepted and persisted. |
| `RESERVED` | Funds reserved before external call. |
| `SENT_TO_PROVIDER` | Provider call attempted. |
| `SUCCESSFUL` | Provider confirmed success and ledger is complete. |
| `FAILED` | Provider or validation failure; reservations released. |
| `UNDETERMINED` | State requires operator or reconciliation resolution. |

Transitions should be explicit and rejected when invalid.

## Implemented Migration Steps

1. `V7__audit_roadmap_production_features.sql` introduces `ledger_accounts`, `ledger_transactions`, `ledger_entries`, reservations, and trial-balance run records.
2. `DoubleEntryLedgerService` rejects unbalanced ledger groups and preserves idempotency by transaction reference.
3. Risk authorization now runs before v2 orchestration provider calls.
4. `V66__ledger_reservation_funds_controls.sql` adds merchant/currency reservation control rows and lookup indexes for serialized funds checks.
5. `DoubleEntryLedgerService.reserve` locks the merchant/currency reservation scope, checks ledger-derived available balance, then inserts the reservation in one transaction.

## Remaining Migration Steps

1. Dual-write every v2 money movement through both legacy rows and normalized ledger entries.
2. Add a daily trial-balance scheduler once finance signs off on the account taxonomy.
3. Move dashboard balance reads to a materialized ledger read model.
4. Remove hardcoded provider balance columns after parity signoff.
