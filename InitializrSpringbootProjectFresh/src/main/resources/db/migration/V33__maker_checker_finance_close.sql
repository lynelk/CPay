-- Maker-checker separation for finance daily close and settlement batch close.
--
-- Previously reconciliation_daily_closes.close and
-- reconciliation_settlement_batches.close were single-actor writes (the row
-- flipped straight to CLOSED with one actor field). For a regulated payment
-- environment "who approved what" must be provable: both flows now move through
-- a PENDING_APPROVAL state where the requester (maker) and the approver
-- (checker) must be different actors.
--
-- Columns added using the add_column_if_missing helper (same pattern as
-- V2__operational_tables.sql) so the migration is safe to run against any
-- existing schema state.

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

-- reconciliation_daily_closes: maker/checker lifecycle on daily close.
CALL add_column_if_missing('reconciliation_daily_closes', 'requested_by', 'VARCHAR(255) NULL');
CALL add_column_if_missing('reconciliation_daily_closes', 'requested_at', 'TIMESTAMP NULL');
CALL add_column_if_missing('reconciliation_daily_closes', 'approved_by', 'VARCHAR(255) NULL');
CALL add_column_if_missing('reconciliation_daily_closes', 'approved_at', 'TIMESTAMP NULL');
CALL add_column_if_missing('reconciliation_daily_closes', 'approved_variance_amount', 'DECIMAL(19,4) NULL');
CALL add_column_if_missing('reconciliation_daily_closes', 'rejection_reason', 'TEXT NULL');

-- reconciliation_settlement_batches: maker/checker lifecycle on batch close.
CALL add_column_if_missing('reconciliation_settlement_batches', 'close_requested_by', 'VARCHAR(255) NULL');
CALL add_column_if_missing('reconciliation_settlement_batches', 'close_requested_at', 'TIMESTAMP NULL');
CALL add_column_if_missing('reconciliation_settlement_batches', 'close_approved_by', 'VARCHAR(255) NULL');
CALL add_column_if_missing('reconciliation_settlement_batches', 'close_approved_at', 'TIMESTAMP NULL');
CALL add_column_if_missing('reconciliation_settlement_batches', 'close_rejection_reason', 'TEXT NULL');

DROP PROCEDURE IF EXISTS add_column_if_missing;
