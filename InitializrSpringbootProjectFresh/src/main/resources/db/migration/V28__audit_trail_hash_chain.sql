-- Audit F8: append-only, hash-chained audit trail. audit_trail/merchants_audit_trail previously had
-- no tamper-evidence (a row could be silently edited or deleted with no way to detect it) and
-- nothing stopped an UPDATE/DELETE at the DB level despite being an audit log. This adds:
--   1. prev_hash/entry_hash columns on both tables, forming a hash chain (each row's entry_hash
--      covers its own content plus the previous row's entry_hash, so altering or removing any row
--      breaks every hash after it - see net.citotech.cito.audit.AuditChainService).
--   2. Triggers that reject UPDATE/DELETE on both tables outright, enforcing append-only at the
--      database level regardless of which application code path writes to them.
-- Existing rows predate the chain and are left with prev_hash/entry_hash NULL - fabricating hashes
-- for historical data after the fact would be meaningless (no integrity guarantee was ever made for
-- it). The chain starts clean from the first row inserted after this migration, using the literal
-- value 'GENESIS' as the first row's prev_hash.

DROP PROCEDURE IF EXISTS add_column_if_missing_v28;

DELIMITER $$
CREATE PROCEDURE add_column_if_missing_v28(
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

CALL add_column_if_missing_v28('audit_trail', 'prev_hash', 'VARCHAR(64) NULL DEFAULT NULL');
CALL add_column_if_missing_v28('audit_trail', 'entry_hash', 'VARCHAR(64) NULL DEFAULT NULL');
CALL add_column_if_missing_v28('merchants_audit_trail', 'prev_hash', 'VARCHAR(64) NULL DEFAULT NULL');
CALL add_column_if_missing_v28('merchants_audit_trail', 'entry_hash', 'VARCHAR(64) NULL DEFAULT NULL');

DROP PROCEDURE IF EXISTS add_column_if_missing_v28;

DROP TRIGGER IF EXISTS audit_trail_no_update;
DROP TRIGGER IF EXISTS audit_trail_no_delete;
DROP TRIGGER IF EXISTS merchants_audit_trail_no_update;
DROP TRIGGER IF EXISTS merchants_audit_trail_no_delete;

DELIMITER $$
CREATE TRIGGER audit_trail_no_update BEFORE UPDATE ON `audit_trail`
FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'audit_trail is append-only: rows cannot be updated';
END$$

CREATE TRIGGER audit_trail_no_delete BEFORE DELETE ON `audit_trail`
FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'audit_trail is append-only: rows cannot be deleted';
END$$

CREATE TRIGGER merchants_audit_trail_no_update BEFORE UPDATE ON `merchants_audit_trail`
FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'merchants_audit_trail is append-only: rows cannot be updated';
END$$

CREATE TRIGGER merchants_audit_trail_no_delete BEFORE DELETE ON `merchants_audit_trail`
FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'merchants_audit_trail is append-only: rows cannot be deleted';
END$$
DELIMITER ;
