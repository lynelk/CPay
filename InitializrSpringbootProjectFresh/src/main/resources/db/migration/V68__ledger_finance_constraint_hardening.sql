-- V68: Ledger finance constraint hardening (backlog §7.3 / P1 finance daily-close enforcement).
--
-- Backs the ledger's money-movement invariants into the database so the finance
-- close gate can rely on them at rest, not only in application code:
--   * entries are strictly DR/CR with positive amounts
--   * reservations hold positive amounts in a closed lifecycle state set
--   * trial-balance runs carry a strict YES/NO balanced flag
--   * account lifecycle states are a closed set
--
-- MySQL 8.0.16+ enforces CHECK constraints; production image is mysql:8.4.
-- These match exactly what DoubleEntryLedgerService already validates, so they
-- are fail-fast guards that surface data anomalies instead of allowing drift.

-- Ledger entries: direction is strictly DR/CR and amounts must be positive.
ALTER TABLE `ledger_entries`
  ADD CONSTRAINT `chk_ledger_entry_direction`
      CHECK (`entry_direction` IN ('DR', 'CR')),
  ADD CONSTRAINT `chk_ledger_entry_amount_positive`
      CHECK (`amount` > 0);

-- Reservations: positive amount and a closed lifecycle state set.
ALTER TABLE `ledger_reservations`
  ADD CONSTRAINT `chk_ledger_reservation_amount_positive`
      CHECK (`amount` > 0),
  ADD CONSTRAINT `chk_ledger_reservation_status`
      CHECK (`reservation_status` IN ('RESERVED', 'CAPTURED', 'RELEASED'));

-- Trial-balance runs: balanced flag is strictly YES/NO.
ALTER TABLE `ledger_trial_balance_runs`
  ADD CONSTRAINT `chk_trial_balance_flag`
      CHECK (`balanced_flag` IN ('YES', 'NO'));

-- Accounts: normalize any legacy status to ACTIVE, then close the lifecycle set.
UPDATE `ledger_accounts`
SET `account_status` = 'ACTIVE'
WHERE `account_status` NOT IN ('ACTIVE', 'SUSPENDED', 'CLOSED');

ALTER TABLE `ledger_accounts`
  ADD CONSTRAINT `chk_ledger_account_status`
      CHECK (`account_status` IN ('ACTIVE', 'SUSPENDED', 'CLOSED'));
