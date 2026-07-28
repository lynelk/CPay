CREATE TABLE IF NOT EXISTS `merchant_webhook_endpoints` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `merchant_id` BIGINT UNSIGNED NOT NULL,
  `event_type` VARCHAR(120) NOT NULL,
  `endpoint_url` TEXT NOT NULL,
  `secret_hash` VARCHAR(64) NOT NULL,
  `secret_value` TEXT NOT NULL,
  `endpoint_status` VARCHAR(40) NOT NULL DEFAULT 'ACTIVE',
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_merchant_webhook_event` (`merchant_id`, `event_type`),
  KEY `idx_merchant_webhook_status` (`endpoint_status`, `event_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `merchant_webhook_deliveries` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `merchant_id` BIGINT UNSIGNED NOT NULL,
  `endpoint_id` BIGINT UNSIGNED NOT NULL,
  `event_type` VARCHAR(120) NOT NULL,
  `event_reference` VARCHAR(255) NOT NULL,
  `payload_json` TEXT NOT NULL,
  `delivery_status` VARCHAR(40) NOT NULL DEFAULT 'PENDING',
  `attempt_count` INT NOT NULL DEFAULT 0,
  `next_attempt_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `last_http_status` INT NULL,
  `last_response_summary` TEXT NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_merchant_webhook_delivery` (`endpoint_id`, `event_reference`),
  KEY `idx_merchant_webhook_delivery_due` (`delivery_status`, `next_attempt_at`),
  KEY `idx_merchant_webhook_delivery_merchant` (`merchant_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `merchant_notification_preferences` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `merchant_id` BIGINT UNSIGNED NOT NULL,
  `event_type` VARCHAR(120) NOT NULL,
  `email_enabled` VARCHAR(3) NOT NULL DEFAULT 'YES',
  `sms_enabled` VARCHAR(3) NOT NULL DEFAULT 'NO',
  `webhook_enabled` VARCHAR(3) NOT NULL DEFAULT 'YES',
  `updated_by` VARCHAR(255) NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_merchant_notification_pref` (`merchant_id`, `event_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
