-- EFRIS e-receipt outbox, Bank-of-Uganda regulator report runs, the PII data-class inventory,
-- and the new risk-rule types (payer velocity, restricted corridor) that complete the
-- compliance-roadmap limit set (single-tx + daily tier caps already exist from V7/V32).

CREATE TABLE IF NOT EXISTS `efris_receipts` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `receipt_reference` VARCHAR(150) NOT NULL,
  `merchant_id` BIGINT UNSIGNED NOT NULL,
  `merchant_number` VARCHAR(100) NOT NULL,
  `transaction_reference` VARCHAR(255) NOT NULL,
  `merchant_transaction_ref` VARCHAR(255) NULL,
  `payer_msisdn_hash` VARCHAR(64) NOT NULL,
  `amount` DECIMAL(19,4) NOT NULL,
  `currency` VARCHAR(10) NOT NULL DEFAULT 'UGX',
  `receipt_status` VARCHAR(40) NOT NULL DEFAULT 'PENDING',
  `retry_count` INT NOT NULL DEFAULT 0,
  `next_retry_at` TIMESTAMP NULL,
  `efris_response_json` TEXT NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_efris_receipt_reference` (`receipt_reference`),
  UNIQUE KEY `uk_efris_transaction_reference` (`transaction_reference`),
  KEY `idx_efris_receipt_queue` (`receipt_status`, `next_retry_at`),
  KEY `idx_efris_receipt_merchant` (`merchant_id`, `receipt_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `regulator_reports` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `report_type` VARCHAR(80) NOT NULL,
  `report_date` DATE NOT NULL,
  `report_status` VARCHAR(40) NOT NULL DEFAULT 'GENERATED',
  `row_count` INT NOT NULL DEFAULT 0,
  `total_amount` DECIMAL(19,4) NOT NULL DEFAULT 0,
  `report_json` LONGTEXT NULL,
  `file_ref` VARCHAR(255) NULL,
  `error_message` TEXT NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_regulator_report_run` (`report_type`, `report_date`),
  KEY `idx_regulator_report_type` (`report_type`, `report_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `pii_inventory_entries` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `data_class` VARCHAR(120) NOT NULL,
  `storage_location` VARCHAR(255) NOT NULL,
  `retention_days` INT NULL,
  `masking_status` VARCHAR(40) NOT NULL DEFAULT 'HASHED_AT_REST',
  `notes` TEXT NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pii_inventory_class` (`data_class`, `storage_location`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Payer-velocity default: scale through risk_rules like existing caps. A payer exceeding 20
-- processed transactions per hour is REVIEWED; the merchant/global decision in the rule row
-- decides whether that is REVIEW or BLOCK.
INSERT IGNORE INTO `risk_rules`
  (`rule_key`, `rule_type`, `scope_type`, `scope_reference`, `currency`, `threshold_count`, `decision`, `enabled`)
VALUES
  ('global-payer-velocity-review', 'PAYER_VELOCITY', 'GLOBAL', '*', NULL, 20, 'REVIEW', 'YES'),
  ('tier-enhanced-payer-velocity-review', 'PAYER_VELOCITY', 'TIER', 'tier:ENHANCED', NULL, 100, 'REVIEW', 'YES');

-- PII inventory: metadata only, no personal data is stored in this table.
INSERT IGNORE INTO `pii_inventory_entries`
  (`data_class`, `storage_location`, `retention_days`, `masking_status`, `notes`)
VALUES
  ('MERCHANT_BUSINESS_NAME', 'merchants.name', NULL, 'MASKED_IN_UI', 'Merchant business name; masked in non-diagnostic UI surfaces'),
  ('MERCHANT_EMAIL', 'merchants/merchant_users email columns', NULL, 'MASKED_IN_LOGS', 'Emails used for auth; masked in logs'),
  ('PAYER_MSISDN', 'merchant_transactions_log.payer_number', NULL, 'MASKED_IN_LOGS', 'Payer numbers masked in logs/non-diagnostic UI; hashed at rest in efris_receipts'),
  ('PAYEE_MSISDN', 'merchant_transactions_log.payer_number (payouts)', NULL, 'MASKED_IN_LOGS', 'Payout payee is stored in payer_number; same masking rules apply'),
  ('CALLBACK_EVENT', 'callback_delivery_signatures/callback_tasks', 30, 'MASKED_IN_LOGS', 'Callback payloads may echo PII; masked in operational logs'),
  ('STATEMENT_EXPORT', 'merchant_statement export pipeline', NULL, 'AUDITED_READ', 'Statement reads write merchants_audit_trail'),
  ('WATCHLIST_SCREENING', 'compliance_watchlist_entries.entry_value_hash', NULL, 'HASHED_AT_REST', 'Only SHA-256 hashes stored; raw value never persisted'),
  ('BLOCKLIST_VALUE', 'compliance_blocklist.value_hash', NULL, 'HASHED_AT_REST', 'Only SHA-256 hashes stored'),
  ('KYC_DOCUMENT', 'merchant_kyc_documents.storage_ref', NULL, 'ENCRYPTED_STORAGE', 'Documents referenced by storage ref + content hash only');
