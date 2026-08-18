-- CPay vending vendor-neutral production foundation (Phase 2 of the end-to-end
-- vending directive).
--
-- Introduces the vendor model (CHARGENOW is the first production vendor, kept
-- separate from the payment provider), OEM provider-reference correlation,
-- callback verification evidence, historical pricing snapshots, reconciliation
-- records, operational exceptions and settlement reporting.
--
-- Everything is additive and backward compatible: existing vending tables gain
-- vendor_code with a safe default so current ChargeNow records remain correct.
-- New tables use CREATE TABLE IF NOT EXISTS; uniqueness is introduced carefully
-- so provider_rental_reference NULLs (no OEM reference yet) never collide.

-- ---------------------------------------------------------------------------
-- 1. Vendor context on existing vending tables.
-- ---------------------------------------------------------------------------

ALTER TABLE `vending_connector_configs`
  ADD COLUMN `vendor_code` VARCHAR(20) NOT NULL DEFAULT 'CHARGENOW' AFTER `merchant_id`,
  ADD KEY `idx_vending_connector_tenant_vendor` (`merchant_id`, `vendor_code`, `active_flag`);

ALTER TABLE `vending_devices`
  ADD COLUMN `vendor_code` VARCHAR(20) NOT NULL DEFAULT 'CHARGENOW' AFTER `merchant_id`,
  ADD COLUMN `connector_id` BIGINT UNSIGNED NULL AFTER `connector_code`,
  ADD KEY `idx_vending_device_tenant_vendor` (`merchant_id`, `vendor_code`, `status`);

ALTER TABLE `vending_rentals`
  ADD COLUMN `vendor_code` VARCHAR(20) NOT NULL DEFAULT 'CHARGENOW' AFTER `merchant_id`,
  ADD COLUMN `connector_id` BIGINT UNSIGNED NULL AFTER `device_id`,
  ADD COLUMN `provider_rental_reference` VARCHAR(180) NULL AFTER `refund_transaction_id`,
  ADD COLUMN `provider_battery_id` VARCHAR(80) NULL AFTER `provider_rental_reference`,
  ADD COLUMN `provider_borrow_slot` VARCHAR(40) NULL AFTER `provider_battery_id`,
  ADD COLUMN `provider_status` VARCHAR(60) NULL AFTER `provider_borrow_slot`,
  ADD COLUMN `provider_snapshot` JSON NULL AFTER `provider_status`,
  ADD COLUMN `last_provider_verified_at` TIMESTAMP NULL AFTER `provider_snapshot`,
  ADD UNIQUE KEY `uk_vending_rental_provider_ref` (`merchant_id`, `vendor_code`, `provider_rental_reference`),
  ADD KEY `idx_vending_rental_tenant_vendor_status` (`merchant_id`, `vendor_code`, `status`, `created_at`);

ALTER TABLE `vending_commands`
  ADD COLUMN `vendor_code` VARCHAR(20) NOT NULL DEFAULT 'CHARGENOW' AFTER `merchant_id`,
  ADD KEY `idx_vending_command_tenant_vendor` (`merchant_id`, `vendor_code`, `status`);

ALTER TABLE `vending_events`
  ADD COLUMN `vendor_code` VARCHAR(20) NOT NULL DEFAULT 'CHARGENOW' AFTER `merchant_id`,
  ADD KEY `idx_vending_event_tenant_vendor` (`merchant_id`, `vendor_code`, `event_type`);

-- Rental callbacks and cabinet events are persisted through the same signed
-- callback table; callback_type keeps the two payload families distinguishable
-- so the separate rental/event endpoints never mutate each other's state.
ALTER TABLE `vending_device_callbacks`
  ADD COLUMN `callback_type` VARCHAR(20) NOT NULL DEFAULT 'DEVICE' AFTER `event_type`;

-- ---------------------------------------------------------------------------
-- 2. OEM provider reference correlation.
-- ---------------------------------------------------------------------------
-- Maps the vendor's own rental reference (e.g. ChargeNow data.tradeNo) back to a
-- CPay rental. tradeNo is not assumed to be globally unique across vendors, so
-- uniqueness is scoped per merchant + vendor. Rows may exist before the rental
-- is fully correlated (callback arrives first), hence nullable rental linkage.
CREATE TABLE IF NOT EXISTS `vending_rental_provider_refs` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `merchant_id` BIGINT UNSIGNED NOT NULL,
  `vendor_code` VARCHAR(20) NOT NULL,
  `connector_code` VARCHAR(80) NOT NULL,
  `provider_rental_reference` VARCHAR(180) NOT NULL,
  `rental_id` BIGINT UNSIGNED NULL,
  `rental_reference` VARCHAR(120) NULL,
  `source` VARCHAR(24) NOT NULL DEFAULT 'COMMAND',
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_vending_provider_ref` (`merchant_id`, `vendor_code`, `provider_rental_reference`),
  KEY `idx_vending_provider_ref_rental` (`merchant_id`, `rental_id`),
  KEY `idx_vending_provider_ref_source` (`merchant_id`, `vendor_code`, `source`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- 3. Callback verification evidence.
-- ---------------------------------------------------------------------------
-- Every consequential callback (release confirmation, return evidence, refund
-- trigger) must survive as an auditable verification record. VERIFY_BY_PROVIDER_QUERY
-- vendors land here as UNVERIFIED/VERIFIED/REJECTED; raw payloads stay in
-- vending_device_callbacks.
CREATE TABLE IF NOT EXISTS `vending_callback_verifications` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `merchant_id` BIGINT UNSIGNED NOT NULL,
  `vendor_code` VARCHAR(20) NOT NULL DEFAULT 'CHARGENOW',
  `connector_code` VARCHAR(80) NOT NULL,
  `callback_id` BIGINT UNSIGNED NULL,
  `callback_type` VARCHAR(20) NOT NULL DEFAULT 'RENTAL',
  `payload_hash` CHAR(64) NOT NULL,
  `verification_mode` VARCHAR(40) NOT NULL,
  `verification_status` VARCHAR(20) NOT NULL DEFAULT 'UNVERIFIED',
  `verification_result` VARCHAR(500) NULL,
  `verification_attempted_at` TIMESTAMP NULL,
  `verified_at` TIMESTAMP NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_vending_callback_verification_hash` (`merchant_id`, `payload_hash`),
  KEY `idx_vending_callback_verification_status` (`merchant_id`, `verification_status`, `created_at`),
  KEY `idx_vending_callback_verification_callback` (`merchant_id`, `callback_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- 4. Historical pricing snapshots.
-- ---------------------------------------------------------------------------
-- Freezes the commercial basis of a rental at start time: the vendor's provider
-- price strategy and the applied CPay policy, plus the comparison verdict.
CREATE TABLE IF NOT EXISTS `vending_price_snapshots` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `merchant_id` BIGINT UNSIGNED NOT NULL,
  `vendor_code` VARCHAR(20) NOT NULL DEFAULT 'CHARGENOW',
  `connector_code` VARCHAR(80) NOT NULL,
  `device_id` BIGINT UNSIGNED NULL,
  `rental_id` BIGINT UNSIGNED NULL,
  `currency` VARCHAR(8) NULL,
  `provider_price_json` JSON NULL,
  `provider_price_hash` CHAR(64) NULL,
  `cpay_policy_id` BIGINT UNSIGNED NULL,
  `pricing_match_status` VARCHAR(20) NOT NULL DEFAULT 'NOT_CHECKED',
  `comparison_json` JSON NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_vending_price_snapshot_rental` (`merchant_id`, `rental_id`),
  KEY `idx_vending_price_snapshot_device` (`merchant_id`, `device_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- 5. Rental reconciliation worker evidence.
-- ---------------------------------------------------------------------------
-- One row per reconciliation pass over an eligible rental. Durable so repeated
-- worker checks never lose their history and mismatch codes can surface as
-- operational exceptions.
CREATE TABLE IF NOT EXISTS `vending_reconciliations` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `merchant_id` BIGINT UNSIGNED NOT NULL,
  `vendor_code` VARCHAR(20) NOT NULL DEFAULT 'CHARGENOW',
  `connector_code` VARCHAR(80) NOT NULL,
  `rental_id` BIGINT UNSIGNED NULL,
  `provider_rental_reference` VARCHAR(180) NULL,
  `reconciliation_type` VARCHAR(40) NOT NULL DEFAULT 'RENTAL',
  `cpay_status_before` VARCHAR(40) NULL,
  `provider_status` VARCHAR(60) NULL,
  `cpay_status_after` VARCHAR(40) NULL,
  `result` VARCHAR(24) NOT NULL,
  `mismatch_code` VARCHAR(40) NULL,
  `attempt` INT NOT NULL DEFAULT 1,
  `started_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `completed_at` TIMESTAMP NULL,
  `error_message` VARCHAR(500) NULL,
  `provider_snapshot` JSON NULL,
  PRIMARY KEY (`id`),
  KEY `idx_vending_reconciliation_status` (`merchant_id`, `result`, `created_at`),
  KEY `idx_vending_reconciliation_rental` (`merchant_id`, `rental_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- 6. Operational exceptions.
-- ---------------------------------------------------------------------------
-- The mandatory 12 exception codes (VEND_PAID_NO_PROVIDER_REFERENCE,
-- VEND_RELEASE_PENDING_TIMEOUT, VEND_DEVICE_OFFLINE, VEND_UNMATCHED_RETURN,
-- VEND_PRICING_MISMATCH, VEND_RECONCILIATION_MISMATCH,
-- VEND_PROVIDER_CALLBACK_UNVERIFIED, VEND_PROVIDER_UNAVAILABLE,
-- VEND_REFUND_FAILED, VEND_SETTLEMENT_MISMATCH,
-- VEND_DUPLICATE_PROVIDER_REFERENCE, VEND_ASSET_FAULT) are created by the
-- application layer. Repeated detection of the same unresolved condition
-- re-opens the same row (ON DUPLICATE KEY UPDATE occurrence_count+1) rather
-- than spawning uncontrolled duplicates.
CREATE TABLE IF NOT EXISTS `vending_exceptions` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `merchant_id` BIGINT UNSIGNED NOT NULL,
  `vendor_code` VARCHAR(20) NOT NULL DEFAULT 'CHARGENOW',
  `connector_code` VARCHAR(80) NULL,
  `code` VARCHAR(60) NOT NULL,
  `severity` VARCHAR(16) NOT NULL,
  `rental_id` BIGINT UNSIGNED NULL,
  `rental_reference` VARCHAR(120) NULL,
  `device_id` BIGINT UNSIGNED NULL,
  `status` VARCHAR(20) NOT NULL DEFAULT 'OPEN',
  `detail_json` JSON NULL,
  `resolution` VARCHAR(500) NULL,
  `resolved_by` VARCHAR(120) NULL,
  `resolved_at` TIMESTAMP NULL,
  `first_seen_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `last_seen_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `occurrence_count` INT NOT NULL DEFAULT 1,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_vending_exception_open` (`merchant_id`, `vendor_code`, `code`, `rental_id`, `device_id`, `status`),
  KEY `idx_vending_exception_status` (`merchant_id`, `severity`, `status`, `last_seen_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- 7. Vending settlement reporting.
-- ---------------------------------------------------------------------------
-- Rental-level financial settlement lines. CPay remains the system of record;
-- this table explains each rental's deposit, usage, surcharge, refund, fees and
-- merchant net without mutating the core ledger.
CREATE TABLE IF NOT EXISTS `vending_settlements` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `merchant_id` BIGINT UNSIGNED NOT NULL,
  `vendor_code` VARCHAR(20) NOT NULL DEFAULT 'CHARGENOW',
  `connector_code` VARCHAR(80) NULL,
  `rental_id` BIGINT UNSIGNED NULL,
  `rental_reference` VARCHAR(120) NULL,
  `currency` VARCHAR(8) NOT NULL,
  `deposit_amount` DECIMAL(20,4) NOT NULL DEFAULT 0,
  `usage_amount` DECIMAL(20,4) NOT NULL DEFAULT 0,
  `surcharge_amount` DECIMAL(20,4) NOT NULL DEFAULT 0,
  `refund_amount` DECIMAL(20,4) NOT NULL DEFAULT 0,
  `gross_amount` DECIMAL(20,4) NOT NULL DEFAULT 0,
  `cpay_fee` DECIMAL(20,4) NOT NULL DEFAULT 0,
  `merchant_net` DECIMAL(20,4) NOT NULL DEFAULT 0,
  `payment_provider` VARCHAR(40) NULL,
  `payment_channel` VARCHAR(40) NULL,
  `status` VARCHAR(24) NOT NULL DEFAULT 'PENDING',
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_vending_settlement_rental` (`merchant_id`, `rental_id`),
  KEY `idx_vending_settlement_status` (`merchant_id`, `vendor_code`, `status`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
