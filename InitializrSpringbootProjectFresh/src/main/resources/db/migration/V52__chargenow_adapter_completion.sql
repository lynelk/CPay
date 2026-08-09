-- ChargeNow/OEM adapter completion.
--
-- Public Bajie/ChargeNow material confirms stations maintain cloud connectivity, receive unlock
-- commands, report status in real time, and support open API/webhook integration. The exact OEM
-- wire contract remains partner-only, so CPay stores operation-specific HTTP mappings rather than
-- hard-coding undocumented endpoints or payloads.

ALTER TABLE `vending_connector_configs`
  ADD COLUMN `auth_timestamp_header` VARCHAR(120) NULL AFTER `auth_header_name`,
  ADD COLUMN `auth_key_header` VARCHAR(120) NULL AFTER `auth_timestamp_header`,
  ADD COLUMN `auth_signature_encoding` VARCHAR(16) NOT NULL DEFAULT 'BASE64' AFTER `auth_key_header`,
  ADD COLUMN `auth_signing_template` VARCHAR(1000) NULL AFTER `auth_signature_encoding`,
  ADD COLUMN `callback_signature_encoding` VARCHAR(16) NOT NULL DEFAULT 'BASE64' AFTER `callback_signature_mode`,
  ADD COLUMN `callback_command_reference_field` VARCHAR(160) NULL AFTER `callback_rental_field`,
  ADD COLUMN `callback_provider_reference_field` VARCHAR(160) NULL AFTER `callback_command_reference_field`;

CREATE TABLE IF NOT EXISTS `vending_connector_operations` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `merchant_id` BIGINT UNSIGNED NOT NULL,
  `connector_code` VARCHAR(80) NOT NULL,
  `command_type` VARCHAR(60) NOT NULL,
  `http_method` VARCHAR(12) NOT NULL DEFAULT 'POST',
  `command_path` VARCHAR(300) NOT NULL,
  `request_template` TEXT NULL,
  `idempotency_header_name` VARCHAR(120) NULL,
  `response_success_field` VARCHAR(160) NULL,
  `response_success_value` VARCHAR(160) NULL,
  `response_reference_field` VARCHAR(160) NULL,
  `response_message_field` VARCHAR(160) NULL,
  `completion_mode` VARCHAR(24) NOT NULL DEFAULT 'CALLBACK',
  `active_flag` VARCHAR(3) NOT NULL DEFAULT 'YES',
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_vending_connector_operation` (`merchant_id`, `connector_code`, `command_type`),
  KEY `idx_vending_connector_operation_active` (`merchant_id`, `connector_code`, `active_flag`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Preserve every V51 connector as a RELEASE_ASSET operation. CALLBACK is the safe default because
-- an HTTP 2xx/accepted response is not proof that a physical power bank actually left the cabinet.
INSERT INTO `vending_connector_operations`
  (`merchant_id`, `connector_code`, `command_type`, `http_method`, `command_path`,
   `request_template`, `response_success_field`, `response_success_value`,
   `response_reference_field`, `response_message_field`, `completion_mode`, `active_flag`)
SELECT `merchant_id`, `connector_code`, 'RELEASE_ASSET', 'POST', `release_path`,
       `release_request_template`, `response_success_field`, `response_success_value`,
       `response_reference_field`, `response_message_field`, 'CALLBACK', `active_flag`
FROM `vending_connector_configs`
ON DUPLICATE KEY UPDATE
  `command_path`=VALUES(`command_path`),
  `request_template`=VALUES(`request_template`),
  `response_success_field`=VALUES(`response_success_field`),
  `response_success_value`=VALUES(`response_success_value`),
  `response_reference_field`=VALUES(`response_reference_field`),
  `response_message_field`=VALUES(`response_message_field`);
