-- Merchant BaaS P0 online charging evidence. The authorized amount remains the gross amount used
-- for balance reservation; the retained net/tax split prevents a later commit from recalculating
-- tax under a newer rule and gives every charging decision an immutable commercial snapshot.

ALTER TABLE `billing_charge_reservations`
  ADD COLUMN `usage_quantity` DECIMAL(19,4) NOT NULL DEFAULT 1 AFTER `entitlement_code`,
  ADD COLUMN `authorized_net_amount` DECIMAL(19,4) NOT NULL DEFAULT 0 AFTER `authorized_amount`,
  ADD COLUMN `authorized_tax_amount` DECIMAL(19,4) NOT NULL DEFAULT 0 AFTER `authorized_net_amount`,
  ADD COLUMN `tax_rule_version_id` BIGINT UNSIGNED NULL AFTER `authorized_tax_amount`,
  ADD COLUMN `tax_code` VARCHAR(40) NULL AFTER `tax_rule_version_id`,
  ADD COLUMN `tax_rate` DECIMAL(12,8) NULL AFTER `tax_code`,
  ADD COLUMN `committed_net_amount` DECIMAL(19,4) NOT NULL DEFAULT 0 AFTER `committed_amount`,
  ADD COLUMN `committed_tax_amount` DECIMAL(19,4) NOT NULL DEFAULT 0 AFTER `committed_net_amount`;

CREATE TABLE IF NOT EXISTS `billing_charge_ledger_links` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `billing_tenant_id` BIGINT UNSIGNED NOT NULL,
  `reservation_reference` VARCHAR(120) NOT NULL,
  `funding_type` VARCHAR(20) NOT NULL,
  `ledger_transaction_id` BIGINT NOT NULL,
  `ledger_transaction_reference` VARCHAR(191) NOT NULL,
  `link_status` VARCHAR(20) NOT NULL DEFAULT 'POSTED',
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `reversed_at` TIMESTAMP NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_billing_charge_ledger_funding` (`reservation_reference`,`funding_type`),
  UNIQUE KEY `uk_billing_charge_ledger_reference` (`ledger_transaction_reference`),
  KEY `idx_billing_charge_ledger_tenant` (`billing_tenant_id`,`reservation_reference`),
  CONSTRAINT `chk_billing_charge_funding_type` CHECK (`funding_type` IN ('PREPAID','CREDIT')),
  CONSTRAINT `chk_billing_charge_link_status` CHECK (`link_status` IN ('POSTED','REVERSED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
