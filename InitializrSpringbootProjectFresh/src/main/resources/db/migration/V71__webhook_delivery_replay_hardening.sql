-- P0 §4: webhook retry, replay and transparency hardening.
--
-- Gap closed: merchant webhook deliveries were signed with a bare payload hash
-- (no nonce, no timestamp) so a captured delivery could be replayed to a merchant
-- endpoint indefinitely with no way for the receiver to de-dupe, retries used a
-- linear 5-minute backoff with no per-attempt history, and the only terminal state
-- was FAILED with no parked/operator-visible soak state.
--
-- This migration adds:
--   1. delivery_nonce + delivery_timestamp on merchant_webhook_deliveries - a
--      per-delivery random nonce and the original delivery timestamp that the
--      receiver de-dupes on; existing rows are backfilled (nonce=event_reference,
--      timestamp=updated_at) so pre-existing deliveries keep a stable identifier.
--   2. Parked state columns (parked_at, parked_by, park_reason) so a delivery that
--      exhausts its attempts soaks as PARKED for an operator instead of silently
--      dropping straight to FAILED.
--   3. merchant_webhook_delivery_attempts - an append-only per-attempt audit trail
--      (attempt number, status, http status, response summary, nonce, timestamp).
--
-- Everything is additive / idempotent (CREATE TABLE IF NOT EXISTS plus the same
-- add_column_if_missing helper pattern used by V2/V33/V70).

DROP PROCEDURE IF EXISTS add_column_if_missing_v71;
DELIMITER //
CREATE PROCEDURE add_column_if_missing_v71(
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

CALL add_column_if_missing_v71('merchant_webhook_deliveries', 'delivery_nonce', 'VARCHAR(64) NULL');
CALL add_column_if_missing_v71('merchant_webhook_deliveries', 'delivery_timestamp', 'TIMESTAMP NULL');
CALL add_column_if_missing_v71('merchant_webhook_deliveries', 'parked_at', 'TIMESTAMP NULL');
CALL add_column_if_missing_v71('merchant_webhook_deliveries', 'parked_by', 'VARCHAR(255) NULL');
CALL add_column_if_missing_v71('merchant_webhook_deliveries', 'park_reason', 'VARCHAR(255) NULL');

UPDATE `merchant_webhook_deliveries`
SET `delivery_nonce` = `event_reference`,
    `delivery_timestamp` = `updated_at`
WHERE `delivery_nonce` IS NULL;

CREATE TABLE IF NOT EXISTS `merchant_webhook_delivery_attempts` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `delivery_id` BIGINT UNSIGNED NOT NULL,
  `attempt_number` INT NOT NULL,
  `attempt_status` VARCHAR(40) NOT NULL,
  `http_status` INT NULL,
  `response_summary` TEXT NULL,
  `delivery_nonce` VARCHAR(64) NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_webhook_attempt_delivery` (`delivery_id`, `attempt_number`),
  CONSTRAINT `fk_webhook_attempt_delivery` FOREIGN KEY (`delivery_id`) REFERENCES `merchant_webhook_deliveries` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP PROCEDURE IF EXISTS add_column_if_missing_v71;
