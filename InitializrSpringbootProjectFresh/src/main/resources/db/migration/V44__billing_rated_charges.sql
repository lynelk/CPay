-- Billing engine Phase 2, Slice 19: stores the output of billing/pricing/RatingEngine - one row
-- per rated charge, capturing exactly which price-book version and (for TIER components) which
-- tier bands produced the amount, so a rated charge is reproducible/auditable after the fact even
-- if the price book is later superseded. Written by billing/pricing/RatedChargeRepository,
-- consumed (async, via the outbox) rather than computed synchronously on the collect()/payout()
-- response path - see Docs/Adr or the Slice 21 commit for the reasoning.
--
-- No foreign keys, matching this schema's existing convention.

CREATE TABLE IF NOT EXISTS `billing_rated_charges` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `billing_tenant_id` BIGINT UNSIGNED NOT NULL,
  `price_book_version_id` BIGINT UNSIGNED NOT NULL,
  `service_code` VARCHAR(40) NOT NULL,
  `meter_code` VARCHAR(60) NOT NULL,
  `charge_type` VARCHAR(20) NOT NULL,
  `source_reference` VARCHAR(191) NOT NULL,
  `base_amount` DECIMAL(19,4) NOT NULL,
  `rated_amount` DECIMAL(19,4) NOT NULL,
  `currency` VARCHAR(10) NOT NULL,
  `rounding_policy` VARCHAR(40) NOT NULL,
  `tier_path` JSON NULL,
  `formula_inputs` JSON NULL,
  `idempotency_key` VARCHAR(191) NOT NULL,
  `computed_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_billing_rated_charge_idem` (`idempotency_key`),
  KEY `idx_billing_rated_charge_tenant` (`billing_tenant_id`, `service_code`, `meter_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
