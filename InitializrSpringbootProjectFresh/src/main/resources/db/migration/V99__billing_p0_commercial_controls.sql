-- Billing P0 production controls: effective-dated tax rules, retained invoice tax/FX snapshots,
-- invoice operational lifecycle evidence, source watermarks, and controlled completeness exceptions.
-- No jurisdiction-specific tax rate is seeded. Production finalization must resolve an explicitly
-- approved rule rather than silently assuming zero tax.

CREATE TABLE IF NOT EXISTS `billing_tax_rule_versions` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `billing_tenant_id` BIGINT UNSIGNED NULL,
  `tax_code` VARCHAR(40) NOT NULL,
  `currency` VARCHAR(10) NOT NULL,
  `rate` DECIMAL(12,8) NOT NULL,
  `status` VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
  `effective_from` TIMESTAMP NOT NULL,
  `effective_to` TIMESTAMP NULL,
  `created_by` VARCHAR(191) NOT NULL,
  `approved_by` VARCHAR(191) NULL,
  `approved_at` TIMESTAMP NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_billing_tax_rule_resolve` (`billing_tenant_id`, `tax_code`, `currency`, `status`, `effective_from`, `effective_to`),
  CONSTRAINT `chk_billing_tax_rate` CHECK (`rate` >= 0 AND `rate` <= 1),
  CONSTRAINT `chk_billing_tax_status` CHECK (`status` IN ('DRAFT','APPROVED','RETIRED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `billing_invoice_tax_snapshots` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `billing_invoice_id` BIGINT UNSIGNED NOT NULL,
  `tax_rule_version_id` BIGINT UNSIGNED NOT NULL,
  `tax_code` VARCHAR(40) NOT NULL,
  `taxable_amount` DECIMAL(19,4) NOT NULL,
  `tax_rate` DECIMAL(12,8) NOT NULL,
  `tax_amount` DECIMAL(19,4) NOT NULL,
  `currency` VARCHAR(10) NOT NULL,
  `captured_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_billing_invoice_tax_snapshot` (`billing_invoice_id`),
  KEY `idx_billing_invoice_tax_rule` (`tax_rule_version_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `billing_fx_snapshots` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `billing_tenant_id` BIGINT UNSIGNED NOT NULL,
  `artifact_type` VARCHAR(40) NOT NULL,
  `artifact_reference` VARCHAR(120) NOT NULL,
  `source_currency` VARCHAR(10) NOT NULL,
  `target_currency` VARCHAR(10) NOT NULL,
  `rate` DECIMAL(24,12) NOT NULL,
  `source_fx_rate_id` BIGINT UNSIGNED NULL,
  `provider` VARCHAR(80) NULL,
  `rate_as_of` TIMESTAMP NOT NULL,
  `captured_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_billing_fx_artifact_pair` (`artifact_type`, `artifact_reference`, `source_currency`, `target_currency`),
  KEY `idx_billing_fx_tenant_asof` (`billing_tenant_id`, `rate_as_of`),
  CONSTRAINT `chk_billing_fx_rate_positive` CHECK (`rate` > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE `billing_invoices`
  ADD COLUMN `outstanding_amount` DECIMAL(19,4) NOT NULL DEFAULT 0 AFTER `total_amount`,
  ADD COLUMN `delivered_at` TIMESTAMP NULL AFTER `finalized_by`,
  ADD COLUMN `delivered_by` VARCHAR(191) NULL AFTER `delivered_at`,
  ADD COLUMN `voided_at` TIMESTAMP NULL AFTER `delivered_by`,
  ADD COLUMN `voided_by` VARCHAR(191) NULL AFTER `voided_at`,
  ADD COLUMN `void_reason` VARCHAR(500) NULL AFTER `voided_by`,
  ADD COLUMN `closed_at` TIMESTAMP NULL AFTER `void_reason`,
  ADD COLUMN `closed_by` VARCHAR(191) NULL AFTER `closed_at`;

-- V48 created credit notes as immediately posted objects. Make that state explicit so later maker-
-- checker workflows can stage a note without changing historical rows' meaning.
ALTER TABLE `billing_credit_notes`
  ADD COLUMN `status` VARCHAR(20) NOT NULL DEFAULT 'POSTED' AFTER `reason`,
  ADD COLUMN `approved_by` VARCHAR(191) NULL AFTER `issued_by`,
  ADD COLUMN `approved_at` TIMESTAMP NULL AFTER `approved_by`;

-- Backfill outstanding balances without changing immutable finalized totals.
UPDATE `billing_invoices` i
SET i.`outstanding_amount` = GREATEST(
  i.`total_amount`
  - COALESCE((SELECT SUM(a.`amount`) FROM `billing_payment_allocations` a WHERE a.`billing_invoice_id` = i.`id`), 0)
  - COALESCE((SELECT SUM(c.`amount`) FROM `billing_credit_notes` c WHERE c.`billing_invoice_id` = i.`id` AND c.`status` = 'POSTED'), 0),
  0
)
WHERE i.`status` IN ('FINALIZED','VOID');

CREATE TABLE IF NOT EXISTS `billing_source_watermarks` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `billing_tenant_id` BIGINT UNSIGNED NOT NULL,
  `source_code` VARCHAR(60) NOT NULL,
  `service_code` VARCHAR(40) NOT NULL,
  `expected_through_at` TIMESTAMP NOT NULL,
  `observed_through_at` TIMESTAMP NULL,
  `status` VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  `last_error` VARCHAR(500) NULL,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_billing_source_watermark` (`billing_tenant_id`, `source_code`, `service_code`),
  KEY `idx_billing_source_watermark_status` (`status`, `expected_through_at`),
  CONSTRAINT `chk_billing_source_watermark_status` CHECK (`status` IN ('PENDING','COMPLETE','LATE','FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `billing_operational_exceptions` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `billing_tenant_id` BIGINT UNSIGNED NOT NULL,
  `billing_invoice_id` BIGINT UNSIGNED NULL,
  `source_code` VARCHAR(60) NOT NULL,
  `exception_code` VARCHAR(80) NOT NULL,
  `severity` VARCHAR(20) NOT NULL,
  `status` VARCHAR(20) NOT NULL DEFAULT 'OPEN',
  `details` VARCHAR(1000) NOT NULL,
  `opened_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `opened_by` VARCHAR(191) NOT NULL,
  `resolved_at` TIMESTAMP NULL,
  `resolved_by` VARCHAR(191) NULL,
  `resolution` VARCHAR(1000) NULL,
  `approved_by` VARCHAR(191) NULL,
  PRIMARY KEY (`id`),
  KEY `idx_billing_exception_invoice_status` (`billing_invoice_id`, `status`),
  KEY `idx_billing_exception_tenant_status` (`billing_tenant_id`, `status`, `severity`),
  CONSTRAINT `chk_billing_exception_severity` CHECK (`severity` IN ('INFO','WARNING','MATERIAL','CRITICAL')),
  CONSTRAINT `chk_billing_exception_status` CHECK (`status` IN ('OPEN','WAIVED','RESOLVED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
