-- Vending Platform Phase 2: contract-provisioned manufacturer integration, authenticated callbacks,
-- and public QR/hosted-rental session state.
--
-- The ChargeNow/Bajie public material and supplied operations manual describe station release,
-- heartbeat, QR/H5 rental and return behaviour, but do not publish a wire-level API contract.
-- Consequently this schema stores the actual manufacturer contract per merchant (URL, auth mode,
-- request template and response/callback field mappings) instead of hard-coding guessed endpoints.

CREATE TABLE IF NOT EXISTS `vending_connector_configs` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `merchant_id` BIGINT UNSIGNED NOT NULL,
  `connector_code` VARCHAR(80) NOT NULL,
  `command_base_url` VARCHAR(500) NOT NULL,
  `release_path` VARCHAR(300) NOT NULL,
  `release_request_template` TEXT NOT NULL,
  `auth_mode` VARCHAR(32) NOT NULL DEFAULT 'BEARER',
  `auth_header_name` VARCHAR(120) NULL,
  `auth_value_ciphertext` TEXT NULL,
  `auth_secret_ciphertext` TEXT NULL,
  `response_success_field` VARCHAR(160) NULL,
  `response_success_value` VARCHAR(160) NULL,
  `response_reference_field` VARCHAR(160) NULL,
  `response_message_field` VARCHAR(160) NULL,
  `callback_secret_ciphertext` TEXT NOT NULL,
  `callback_signature_mode` VARCHAR(40) NOT NULL DEFAULT 'HMAC_SHA256_TS_NONCE_BODY',
  `callback_signature_header` VARCHAR(120) NOT NULL DEFAULT 'X-CPay-Vending-Signature',
  `callback_timestamp_header` VARCHAR(120) NOT NULL DEFAULT 'X-CPay-Vending-Timestamp',
  `callback_nonce_header` VARCHAR(120) NOT NULL DEFAULT 'X-CPay-Vending-Nonce',
  `callback_event_type_field` VARCHAR(160) NOT NULL DEFAULT 'eventType',
  `callback_event_id_field` VARCHAR(160) NOT NULL DEFAULT 'eventId',
  `callback_device_field` VARCHAR(160) NOT NULL DEFAULT 'deviceId',
  `callback_rental_field` VARCHAR(160) NULL,
  `callback_asset_field` VARCHAR(160) NULL,
  `callback_available_count_field` VARCHAR(160) NULL,
  `active_flag` VARCHAR(3) NOT NULL DEFAULT 'NO',
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_vending_connector_tenant_code` (`merchant_id`, `connector_code`),
  KEY `idx_vending_connector_active` (`merchant_id`, `active_flag`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `vending_callback_nonces` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `merchant_id` BIGINT UNSIGNED NOT NULL,
  `connector_code` VARCHAR(80) NOT NULL,
  `nonce_value` VARCHAR(160) NOT NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_vending_callback_nonce` (`merchant_id`, `connector_code`, `nonce_value`),
  KEY `idx_vending_callback_nonce_time` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `vending_device_callbacks` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `merchant_id` BIGINT UNSIGNED NOT NULL,
  `connector_code` VARCHAR(80) NOT NULL,
  `external_event_id` VARCHAR(220) NOT NULL,
  `external_device_id` VARCHAR(220) NULL,
  `event_type` VARCHAR(100) NOT NULL,
  `body_sha256` CHAR(64) NOT NULL,
  `signature_status` VARCHAR(24) NOT NULL,
  `processing_status` VARCHAR(24) NOT NULL DEFAULT 'RECEIVED',
  `raw_body` LONGTEXT NOT NULL,
  `error_message` VARCHAR(500) NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `processed_at` TIMESTAMP NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_vending_device_callback_event` (`merchant_id`, `connector_code`, `external_event_id`),
  KEY `idx_vending_device_callback_status` (`merchant_id`, `processing_status`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `vending_hosted_sessions` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `merchant_id` BIGINT UNSIGNED NOT NULL,
  `device_id` BIGINT UNSIGNED NOT NULL,
  `rental_reference` VARCHAR(120) NOT NULL,
  `session_token_hash` CHAR(64) NOT NULL,
  `status` VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
  `expires_at` TIMESTAMP NOT NULL,
  `last_seen_at` TIMESTAMP NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_vending_hosted_session_token` (`session_token_hash`),
  UNIQUE KEY `uk_vending_hosted_session_rental` (`merchant_id`, `rental_reference`),
  KEY `idx_vending_hosted_session_expiry` (`expires_at`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Existing V50 devices already carry a nullable public_token. Phase 2 makes it operational and
-- indexed by the existing unique key; tokens are generated/rotated in application code with a
-- cryptographically strong random source rather than predictable SQL UUIDs.