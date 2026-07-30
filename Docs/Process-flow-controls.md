# Process Flow Controls

This document maps the specific process weaknesses found in the audit to target controls.

## Payin and Payout

- Use idempotency keys for all money-moving requests.
- Persist the request before external provider calls.
- Reserve funds before payout provider calls.
- Route all merchant callbacks through the callback task queue.
- Run `LegacyLedgerRepairService` from `LedgerOperationsScheduler` to backfill successful legacy
  pay-in/pay-out rows missing their idempotent `payment:<tx_unique_id>` ledger posting before the
  daily trial-balance check runs.

## Provider Callbacks

- Deduplicate inbound callbacks by provider reference and terminal status before statement/ledger
  effects are applied.
- Reject invalid state regressions.
- Preserve raw provider payloads for audit, but expose normalized events to merchants.

## Signup and Password Reset

- Verify email before activating self-service merchants.
- Throttle signup by IP and email.
- Replace password reset codes with single-use token records.
- Revoke sessions after password change.

## SMS Credits

- Treat SMS credits as ledger entries.
- Add SMS delivery states.
- Refund credits when the provider fails permanently.

## Reconciliation

- Matched statements should visibly update transaction/reconciliation state.
- Mismatch correction must be auditable and reversible.

## Cross-border Transfers

- Claim an FX quote atomically (`ACTIVE` to `BOUND`) before inserting the transfer intent so expired,
  stale, or concurrently reused quotes cannot be replayed.
