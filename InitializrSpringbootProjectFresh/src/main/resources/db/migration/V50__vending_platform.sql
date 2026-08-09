-- CPay multi-tenant vending platform foundation.
--
-- The vending domain is deliberately generic: a power-bank cabinet is one device/asset profile,
-- not a one-off subsystem. Merchant id is the tenant boundary on every owned table. The normal
-- CPay transaction/ledger tables remain the financial system of record; vending tables store
-- operational state, pricing snapshots, escrow attribution and device workflow evidence.

CREATE TABLE IF NOT EXISTS `vending_locations` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `merchant_id` BIGINT UNSIGNED NOT NULL,
  `location_code` VARCHAR(80) NOT NULL,
  `name` VARCHAR(160) NOT NULL,
  `address` VARCHAR(500) NULL,
  `latitude` DECIMAL(10,7) NULL,
  `longitude` DECIMAL(10,7) NULL,
  `business_hours_json` JSON NULL,
  `status` VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_vending_location_tenant_code` (`merchant_id`, `location_code`),
  KEY `idx_vending_location_tenant_status` (`merchant_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `vending_pricing_policies` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `merchant_id` BIGINT UNSIGNED NOT NULL,
  `policy_code` VARCHAR(80) NOT NULL,
  `name` VARCHAR(160) NOT NULL,
  `currency` VARCHAR(8) NOT NULL,
  `deposit_amount` DECIMAL(20,4) NOT NULL DEFAULT 0,
  `free_minutes` INT NOT NULL DEFAULT 0,
  `unit_price` DECIMAL(20,4) NOT NULL,
  `billing_block_minutes` INT NOT NULL DEFAULT 60,
  `minimum_billing_blocks` INT NOT NULL DEFAULT 1,
  `daily_cap_amount` DECIMAL(20,4) NULL,
  `overtime_amount` DECIMAL(20,4) NULL,
  `overtime_days` INT NULL,
  `refund_mode` VARCHAR(24) NOT NULL DEFAULT 'ORIGINAL_ROUTE',
  `active_flag` VARCHAR(3) NOT NULL DEFAULT 'YES',
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_vending_pricing_tenant_code` (`merchant_id`, `policy_code`),
  KEY `idx_vending_pricing_tenant_active` (`merchant_id`, `active_flag`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `vending_devices` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `merchant_id` BIGINT UNSIGNED NOT NULL,
  `location_id` BIGINT UNSIGNED NULL,
  `pricing_policy_id` BIGINT UNSIGNED NULL,
  `device_code` VARCHAR(100) NOT NULL,
  `device_type` VARCHAR(60) NOT NULL,
  `connector_code` VARCHAR(80) NOT NULL DEFAULT 'SIMULATED',
  `external_device_id` VARCHAR(180) NULL,
  `public_token` VARCHAR(96) NULL,
  `status` VARCHAR(24) NOT NULL DEFAULT 'REGISTERED',
  `slot_count` INT NOT NULL DEFAULT 0,
  `available_count` INT NOT NULL DEFAULT 0,
  `heartbeat_at` TIMESTAMP NULL,
  `metadata_json` JSON NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_vending_device_tenant_code` (`merchant_id`, `device_code`),
  UNIQUE KEY `uk_vending_device_public_token` (`public_token`),
  KEY `idx_vending_device_tenant_location` (`merchant_id`, `location_id`),
  KEY `idx_vending_device_tenant_status` (`merchant_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `vending_assets` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `merchant_id` BIGINT UNSIGNED NOT NULL,
  `device_id` BIGINT UNSIGNED NULL,
  `asset_code` VARCHAR(120) NOT NULL,
  `asset_type` VARCHAR(60) NOT NULL,
  `slot_number` VARCHAR(40) NULL,
  `status` VARCHAR(24) NOT NULL DEFAULT 'AVAILABLE',
  `battery_percent` INT NULL,
  `last_seen_at` TIMESTAMP NULL,
  `metadata_json` JSON NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_vending_asset_tenant_code` (`merchant_id`, `asset_code`),
  KEY `idx_vending_asset_tenant_device` (`merchant_id`, `device_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `vending_customer_balances` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `merchant_id` BIGINT UNSIGNED NOT NULL,
  `customer_hash` CHAR(64) NOT NULL,
  `currency` VARCHAR(8) NOT NULL,
  `surcharge_balance` DECIMAL(20,4) NOT NULL DEFAULT 0,
  `blocked_flag` VARCHAR(3) NOT NULL DEFAULT 'NO',
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_vending_customer_balance` (`merchant_id`, `customer_hash`, `currency`),
  KEY `idx_vending_customer_blocked` (`merchant_id`, `blocked_flag`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `vending_rentals` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `merchant_id` BIGINT UNSIGNED NOT NULL,
  `rental_reference` VARCHAR(120) NOT NULL,
  `device_id` BIGINT UNSIGNED NOT NULL,
  `asset_id` BIGINT UNSIGNED NULL,
  `pricing_policy_id` BIGINT UNSIGNED NOT NULL,
  `customer_hash` CHAR(64) NOT NULL,
  `customer_mask` VARCHAR(40) NOT NULL,
  `customer_ciphertext` TEXT NOT NULL,
  `channel_code` VARCHAR(80) NULL,
  `currency` VARCHAR(8) NOT NULL,
  `deposit_amount` DECIMAL(20,4) NOT NULL,
  `surcharge_settled_from_deposit` DECIMAL(20,4) NOT NULL DEFAULT 0,
  `escrow_amount` DECIMAL(20,4) NOT NULL DEFAULT 0,
  `usage_amount` DECIMAL(20,4) NOT NULL DEFAULT 0,
  `refund_amount` DECIMAL(20,4) NOT NULL DEFAULT 0,
  `surcharge_created` DECIMAL(20,4) NOT NULL DEFAULT 0,
  `billed_blocks` INT NOT NULL DEFAULT 0,
  `status` VARCHAR(32) NOT NULL,
  `collect_reference` VARCHAR(160) NOT NULL,
  `collect_transaction_id` VARCHAR(160) NULL,
  `refund_reference` VARCHAR(160) NULL,
  `refund_transaction_id` VARCHAR(160) NULL,
  `started_at` TIMESTAMP NULL,
  `ended_at` TIMESTAMP NULL,
  `billing_suspended_at` TIMESTAMP NULL,
  `billing_suspended_seconds` BIGINT NOT NULL DEFAULT 0,
  `bill_suspended_time_flag` VARCHAR(3) NOT NULL DEFAULT 'NO',
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_vending_rental_tenant_ref` (`merchant_id`, `rental_reference`),
  UNIQUE KEY `uk_vending_rental_collect_ref` (`collect_reference`),
  UNIQUE KEY `uk_vending_rental_refund_ref` (`refund_reference`),
  KEY `idx_vending_rental_tenant_status` (`merchant_id`, `status`, `created_at`),
  KEY `idx_vending_rental_customer` (`merchant_id`, `customer_hash`, `status`),
  KEY `idx_vending_rental_device` (`merchant_id`, `device_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `vending_commands` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `merchant_id` BIGINT UNSIGNED NOT NULL,
  `device_id` BIGINT UNSIGNED NOT NULL,
  `rental_id` BIGINT UNSIGNED NULL,
  `command_reference` VARCHAR(160) NOT NULL,
  `command_type` VARCHAR(40) NOT NULL,
  `connector_code` VARCHAR(80) NOT NULL,
  `status` VARCHAR(24) NOT NULL DEFAULT 'PENDING',
  `provider_reference` VARCHAR(180) NULL,
  `request_json` JSON NULL,
  `response_json` JSON NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `completed_at` TIMESTAMP NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_vending_command_ref` (`command_reference`),
  KEY `idx_vending_command_tenant_status` (`merchant_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `vending_events` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `merchant_id` BIGINT UNSIGNED NOT NULL,
  `event_type` VARCHAR(80) NOT NULL,
  `entity_type` VARCHAR(40) NOT NULL,
  `entity_reference` VARCHAR(180) NOT NULL,
  `actor` VARCHAR(255) NULL,
  `amount` DECIMAL(20,4) NULL,
  `currency` VARCHAR(8) NULL,
  `detail_json` JSON NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_vending_event_tenant_time` (`merchant_id`, `created_at`),
  KEY `idx_vending_event_entity` (`merchant_id`, `entity_type`, `entity_reference`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO `feature_flags` (`flag_key`, `enabled`, `description`)
VALUES ('vending-platform', 0, 'Multi-tenant vending operations and device/rental workflows')
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`);
