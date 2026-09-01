ALTER TABLE `admins`
  ADD COLUMN `must_change_password` TINYINT(1) NOT NULL DEFAULT 0 AFTER `password`;

CREATE TABLE `admin_bootstrap_operations` (
  `operation_id` VARCHAR(64) NOT NULL,
  `target_email_sha256` CHAR(64) NOT NULL,
  `target_admin_id` BIGINT UNSIGNED NULL,
  `removed_admin_count` INT UNSIGNED NULL,
  `granted_privilege_count` INT UNSIGNED NULL,
  `started_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `completed_at` TIMESTAMP NULL,
  PRIMARY KEY (`operation_id`),
  KEY `idx_admin_bootstrap_completed` (`completed_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
