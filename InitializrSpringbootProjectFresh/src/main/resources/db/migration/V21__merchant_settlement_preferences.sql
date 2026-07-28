-- Audit N4: merchant-configurable settlement scheduling. Settlement cadence today is entirely
-- ops-configured via `settlement_schedules` (provider/channel/currency + a fixed sweep hour, see
-- V7__audit_roadmap_production_features.sql) - a merchant has no way to say how often their float
-- should settle. This adds a single self-service preference row per merchant (DAILY vs WEEKLY, an
-- optional preferred day for WEEKLY, and a minimum settlement threshold amount) that
-- SettlementScheduleService consults in addition to - not instead of - the existing ops-configured
-- schedule before opening a settlement batch.
CREATE TABLE IF NOT EXISTS `merchant_settlement_preferences` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `merchant_id` BIGINT UNSIGNED NOT NULL,
  `settlement_frequency` VARCHAR(20) NOT NULL DEFAULT 'DAILY',
  `settlement_day_of_week` VARCHAR(10) NULL,
  `minimum_settlement_amount` DECIMAL(19,4) NOT NULL DEFAULT 0,
  `updated_by` VARCHAR(255) NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_merchant_settlement_preference` (`merchant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
