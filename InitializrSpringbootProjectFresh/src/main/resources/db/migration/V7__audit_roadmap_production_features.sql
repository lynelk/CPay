-- Durable production schema for the audit-roadmap implementation tranche.
-- All objects are additive so existing legacy flows can continue while v2 moves
-- onto normalized ledger, risk, MFA, hosted checkout, token, and settlement rails.

CREATE TABLE IF NOT EXISTS `ledger_accounts` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `account_code` VARCHAR(150) NOT NULL,
  `account_name` VARCHAR(255) NOT NULL,
  `account_type` VARCHAR(60) NOT NULL,
  `owner_type` VARCHAR(60) NOT NULL,
  `owner_id` BIGINT UNSIGNED NULL,
  `currency` VARCHAR(10) NOT NULL,
  `account_status` VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ledger_account_code` (`account_code`),
  KEY `idx_ledger_account_owner` (`owner_type`, `owner_id`, `currency`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `ledger_transactions` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `transaction_reference` VARCHAR(255) NOT NULL,
  `source_type` VARCHAR(100) NOT NULL,
  `source_reference` VARCHAR(255) NOT NULL,
  `transaction_status` VARCHAR(40) NOT NULL DEFAULT 'POSTED',
  `description` TEXT NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ledger_transaction_ref` (`transaction_reference`),
  KEY `idx_ledger_transaction_source` (`source_type`, `source_reference`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `ledger_entries` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `ledger_transaction_id` BIGINT UNSIGNED NOT NULL,
  `account_id` BIGINT UNSIGNED NOT NULL,
  `entry_direction` VARCHAR(2) NOT NULL,
  `amount` DECIMAL(19,4) NOT NULL,
  `currency` VARCHAR(10) NOT NULL,
  `entry_memo` VARCHAR(255) NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_ledger_entry_transaction` (`ledger_transaction_id`),
  KEY `idx_ledger_entry_account` (`account_id`, `currency`),
  CONSTRAINT `fk_ledger_entry_transaction` FOREIGN KEY (`ledger_transaction_id`) REFERENCES `ledger_transactions` (`id`),
  CONSTRAINT `fk_ledger_entry_account` FOREIGN KEY (`account_id`) REFERENCES `ledger_accounts` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `ledger_reservations` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `reservation_reference` VARCHAR(255) NOT NULL,
  `merchant_id` BIGINT UNSIGNED NOT NULL,
  `source_reference` VARCHAR(255) NOT NULL,
  `amount` DECIMAL(19,4) NOT NULL,
  `currency` VARCHAR(10) NOT NULL,
  `reservation_status` VARCHAR(40) NOT NULL DEFAULT 'RESERVED',
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ledger_reservation_ref` (`reservation_reference`),
  KEY `idx_ledger_reservation_merchant` (`merchant_id`, `reservation_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `ledger_trial_balance_runs` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `run_date` DATE NOT NULL,
  `currency` VARCHAR(10) NOT NULL,
  `total_debits` DECIMAL(19,4) NOT NULL DEFAULT 0,
  `total_credits` DECIMAL(19,4) NOT NULL DEFAULT 0,
  `balanced_flag` VARCHAR(3) NOT NULL DEFAULT 'NO',
  `message` TEXT NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_trial_balance_run` (`run_date`, `currency`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `risk_rules` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `rule_key` VARCHAR(150) NOT NULL,
  `rule_type` VARCHAR(80) NOT NULL,
  `scope_type` VARCHAR(50) NOT NULL DEFAULT 'GLOBAL',
  `scope_reference` VARCHAR(150) NOT NULL DEFAULT '*',
  `currency` VARCHAR(10) NULL,
  `threshold_amount` DECIMAL(19,4) NULL,
  `threshold_count` INT NULL,
  `decision` VARCHAR(40) NOT NULL DEFAULT 'REVIEW',
  `enabled` VARCHAR(3) NOT NULL DEFAULT 'YES',
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_risk_rule_key` (`rule_key`),
  KEY `idx_risk_rule_lookup` (`rule_type`, `scope_type`, `scope_reference`, `enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `compliance_blocklist` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `value_type` VARCHAR(50) NOT NULL,
  `value_hash` VARCHAR(64) NOT NULL,
  `reason` TEXT NULL,
  `active_flag` VARCHAR(3) NOT NULL DEFAULT 'YES',
  `created_by` VARCHAR(255) NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_compliance_blocklist_value` (`value_type`, `value_hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `risk_decisions` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `merchant_id` BIGINT UNSIGNED NOT NULL,
  `request_reference` VARCHAR(255) NOT NULL,
  `direction` VARCHAR(30) NOT NULL,
  `amount` DECIMAL(19,4) NOT NULL,
  `currency` VARCHAR(10) NOT NULL,
  `decision` VARCHAR(40) NOT NULL,
  `reason_code` VARCHAR(120) NOT NULL,
  `decision_summary` TEXT NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_risk_decision_merchant` (`merchant_id`, `created_at`),
  KEY `idx_risk_decision_reference` (`request_reference`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `payment_links` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `link_reference` VARCHAR(255) NOT NULL,
  `merchant_id` BIGINT UNSIGNED NOT NULL,
  `merchant_number` VARCHAR(100) NOT NULL,
  `amount` DECIMAL(19,4) NOT NULL,
  `currency` VARCHAR(10) NOT NULL,
  `country` VARCHAR(10) NOT NULL,
  `description` TEXT NULL,
  `callback_url` TEXT NULL,
  `token_hash` VARCHAR(64) NOT NULL,
  `link_status` VARCHAR(40) NOT NULL DEFAULT 'ACTIVE',
  `expires_at` TIMESTAMP NULL,
  `paid_transaction_id` VARCHAR(255) NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_payment_link_reference` (`link_reference`),
  UNIQUE KEY `uk_payment_link_token` (`token_hash`),
  KEY `idx_payment_link_merchant` (`merchant_id`, `link_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `hosted_checkout_attempts` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `payment_link_id` BIGINT UNSIGNED NOT NULL,
  `payer_account` VARCHAR(255) NOT NULL,
  `channel_code` VARCHAR(100) NULL,
  `attempt_status` VARCHAR(40) NOT NULL DEFAULT 'PENDING',
  `transaction_id` VARCHAR(255) NULL,
  `message` TEXT NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_checkout_attempt_link` (`payment_link_id`, `attempt_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `admin_mfa_totp` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `admin_id` BIGINT UNSIGNED NOT NULL,
  `secret_value` TEXT NOT NULL,
  `enabled_flag` VARCHAR(3) NOT NULL DEFAULT 'NO',
  `verified_at` TIMESTAMP NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_admin_mfa_totp_admin` (`admin_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `provider_tokens` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `provider_code` VARCHAR(100) NOT NULL,
  `segment` VARCHAR(100) NOT NULL,
  `environment` VARCHAR(40) NOT NULL DEFAULT 'SANDBOX',
  `token_value` TEXT NOT NULL,
  `expires_at` TIMESTAMP NULL,
  `lease_owner` VARCHAR(255) NULL,
  `lease_expires_at` TIMESTAMP NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_provider_token` (`provider_code`, `segment`, `environment`),
  KEY `idx_provider_token_expiry` (`expires_at`),
  KEY `idx_provider_token_lease` (`lease_expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `settlement_schedules` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `merchant_id` BIGINT UNSIGNED NOT NULL,
  `provider_code` VARCHAR(100) NOT NULL,
  `channel_code` VARCHAR(100) NOT NULL,
  `currency` VARCHAR(10) NOT NULL,
  `schedule_status` VARCHAR(40) NOT NULL DEFAULT 'ACTIVE',
  `minimum_retained_balance` DECIMAL(19,4) NOT NULL DEFAULT 0,
  `sweep_hour` INT NOT NULL DEFAULT 2,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_settlement_schedule` (`merchant_id`, `provider_code`, `channel_code`, `currency`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `settlement_sweep_runs` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `schedule_id` BIGINT UNSIGNED NOT NULL,
  `run_reference` VARCHAR(255) NOT NULL,
  `run_status` VARCHAR(40) NOT NULL DEFAULT 'OPENED',
  `sweep_amount` DECIMAL(19,4) NOT NULL DEFAULT 0,
  `message` TEXT NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_settlement_sweep_run` (`run_reference`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT IGNORE INTO `risk_rules`
  (`rule_key`, `rule_type`, `scope_type`, `scope_reference`, `currency`, `threshold_amount`, `decision`, `enabled`)
VALUES
  ('global-single-payout-review-ugx', 'SINGLE_TRANSACTION_CAP', 'GLOBAL', '*', 'UGX', 5000000.0000, 'REVIEW', 'YES'),
  ('global-daily-merchant-block-ugx', 'MERCHANT_DAILY_CAP', 'GLOBAL', '*', 'UGX', 50000000.0000, 'BLOCK', 'YES');
