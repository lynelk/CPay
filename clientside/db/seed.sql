-- =====================================================================
-- cpayadmin — Deployment seed script
-- Bootstraps the first super-admin account + baseline reference data.
-- Safe to re-run: uses INSERT IGNORE against the schema's UNIQUE keys,
-- so it will not duplicate rows on subsequent deploys.
-- =====================================================================

SET NAMES utf8;
SET FOREIGN_KEY_CHECKS = 0;

-- ---------------------------------------------------------------------
-- 1. Bootstrap super-admin account (table: admins)
-- ---------------------------------------------------------------------
-- The password hash below is a placeholder and MUST be replaced by your
-- deployment script. Do not commit a real password hash to version control.
--
-- To generate a hash for a new password, you can use a script.
-- For example, in Python:
--   pip install bcrypt
--   python3 -c "import bcrypt; print(bcrypt.hashpw(b'YourSecurePasswordHere!', bcrypt.gensalt(10)).decode())"
--
-- Your deployment process should:
-- 1. Generate a new password hash from a secure source (like an env variable or secrets manager).
-- 2. Replace the '__ADMIN_PASSWORD_HASH__' placeholder in this script with the real hash.
-- 3. Run the modified SQL script.
--
-- Example using `sed` on Linux/macOS:
--   export ADMIN_PASS_HASH=$(python3 -c "import bcrypt; print(bcrypt.hashpw(b'YourSecurePasswordHere!', bcrypt.gensalt(10)).decode())")
--   sed "s|__ADMIN_PASSWORD_HASH__|${ADMIN_PASS_HASH}|" seed.sql | mysql -u... -p... your_db

INSERT IGNORE INTO `admins`
    (`name`, `email`, `phone`, `status`, `password`,
     `email_verification_code`, `email_verification_sent_on`)
VALUES
    ('Super Admin', 'admin@example.com', '+256700000000', 'ACTIVE',
     '__ADMIN_PASSWORD_HASH__', -- MUST be replaced by deploy script
     '', NOW());

-- ---------------------------------------------------------------------
-- 2. Privileges for the bootstrap admin (table: admin_privileges)
-- ---------------------------------------------------------------------
-- Adjust these strings to match whatever privilege constants your
-- authorization checks expect (e.g. UserPrivilege.getPrivilege() values).

INSERT IGNORE INTO `admin_privileges` (`admin_id`, `privilege`)
SELECT a.id, p.privilege
FROM `admins` a
JOIN (
    SELECT 'ACCESS_ADMIN' AS privilege
    UNION ALL SELECT 'CREATE_ADMIN'
    UNION ALL SELECT 'UPDATE_ADMIN'
    UNION ALL SELECT 'DELETE_ADMIN'
    UNION ALL SELECT 'ACCESS_AUDITTRAIL'
    UNION ALL SELECT 'ACCESS_TRANSACTION_LOG'
    UNION ALL SELECT 'ACCESS_SMS_LOG'
    UNION ALL SELECT 'CREATE_MERCHANT'
    UNION ALL SELECT 'UPDATE_MERCHANT'
    UNION ALL SELECT 'DELETE_MERCHANT'
    UNION ALL SELECT 'CREDIT_MERCHANT'
    UNION ALL SELECT 'SEND_SMS'
    UNION ALL SELECT 'CREATE_BATCH_TX'
    UNION ALL SELECT 'RESOLVE_TRANSACTIONS'
) p ON 1 = 1
WHERE a.email = 'admin@example.com';

-- ---------------------------------------------------------------------
-- 3. Global application settings (table: settings)
-- ---------------------------------------------------------------------
-- These are placeholders — replace name/value/description/group with
-- your app's actual configuration keys. `name` is UNIQUE, so re-running
-- this script won't create duplicates; it just won't overwrite existing
-- values (use UPDATE separately if you need to change a live value).

INSERT IGNORE INTO `settings`
    (`label`, `name`, `setting_value`, `description`, `setting_group`)
VALUES
    ('Session Timeout (minutes)', 'session_timeout_minutes', '30',
     'Inactive session expiry', 'SECURITY'),
    ('Max Login Attempts', 'max_login_attempts', '5',
     'Failed login attempts before rate-limit lockout', 'SECURITY'),
    ('Login Rate-Limit Window (minutes)', 'login_rate_limit_window_minutes', '15',
     'Window used by the IP rate limiter in authenticatedUser()', 'SECURITY');

-- ---------------------------------------------------------------------
-- 5. Migration tracking table (table: db_changes)
-- ---------------------------------------------------------------------
-- This table is essential for the migration_2024.sql script to track
-- which changes have already been applied.

CREATE TABLE IF NOT EXISTS `db_changes` (
    `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
    `query_id` varchar(255) NOT NULL DEFAULT '',
    `sql_text` text,
    `roll_back` text,
    `created_on` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `query_id` (`query_id`)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- 4. (Optional) Sample merchant for local/staging environments only
-- ---------------------------------------------------------------------
-- Comment this whole block out for production deploys.

-- INSERT IGNORE INTO `merchants`
--     (`name`, `status`, `account_number`, `created_by`, `account_type`, `short_name`)
-- VALUES
--     ('Demo Merchant', 'ACTIVE', 'ACCT-0001', 'system-seed', 'business', 'demo');

-- INSERT IGNORE INTO `merchant_admins`
--     (`merchant_id`, `name`, `email`, `phone`, `password`, `status`,
--      `email_verification_code`, `email_verification_sent_on`)
-- SELECT m.id, 'Demo Merchant Admin', 'merchantadmin@example.com', '+256700000001',
--        '$2b$10$cTGA64pS9QMHUTOQ3g1u0.1UvMX5bcmlVQaUDW0s685lwH.Ruj1EW',
--        'ACTIVE', '', NOW()
-- FROM `merchants` m WHERE m.account_number = 'ACCT-0001';

SET FOREIGN_KEY_CHECKS = 1;