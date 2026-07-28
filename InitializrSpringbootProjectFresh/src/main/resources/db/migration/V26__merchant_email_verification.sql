-- Audit P4: email verify-before-login gate for merchant portal users. Self-service signup
-- (MerchantSelfServiceSignupService) lets anyone create a merchant_admins row with any email
-- address and log in immediately - nothing confirms the signer-upper actually owns that inbox.
-- The existing `email_verification_code`/`email_verification_sent_on` columns on merchant_admins
-- are already the live mechanism for password-reset OTPs (see AuthenticationController's
-- requestMerchantUserResetPassword/resetPasswordMerchant), so this adds a separate, dedicated
-- verified-at marker and token table rather than overloading that column for a second purpose.

DROP PROCEDURE IF EXISTS add_column_if_missing_v26;

DELIMITER $$
CREATE PROCEDURE add_column_if_missing_v26(
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

CALL add_column_if_missing_v26('merchant_admins', 'email_verified_at', 'TIMESTAMP NULL DEFAULT NULL');

DROP PROCEDURE IF EXISTS add_column_if_missing_v26;

-- Backfill existing accounts as already-verified (one-time, at migration time only) so this gate
-- only ever affects accounts created after it ships - nobody who could already log in gets locked
-- out retroactively.
UPDATE `merchant_admins` SET `email_verified_at` = `created_on` WHERE `email_verified_at` IS NULL;

CREATE TABLE IF NOT EXISTS `merchant_email_verification_tokens` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `merchant_admin_id` bigint unsigned NOT NULL,
  `token_hash` varchar(255) NOT NULL,
  `expires_at` timestamp NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `consumed_at` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_mevt_merchant_admin_id` (`merchant_admin_id`),
  CONSTRAINT `merchant_email_verification_tokens_ibfk_1` FOREIGN KEY (`merchant_admin_id`) REFERENCES `merchant_admins` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;

INSERT INTO `settings` (`name`, `label`, `setting_value`, `setting_group`, `description`) VALUES
('email_tmp_merchant_email_verification', 'Merchant Email Verification Email', 'Hi {name}, please confirm your email address by entering this verification code: {verification_code}. This code expires in 24 hours.', 'Email Templates', 'Email sent to a merchant portal user to confirm their email address before they can log in.')
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`);
