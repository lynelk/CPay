-- Audit N7: merchant team roles (owner/finance/developer/viewer). Today every merchant user
-- under an account has identical (full) access with no role distinction. This adds a `role`
-- column to `merchant_admins` (the merchant-side team-user table backing
-- net.citotech.cito.Model.MerchantUser) so merchant-side authorization can enforce a real
-- capability matrix - see net.citotech.cito.merchant.MerchantRole.
--
-- Existing rows default to OWNER (full access), NOT the more restrictive VIEWER, so that no
-- currently-active merchant user silently loses access when this ships. New rows also default
-- to OWNER unless application code explicitly assigns a lesser role when creating the user.

DROP PROCEDURE IF EXISTS add_column_if_missing_v23;

DELIMITER $$
CREATE PROCEDURE add_column_if_missing_v23(
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

CALL add_column_if_missing_v23('merchant_admins', 'role', "ENUM('OWNER','FINANCE','DEVELOPER','VIEWER') NOT NULL DEFAULT 'OWNER'");

DROP PROCEDURE IF EXISTS add_column_if_missing_v23;
