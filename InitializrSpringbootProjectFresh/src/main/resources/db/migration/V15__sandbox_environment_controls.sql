-- Developer sandbox and per-merchant environment controls.
-- These controls keep sandbox easy for new integrators while protecting
-- production traffic with a configurable daily cap.

ALTER TABLE `provider_endpoint_runs`
  ADD COLUMN `merchant_number` VARCHAR(100) NULL,
  ADD COLUMN `environment` VARCHAR(40) NOT NULL DEFAULT 'SANDBOX';

ALTER TABLE `provider_endpoint_runs`
  ADD INDEX  `idx_provider_runs_merchant_env` (`merchant_number`, `environment`, `created_at`);

CREATE TABLE `merchant_environment_preferences` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `merchant_id` BIGINT UNSIGNED NOT NULL,
  `merchant_user_id` BIGINT UNSIGNED NULL,
  `channel_code` VARCHAR(100) NOT NULL DEFAULT '*',
  `active_environment` VARCHAR(40) NOT NULL DEFAULT 'SANDBOX',
  `production_limit_enabled` TINYINT(1) NOT NULL DEFAULT 1,
  `production_transaction_limit` INT NOT NULL DEFAULT 10,
  `updated_by` VARCHAR(255) NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_merchant_environment_preference` (`merchant_id`, `merchant_user_id`, `channel_code`),
  KEY `idx_merchant_environment_active` (`merchant_id`, `active_environment`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO `settings` (`name`, `label`, `setting_value`, `setting_group`, `description`) VALUES
('developer_sandbox_base_url', 'Developer Sandbox Base URL', 'https://sandbox.cpay.example', 'Developer Sandbox', 'Base URL shown to developers when testing CPay integrations.'),
('developer_production_base_url', 'Developer Production Base URL', 'https://api.cpay.example', 'Developer Sandbox', 'Production API base URL shown beside sandbox examples.'),
('developer_sandbox_merchant_number', 'Sandbox Merchant Number', '1000000', 'Developer Sandbox', 'Default sandbox merchant number used in examples when a merchant has not been assigned one.'),
('developer_sandbox_retention_days', 'Sandbox Retention Days', '7', 'Developer Sandbox', 'Number of days sandbox events and examples are retained.'),
('developer_sandbox_idempotency_hours', 'Sandbox Idempotency Hours', '24', 'Developer Sandbox', 'Idempotency window shown in sandbox setup guidance.'),
('production_transaction_limit_enabled', 'Production Transaction Limit Enabled', 'true', 'Developer Sandbox', 'Enable the default daily production transaction cap for merchant API traffic.'),
('production_transaction_limit_count', 'Production Transaction Limit Count', '10', 'Developer Sandbox', 'Default daily production transaction cap. Set a higher number from the admin portal as merchants graduate from sandbox.'),
('yo_payments_channel_label', 'Yo! Payments Channel Label', 'Yo! Payments', 'Payment Channels', 'Display label for the Yo! Payments channel.')
ON DUPLICATE KEY UPDATE
  `label` = VALUES(`label`),
  `setting_group` = VALUES(`setting_group`),
  `description` = VALUES(`description`),
  `setting_value` = CASE
    WHEN `settings`.`setting_value` IS NULL OR TRIM(`settings`.`setting_value`) = '' THEN VALUES(`setting_value`)
    ELSE `settings`.`setting_value`
  END;