-- Payout risk controls: configurable limits + maker-checker approval queue.
--
-- payout_controls: per-merchant/channel/currency/country limits. approval_required_flag
-- ('YES'/'NO') opts a merchant/channel/corridor into review for first-payout-to-beneficiary and
-- risk-REVIEW decisions. Defaults are seeded with one disabled template row so the table exists
-- with a documented shape; an operator enables limits by inserting/updating real rows.
--
-- payout_approval_queue: a payout that breaches a limit or triggers a risk REVIEW lands here in
-- PENDING_APPROVAL. A different actor (checker) must approve it; approval re-invokes the normal
-- payout execution path (reservation + provider submission).

CREATE TABLE IF NOT EXISTS `payout_controls` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `merchant_id` BIGINT UNSIGNED NOT NULL,
  `channel_code` VARCHAR(100) NOT NULL,
  `currency` VARCHAR(10) NOT NULL,
  `country` VARCHAR(10) NOT NULL DEFAULT 'UG',
  `daily_amount_limit` DECIMAL(19,4) NULL,
  `monthly_amount_limit` DECIMAL(19,4) NULL,
  `per_transaction_limit` DECIMAL(19,4) NULL,
  `beneficiary_velocity_limit` INT NULL,
  `approval_required_flag` VARCHAR(3) NOT NULL DEFAULT 'NO',
  `enabled_flag` VARCHAR(3) NOT NULL DEFAULT 'YES',
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_payout_control` (`merchant_id`, `channel_code`, `currency`, `country`),
  KEY `idx_payout_control_enabled` (`enabled_flag`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `payout_approval_queue` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `payout_reference` VARCHAR(255) NOT NULL,
  `merchant_id` BIGINT UNSIGNED NOT NULL,
  `merchant_number` VARCHAR(100) NOT NULL,
  `payload_json` TEXT NOT NULL,
  `amount` DECIMAL(19,4) NOT NULL,
  `currency` VARCHAR(10) NOT NULL,
  `channel_code` VARCHAR(100) NULL,
  `country` VARCHAR(10) NULL,
  `beneficiary_reference` VARCHAR(255) NULL,
  `trigger_reason` VARCHAR(80) NOT NULL,
  `queue_status` VARCHAR(40) NOT NULL DEFAULT 'PENDING_APPROVAL',
  `requested_by` VARCHAR(255) NULL,
  `requested_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `approved_by` VARCHAR(255) NULL,
  `approved_at` TIMESTAMP NULL,
  `rejection_reason` TEXT NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_payout_approval_reference` (`payout_reference`),
  KEY `idx_payout_approval_queue` (`queue_status`, `created_at`),
  KEY `idx_payout_approval_merchant` (`merchant_id`, `queue_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
