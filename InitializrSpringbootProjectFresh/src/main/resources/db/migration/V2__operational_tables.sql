-- Operational schema used by the modernized payment, callback, reconciliation,
-- channel credential, compliance, and API security services.

DROP PROCEDURE IF EXISTS add_column_if_missing;
DELIMITER //
CREATE PROCEDURE add_column_if_missing(
    IN table_name_value VARCHAR(128),
    IN column_name_value VARCHAR(128),
    IN column_definition_value TEXT
)
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE table_schema = DATABASE()
          AND table_name = table_name_value
          AND column_name = column_name_value
    ) THEN
        SET @ddl = CONCAT('ALTER TABLE `', table_name_value, '` ADD COLUMN `', column_name_value, '` ', column_definition_value);
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END//
DELIMITER ;

DROP PROCEDURE IF EXISTS add_index_if_missing;
DELIMITER //
CREATE PROCEDURE add_index_if_missing(
    IN table_name_value VARCHAR(128),
    IN index_name_value VARCHAR(128),
    IN index_definition_value TEXT
)
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.STATISTICS
        WHERE table_schema = DATABASE()
          AND table_name = table_name_value
          AND index_name = index_name_value
    ) THEN
        SET @ddl = CONCAT('ALTER TABLE `', table_name_value, '` ADD ', index_definition_value);
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END//
DELIMITER ;

CALL add_column_if_missing('merchant_transactions_log', 'callback_status', 'VARCHAR(50) NOT NULL DEFAULT ''PENDING''');
CALL add_column_if_missing('merchant_transactions_log', 'callback_retry_count', 'INT NOT NULL DEFAULT 0');
CALL add_column_if_missing('merchant_transactions_log', 'callback_next_retry', 'DATETIME NULL');
CALL add_column_if_missing('merchant_transactions_log', 'currency', 'VARCHAR(10) NOT NULL DEFAULT ''UGX''');
CALL add_column_if_missing('merchant_transactions_log', 'network_reference', 'VARCHAR(255) NULL');
CALL add_column_if_missing('merchants', 'hmac_secret', 'TEXT NULL');
CALL add_column_if_missing('merchant_statement', 'currency', 'VARCHAR(10) NOT NULL DEFAULT ''UGX''');

CALL add_index_if_missing('merchant_transactions_log', 'idx_mtl_callback_status', 'INDEX `idx_mtl_callback_status` (`callback_status`)');
CALL add_index_if_missing('merchant_transactions_log', 'idx_mtl_merchant_status', 'INDEX `idx_mtl_merchant_status` (`merchant_id`, `status`)');
CALL add_index_if_missing('merchant_transactions_log', 'idx_mtl_network_ref', 'INDEX `idx_mtl_network_ref` (`network_reference`)');
CALL add_index_if_missing('merchant_transactions_log', 'idx_mtl_merchant_ref', 'INDEX `idx_mtl_merchant_ref` (`tx_merchant_ref`)');
CALL add_index_if_missing('merchant_statement', 'idx_ms_merchant_id', 'INDEX `idx_ms_merchant_id` (`merchant_id`)');

CREATE TABLE IF NOT EXISTS `cpay_idempotency_keys` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `merchant_number` VARCHAR(100) NOT NULL,
  `idempotency_key` VARCHAR(255) NOT NULL,
  `request_hash` VARCHAR(64) NOT NULL,
  `response_body` JSON NOT NULL,
  `status` VARCHAR(50) DEFAULT NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cpay_idempotency` (`merchant_number`, `idempotency_key`),
  KEY `idx_cpay_idempotency_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `cpay_request_nonces` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `merchant_number` VARCHAR(100) NOT NULL,
  `nonce_value` VARCHAR(255) NOT NULL,
  `expires_at` TIMESTAMP NOT NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cpay_request_nonce` (`merchant_number`, `nonce_value`),
  KEY `idx_cpay_request_nonce_expires` (`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `api_rate_limits` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `rate_key` VARCHAR(255) NOT NULL,
  `window_start` TIMESTAMP NOT NULL,
  `request_count` INT NOT NULL DEFAULT 1,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_api_rate_limit_window` (`rate_key`, `window_start`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `callback_tasks` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `merchant_id` BIGINT UNSIGNED NOT NULL,
  `transaction_id` VARCHAR(100) NOT NULL,
  `reference_value` VARCHAR(255) NOT NULL,
  `target_url` TEXT NOT NULL,
  `request_body` JSON NULL,
  `task_status` VARCHAR(50) NOT NULL DEFAULT 'PENDING',
  `attempt_count` INT NOT NULL DEFAULT 0,
  `attempt_limit` INT NOT NULL DEFAULT 5,
  `next_run_at` TIMESTAMP NULL,
  `last_run_at` TIMESTAMP NULL,
  `message` TEXT NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_callback_task_ref` (`merchant_id`, `transaction_id`, `reference_value`),
  KEY `idx_callback_due` (`task_status`, `next_run_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `callback_task_claims` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `task_id` BIGINT UNSIGNED NOT NULL,
  `worker_name` VARCHAR(255) NOT NULL,
  `claim_status` VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_callback_task_active_claim` (`task_id`, `claim_status`),
  KEY `idx_callback_claim_worker` (`worker_name`, `claim_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `callback_delivery_signatures` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `callback_task_id` BIGINT UNSIGNED NOT NULL,
  `signature_algorithm` VARCHAR(50) NOT NULL,
  `signature_value` TEXT NOT NULL,
  `nonce` VARCHAR(255) NOT NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_callback_signature_task` (`callback_task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `merchant_callback_secrets` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `merchant_id` BIGINT UNSIGNED NOT NULL,
  `secret_alias` VARCHAR(100) NOT NULL DEFAULT 'default',
  `secret_value` TEXT NOT NULL,
  `active_flag` VARCHAR(3) NOT NULL DEFAULT 'YES',
  `rotated_at` TIMESTAMP NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_callback_secret_merchant` (`merchant_id`, `active_flag`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `merchant_channel_credentials` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `merchant_id` BIGINT UNSIGNED NOT NULL,
  `channel_code` VARCHAR(100) NOT NULL,
  `environment` VARCHAR(40) NOT NULL DEFAULT 'SANDBOX',
  `display_name` VARCHAR(255) NOT NULL,
  `credential_payload` TEXT NOT NULL,
  `credential_mask` JSON NULL,
  `status` VARCHAR(50) NOT NULL DEFAULT 'CONFIGURED',
  `last_test_status` VARCHAR(50) NULL,
  `last_test_message` TEXT NULL,
  `last_tested_at` TIMESTAMP NULL,
  `submitted_for_approval_at` TIMESTAMP NULL,
  `approved_by` VARCHAR(255) NULL,
  `approved_at` TIMESTAMP NULL,
  `created_by` VARCHAR(255) NULL,
  `updated_by` VARCHAR(255) NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_merchant_channel_credentials` (`merchant_id`, `channel_code`, `environment`),
  KEY `idx_merchant_channel_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `merchant_channel_audit_events` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `merchant_id` BIGINT UNSIGNED NOT NULL,
  `channel_code` VARCHAR(100) NOT NULL,
  `environment` VARCHAR(40) NOT NULL DEFAULT 'SANDBOX',
  `action` VARCHAR(100) NOT NULL,
  `actor` VARCHAR(255) NULL,
  `message` TEXT NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_channel_audit_merchant` (`merchant_id`, `channel_code`, `environment`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `merchant_channel_balances` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `merchant_id` BIGINT UNSIGNED NOT NULL,
  `channel_code` VARCHAR(100) NOT NULL,
  `gateway_id` VARCHAR(100) NULL,
  `currency` VARCHAR(10) NOT NULL,
  `available_balance` DECIMAL(19,4) NOT NULL DEFAULT 0,
  `ledger_balance` DECIMAL(19,4) NOT NULL DEFAULT 0,
  `pending_balance` DECIMAL(19,4) NOT NULL DEFAULT 0,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_merchant_channel_balance` (`merchant_id`, `channel_code`, `currency`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `balance_ledger_events` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `merchant_id` BIGINT UNSIGNED NOT NULL,
  `channel_code` VARCHAR(100) NOT NULL,
  `gateway_id` VARCHAR(100) NULL,
  `currency` VARCHAR(10) NOT NULL,
  `event_type` VARCHAR(100) NOT NULL,
  `source_type` VARCHAR(100) NOT NULL,
  `source_reference` VARCHAR(255) NOT NULL,
  `amount_delta` DECIMAL(19,4) NOT NULL DEFAULT 0,
  `pending_delta` DECIMAL(19,4) NOT NULL DEFAULT 0,
  `ledger_delta` DECIMAL(19,4) NOT NULL DEFAULT 0,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_balance_ledger_source` (`source_type`, `source_reference`, `event_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `normalized_balance_backfill_runs` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `started_by` VARCHAR(255) NOT NULL,
  `run_status` VARCHAR(50) NOT NULL DEFAULT 'RUNNING',
  `merchants_processed` INT NOT NULL DEFAULT 0,
  `balances_written` INT NOT NULL DEFAULT 0,
  `message` TEXT NULL,
  `started_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `finished_at` TIMESTAMP NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `reconciliation_imports` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `provider_code` VARCHAR(100) NOT NULL,
  `channel_code` VARCHAR(100) NOT NULL,
  `source_file_name` VARCHAR(255) NOT NULL,
  `imported_by` VARCHAR(255) NULL,
  `total_records` INT NOT NULL DEFAULT 0,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `reconciliation_records` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `import_id` BIGINT UNSIGNED NULL,
  `provider_code` VARCHAR(100) NOT NULL,
  `channel_code` VARCHAR(100) NOT NULL,
  `provider_reference` VARCHAR(255) NULL,
  `merchant_reference` VARCHAR(255) NULL,
  `transaction_id` VARCHAR(255) NULL,
  `amount` DECIMAL(19,4) NOT NULL DEFAULT 0,
  `currency` VARCHAR(10) NOT NULL,
  `match_status` VARCHAR(50) NOT NULL DEFAULT 'UNMATCHED',
  `match_reason` VARCHAR(255) NULL,
  `exception_category` VARCHAR(100) NULL,
  `settlement_batch` VARCHAR(255) NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_recon_records_match` (`match_status`, `currency`),
  KEY `idx_recon_records_reference` (`merchant_reference`, `provider_reference`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `reconciliation_reviews` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `reconciliation_record_id` BIGINT UNSIGNED NOT NULL,
  `transaction_id` VARCHAR(255) NULL,
  `review_type` VARCHAR(100) NOT NULL,
  `amount` DECIMAL(19,4) NOT NULL DEFAULT 0,
  `currency` VARCHAR(10) NOT NULL,
  `reason` TEXT NULL,
  `requested_by` VARCHAR(255) NULL,
  `review_status` VARCHAR(50) NOT NULL DEFAULT 'PENDING',
  `reviewed_by` VARCHAR(255) NULL,
  `reviewed_at` TIMESTAMP NULL,
  `review_note` TEXT NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_recon_reviews_status` (`review_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `reconciliation_daily_closes` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `close_date` DATE NOT NULL,
  `currency` VARCHAR(10) NOT NULL,
  `close_status` VARCHAR(50) NOT NULL DEFAULT 'OPEN',
  `matched_count` INT NOT NULL DEFAULT 0,
  `unmatched_count` INT NOT NULL DEFAULT 0,
  `exception_count` INT NOT NULL DEFAULT 0,
  `variance_amount` DECIMAL(19,4) NOT NULL DEFAULT 0,
  `closed_by` VARCHAR(255) NULL,
  `closed_at` TIMESTAMP NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_daily_close_currency` (`close_date`, `currency`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `reconciliation_settlement_batches` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `batch_reference` VARCHAR(255) NOT NULL,
  `provider_code` VARCHAR(100) NOT NULL,
  `channel_code` VARCHAR(100) NOT NULL,
  `currency` VARCHAR(10) NOT NULL,
  `expected_amount` DECIMAL(19,4) NOT NULL DEFAULT 0,
  `opened_by` VARCHAR(255) NULL,
  `batch_status` VARCHAR(50) NOT NULL DEFAULT 'OPEN',
  `closed_by` VARCHAR(255) NULL,
  `closed_at` TIMESTAMP NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_recon_settlement_batch` (`batch_reference`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `provider_statement_validation_runs` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `provider_code` VARCHAR(100) NOT NULL,
  `channel_code` VARCHAR(100) NOT NULL,
  `file_name` VARCHAR(255) NOT NULL,
  `validation_status` VARCHAR(50) NOT NULL,
  `total_rows` INT NOT NULL DEFAULT 0,
  `valid_rows` INT NOT NULL DEFAULT 0,
  `invalid_rows` INT NOT NULL DEFAULT 0,
  `duplicate_rows` INT NOT NULL DEFAULT 0,
  `message` TEXT NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `provider_sandbox_runs` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `provider_code` VARCHAR(100) NOT NULL,
  `channel_code` VARCHAR(100) NOT NULL,
  `scenario_name` VARCHAR(255) NOT NULL,
  `run_status` VARCHAR(50) NOT NULL,
  `request_summary` TEXT NULL,
  `response_summary` TEXT NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `provider_endpoint_runs` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `channel_code` VARCHAR(100) NOT NULL,
  `operation_name` VARCHAR(100) NOT NULL,
  `reference_value` VARCHAR(255) NULL,
  `endpoint_url` TEXT NULL,
  `http_status` INT NULL,
  `request_hash` VARCHAR(64) NULL,
  `response_summary` TEXT NULL,
  `run_status` VARCHAR(50) NOT NULL,
  `message` TEXT NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `operations_alerts` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `alert_type` VARCHAR(100) NOT NULL,
  `alert_status` VARCHAR(50) NOT NULL DEFAULT 'OPEN',
  `severity` VARCHAR(50) NOT NULL DEFAULT 'MEDIUM',
  `reference_value` VARCHAR(255) NULL,
  `message` TEXT NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `resolved_by` VARCHAR(255) NULL,
  `resolved_at` TIMESTAMP NULL,
  PRIMARY KEY (`id`),
  KEY `idx_operations_alert_status` (`alert_status`, `severity`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `operating_control_events` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `event_type` VARCHAR(100) NOT NULL,
  `severity` VARCHAR(50) NOT NULL DEFAULT 'MEDIUM',
  `event_status` VARCHAR(50) NOT NULL DEFAULT 'OPEN',
  `reference_value` VARCHAR(255) NULL,
  `message` TEXT NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `reviewed_by` VARCHAR(255) NULL,
  `reviewed_at` TIMESTAMP NULL,
  PRIMARY KEY (`id`),
  KEY `idx_control_event_status` (`event_status`, `severity`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `admin_permissions` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `role_name` VARCHAR(100) NOT NULL,
  `permission_code` VARCHAR(150) NOT NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_admin_permission` (`role_name`, `permission_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `admin_audit_events` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `actor` VARCHAR(255) NULL,
  `permission_code` VARCHAR(150) NULL,
  `action_name` VARCHAR(150) NOT NULL,
  `resource_reference` VARCHAR(255) NULL,
  `request_summary` TEXT NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_admin_audit_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP PROCEDURE IF EXISTS add_index_if_missing;
DROP PROCEDURE IF EXISTS add_column_if_missing;
