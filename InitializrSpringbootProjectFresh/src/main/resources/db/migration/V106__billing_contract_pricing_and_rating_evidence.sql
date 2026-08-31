-- Cito billing convergence: effective-dated contract price overrides and retained commercial-rating
-- evidence. Existing public charging contracts remain compatible; the new rate-and-authorize path
-- records exactly which contract, price, tax and FX versions produced a committed commercial quote.

CREATE TABLE IF NOT EXISTS `billing_contract_price_overrides` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `billing_tenant_id` BIGINT UNSIGNED NOT NULL,
  `billing_contract_id` BIGINT UNSIGNED NOT NULL,
  `service_code` VARCHAR(40) NOT NULL,
  `meter_code` VARCHAR(60) NOT NULL,
  `charge_type` VARCHAR(20) NOT NULL DEFAULT 'CUSTOMER_CHARGE',
  `price_book_version_id` BIGINT UNSIGNED NOT NULL,
  `status` VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
  `effective_from` TIMESTAMP NOT NULL,
  `effective_to` TIMESTAMP NULL,
  `created_by` VARCHAR(191) NOT NULL,
  `submitted_by` VARCHAR(191) NULL,
  `submitted_at` TIMESTAMP NULL,
  `approved_by` VARCHAR(191) NULL,
  `approved_at` TIMESTAMP NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_billing_contract_price_override_version`
    (`billing_contract_id`,`service_code`,`meter_code`,`charge_type`,`effective_from`),
  KEY `idx_billing_contract_price_override_resolve`
    (`billing_tenant_id`,`billing_contract_id`,`service_code`,`meter_code`,`charge_type`,`status`,`effective_from`,`effective_to`),
  CONSTRAINT `chk_billing_contract_price_override_status`
    CHECK (`status` IN ('DRAFT','SUBMITTED','APPROVED','RETIRED')),
  CONSTRAINT `chk_billing_contract_price_override_charge_type`
    CHECK (`charge_type` IN ('CUSTOMER_CHARGE','PROVIDER_COST'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `billing_commercial_rating_evidence` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `billing_tenant_id` BIGINT UNSIGNED NOT NULL,
  `reservation_reference` VARCHAR(120) NOT NULL,
  `billing_contract_id` BIGINT UNSIGNED NULL,
  `contract_price_override_id` BIGINT UNSIGNED NULL,
  `service_code` VARCHAR(40) NOT NULL,
  `meter_code` VARCHAR(60) NOT NULL,
  `source_currency` VARCHAR(10) NOT NULL,
  `source_base_amount` DECIMAL(19,4) NOT NULL,
  `billing_currency` VARCHAR(10) NOT NULL,
  `normalized_base_amount` DECIMAL(19,4) NOT NULL,
  `customer_price_book_version_id` BIGINT UNSIGNED NOT NULL,
  `provider_price_book_version_id` BIGINT UNSIGNED NULL,
  `customer_net_amount` DECIMAL(19,4) NOT NULL,
  `tax_rule_version_id` BIGINT UNSIGNED NOT NULL,
  `tax_amount` DECIMAL(19,4) NOT NULL,
  `gross_amount` DECIMAL(19,4) NOT NULL,
  `provider_cost_amount` DECIMAL(19,4) NULL,
  `margin_amount` DECIMAL(19,4) NULL,
  `source_fx_rate_id` BIGINT UNSIGNED NULL,
  `fx_rate` DECIMAL(24,12) NOT NULL DEFAULT 1,
  `fx_provider` VARCHAR(80) NULL,
  `rated_at` TIMESTAMP NOT NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_billing_commercial_rating_reservation` (`reservation_reference`),
  KEY `idx_billing_commercial_rating_tenant_time` (`billing_tenant_id`,`rated_at`),
  KEY `idx_billing_commercial_rating_contract` (`billing_contract_id`,`rated_at`),
  CONSTRAINT `chk_billing_commercial_rating_amounts`
    CHECK (`source_base_amount` >= 0 AND `normalized_base_amount` >= 0 AND `customer_net_amount` >= 0
      AND `tax_amount` >= 0 AND `gross_amount` >= 0),
  CONSTRAINT `chk_billing_commercial_rating_fx_rate` CHECK (`fx_rate` > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
