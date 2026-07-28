-- Production controls that replace the remaining audit-roadmap placeholders
-- with executable data models. These tables are additive and do not disturb
-- legacy rows while v2 traffic migrates onto ledger, risk, FX, and intent rails.

CREATE TABLE IF NOT EXISTS `ledger_account_balances` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `account_id` BIGINT UNSIGNED NOT NULL,
  `account_code` VARCHAR(150) NOT NULL,
  `currency` VARCHAR(10) NOT NULL,
  `debit_total` DECIMAL(19,4) NOT NULL DEFAULT 0,
  `credit_total` DECIMAL(19,4) NOT NULL DEFAULT 0,
  `net_balance` DECIMAL(19,4) NOT NULL DEFAULT 0,
  `last_ledger_entry_id` BIGINT UNSIGNED NULL,
  `refreshed_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ledger_account_balance` (`account_id`, `currency`),
  KEY `idx_ledger_account_balance_code` (`account_code`, `currency`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `compliance_watchlist_entries` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `list_name` VARCHAR(120) NOT NULL,
  `entry_type` VARCHAR(50) NOT NULL,
  `entry_value_hash` VARCHAR(64) NOT NULL,
  `entry_label` VARCHAR(255) NULL,
  `risk_rating` VARCHAR(40) NOT NULL DEFAULT 'HIGH',
  `action` VARCHAR(40) NOT NULL DEFAULT 'REVIEW',
  `source_reference` VARCHAR(255) NULL,
  `active_flag` VARCHAR(3) NOT NULL DEFAULT 'YES',
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_watchlist_entry` (`list_name`, `entry_type`, `entry_value_hash`),
  KEY `idx_watchlist_lookup` (`entry_type`, `entry_value_hash`, `active_flag`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `compliance_screening_hits` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `merchant_id` BIGINT UNSIGNED NOT NULL,
  `request_reference` VARCHAR(255) NOT NULL,
  `direction` VARCHAR(30) NOT NULL,
  `screened_type` VARCHAR(50) NOT NULL,
  `screened_value_hash` VARCHAR(64) NOT NULL,
  `watchlist_entry_id` BIGINT UNSIGNED NOT NULL,
  `decision` VARCHAR(40) NOT NULL,
  `hit_summary` TEXT NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_screening_hit_reference` (`merchant_id`, `request_reference`, `created_at`),
  KEY `idx_screening_hit_watchlist` (`watchlist_entry_id`, `decision`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `beneficial_owners` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `merchant_id` BIGINT UNSIGNED NOT NULL,
  `full_name` VARCHAR(255) NOT NULL,
  `id_type` VARCHAR(80) NULL,
  `id_value_hash` VARCHAR(64) NULL,
  `ownership_percent` DECIMAL(7,4) NULL,
  `screening_status` VARCHAR(40) NOT NULL DEFAULT 'PENDING',
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_beneficial_owner_merchant` (`merchant_id`, `screening_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `merchant_kyc_documents` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `merchant_id` BIGINT UNSIGNED NOT NULL,
  `document_type` VARCHAR(100) NOT NULL,
  `storage_ref` TEXT NOT NULL,
  `document_hash` VARCHAR(64) NULL,
  `verification_status` VARCHAR(40) NOT NULL DEFAULT 'PENDING',
  `verified_by` VARCHAR(255) NULL,
  `verified_at` TIMESTAMP NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_kyc_document_merchant` (`merchant_id`, `verification_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `fx_rates` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `source_currency` VARCHAR(10) NOT NULL,
  `target_currency` VARCHAR(10) NOT NULL,
  `rate` DECIMAL(24,10) NOT NULL,
  `provider_code` VARCHAR(100) NOT NULL DEFAULT 'INTERNAL',
  `rate_status` VARCHAR(40) NOT NULL DEFAULT 'ACTIVE',
  `valid_from` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `valid_until` TIMESTAMP NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_fx_rate_lookup` (`source_currency`, `target_currency`, `rate_status`, `valid_from`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `fx_quotes` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `quote_reference` VARCHAR(255) NOT NULL,
  `merchant_id` BIGINT UNSIGNED NOT NULL,
  `source_currency` VARCHAR(10) NOT NULL,
  `target_currency` VARCHAR(10) NOT NULL,
  `source_amount` DECIMAL(19,4) NOT NULL,
  `target_amount` DECIMAL(19,4) NOT NULL,
  `rate` DECIMAL(24,10) NOT NULL,
  `quote_status` VARCHAR(40) NOT NULL DEFAULT 'ACTIVE',
  `expires_at` TIMESTAMP NOT NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_fx_quote_reference` (`quote_reference`),
  KEY `idx_fx_quote_merchant` (`merchant_id`, `quote_status`, `expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `treasury_positions` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `currency` VARCHAR(10) NOT NULL,
  `available_balance` DECIMAL(19,4) NOT NULL DEFAULT 0,
  `reserved_balance` DECIMAL(19,4) NOT NULL DEFAULT 0,
  `position_status` VARCHAR(40) NOT NULL DEFAULT 'ACTIVE',
  `updated_by` VARCHAR(255) NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_treasury_position_currency` (`currency`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `cross_border_corridors` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `source_country` VARCHAR(10) NOT NULL,
  `target_country` VARCHAR(10) NOT NULL,
  `source_currency` VARCHAR(10) NOT NULL,
  `target_currency` VARCHAR(10) NOT NULL,
  `provider_code` VARCHAR(100) NOT NULL,
  `corridor_status` VARCHAR(40) NOT NULL DEFAULT 'ACTIVE',
  `daily_limit` DECIMAL(19,4) NULL,
  `single_transfer_limit` DECIMAL(19,4) NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cross_border_corridor` (`source_country`, `target_country`, `source_currency`, `target_currency`, `provider_code`),
  KEY `idx_cross_border_corridor_lookup` (`source_country`, `target_country`, `corridor_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `payment_intents` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `intent_reference` VARCHAR(255) NOT NULL,
  `merchant_id` BIGINT UNSIGNED NOT NULL,
  `amount` DECIMAL(19,4) NOT NULL,
  `currency` VARCHAR(10) NOT NULL,
  `country` VARCHAR(10) NOT NULL,
  `intent_status` VARCHAR(40) NOT NULL DEFAULT 'CREATED',
  `description` TEXT NULL,
  `expires_at` TIMESTAMP NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_payment_intent_reference` (`intent_reference`),
  KEY `idx_payment_intent_merchant` (`merchant_id`, `intent_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `transfer_intents` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `intent_reference` VARCHAR(255) NOT NULL,
  `merchant_id` BIGINT UNSIGNED NOT NULL,
  `quote_reference` VARCHAR(255) NULL,
  `source_country` VARCHAR(10) NOT NULL,
  `target_country` VARCHAR(10) NOT NULL,
  `source_currency` VARCHAR(10) NOT NULL,
  `target_currency` VARCHAR(10) NOT NULL,
  `source_amount` DECIMAL(19,4) NOT NULL,
  `target_amount` DECIMAL(19,4) NULL,
  `beneficiary_account` VARCHAR(255) NOT NULL,
  `beneficiary_name` VARCHAR(255) NULL,
  `intent_status` VARCHAR(40) NOT NULL DEFAULT 'CREATED',
  `risk_decision` VARCHAR(40) NOT NULL DEFAULT 'PENDING',
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_transfer_intent_reference` (`intent_reference`),
  KEY `idx_transfer_intent_merchant` (`merchant_id`, `intent_status`),
  KEY `idx_transfer_intent_quote` (`quote_reference`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `merchant_mfa_totp` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `merchant_admin_id` BIGINT UNSIGNED NOT NULL,
  `secret_value` TEXT NOT NULL,
  `enabled_flag` VARCHAR(3) NOT NULL DEFAULT 'NO',
  `verified_at` TIMESTAMP NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_merchant_mfa_totp_admin` (`merchant_admin_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT IGNORE INTO `cross_border_corridors`
  (`source_country`, `target_country`, `source_currency`, `target_currency`, `provider_code`, `single_transfer_limit`, `daily_limit`)
VALUES
  ('UG', 'KE', 'UGX', 'KES', 'AIRTEL', 2000000.0000, 10000000.0000),
  ('UG', 'TZ', 'UGX', 'TZS', 'AIRTEL', 2000000.0000, 10000000.0000),
  ('UG', 'RW', 'UGX', 'RWF', 'AIRTEL', 2000000.0000, 10000000.0000);
