-- P0 section 3: payout risk-trigger rules (provider switch + beneficiary amount change).
--
-- Gap closed: payout_controls already covered per-transaction/daily/monthly limits,
-- beneficiary velocity, and first-payout-to-beneficiary approval (V34), but two risk
-- triggers were absent: a payout silently routing a known beneficiary through a
-- different provider channel, and a single payout materially exceeding the
-- beneficiary's historical payout profile.
--
-- This migration adds two opt-in controls:
--   1. provider_switch_approval_flag ('YES'/'NO', default 'NO') - when YES, a payout
--      whose channel differs from the beneficiary's most recent payout channel is
--      parked as PROVIDER_SWITCH for maker-checker approval.
--   2. beneficiary_amount_factor (DECIMAL(6,2) NULL) - when set, a payout to a
--      beneficiary that exceeds (factor x that beneficiary's historical max payout)
--      is parked as BENEFICIARY_AMOUNT_CHANGE for maker-checker approval.
--
-- Both are fail-open: a NULL flag / factor (or a merchant/corridor without a control
-- row) preserves the exact pre-migration behavior - immediate execution.

DROP PROCEDURE IF EXISTS add_column_if_missing_v72;
DELIMITER //
CREATE PROCEDURE add_column_if_missing_v72(
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

CALL add_column_if_missing_v72('payout_controls', 'provider_switch_approval_flag', "VARCHAR(3) NOT NULL DEFAULT 'NO'");
CALL add_column_if_missing_v72('payout_controls', 'beneficiary_amount_factor', 'DECIMAL(6,2) NULL');

DROP PROCEDURE IF EXISTS add_column_if_missing_v72;
