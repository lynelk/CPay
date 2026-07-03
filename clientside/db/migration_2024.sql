-- ============================================================
-- CPay DB Migration 2024
-- Run these against an existing database to bring it up to date.
-- Each ALTER is guarded by a matching db_changes INSERT so it
-- won't be re-applied if you run this script twice.
-- ============================================================

-- 11: Callback idempotency columns
ALTER TABLE `merchant_transactions_log`
    ADD COLUMN IF NOT EXISTS `callback_status`      VARCHAR(50)  NOT NULL DEFAULT 'PENDING',
    ADD COLUMN IF NOT EXISTS `callback_retry_count` INT          NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS `callback_next_retry`  DATETIME     NULL;

INSERT IGNORE INTO `db_changes` (`query_id`, `sql_text`, `roll_back`) VALUES (
    '2024-01-01-01',
    'ALTER TABLE merchant_transactions_log ADD COLUMN callback_status VARCHAR(50) NOT NULL DEFAULT ''PENDING'', ADD COLUMN callback_retry_count INT NOT NULL DEFAULT 0, ADD COLUMN callback_next_retry DATETIME NULL',
    'ALTER TABLE merchant_transactions_log DROP COLUMN callback_status, DROP COLUMN callback_retry_count, DROP COLUMN callback_next_retry'
);

-- 12: Safaricom request reference (needed for STK push correlation)
ALTER TABLE `merchant_transactions_log`
    ADD COLUMN IF NOT EXISTS `safaricom_request_reference` VARCHAR(255) NOT NULL DEFAULT '';

INSERT IGNORE INTO `db_changes` (`query_id`, `sql_text`, `roll_back`) VALUES (
    '2024-01-01-02',
    'ALTER TABLE merchant_transactions_log ADD COLUMN safaricom_request_reference VARCHAR(255) NOT NULL DEFAULT ''''',
    'ALTER TABLE merchant_transactions_log DROP COLUMN safaricom_request_reference'
);

-- 13: Currency on transaction log
ALTER TABLE `merchant_transactions_log`
    ADD COLUMN IF NOT EXISTS `currency` VARCHAR(10) NOT NULL DEFAULT '';

INSERT IGNORE INTO `db_changes` (`query_id`, `sql_text`, `roll_back`) VALUES (
    '2024-01-01-03',
    'ALTER TABLE merchant_transactions_log ADD COLUMN currency VARCHAR(10) NOT NULL DEFAULT ''''',
    'ALTER TABLE merchant_transactions_log DROP COLUMN currency'
);

-- 14: Currency on statement
ALTER TABLE `merchant_statement`
    ADD COLUMN IF NOT EXISTS `currency` VARCHAR(10) NOT NULL DEFAULT '';

INSERT IGNORE INTO `db_changes` (`query_id`, `sql_text`, `roll_back`) VALUES (
    '2024-01-01-04',
    'ALTER TABLE merchant_statement ADD COLUMN currency VARCHAR(10) NOT NULL DEFAULT ''''',
    'ALTER TABLE merchant_statement DROP COLUMN currency'
);

-- 15: HMAC secret on merchants (alternative to RSA for callback signing)
ALTER TABLE `merchants`
    ADD COLUMN IF NOT EXISTS `hmac_secret` TEXT NULL;

INSERT IGNORE INTO `db_changes` (`query_id`, `sql_text`, `roll_back`) VALUES (
    '2024-01-01-05',
    'ALTER TABLE merchants ADD COLUMN hmac_secret TEXT NULL',
    'ALTER TABLE merchants DROP COLUMN hmac_secret'
);

-- 16: Safaricom balance column on merchant_statement
ALTER TABLE `merchant_statement`
    ADD COLUMN IF NOT EXISTS `safaricom_balance` DOUBLE NOT NULL DEFAULT 0;

INSERT IGNORE INTO `db_changes` (`query_id`, `sql_text`, `roll_back`) VALUES (
    '2024-01-01-06',
    'ALTER TABLE merchant_statement ADD COLUMN safaricom_balance DOUBLE NOT NULL DEFAULT 0',
    'ALTER TABLE merchant_statement DROP COLUMN safaricom_balance'
);

-- 17: Indexes on hot query paths
-- callback_status is scanned by CallbackRetryScheduler every minute
CREATE INDEX IF NOT EXISTS `idx_mtl_callback_status`
    ON `merchant_transactions_log` (`callback_status`);

-- merchant_id + status is the most common filter on dashboard / admin queries
CREATE INDEX IF NOT EXISTS `idx_mtl_merchant_status`
    ON `merchant_transactions_log` (`merchant_id`, `status`);

-- network_reference is looked up on every gateway callback
CREATE INDEX IF NOT EXISTS `idx_mtl_network_ref`
    ON `merchant_transactions_log` (`network_reference`);

-- tx_merchant_ref is the primary correlation key for refunds / idempotency checks
CREATE INDEX IF NOT EXISTS `idx_mtl_merchant_ref`
    ON `merchant_transactions_log` (`tx_merchant_ref`);

-- merchant_id on statement is the main read path for balance lookups
CREATE INDEX IF NOT EXISTS `idx_ms_merchant_id`
    ON `merchant_statement` (`merchant_id`);

INSERT IGNORE INTO `db_changes` (`query_id`, `sql_text`, `roll_back`) VALUES (
    '2024-01-01-07',
    'CREATE INDEX idx_mtl_callback_status ON merchant_transactions_log (callback_status); CREATE INDEX idx_mtl_merchant_status ON merchant_transactions_log (merchant_id, status); CREATE INDEX idx_mtl_network_ref ON merchant_transactions_log (network_reference); CREATE INDEX idx_mtl_merchant_ref ON merchant_transactions_log (tx_merchant_ref); CREATE INDEX idx_ms_merchant_id ON merchant_statement (merchant_id)',
    'DROP INDEX idx_mtl_callback_status ON merchant_transactions_log; DROP INDEX idx_mtl_merchant_status ON merchant_transactions_log; DROP INDEX idx_mtl_network_ref ON merchant_transactions_log; DROP INDEX idx_mtl_merchant_ref ON merchant_transactions_log; DROP INDEX idx_ms_merchant_id ON merchant_statement'
);

-- Recommended: change DOUBLE monetary columns to DECIMAL(20,4) for exactness
-- (Run manually after verifying application behaviour with BigDecimal types)
-- ALTER TABLE merchant_transactions_log
--     MODIFY COLUMN original_amount DECIMAL(20,4) NOT NULL DEFAULT 0,
--     MODIFY COLUMN charges         DECIMAL(20,4) NOT NULL DEFAULT 0,
--     MODIFY COLUMN tx_cost         DECIMAL(20,4) NOT NULL DEFAULT 0;
-- ALTER TABLE merchant_statement
--     MODIFY COLUMN amount          DECIMAL(20,4) NOT NULL DEFAULT 0;
