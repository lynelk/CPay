-- =============================================================
-- CPay Admin Seeding Script
-- Default password for all accounts: Admin@123
-- SHA-256 hash (no salt, hex lowercase — matches Common.getSha256EncodedString)
-- =============================================================

SET @pwd = 'e86f78a8a3caf0b60d8e74e5942aa6d86dc150cd3c03338aef25b7d2d7e3acc7';

-- -------------------------------------------------------------
-- 1. SUPER ADMIN — all privileges
-- -------------------------------------------------------------
INSERT INTO admins (name, email, phone, status, password)
VALUES ('Super Admin', 'svcs@coresynergi.es', '256701438948', 'ACTIVE', @pwd);

SET @super_id = LAST_INSERT_ID();

INSERT INTO admin_privileges (admin_id, privilege) VALUES
  (@super_id, 'ACCESS_ADMIN'),
  (@super_id, 'CREATE_ADMIN'),
  (@super_id, 'UPDATE_ADMIN'),
  (@super_id, 'DELETE_ADMIN'),
  (@super_id, 'ACCESS_AUDITTRAIL'),
  (@super_id, 'ACCESS_TRANSACTION_LOG'),
  (@super_id, 'ACCESS_SMS_LOG'),
  (@super_id, 'CREATE_MERCHANT'),
  (@super_id, 'UPDATE_MERCHANT'),
  (@super_id, 'DELETE_MERCHANT'),
  (@super_id, 'CREDIT_MERCHANT'),
  (@super_id, 'SEND_SMS'),
  (@super_id, 'CREATE_BATCH_TX'),
  (@super_id, 'RESOLVE_TRANSACTIONS');
