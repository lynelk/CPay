-- Vending Platform Phase 4.2: vendor-neutral domain upgrades.
--
-- Adds vendor_code columns to key vending tables so every connector, device, rental, command,
-- event, and exception is explicitly scoped by vending vendor. Existing ChargeNow rows are
-- backfilled. Creates the vendor-neutral exception and reconciliation tables required by
-- the rental-status reconciliation worker.

-- ── Vendor code columns ──────────────────────────────────────────────────────────────────────
ALTER TABLE `vending_connector_configs`
  ADD COLUMN `vendor_code` VARCHAR(60) NOT NULL DEFAULT 'CHARGENOW' AFTER `connector_code`,
  ADD KEY `idx_vending_connector_vendor` (`merchant_id`, `vendor_code`);

ALTER TABLE `vending_devices`
  ADD COLUMN `vendor_code` VARCHAR(60) NOT NULL DEFAULT 'CHARGENOW' AFTER `connector_code`,
  ADD KEY `idx_vending_device_vendor` (`merchant_id`, `vendor_code`);

ALTER TABLE `vending_rentals`
  ADD COLUMN `vendor_code` VARCHAR(60) NOT NULL DEFAULT 'CHARGENOW' AFTER `status`,
  ADD KEY `idx_vending_rental_vendor` (`merchant_id`, `vendor_code`);

ALTER TABLE `vending_commands`
  ADD COLUMN `vendor_code` VARCHAR(60) NOT NULL DEFAULT 'CHARGENOW' AFTER `connector_code`,
  ADD KEY `idx_vending_command_vendor` (`merchant_id`, `vendor_code`);

ALTER TABLE `vending_events`
  ADD COLUMN `vendor_code` VARCHAR(60) NOT NULL DEFAULT 'CHARGENOW' AFTER `entity_reference`,
  ADD KEY `idx_vending_event_vendor` (`merchant_id`, `vendor_code`);

-- ── Vending exceptions ───────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS `vending_exceptions` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `merchant_id` BIGINT UNSIGNED NOT NULL,
  `vendor_code` VARCHAR(60) NOT NULL DEFAULT 'CHARGENOW',
  `connector_code` VARCHAR(80) NULL,
  `rental_id` BIGINT UNSIGNED NULL,
  `device_id` BIGINT UNSIGNED NULL,
  `exception_code` VARCHAR(80) NOT NULL,
  `severity` VARCHAR(16) NOT NULL DEFAULT 'INFO',
  `description` TEXT NULL,
  `first_seen_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `last_seen_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `occurrence_count` INT UNSIGNED NOT NULL DEFAULT 1,
  `status` VARCHAR(24) NOT NULL DEFAULT 'OPEN',
  `resolution` TEXT NULL,
  `resolved_by` VARCHAR(255) NULL,
  `resolved_at` TIMESTAMP NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_vending_exception_tenant_status` (`merchant_id`, `status`, `created_at`),
  KEY `idx_vending_exception_tenant_code` (`merchant_id`, `exception_code`),
  KEY `idx_vending_exception_rental` (`merchant_id`, `rental_id`),
  KEY `idx_vending_exception_device` (`merchant_id`, `device_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── Vending reconciliations ──────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS `vending_reconciliations` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `merchant_id` BIGINT UNSIGNED NOT NULL,
  `vendor_code` VARCHAR(60) NOT NULL DEFAULT 'CHARGENOW',
  `connector_code` VARCHAR(80) NULL,
  `rental_id` BIGINT UNSIGNED NOT NULL,
  `provider_reference` VARCHAR(180) NULL,
  `reconciliation_type` VARCHAR(60) NOT NULL,
  `cpay_status_before` VARCHAR(32) NULL,
  `provider_status` VARCHAR(60) NULL,
  `cpay_status_after` VARCHAR(32) NULL,
  `result` VARCHAR(24) NOT NULL,
  `mismatch_code` VARCHAR(80) NULL,
  `attempt` INT UNSIGNED NOT NULL DEFAULT 1,
  `started_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `completed_at` TIMESTAMP NULL,
  `error` TEXT NULL,
  `provider_snapshot` JSON NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_vending_recon_tenant_rental` (`merchant_id`, `rental_id`),
  KEY `idx_vending_recon_tenant_status` (`merchant_id`, `result`, `created_at`),
  KEY `idx_vending_recon_next` (`merchant_id`, `result`, `next_reconciliation_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Add a nullable next_reconciliation_at to support durable worker scheduling
ALTER TABLE `vending_reconciliations`
  ADD COLUMN `next_reconciliation_at` TIMESTAMP NULL AFTER `provider_snapshot`;
