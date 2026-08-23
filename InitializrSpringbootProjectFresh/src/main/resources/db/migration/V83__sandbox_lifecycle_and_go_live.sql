-- Complete customer sandbox lifecycle and controlled production graduation.
-- All mutable sandbox data is isolated in sandbox_* tables; production financial records
-- and credentials are deliberately never cloned or deleted by sandbox self-service actions.

CREATE TABLE IF NOT EXISTS `sandbox_wallet_balances` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `merchant_id` BIGINT UNSIGNED NOT NULL,
  `channel_code` VARCHAR(100) NOT NULL DEFAULT 'GENERAL',
  `currency` VARCHAR(12) NOT NULL,
  `available_balance` DECIMAL(20,4) NOT NULL DEFAULT 0,
  `updated_by` VARCHAR(255) NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sandbox_wallet` (`merchant_id`, `channel_code`, `currency`),
  KEY `idx_sandbox_wallet_merchant` (`merchant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `sandbox_snapshots` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `merchant_id` BIGINT UNSIGNED NOT NULL,
  `snapshot_name` VARCHAR(160) NOT NULL,
  `snapshot_json` LONGTEXT NOT NULL,
  `created_by` VARCHAR(255) NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_sandbox_snapshot_merchant` (`merchant_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `sandbox_test_personas` (
  `persona_code` VARCHAR(64) NOT NULL,
  `persona_type` VARCHAR(24) NOT NULL DEFAULT 'KYC',
  `display_name` VARCHAR(160) NOT NULL,
  `expected_status` VARCHAR(40) NOT NULL,
  `scenario` VARCHAR(80) NOT NULL,
  `payload_json` LONGTEXT NOT NULL,
  `active_flag` TINYINT(1) NOT NULL DEFAULT 1,
  PRIMARY KEY (`persona_code`),
  KEY `idx_sandbox_persona_type` (`persona_type`, `active_flag`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `sandbox_certification_runs` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `merchant_id` BIGINT UNSIGNED NOT NULL,
  `run_status` VARCHAR(32) NOT NULL DEFAULT 'RUNNING',
  `passed_checks` INT NOT NULL DEFAULT 0,
  `total_checks` INT NOT NULL DEFAULT 0,
  `requested_by` VARCHAR(255) NULL,
  `started_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `completed_at` TIMESTAMP NULL,
  PRIMARY KEY (`id`),
  KEY `idx_sandbox_cert_merchant` (`merchant_id`, `started_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `sandbox_certification_checks` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `run_id` BIGINT UNSIGNED NOT NULL,
  `check_key` VARCHAR(100) NOT NULL,
  `check_label` VARCHAR(255) NOT NULL,
  `passed` TINYINT(1) NOT NULL DEFAULT 0,
  `evidence` VARCHAR(1000) NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sandbox_cert_check` (`run_id`, `check_key`),
  CONSTRAINT `fk_sandbox_cert_run` FOREIGN KEY (`run_id`) REFERENCES `sandbox_certification_runs` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `merchant_go_live_requests` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `merchant_id` BIGINT UNSIGNED NOT NULL,
  `certification_run_id` BIGINT UNSIGNED NULL,
  `request_status` VARCHAR(40) NOT NULL DEFAULT 'REQUESTED',
  `current_stage` VARCHAR(40) NOT NULL DEFAULT 'TECHNICAL_REVIEW',
  `requested_by` VARCHAR(255) NULL,
  `decision_by` VARCHAR(255) NULL,
  `decision_notes` VARCHAR(1000) NULL,
  `requested_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `approved_at` TIMESTAMP NULL,
  `activated_at` TIMESTAMP NULL,
  PRIMARY KEY (`id`),
  KEY `idx_go_live_merchant_status` (`merchant_id`, `request_status`, `updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `sandbox_configuration_promotions` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `merchant_id` BIGINT UNSIGNED NOT NULL,
  `go_live_request_id` BIGINT UNSIGNED NULL,
  `promotion_status` VARCHAR(32) NOT NULL DEFAULT 'VALIDATED',
  `manifest_json` LONGTEXT NOT NULL,
  `promoted_by` VARCHAR(255) NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_sandbox_promotion_merchant` (`merchant_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `sandbox_live_smoke_tests` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `merchant_id` BIGINT UNSIGNED NOT NULL,
  `transaction_reference` VARCHAR(255) NOT NULL,
  `test_status` VARCHAR(32) NOT NULL,
  `transaction_verified` TINYINT(1) NOT NULL DEFAULT 0,
  `provider_run_verified` TINYINT(1) NOT NULL DEFAULT 0,
  `callback_verified` TINYINT(1) NOT NULL DEFAULT 0,
  `evidence_json` LONGTEXT NOT NULL,
  `verified_by` VARCHAR(255) NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_smoke_merchant` (`merchant_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `merchant_rollout_stages` (
  `merchant_id` BIGINT UNSIGNED NOT NULL,
  `stage_code` VARCHAR(40) NOT NULL DEFAULT 'SANDBOX',
  `production_daily_limit` INT NOT NULL DEFAULT 10,
  `collections_enabled` TINYINT(1) NOT NULL DEFAULT 0,
  `refunds_enabled` TINYINT(1) NOT NULL DEFAULT 0,
  `payouts_enabled` TINYINT(1) NOT NULL DEFAULT 0,
  `updated_by` VARCHAR(255) NULL,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`merchant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `sandbox_isolation_verifications` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `verification_status` VARCHAR(32) NOT NULL,
  `checks_json` LONGTEXT NOT NULL,
  `verified_by` VARCHAR(255) NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_sandbox_isolation_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT IGNORE INTO `sandbox_test_personas`
(`persona_code`, `persona_type`, `display_name`, `expected_status`, `scenario`, `payload_json`) VALUES
('TEST-KYC-001','KYC','Verified Individual','VERIFIED','SUCCESS','{"nin":"CM00000001TEST","msisdn":"256770000001","documentStatus":"VALID","screening":"CLEAR","biometricMatch":true}'),
('TEST-KYC-002','KYC','Identity Not Found','FAILED','ID_NOT_FOUND','{"nin":"CM00000002TEST","msisdn":"256770000002","identityFound":false}'),
('TEST-KYC-003','KYC','Name Mismatch','REVIEW','NAME_MISMATCH','{"nin":"CM00000003TEST","nameMatch":false,"screening":"CLEAR"}'),
('TEST-KYC-004','KYC','Date of Birth Mismatch','REVIEW','DOB_MISMATCH','{"nin":"CM00000004TEST","dobMatch":false,"screening":"CLEAR"}'),
('TEST-KYC-005','KYC','Watchlist Hit','BLOCKED','WATCHLIST_HIT','{"nin":"CM00000005TEST","screening":"POTENTIAL_MATCH"}'),
('TEST-KYC-006','KYC','Expired Document','FAILED','DOCUMENT_EXPIRED','{"nin":"CM00000006TEST","documentStatus":"EXPIRED"}'),
('TEST-KYC-007','KYC','Manual Review','REVIEW','MANUAL_REVIEW','{"nin":"CM00000007TEST","screening":"REVIEW"}'),
('TEST-KYC-008','KYC','Biometric Mismatch','FAILED','BIOMETRIC_MISMATCH','{"nin":"CM00000008TEST","biometricMatch":false}'),
('TEST-KYB-001','KYB','Verified Business','VERIFIED','SUCCESS','{"registrationNumber":"800000001TEST","beneficialOwners":2,"screening":"CLEAR"}'),
('TEST-KYB-002','KYB','Beneficial Owner Review','REVIEW','OWNER_REVIEW','{"registrationNumber":"800000002TEST","beneficialOwnerScreening":"REVIEW"}');

INSERT IGNORE INTO `feature_flags` (`flag_key`, `enabled`, `description`) VALUES
('production-collections', 0, 'Progressive go-live gate for production collections'),
('production-refunds', 0, 'Progressive go-live gate for production refunds'),
('production-payouts', 0, 'Progressive go-live gate for production payouts'),
('sandbox-lifecycle', 1, 'Merchant sandbox readiness, certification, snapshots and go-live workflow');
