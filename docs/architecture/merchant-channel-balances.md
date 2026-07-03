# Normalized Merchant-Channel Balances

This branch introduces `merchant_channel_balances` as the v2 balance model.

## Purpose

The older ledger path stores balances in fixed provider-specific fields. That makes every new provider require schema and code changes. The normalized table stores balances by merchant, channel, and currency.

## Table

```text
merchant_channel_balances
  merchant_id
  channel_code
  gateway_id
  currency
  available_balance
  ledger_balance
  reserved_balance
```

## Migration approach

- `/api/v1` continues using the legacy balance shape.
- `/api/v2/merchant/balances` reads normalized balances first.
- If no normalized rows exist yet, v2 falls back to legacy balances so rollout is safe.
- Future work should dual-write ledger events into this table, then switch v2 fully to normalized balances.

## Money handling

New v2 balance and reconciliation components use `BigDecimal` through `MoneyAmount`. Legacy paths still convert at the boundary until deeper ledger migration is completed.
