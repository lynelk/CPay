DROP PROCEDURE IF EXISTS ensure_merchant_tx_currency_column;
DELIMITER //
CREATE PROCEDURE ensure_merchant_tx_currency_column()
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE table_schema = DATABASE()
          AND table_name = 'merchant_transactions_log'
          AND column_name = 'currency'
    ) THEN
        ALTER TABLE `merchant_transactions_log` ADD COLUMN `currency` VARCHAR(25) NOT NULL DEFAULT 'UGX';
    ELSEIF EXISTS (
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE table_schema = DATABASE()
          AND table_name = 'merchant_transactions_log'
          AND column_name = 'currency'
          AND data_type = 'varchar'
          AND character_maximum_length < 25
    ) THEN
        ALTER TABLE `merchant_transactions_log` MODIFY COLUMN `currency` VARCHAR(25) NOT NULL DEFAULT 'UGX';
    END IF;
END//
DELIMITER ;

CALL ensure_merchant_tx_currency_column();
DROP PROCEDURE IF EXISTS ensure_merchant_tx_currency_column;
