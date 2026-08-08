-- Billing engine Phase 1, Slice 11: price-book schema for all component types (flat, percentage,
-- tier, minimum, maximum). Calculation logic (billing/pricing/RatingEngine, TierCalculator) is
-- deliberately deferred to Phase 2 - this slice only captures the data shape. billing_tenant_id is
-- nullable (NULL = a global default price book, overridable per tenant) and there is no unique
-- constraint enforcing "at most one active version" - mirroring the existing fee_schedules (V17)
-- precedent, which resolves the active row by application-level effective-dated lookup rather than
-- a DB constraint.
--
-- No foreign keys, matching this schema's existing convention.

CREATE TABLE IF NOT EXISTS `billing_price_book_versions` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `billing_tenant_id` BIGINT UNSIGNED NULL,
  `service_code` VARCHAR(40) NOT NULL,
  `meter_code` VARCHAR(60) NOT NULL,
  `charge_type` VARCHAR(20) NOT NULL DEFAULT 'CUSTOMER_CHARGE',
  `currency` VARCHAR(10) NOT NULL,
  `version_no` INT NOT NULL,
  `effective_from` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `effective_to` TIMESTAMP NULL DEFAULT NULL,
  `created_by` VARCHAR(255) NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_billing_price_book_lookup` (`billing_tenant_id`, `service_code`, `meter_code`, `charge_type`, `effective_from`, `effective_to`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- One price_book_version can combine several components applied in sequence_no order (e.g. a
-- PERCENTAGE rate followed by a MINIMUM floor and a MAXIMUM cap). component_type is a
-- discriminator on one shared table, matching fee_schedules' charge_type discriminator pattern;
-- only the columns relevant to a row's component_type are populated. tier_definition captures the
-- raw tier boundaries/rates as JSON for the TIER type - FeeSchedule.apply() never actually
-- implemented tier calculation, so there is no existing tier data shape to match against; this is
-- a fresh design, not yet consumed by any calculation logic.
CREATE TABLE IF NOT EXISTS `billing_price_components` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `price_book_version_id` BIGINT UNSIGNED NOT NULL,
  `component_type` VARCHAR(20) NOT NULL,
  `sequence_no` INT NOT NULL DEFAULT 1,
  `flat_amount` DECIMAL(19,4) NULL,
  `percentage_rate` DECIMAL(9,6) NULL,
  `tier_definition` JSON NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_billing_price_component_version` (`price_book_version_id`, `sequence_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
