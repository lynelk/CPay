-- Vending Platform Phase 4.8: rental safety, pricing comparison, and reconciliation fields.
--
-- Adds provider pricing snapshot, pricing match status, and settlement tracking columns
-- to vending_rentals so the reconciliation worker and settlement dashboard have the data
-- they need.

ALTER TABLE `vending_rentals`
  ADD COLUMN `provider_rental_reference` VARCHAR(180) NULL AFTER `vendor_code`
    COMMENT 'OEM tradeNo / provider-side order id',
  ADD COLUMN `provider_battery_id` VARCHAR(180) NULL AFTER `provider_rental_reference`,
  ADD COLUMN `provider_borrow_slot` VARCHAR(40) NULL AFTER `provider_battery_id`,
  ADD COLUMN `provider_status` VARCHAR(60) NULL AFTER `provider_borrow_slot`
    COMMENT 'Provider-reported rental status',
  ADD COLUMN `provider_snapshot` JSON NULL AFTER `provider_status`
    COMMENT 'Full provider detail snapshot for audit',
  ADD COLUMN `provider_price_snapshot` JSON NULL AFTER `provider_snapshot`
    COMMENT 'ChargeNow priceStrategy at rental start',
  ADD COLUMN `provider_price_hash` CHAR(64) NULL AFTER `provider_price_snapshot`
    COMMENT 'SHA-256 of provider price strategy for mismatch detection',
  ADD COLUMN `pricing_match_status` VARCHAR(24) NOT NULL DEFAULT 'NOT_CHECKED' AFTER `provider_price_hash`
    COMMENT 'MATCH | MISMATCH | UNKNOWN | NOT_CHECKED',
  ADD COLUMN `last_provider_verified_at` TIMESTAMP NULL AFTER `pricing_match_status`,
  ADD COLUMN `settlement_status` VARCHAR(24) NOT NULL DEFAULT 'PENDING' AFTER `last_provider_verified_at`
    COMMENT 'PENDING | SETTLED | MISMATCH | EXEMPT',
  ADD COLUMN `settlement_id` BIGINT UNSIGNED NULL AFTER `settlement_status`,
  ADD COLUMN `settlement_at` TIMESTAMP NULL AFTER `settlement_id`,
  ADD COLUMN `attempt_count` INT UNSIGNED NOT NULL DEFAULT 0 AFTER `settlement_at`
    COMMENT 'Reconciliation retry counter',
  ADD COLUMN `next_reconciliation_at` TIMESTAMP NULL AFTER `attempt_count`
    COMMENT 'Earliest time the reconciliation worker should retry this rental',
  ADD KEY `idx_vending_rental_provider_ref` (`merchant_id`, `vendor_code`, `provider_rental_reference`(64)),
  ADD KEY `idx_vending_rental_settlement` (`merchant_id`, `settlement_status`, `created_at`),
  ADD KEY `idx_vending_rental_pricing_status` (`merchant_id`, `pricing_match_status`, `created_at`),
  ADD KEY `idx_vending_rental_recon_worker` (`status`, `attempt_count`, `next_reconciliation_at`);
