-- Audit N5: self-service merchant notification preferences - which channel (EMAIL/SMS/NONE) and
-- address a merchant wants used per event in net.citotech.cito.webhook.WebhookEventCatalog,
-- replacing the previously hardcoded "always email the merchant's primary contact, no opt-out"
-- behavior.
--
-- V14__merchant_webhooks_and_notifications.sql already created a `merchant_notification_preferences`
-- table as a placeholder for this feature, but it was never wired up: no service or controller ever
-- read or wrote it, and its shape (three separate email_enabled/sms_enabled/webhook_enabled YES/NO
-- flags, no way to redirect a notification to a different address) doesn't match what the real
-- self-service feature needs. Rather than creating a second, competing table, this migration adds
-- the columns the feature actually needs (`channel`, `notify_address`) to that existing table.
-- The CREATE TABLE IF NOT EXISTS below is only a safety net for a hypothetical environment that
-- somehow reaches V22 without V14 (a full baseline is provided so the migration is correct either
-- way); on every real environment the table already exists and this is a no-op. The unused V14
-- columns (email_enabled/sms_enabled/webhook_enabled/updated_by) are left in place rather than
-- dropped, per the no-destructive-changes-without-a-migration-plan rule.

CREATE TABLE IF NOT EXISTS `merchant_notification_preferences` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `merchant_id` BIGINT UNSIGNED NOT NULL,
  `event_type` VARCHAR(120) NOT NULL,
  `channel` ENUM('EMAIL','SMS','NONE') NOT NULL DEFAULT 'EMAIL',
  `notify_address` VARCHAR(255) NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_merchant_notification_pref` (`merchant_id`, `event_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP PROCEDURE IF EXISTS add_column_if_missing_v22;

DELIMITER $$
CREATE PROCEDURE add_column_if_missing_v22(
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

CALL add_column_if_missing_v22('merchant_notification_preferences', 'channel', "ENUM('EMAIL','SMS','NONE') NOT NULL DEFAULT 'EMAIL'");
CALL add_column_if_missing_v22('merchant_notification_preferences', 'notify_address', 'VARCHAR(255) NULL');

DROP PROCEDURE IF EXISTS add_column_if_missing_v22;
