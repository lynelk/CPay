-- Audited admin impersonation (audit O4): lets support staff view CPay as a specific merchant
-- (e.g. to debug a support ticket) without knowing the merchant's password. Sessions are
-- time-boxed and read-mostly by design - see net.citotech.cito.admin.AdminImpersonationService,
-- whose requireNotImpersonating(...) guard money-moving code paths must call so an active
-- impersonation session can never authorize a mutating action (payouts, refunds, channel
-- credential changes, ...). Those still require the merchant's own real session or v2 signing.
CREATE TABLE IF NOT EXISTS `admin_impersonation_sessions` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `admin_user_id` BIGINT UNSIGNED NOT NULL,
  `merchant_id` BIGINT UNSIGNED NOT NULL,
  `reason` TEXT NOT NULL,
  `started_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `expires_at` TIMESTAMP NOT NULL,
  `ended_at` TIMESTAMP NULL,
  `ended_reason` VARCHAR(30) NULL,
  PRIMARY KEY (`id`),
  KEY `idx_admin_impersonation_admin_active` (`admin_user_id`, `ended_at`),
  KEY `idx_admin_impersonation_merchant` (`merchant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Reuse the existing admin-permission pattern (net.citotech.cito.admin.AdminPermissionService /
-- admin_permissions table from V2__operational_tables.sql) instead of inventing a second gate -
-- only an admin whose role has this permission code can start an impersonation session.
INSERT IGNORE INTO `admin_permissions` (`role_name`, `permission_code`)
VALUES ('ADMIN', 'ADMIN_IMPERSONATE_MERCHANT');
