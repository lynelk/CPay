-- Session-oriented USSD communication support.
-- MSISDN is stored only as a SHA-256 hash to keep raw customer identifiers out of the session log.
CREATE TABLE IF NOT EXISTS `communication_ussd_sessions` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `session_id` VARCHAR(120) NOT NULL,
  `merchant_id` BIGINT UNSIGNED NOT NULL,
  `msisdn_hash` CHAR(64) NOT NULL,
  `last_input` VARCHAR(500) NULL,
  `response_text` VARCHAR(1000) NOT NULL,
  `session_status` VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_comm_ussd_session` (`session_id`),
  KEY `idx_comm_ussd_merchant_status` (`merchant_id`, `session_status`, `updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
