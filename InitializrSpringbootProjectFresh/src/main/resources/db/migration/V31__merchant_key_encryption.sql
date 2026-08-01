-- Audit E6: dedicated encryption tracking for merchant RSA private keys.
--
-- New keys are always written encrypted (MerchantKeyEncryptionService + MerchantKeyCryptoRegistry).
-- Existing rows written before E6 either hold a raw PEM blob (legacy plaintext) or a blob
-- encrypted under the channel-credentials key (if created after the first E6 wave). This migration
-- only adds the tracking column - AES-GCM re-encryption cannot be done in SQL, so the actual
-- backfill happens in MerchantKeyReencryptionService, which decrypts each row via the legacy
-- (channel-key) path and re-encrypts under the dedicated cpay.key.encryption.key.
--
-- key_encryption_version values:
--   0 = legacy plaintext (raw PEM)           -> backfill target: encrypt + set 2
--   1 = encrypted under channel key          -> backfill target: re-encrypt + set 2
--   2 = encrypted under cpay.key.encryption.key (current)
-- NULL = unknown (pre-V31 rows)              -> backfill target: detect + set 0/1/2 accordingly

DROP PROCEDURE IF EXISTS add_column_if_missing_v31;

DELIMITER $$
CREATE PROCEDURE add_column_if_missing_v31(
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

CALL add_column_if_missing_v31('merchants', 'key_encryption_version', 'TINYINT NOT NULL DEFAULT 0');

DROP PROCEDURE IF EXISTS add_column_if_missing_v31;

-- The Flyway migration intentionally does NOT set every row to a non-zero version: existing rows
-- are left at 0 so MerchantKeyReencryptionService can classify and upgrade them at startup
-- (it alone knows which key was used). Only rows created after this migration apply the encoder.
