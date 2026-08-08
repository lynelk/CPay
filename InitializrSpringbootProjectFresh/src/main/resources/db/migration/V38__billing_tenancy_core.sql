-- Billing engine Phase 1, Slice 2 (see Docs/Adr/0003-billing-tenant-model.md): the tenancy root
-- for the new billing domain. billing_tenant_id is a first-class scoping key, kept separate from
-- merchant_id even though every billing_tenants row maps to exactly one merchant today (ADR 0003)
-- so a later multi-tenant BaaS model does not require a breaking migration.
--
-- No foreign keys, matching this schema's existing convention (see compliance_profiles,
-- efris_receipts) of enforcing referential integrity in application code rather than the DB.

CREATE TABLE IF NOT EXISTS `billing_tenants` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `merchant_id` BIGINT UNSIGNED NOT NULL,
  `tenant_type` VARCHAR(40) NOT NULL DEFAULT 'CPAY_MERCHANT',
  `tenant_status` VARCHAR(40) NOT NULL DEFAULT 'ACTIVE',
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_billing_tenant_merchant` (`merchant_id`),
  KEY `idx_billing_tenant_status` (`tenant_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- The legal/commercial party that owes money to the tenant. For a CPay merchant tenant today,
-- this is always the merchant itself (customer_type='MERCHANT_SELF') - a merchant's own end
-- customers under BaaS are a later phase (see ADR 0003 follow-ups), not built here.
CREATE TABLE IF NOT EXISTS `billing_customers` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `billing_tenant_id` BIGINT UNSIGNED NOT NULL,
  `customer_type` VARCHAR(40) NOT NULL DEFAULT 'MERCHANT_SELF',
  `display_name` VARCHAR(255) NOT NULL DEFAULT '',
  `customer_status` VARCHAR(40) NOT NULL DEFAULT 'ACTIVE',
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_billing_customer_tenant` (`billing_tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Currency/terms/balance boundary. billing_tenant_id is denormalized here (in addition to being
-- reachable via billing_customer_id) so billing_tenant_id-scoped queries never need a join,
-- matching how other CPay tables favor a denormalized scoping column for query simplicity.
CREATE TABLE IF NOT EXISTS `billing_accounts` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `billing_tenant_id` BIGINT UNSIGNED NOT NULL,
  `billing_customer_id` BIGINT UNSIGNED NOT NULL,
  `currency` VARCHAR(10) NOT NULL,
  `account_status` VARCHAR(40) NOT NULL DEFAULT 'ACTIVE',
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_billing_account_tenant` (`billing_tenant_id`),
  KEY `idx_billing_account_customer` (`billing_customer_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 1:1 backfill: every existing merchant (any status - a suspended/inactive merchant can still have
-- historical transactions that later need billing_tenant_id scoping) gets exactly one tenant, one
-- default "self" customer, and one default account. No merchant-level settlement-currency column
-- exists anywhere in this schema (confirmed) so the default account currency is a fixed 'UGX'
-- placeholder, matching this codebase's Uganda-centric defaults (EFRIS, most risk-rule examples) -
-- operators can update individual billing_accounts.currency rows after migration if a merchant
-- actually settles in a different currency.
INSERT INTO `billing_tenants` (`merchant_id`, `tenant_type`, `tenant_status`)
SELECT `id`, 'CPAY_MERCHANT', 'ACTIVE'
FROM `merchants`
WHERE `id` NOT IN (SELECT `merchant_id` FROM `billing_tenants`);

INSERT INTO `billing_customers` (`billing_tenant_id`, `customer_type`, `display_name`, `customer_status`)
SELECT bt.`id`, 'MERCHANT_SELF', m.`name`, 'ACTIVE'
FROM `billing_tenants` bt
JOIN `merchants` m ON m.`id` = bt.`merchant_id`
WHERE bt.`id` NOT IN (SELECT `billing_tenant_id` FROM `billing_customers`);

INSERT INTO `billing_accounts` (`billing_tenant_id`, `billing_customer_id`, `currency`, `account_status`)
SELECT bc.`billing_tenant_id`, bc.`id`, 'UGX', 'ACTIVE'
FROM `billing_customers` bc
WHERE bc.`id` NOT IN (SELECT `billing_customer_id` FROM `billing_accounts`);
