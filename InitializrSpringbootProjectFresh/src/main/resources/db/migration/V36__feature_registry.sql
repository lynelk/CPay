-- Feature registry: the "@Owner" of the V35 baseline contract (ADR 0002).
--
-- feature_flags (V5) already carries GLOBAL feature toggles. This migration adds the
-- per-merchant override table so a feature can be enabled per environment and per merchant
-- without a code deploy (rollout is reversible per merchant via the feature registry).
--
-- Tenant scope: merchant_feature_flags is keyed by merchant_id; TenantScopeGuard
-- (admin/TenantScopeGuard.java) enforces that merchant-scoped reads always bind the tenant
-- key, and the cross-tenant isolation tests prove a merchant can never read another
-- merchant's override rows.

CREATE TABLE IF NOT EXISTS `merchant_feature_flags` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `merchant_id` BIGINT UNSIGNED NOT NULL,
  `flag_key` VARCHAR(120) NOT NULL,
  `enabled` TINYINT(1) NOT NULL DEFAULT 0,
  `description` VARCHAR(255) NOT NULL DEFAULT '',
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_merchant_feature_flag` (`merchant_id`, `flag_key`),
  KEY `idx_merchant_feature_flag_key` (`flag_key`, `enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Documented feature-key catalog (global defaults). Each row is the system-of-record default;
-- merchant_feature_flags overrides a feature per merchant. New features are added as inserts
-- here, never as ad-hoc code paths.
--
-- The S5/S6 pilot surfaces ship enabled so their screens are live out of the box; operators
-- can flip any of them off here or per merchant, which is exactly the reversible-rollout
-- behaviour the registry exists to provide. identity-gnugrid stays disabled by default: it
-- turns on real outbound identity-provider calls and must be opted in.
INSERT IGNORE INTO `feature_flags` (`flag_key`, `enabled`, `description`) VALUES
  ('balance-monitoring', 1, 'Balance monitoring dashboard for treasury and gateway positions (S5 pilot)'),
  ('identity-gnugrid', 0, 'GnuGrid NIN identity verification connector (S5 pilot)'),
  ('payout-controls-config', 1, 'Admin self-service configuration of payout_controls rows (S6)'),
  ('compliance-dashboard', 1, 'Compliance dashboard and case decision workbench (S6)'),
  ('kyb-review', 1, 'KYB review workbench for beneficial owners and KYC documents (S6)'),
  ('provider-certification-dashboard', 1, 'Provider certification evidence dashboard (S6)'),
  ('regulator-reporting-ui', 1, 'Regulator reporting and PII inventory screens (S6)'),
  ('treasury-dashboard', 1, 'Treasury positions dashboard (S6)');
