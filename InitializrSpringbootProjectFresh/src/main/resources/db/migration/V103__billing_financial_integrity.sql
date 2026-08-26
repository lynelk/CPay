-- P0 financial-integrity enforcement for verified CPay funding consumption.
CREATE TABLE IF NOT EXISTS `billing_payment_funding_allocations` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `billing_tenant_id` BIGINT UNSIGNED NOT NULL,
  `source_transaction_id` BIGINT NOT NULL,
  `allocation_key` VARCHAR(220) NOT NULL,
  `purpose` VARCHAR(40) NOT NULL,
  `target_reference` VARCHAR(160) NOT NULL,
  `amount` DECIMAL(19,4) NOT NULL,
  `currency` VARCHAR(10) NOT NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_billing_funding_allocation_key` (`billing_tenant_id`,`allocation_key`),
  KEY `idx_billing_funding_source` (`billing_tenant_id`,`source_transaction_id`),
  CONSTRAINT `chk_billing_funding_amount` CHECK (`amount` > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
