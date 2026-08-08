-- Billing engine Phase 3, Slice 28 (see Docs/Adr/0004-billing-ledger-integration.md): lets an
-- operator (or a future finance daily-close workflow) temporarily block new
-- DoubleEntryLedgerService.post()/reverse() postings for a currency during a date range, so a
-- trial-balance snapshot being calculated cannot be silently invalidated by a concurrent posting.
--
-- post()/reverse() have no explicit backdating parameter (every posting lands as of "now"), so
-- this only ever blocks *today's* posting for a currency, not arbitrary historical periods - a
-- lock is a live "posting halted" switch, not a retroactive close. The check defaults fail-open:
-- an empty table (the default, always true until someone explicitly locks a period) means every
-- post()/reverse() call is allowed, since DoubleEntryLedgerService already has 9 real production
-- dependents and an accidental lock must never silently halt payment processing platform-wide.
--
-- No foreign keys, matching this schema's existing convention.

CREATE TABLE IF NOT EXISTS `ledger_period_locks` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `currency` VARCHAR(10) NOT NULL,
  `period_start` DATE NOT NULL,
  `period_end` DATE NOT NULL,
  `locked_by` VARCHAR(255) NOT NULL,
  `reason` VARCHAR(255) NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `released_at` TIMESTAMP NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_ledger_period_lock_active` (`currency`, `period_start`, `period_end`, `released_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
