CREATE TABLE IF NOT EXISTS `compliance_profiles` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `entity_type` VARCHAR(40) NOT NULL,
  `entity_id` BIGINT UNSIGNED NOT NULL,
  `profile_type` VARCHAR(80) NOT NULL,
  `tier` VARCHAR(40) NOT NULL DEFAULT 'STANDARD',
  `status` VARCHAR(40) NOT NULL DEFAULT 'PENDING',
  `risk_rating` VARCHAR(40) NOT NULL DEFAULT 'UNKNOWN',
  `required_documents_json` TEXT NULL,
  `decision_reason` TEXT NULL,
  `verified_by` VARCHAR(255) NULL,
  `verified_at` TIMESTAMP NULL,
  `expires_at` TIMESTAMP NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_compliance_profile_entity` (`entity_type`, `entity_id`, `profile_type`),
  KEY `idx_compliance_profile_status` (`status`, `risk_rating`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `compliance_cases` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `case_reference` VARCHAR(150) NOT NULL,
  `case_type` VARCHAR(80) NOT NULL,
  `entity_type` VARCHAR(40) NOT NULL,
  `entity_id` BIGINT UNSIGNED NOT NULL,
  `source_reference` VARCHAR(255) NULL,
  `severity` VARCHAR(40) NOT NULL DEFAULT 'MEDIUM',
  `case_status` VARCHAR(40) NOT NULL DEFAULT 'OPEN',
  `assigned_to` VARCHAR(255) NULL,
  `decision` VARCHAR(40) NULL,
  `decision_reason` TEXT NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `closed_at` TIMESTAMP NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_compliance_case_reference` (`case_reference`),
  KEY `idx_compliance_case_entity` (`entity_type`, `entity_id`, `case_status`),
  KEY `idx_compliance_case_queue` (`case_status`, `severity`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `compliance_case_notes` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `case_id` BIGINT UNSIGNED NOT NULL,
  `note_type` VARCHAR(60) NOT NULL DEFAULT 'SYSTEM',
  `note_text` TEXT NOT NULL,
  `created_by` VARCHAR(255) NOT NULL DEFAULT 'system',
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_compliance_case_note_case` (`case_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `risk_decision_scores` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `risk_decision_id` BIGINT UNSIGNED NULL,
  `merchant_id` BIGINT UNSIGNED NOT NULL,
  `request_reference` VARCHAR(255) NOT NULL,
  `score_type` VARCHAR(80) NOT NULL,
  `score_value` DECIMAL(9,4) NOT NULL DEFAULT 0,
  `features_json` TEXT NULL,
  `score_status` VARCHAR(40) NOT NULL DEFAULT 'SHADOW',
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_risk_decision_score_reference` (`merchant_id`, `request_reference`, `created_at`),
  KEY `idx_risk_decision_score_status` (`score_type`, `score_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `provider_certification_requirements` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `provider_code` VARCHAR(100) NOT NULL DEFAULT '*',
  `channel_code` VARCHAR(100) NOT NULL DEFAULT '*',
  `scenario_name` VARCHAR(120) NOT NULL,
  `required_flag` VARCHAR(3) NOT NULL DEFAULT 'YES',
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_provider_cert_requirement` (`provider_code`, `channel_code`, `scenario_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `provider_certification_evidence` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `provider_code` VARCHAR(100) NOT NULL,
  `channel_code` VARCHAR(100) NOT NULL,
  `scenario_name` VARCHAR(120) NOT NULL,
  `evidence_type` VARCHAR(80) NOT NULL,
  `run_id` BIGINT UNSIGNED NULL,
  `statement_run_id` BIGINT UNSIGNED NULL,
  `evidence_status` VARCHAR(40) NOT NULL DEFAULT 'CAPTURED',
  `evidence_summary` TEXT NULL,
  `storage_ref` TEXT NULL,
  `approved_by` VARCHAR(255) NULL,
  `approved_at` TIMESTAMP NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_provider_cert_evidence_provider` (`provider_code`, `channel_code`, `scenario_name`),
  KEY `idx_provider_cert_evidence_status` (`evidence_status`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT IGNORE INTO `provider_certification_requirements`
  (`provider_code`, `channel_code`, `scenario_name`, `required_flag`)
VALUES
  ('*', '*', 'collect_success', 'YES'),
  ('*', '*', 'payout_success', 'YES'),
  ('*', '*', 'status_check', 'YES'),
  ('*', '*', 'callback_success', 'YES'),
  ('*', '*', 'duplicate_reference', 'YES'),
  ('*', '*', 'invalid_account', 'YES'),
  ('*', '*', 'insufficient_funds', 'YES'),
  ('*', '*', 'timeout', 'YES'),
  ('*', '*', 'statement_validation', 'YES'),
  ('*', '*', 'reconciliation_match', 'YES'),
  ('*', '*', 'daily_close_dry_run', 'YES');
