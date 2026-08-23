-- Sandbox-only financial workflow state for money-moving v2 routes that use legacy payout internals.
-- These tables prevent sandbox requests from invoking production refund/batch payout execution.

CREATE TABLE IF NOT EXISTS `sandbox_refunds` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `merchant_id` BIGINT UNSIGNED NOT NULL,
  `refund_reference` VARCHAR(255) NOT NULL,
  `original_reference` VARCHAR(255) NOT NULL,
  `requested_amount` DECIMAL(20,4) NULL,
  `refund_status` VARCHAR(32) NOT NULL,
  `reason` VARCHAR(1000) NULL,
  `failure_message` VARCHAR(1000) NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sandbox_refund_merchant_ref` (`merchant_id`, `refund_reference`),
  KEY `idx_sandbox_refund_original` (`merchant_id`, `original_reference`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `sandbox_batch_payout_runs` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `merchant_id` BIGINT UNSIGNED NOT NULL,
  `batch_id` BIGINT NOT NULL,
  `operation` VARCHAR(40) NOT NULL,
  `result_status` VARCHAR(32) NOT NULL,
  `retried_count` INT NOT NULL DEFAULT 0,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_sandbox_batch_merchant` (`merchant_id`, `batch_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
