-- F4/N10/O3: nightly reporting aggregates so admin/ops dashboards stop scanning
-- merchant_transactions_log directly for stats, failure-reason breakdowns, and float balance
-- history (this project has no read replica). Populated by
-- net.citotech.cito.scheduler.ReportingAggregateScheduler; read via /api/v2/admin/reporting/**.
-- All additive - no changes to merchant_transactions_log's schema.

-- F4: one row per (day, merchant, gateway, tx_type, status) instead of scanning the full log.
CREATE TABLE IF NOT EXISTS `daily_transaction_stats` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `stat_date` DATE NOT NULL,
  `merchant_id` BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `gateway_id` VARCHAR(255) NOT NULL DEFAULT '',
  `tx_type` VARCHAR(20) NOT NULL,
  `status` VARCHAR(20) NOT NULL,
  `tx_count` INT NOT NULL DEFAULT 0,
  `total_amount` DECIMAL(18,4) NOT NULL DEFAULT 0,
  `total_charges` DECIMAL(18,4) NOT NULL DEFAULT 0,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_daily_tx_stats` (`stat_date`, `merchant_id`, `gateway_id`, `tx_type`, `status`),
  KEY `idx_daily_tx_stats_date` (`stat_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- N10: aggregate failure counts by gateway/day. `error_code` is populated only when the stored
-- trace is the structured JSON produced by GeneralException.getError (its "code" field, a legacy
-- numeric code resolvable via ErrorCatalog); otherwise it stays blank and `failure_reason` holds a
-- truncated/normalized version of whatever provider or callback trace text was actually recorded
-- (see Common.java's tx_update_trace/tx_request_trace handling in the doPayIn/doPayOut FAILED
-- paths - there is currently no dedicated structured error-code column on the log table).
CREATE TABLE IF NOT EXISTS `daily_failure_reason_stats` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `stat_date` DATE NOT NULL,
  `gateway_id` VARCHAR(100) NOT NULL DEFAULT '',
  `error_code` VARCHAR(50) NOT NULL DEFAULT '',
  `failure_reason` VARCHAR(191) NOT NULL DEFAULT '',
  `tx_count` INT NOT NULL DEFAULT 0,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_daily_failure_stats` (`stat_date`, `gateway_id`, `error_code`, `failure_reason`),
  KEY `idx_daily_failure_stats_date` (`stat_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- O3: one balance snapshot per float/stock gateway account per day, so the float dashboard can
-- chart balance-over-time and derive a burn rate without recomputing merchant statement history
-- live on every request.
CREATE TABLE IF NOT EXISTS `float_balance_snapshots` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `stat_date` DATE NOT NULL,
  `account_type` VARCHAR(100) NOT NULL,
  `balance` DECIMAL(18,4) NOT NULL DEFAULT 0,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_float_balance_snapshot` (`stat_date`, `account_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- O3: minimal top-up log - no existing table records float/stock top-up events today.
CREATE TABLE IF NOT EXISTS `float_topups` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `topup_date` DATE NOT NULL,
  `account` VARCHAR(150) NOT NULL,
  `amount` DECIMAL(18,4) NOT NULL,
  `recorded_by` VARCHAR(255) NOT NULL DEFAULT 'system',
  `note` VARCHAR(255) NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_float_topups_date` (`topup_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
