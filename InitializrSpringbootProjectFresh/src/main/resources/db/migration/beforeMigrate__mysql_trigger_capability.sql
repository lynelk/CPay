-- Clean-install preflight for V28__audit_trail_hash_chain.sql.
--
-- V28 is a published historical migration and MUST NOT be edited because doing so would change
-- its Flyway checksum for environments where it has already been applied. On MySQL with binary
-- logging enabled, CREATE TRIGGER requires either elevated privilege or
-- log_bin_trust_function_creators=1. Detect that requirement before Flyway reaches V28 so a fresh
-- database fails clearly and without a partially-applied audit migration.
--
-- Existing databases where V28 is already successful are intentionally exempt because Flyway will
-- not recreate those triggers.
--
-- This callback can execute concurrently when multiple application replicas start together. The
-- helper procedure is schema-global, so serialize only this short preflight with a MySQL named lock
-- to prevent DROP/CREATE/CALL/DROP races between replicas. Named locks are connection-scoped and
-- are released automatically if the connection terminates unexpectedly.

SELECT GET_LOCK('cpay_flyway_mysql_trigger_capability', 60);

DROP PROCEDURE IF EXISTS cpay_assert_mysql_trigger_capability;

DELIMITER $$
CREATE PROCEDURE cpay_assert_mysql_trigger_capability()
BEGIN
    DECLARE v_log_bin INT DEFAULT 0;
    DECLARE v_trust_function_creators INT DEFAULT 1;
    DECLARE v_v28_applied INT DEFAULT 0;
    DECLARE v_history_exists INT DEFAULT 0;

    SELECT @@GLOBAL.log_bin, @@GLOBAL.log_bin_trust_function_creators
      INTO v_log_bin, v_trust_function_creators;

    SELECT COUNT(*)
      INTO v_history_exists
      FROM information_schema.tables
     WHERE table_schema = DATABASE()
       AND table_name = 'flyway_schema_history';

    IF v_history_exists > 0 THEN
        SELECT COUNT(*)
          INTO v_v28_applied
          FROM flyway_schema_history
         WHERE version = '28'
           AND success = 1;
    END IF;

    IF v_v28_applied = 0 AND v_log_bin = 1 AND v_trust_function_creators = 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'CPay clean migration requires log_bin_trust_function_creators=1 before V28 trigger creation';
    END IF;
END$$
DELIMITER ;

CALL cpay_assert_mysql_trigger_capability();
DROP PROCEDURE IF EXISTS cpay_assert_mysql_trigger_capability;
SELECT RELEASE_LOCK('cpay_flyway_mysql_trigger_capability');
