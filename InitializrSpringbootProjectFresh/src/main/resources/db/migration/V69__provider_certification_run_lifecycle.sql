-- P0 §1 provider certification evidence workflow: run lifecycle + enforcement metadata.
-- Complements V9's evidence/requirements tables with a run-level workflow so provider
-- production activation can be gated on approved, scenario-complete certification.

CREATE TABLE IF NOT EXISTS `provider_certification_runs` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `provider_code` VARCHAR(100) NOT NULL,
  `channel_code` VARCHAR(100) NOT NULL,
  `country` VARCHAR(3) NULL,
  `currency` VARCHAR(3) NULL,
  `environment` VARCHAR(32) NOT NULL DEFAULT 'SANDBOX',
  `scope_type` VARCHAR(32) NOT NULL DEFAULT 'GLOBAL',
  `merchant_scope_id` BIGINT UNSIGNED NULL,
  `run_status` VARCHAR(40) NOT NULL DEFAULT 'DRAFT',
  `all_required_scenarios_passed` TINYINT(1) NOT NULL DEFAULT 0,
  `unresolved_blocking_exceptions` TINYINT(1) NOT NULL DEFAULT 0,
  `created_by` VARCHAR(255) NOT NULL DEFAULT 'system',
  `reviewed_by` VARCHAR(255) NULL,
  `approved_by` VARCHAR(255) NULL,
  `reject_reason` TEXT NULL,
  `started_at` TIMESTAMP NULL,
  `evidence_completed_at` TIMESTAMP NULL,
  `reviewed_at` TIMESTAMP NULL,
  `decided_at` TIMESTAMP NULL,
  `expires_at` TIMESTAMP NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cert_run_provider_channel_env` (`provider_code`, `channel_code`, `environment`, `scope_type`),
  KEY `idx_cert_run_status_created` (`run_status`, `created_at`),
  CONSTRAINT `chk_cert_run_status` CHECK (`run_status` IN ('DRAFT','RUNNING','EVIDENCE_PENDING','REVIEW_PENDING','APPROVED','REJECTED','EXPIRED')),
  CONSTRAINT `chk_cert_run_env` CHECK (`environment` IN ('SANDBOX','PRODUCTION'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `provider_certification_run_scenarios` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `run_id` BIGINT UNSIGNED NOT NULL,
  `scenario_name` VARCHAR(120) NOT NULL,
  `scenario_result` VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  `observed_status` VARCHAR(64) NULL,
  `evidence_status` VARCHAR(40) NOT NULL DEFAULT 'PENDING',
  `notes` TEXT NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cert_run_scenario` (`run_id`, `scenario_name`),
  KEY `idx_cert_run_scenario_result` (`run_id`, `scenario_result`),
  CONSTRAINT `fk_cert_run_scenario_run` FOREIGN KEY (`run_id`) REFERENCES `provider_certification_runs` (`id`) ON DELETE CASCADE,
  CONSTRAINT `chk_cert_scenario_result` CHECK (`scenario_result` IN ('PENDING','PASSED','FAILED')),
  CONSTRAINT `chk_cert_scenario_evidence` CHECK (`evidence_status` IN ('PENDING','CAPTURED','APPROVED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `provider_certification_run_exceptions` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `run_id` BIGINT UNSIGNED NOT NULL,
  `exception_code` VARCHAR(64) NOT NULL,
  `exception_type` VARCHAR(64) NOT NULL DEFAULT 'BLOCKING',
  `severity` VARCHAR(32) NOT NULL DEFAULT 'MEDIUM',
  `description` TEXT NULL,
  `resolution` TEXT NULL,
  `resolved_flag` TINYINT(1) NOT NULL DEFAULT 0,
  `created_by` VARCHAR(255) NOT NULL DEFAULT 'system',
  `resolved_by` VARCHAR(255) NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `resolved_at` TIMESTAMP NULL,
  PRIMARY KEY (`id`),
  KEY `idx_cert_run_exception_run` (`run_id`, `resolved_flag`, `exception_type`),
  CONSTRAINT `fk_cert_run_exception_run` FOREIGN KEY (`run_id`) REFERENCES `provider_certification_runs` (`id`) ON DELETE CASCADE,
  CONSTRAINT `chk_cert_exception_type` CHECK (`exception_type` IN ('BLOCKING','NON_BLOCKING'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Backfill an APPROVED PRODUCTION run for each provider/channel that already has at least
-- three approved evidence rows, so channels certified before this migration stay activatable
-- after the enforcement gate goes live. ODKU is meaningful thanks to uk_cert_run_provider_channel_env.
INSERT INTO `provider_certification_runs`
  (`provider_code`, `channel_code`, `environment`, `scope_type`, `run_status`,
   `all_required_scenarios_passed`, `unresolved_blocking_exceptions`, `created_by`,
   `evidence_completed_at`, `reviewed_at`, `decided_at`)
SELECT
  e.`provider_code`,
  e.`channel_code`,
  'PRODUCTION',
  'GLOBAL',
  'APPROVED',
  CASE WHEN c.`approved_scenarios` >= 3 THEN 1 ELSE 0 END,
  0,
  'system',
  MAX(e.`approved_at`),
  MAX(e.`approved_at`),
  MAX(e.`approved_at`)
FROM `provider_certification_evidence` e
JOIN (
  SELECT `provider_code`, `channel_code`, COUNT(DISTINCT `scenario_name`) AS `approved_scenarios`
  FROM `provider_certification_evidence`
  WHERE `evidence_status` = 'APPROVED'
  GROUP BY `provider_code`, `channel_code`
) c
  ON c.`provider_code` = e.`provider_code` AND c.`channel_code` = e.`channel_code`
WHERE e.`evidence_status` = 'APPROVED'
GROUP BY e.`provider_code`, e.`channel_code`, c.`approved_scenarios`
ON DUPLICATE KEY UPDATE `run_status` = 'APPROVED';
