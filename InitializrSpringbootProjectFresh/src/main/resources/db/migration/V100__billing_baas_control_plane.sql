-- Billing-as-a-Service P0 control plane. Reuses V93 developer projects/service accounts/credentials
-- for authentication, scopes and sandbox lifecycle; adds billing tenant commercial approval,
-- customer/subscription lifecycle, webhooks/quotas, entitlements and concurrency-safe charging.

ALTER TABLE `billing_customers`
  ADD COLUMN `external_reference` VARCHAR(120) NULL AFTER `billing_tenant_id`,
  ADD COLUMN `legal_name` VARCHAR(255) NULL AFTER `display_name`,
  ADD COLUMN `email` VARCHAR(255) NULL AFTER `legal_name`,
  ADD COLUMN `metadata_json` JSON NULL AFTER `customer_status`;
CREATE UNIQUE INDEX `uk_billing_customer_external_ref`
  ON `billing_customers` (`billing_tenant_id`, `external_reference`);

ALTER TABLE `billing_accounts`
  ADD COLUMN `external_reference` VARCHAR(120) NULL AFTER `billing_customer_id`,
  ADD COLUMN `credit_limit` DECIMAL(19,4) NOT NULL DEFAULT 0 AFTER `currency`;
CREATE UNIQUE INDEX `uk_billing_account_external_ref`
  ON `billing_accounts` (`billing_tenant_id`, `external_reference`);

CREATE TABLE IF NOT EXISTS `billing_baas_tenant_profiles` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `billing_tenant_id` BIGINT UNSIGNED NOT NULL,
  `legal_model_status` VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  `commercial_model_status` VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  `tax_model_status` VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  `funds_flow_status` VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  `activation_status` VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
  `approved_by` VARCHAR(191) NULL,
  `approved_at` TIMESTAMP NULL,
  `suspended_by` VARCHAR(191) NULL,
  `suspended_at` TIMESTAMP NULL,
  `suspension_reason` VARCHAR(500) NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_billing_baas_tenant_profile` (`billing_tenant_id`),
  CONSTRAINT `chk_baas_legal_status` CHECK (`legal_model_status` IN ('PENDING','APPROVED','REJECTED')),
  CONSTRAINT `chk_baas_commercial_status` CHECK (`commercial_model_status` IN ('PENDING','APPROVED','REJECTED')),
  CONSTRAINT `chk_baas_tax_status` CHECK (`tax_model_status` IN ('PENDING','APPROVED','REJECTED')),
  CONSTRAINT `chk_baas_funds_flow_status` CHECK (`funds_flow_status` IN ('PENDING','APPROVED','REJECTED')),
  CONSTRAINT `chk_baas_activation_status` CHECK (`activation_status` IN ('DRAFT','READY','ACTIVE','SUSPENDED','CLOSED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `billing_tenant_developer_projects` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `billing_tenant_id` BIGINT UNSIGNED NOT NULL,
  `developer_project_id` BIGINT NOT NULL,
  `environment` VARCHAR(16) NOT NULL,
  `status` VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_billing_tenant_dev_project_env` (`billing_tenant_id`, `developer_project_id`, `environment`),
  KEY `idx_billing_tenant_dev_project` (`developer_project_id`, `environment`, `status`),
  CONSTRAINT `chk_billing_tenant_dev_project_status` CHECK (`status` IN ('ACTIVE','SUSPENDED','REVOKED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `billing_api_quota_policies` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `billing_tenant_id` BIGINT UNSIGNED NOT NULL,
  `developer_project_id` BIGINT NULL,
  `environment` VARCHAR(16) NOT NULL,
  `requests_per_minute` INT NOT NULL DEFAULT 300,
  `usage_events_per_day` BIGINT NOT NULL DEFAULT 100000,
  `max_batch_size` INT NOT NULL DEFAULT 1000,
  `status` VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_billing_quota_policy` (`billing_tenant_id`,`developer_project_id`,`environment`),
  CONSTRAINT `chk_billing_quota_values` CHECK (`requests_per_minute` > 0 AND `usage_events_per_day` > 0 AND `max_batch_size` > 0),
  CONSTRAINT `chk_billing_quota_status` CHECK (`status` IN ('ACTIVE','SUSPENDED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `billing_baas_webhook_subscriptions` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `billing_tenant_id` BIGINT UNSIGNED NOT NULL,
  `developer_project_id` BIGINT NULL,
  `environment` VARCHAR(16) NOT NULL,
  `subscription_reference` VARCHAR(80) NOT NULL,
  `endpoint_url` VARCHAR(1000) NOT NULL,
  `event_types_json` JSON NOT NULL,
  `secret_hash` CHAR(64) NOT NULL,
  `status` VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  `created_by` VARCHAR(191) NOT NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `disabled_at` TIMESTAMP NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_billing_baas_webhook_ref` (`subscription_reference`),
  KEY `idx_billing_baas_webhook_tenant` (`billing_tenant_id`,`environment`,`status`),
  CONSTRAINT `chk_billing_baas_webhook_status` CHECK (`status` IN ('ACTIVE','DISABLED','REVOKED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `billing_contracts` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `billing_tenant_id` BIGINT UNSIGNED NOT NULL,
  `billing_customer_id` BIGINT UNSIGNED NOT NULL,
  `contract_reference` VARCHAR(120) NOT NULL,
  `currency` VARCHAR(10) NOT NULL,
  `status` VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
  `effective_from` TIMESTAMP NOT NULL,
  `effective_to` TIMESTAMP NULL,
  `terms_json` JSON NULL,
  `created_by` VARCHAR(191) NOT NULL,
  `submitted_by` VARCHAR(191) NULL,
  `submitted_at` TIMESTAMP NULL,
  `approved_by` VARCHAR(191) NULL,
  `approved_at` TIMESTAMP NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_billing_contract_reference` (`billing_tenant_id`, `contract_reference`),
  KEY `idx_billing_contract_customer_status` (`billing_customer_id`, `status`),
  CONSTRAINT `chk_billing_contract_status` CHECK (`status` IN ('DRAFT','SUBMITTED','APPROVED','ACTIVE','SUSPENDED','ENDED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `billing_subscriptions` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `billing_tenant_id` BIGINT UNSIGNED NOT NULL,
  `billing_customer_id` BIGINT UNSIGNED NOT NULL,
  `billing_account_id` BIGINT UNSIGNED NOT NULL,
  `billing_contract_id` BIGINT UNSIGNED NOT NULL,
  `subscription_reference` VARCHAR(120) NOT NULL,
  `service_code` VARCHAR(40) NOT NULL,
  `plan_code` VARCHAR(80) NOT NULL,
  `quantity` DECIMAL(19,4) NOT NULL DEFAULT 1,
  `status` VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  `starts_at` TIMESTAMP NOT NULL,
  `ends_at` TIMESTAMP NULL,
  `paused_at` TIMESTAMP NULL,
  `cancelled_at` TIMESTAMP NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_billing_subscription_reference` (`billing_tenant_id`, `subscription_reference`),
  KEY `idx_billing_subscription_customer_status` (`billing_customer_id`, `status`),
  KEY `idx_billing_subscription_service_status` (`billing_tenant_id`, `service_code`, `status`),
  CONSTRAINT `chk_billing_subscription_quantity` CHECK (`quantity` > 0),
  CONSTRAINT `chk_billing_subscription_status` CHECK (`status` IN ('PENDING','ACTIVE','PAUSED','CANCELLED','EXPIRED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `billing_entitlement_grants` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `billing_tenant_id` BIGINT UNSIGNED NOT NULL,
  `billing_customer_id` BIGINT UNSIGNED NOT NULL,
  `billing_subscription_id` BIGINT UNSIGNED NOT NULL,
  `entitlement_code` VARCHAR(100) NOT NULL,
  `limit_quantity` DECIMAL(19,4) NULL,
  `valid_from` TIMESTAMP NOT NULL,
  `valid_to` TIMESTAMP NULL,
  `status` VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_billing_entitlement_subscription_code` (`billing_subscription_id`, `entitlement_code`),
  KEY `idx_billing_entitlement_eval` (`billing_tenant_id`, `billing_customer_id`, `entitlement_code`, `status`, `valid_from`, `valid_to`),
  CONSTRAINT `chk_billing_entitlement_status` CHECK (`status` IN ('ACTIVE','SUSPENDED','REVOKED','EXPIRED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `billing_entitlement_usage` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `billing_tenant_id` BIGINT UNSIGNED NOT NULL,
  `billing_customer_id` BIGINT UNSIGNED NOT NULL,
  `entitlement_code` VARCHAR(100) NOT NULL,
  `period_key` VARCHAR(40) NOT NULL,
  `consumed_quantity` DECIMAL(19,4) NOT NULL DEFAULT 0,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_billing_entitlement_usage_period` (`billing_tenant_id`, `billing_customer_id`, `entitlement_code`, `period_key`),
  CONSTRAINT `chk_billing_entitlement_consumed` CHECK (`consumed_quantity` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `billing_charging_accounts` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `billing_tenant_id` BIGINT UNSIGNED NOT NULL,
  `billing_customer_id` BIGINT UNSIGNED NOT NULL,
  `billing_account_id` BIGINT UNSIGNED NOT NULL,
  `currency` VARCHAR(10) NOT NULL,
  `prepaid_balance` DECIMAL(19,4) NOT NULL DEFAULT 0,
  `credit_limit` DECIMAL(19,4) NOT NULL DEFAULT 0,
  `credit_used` DECIMAL(19,4) NOT NULL DEFAULT 0,
  `reserved_amount` DECIMAL(19,4) NOT NULL DEFAULT 0,
  `status` VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  `lock_version` BIGINT NOT NULL DEFAULT 0,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_billing_charging_account` (`billing_account_id`, `currency`),
  KEY `idx_billing_charging_tenant_status` (`billing_tenant_id`, `status`),
  CONSTRAINT `chk_billing_charging_balances` CHECK (`prepaid_balance` >= 0 AND `credit_limit` >= 0 AND `credit_used` >= 0 AND `credit_used` <= `credit_limit` AND `reserved_amount` >= 0),
  CONSTRAINT `chk_billing_charging_account_status` CHECK (`status` IN ('ACTIVE','SUSPENDED','CLOSED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `billing_charge_reservations` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `billing_tenant_id` BIGINT UNSIGNED NOT NULL,
  `billing_customer_id` BIGINT UNSIGNED NOT NULL,
  `charging_account_id` BIGINT UNSIGNED NOT NULL,
  `reservation_reference` VARCHAR(120) NOT NULL,
  `service_code` VARCHAR(40) NOT NULL,
  `entitlement_code` VARCHAR(100) NULL,
  `currency` VARCHAR(10) NOT NULL,
  `authorized_amount` DECIMAL(19,4) NOT NULL,
  `committed_amount` DECIMAL(19,4) NOT NULL DEFAULT 0,
  `prepaid_committed_amount` DECIMAL(19,4) NOT NULL DEFAULT 0,
  `credit_committed_amount` DECIMAL(19,4) NOT NULL DEFAULT 0,
  `released_amount` DECIMAL(19,4) NOT NULL DEFAULT 0,
  `status` VARCHAR(20) NOT NULL DEFAULT 'AUTHORIZED',
  `idempotency_key` VARCHAR(160) NOT NULL,
  `expires_at` TIMESTAMP NOT NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_billing_charge_reservation_ref` (`reservation_reference`),
  UNIQUE KEY `uk_billing_charge_reservation_idempotency` (`billing_tenant_id`, `idempotency_key`),
  KEY `idx_billing_charge_reservation_account_status` (`charging_account_id`, `status`, `expires_at`),
  CONSTRAINT `chk_billing_charge_reservation_amounts` CHECK (`authorized_amount` > 0 AND `committed_amount` >= 0 AND `prepaid_committed_amount` >= 0 AND `credit_committed_amount` >= 0 AND `released_amount` >= 0),
  CONSTRAINT `chk_billing_charge_reservation_status` CHECK (`status` IN ('AUTHORIZED','PARTIALLY_COMMITTED','COMMITTED','RELEASED','REVERSED','EXPIRED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `billing_charging_adjustments` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `billing_tenant_id` BIGINT UNSIGNED NOT NULL,
  `charging_account_id` BIGINT UNSIGNED NOT NULL,
  `reservation_reference` VARCHAR(120) NULL,
  `adjustment_type` VARCHAR(40) NOT NULL,
  `prepaid_delta` DECIMAL(19,4) NOT NULL DEFAULT 0,
  `credit_used_delta` DECIMAL(19,4) NOT NULL DEFAULT 0,
  `reserved_delta` DECIMAL(19,4) NOT NULL DEFAULT 0,
  `ledger_transaction_id` BIGINT NULL,
  `idempotency_key` VARCHAR(160) NOT NULL,
  `created_by` VARCHAR(191) NOT NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_billing_charging_adjustment_idem` (`billing_tenant_id`,`idempotency_key`),
  KEY `idx_billing_charging_adjustment_account` (`charging_account_id`,`created_at`),
  CONSTRAINT `chk_billing_charging_adjustment_type` CHECK (`adjustment_type` IN ('TOP_UP','AUTHORIZE','COMMIT','RELEASE','REVERSE','EXPIRE','CREDIT_LIMIT'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `billing_protected_action_requests` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `billing_tenant_id` BIGINT UNSIGNED NOT NULL,
  `action_type` VARCHAR(80) NOT NULL,
  `resource_type` VARCHAR(80) NOT NULL,
  `resource_reference` VARCHAR(120) NOT NULL,
  `requested_by` VARCHAR(191) NOT NULL,
  `requested_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `status` VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  `approved_by` VARCHAR(191) NULL,
  `approved_at` TIMESTAMP NULL,
  `decision_reason` VARCHAR(500) NULL,
  PRIMARY KEY (`id`),
  KEY `idx_billing_protected_action_pending` (`billing_tenant_id`, `status`, `action_type`),
  CONSTRAINT `chk_billing_protected_action_status` CHECK (`status` IN ('PENDING','APPROVED','REJECTED','EXPIRED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO `billing_baas_tenant_profiles` (`billing_tenant_id`)
SELECT bt.`id` FROM `billing_tenants` bt
WHERE NOT EXISTS (SELECT 1 FROM `billing_baas_tenant_profiles` p WHERE p.`billing_tenant_id`=bt.`id`);
