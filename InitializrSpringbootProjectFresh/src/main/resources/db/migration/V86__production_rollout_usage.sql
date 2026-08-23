-- Authoritative per-merchant production execution attempts for progressive rollout quotas.
-- Each money-moving command reserves one usage slot before execution. Retries of the same
-- operation/reference are idempotent and do not consume another slot.

CREATE TABLE IF NOT EXISTS `merchant_production_usage` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `merchant_id` BIGINT UNSIGNED NOT NULL,
  `operation` VARCHAR(40) NOT NULL,
  `request_reference` VARCHAR(255) NOT NULL,
  `usage_date` DATE NOT NULL DEFAULT (CURRENT_DATE),
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_production_usage_command` (`merchant_id`, `operation`, `request_reference`),
  KEY `idx_production_usage_daily` (`merchant_id`, `usage_date`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
