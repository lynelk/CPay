-- Audit F3: archival policy for merchant_transactions_log. This is an append-heavy, unbounded
-- money-movement ledger table with no archival path today, so it only ever grows. Rather than
-- deleting rows outright (risking the merchant_statement.transactions_log_id ON DELETE SET NULL
-- cascade silently severing old statement rows from their transaction reference data), this adds:
--   1. an `archived_on` marker column on the live table (rows are marked, not removed, once copied)
--   2. a disconnected archive table holding a full copy of archived rows
--   3. a status+created_on index to make the archival sweep's WHERE clause efficient
-- Physical deletion of archived rows is a separate, explicitly-gated operation left to a future,
-- deliberate decision - see TransactionLogArchivalService.

DROP PROCEDURE IF EXISTS add_column_if_missing_v20;

DELIMITER $$
CREATE PROCEDURE add_column_if_missing_v20(
    IN p_table_name VARCHAR(128),
    IN p_column_name VARCHAR(128),
    IN p_column_definition VARCHAR(512)
)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = p_table_name
          AND column_name = p_column_name
    ) THEN
        SET @ddl = CONCAT('ALTER TABLE `', p_table_name, '` ADD COLUMN `', p_column_name, '` ', p_column_definition);
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;

DROP PROCEDURE IF EXISTS add_index_if_missing_v20;

DELIMITER $$
CREATE PROCEDURE add_index_if_missing_v20(
    IN p_table_name VARCHAR(128),
    IN p_index_name VARCHAR(128),
    IN p_index_definition VARCHAR(512)
)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = p_table_name
          AND index_name = p_index_name
    ) THEN
        SET @ddl = CONCAT('ALTER TABLE `', p_table_name, '` ADD ', p_index_definition);
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;

CALL add_column_if_missing_v20('merchant_transactions_log', 'archived_on', 'TIMESTAMP NULL DEFAULT NULL');
CALL add_index_if_missing_v20('merchant_transactions_log', 'idx_mtl_status_created_on', 'INDEX `idx_mtl_status_created_on` (`status`, `created_on`)');
CALL add_index_if_missing_v20('merchant_transactions_log', 'idx_mtl_archived_on', 'INDEX `idx_mtl_archived_on` (`archived_on`)');

DROP PROCEDURE IF EXISTS add_index_if_missing_v20;
DROP PROCEDURE IF EXISTS add_column_if_missing_v20;

CREATE TABLE IF NOT EXISTS `merchant_transactions_log_archive` (
  `id` bigint unsigned NOT NULL,
  `merchant_id` bigint unsigned DEFAULT NULL,
  `gateway_id` varchar(255) NOT NULL DEFAULT '',
  `original_amount` double NOT NULL DEFAULT '0',
  `charges` double NOT NULL DEFAULT '0',
  `status` enum('SUCCESSFUL','FAILED','PENDING','UNDETERMINED') DEFAULT 'PENDING',
  `charging_method` varchar(255) DEFAULT NULL,
  `tx_request_trace` blob,
  `tx_update_trace` blob,
  `tx_description` text,
  `tx_merchant_description` text,
  `tx_unique_id` varchar(255) NOT NULL,
  `tx_gateway_ref` varchar(255) NOT NULL,
  `tx_merchant_ref` varchar(255) DEFAULT NULL,
  `created_on` datetime NOT NULL,
  `updated_on` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `payer_number` varchar(255) NOT NULL DEFAULT '',
  `tx_type` enum('PAYOUT','PAYIN') NOT NULL DEFAULT 'PAYIN',
  `merchant_batch_transactions_log_id` bigint unsigned DEFAULT NULL,
  `tx_cost` double NOT NULL DEFAULT '0',
  `callback_url` varchar(255) NOT NULL DEFAULT '',
  `callback_trace` text,
  `name` varchar(255) NOT NULL DEFAULT '',
  `account_type` varchar(255) NOT NULL DEFAULT 'phone',
  `beneficiary_id` bigint unsigned DEFAULT NULL,
  `originate_ip` varchar(255) NOT NULL DEFAULT '',
  `resolved_by` varchar(255) NOT NULL DEFAULT '',
  `safaricom_request_reference` varchar(255) NOT NULL DEFAULT '',
  `callback_status` varchar(50) NOT NULL DEFAULT 'PENDING',
  `callback_retry_count` int NOT NULL DEFAULT 0,
  `callback_next_retry` datetime NULL,
  `currency` varchar(25) NOT NULL DEFAULT 'UGX',
  `network_reference` varchar(255) NULL,
  `archived_on` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_mtla_merchant_created_on` (`merchant_id`, `created_on`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;
