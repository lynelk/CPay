-- S5 pilot (ADR 0002): GnuGrid NIN identity verification. The verified profile is stored
-- PII-safe (only the full-name + MSISDN search fingerprints and the decision/expiry), while
-- the consent record proves the data subject authorised the check and the audit trail records
-- who ran it. The identity-gnugrid feature flag (V36) gates the connector + endpoints.

CREATE TABLE IF NOT EXISTS `verified_profiles` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `merchant_id` BIGINT UNSIGNED NOT NULL,
  `identity_number_hash` VARCHAR(64) NOT NULL,
  `identity_number_mask` VARCHAR(32) NOT NULL,
  `full_name_hash` VARCHAR(64) NULL,
  `full_name_mask` VARCHAR(255) NULL,
  `msisdn_hash` VARCHAR(64) NULL,
  `msisdn_mask` VARCHAR(32) NULL,
  `verification_status` VARCHAR(40) NOT NULL DEFAULT 'PENDING',
  `verified_service` VARCHAR(80) NOT NULL DEFAULT 'gnugrid',
  `provider_reference` VARCHAR(255) NULL,
  `verified_at` TIMESTAMP NULL,
  `expires_at` TIMESTAMP NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_verified_profile_identity` (`identity_number_hash`),
  KEY `idx_verified_profile_merchant` (`merchant_id`, `verification_status`),
  KEY `idx_verified_profile_lookup` (`identity_number_hash`, `verification_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `identity_verification_requests` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `request_reference` VARCHAR(255) NOT NULL,
  `merchant_id` BIGINT UNSIGNED NOT NULL,
  `subject_name` VARCHAR(255) NULL,
  `subject_msisdn` VARCHAR(32) NULL,
  `identity_number_hash` VARCHAR(64) NOT NULL,
  `identity_number_mask` VARCHAR(32) NOT NULL,
  `consent_granted` VARCHAR(3) NOT NULL DEFAULT 'NO',
  `consent_recorded_by` VARCHAR(255) NULL,
  `consent_recorded_at` TIMESTAMP NULL,
  `request_status` VARCHAR(40) NOT NULL DEFAULT 'PENDING',
  `provider_result_json` TEXT NULL,
  `provider_reference` VARCHAR(255) NULL,
  `requested_by` VARCHAR(255) NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_identity_request_reference` (`request_reference`),
  KEY `idx_identity_request_merchant` (`merchant_id`, `request_status`),
  KEY `idx_identity_request_lookup` (`identity_number_hash`, `request_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `identity_verification_audit` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `request_reference` VARCHAR(255) NOT NULL,
  `merchant_id` BIGINT UNSIGNED NOT NULL,
  `action_name` VARCHAR(100) NOT NULL,
  `performed_by` VARCHAR(255) NULL,
  `notes` TEXT NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_identity_audit_merchant` (`merchant_id`, `created_at`),
  KEY `idx_identity_audit_reference` (`request_reference`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
