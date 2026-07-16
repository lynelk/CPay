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
- Insufficient-funds checks reserve funds before provider calls and release on failure.

## Orchestration State Machine

Target states:

| State | Meaning |
|---|---|
| `PENDING` | Request accepted and persisted. |
| `RESERVED` | Funds reserved before external call. |
| `SENT_TO_PROVIDER` | Provider call attempted. |
| `SUCCESSFUL` | Provider confirmed success and ledger is complete. |
| `FAILED` | Provider or validation failure; reservations released. |
| `UNDERMINED` | State requires operator or reconciliation resolution. |

Transitions should be explicit and rejected when invalid.

## Migration Steps

1. Introduce `ledger_accounts` and `ledger_entries` alongside existing tables.
2. Write new v2 payments through both legacy statement rows and ledger entries.
3. Add a daily trial-balance job.
4. Move dashboard balance reads to a materialized read model.
5. Remove hardcoded provider balance columns after parity signoff.
