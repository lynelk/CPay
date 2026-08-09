-- CPay communication provider + routing-rule registry (ISO domain: communication/routing; track B1a).
--
-- communication_providers: the catalog of delivery backends the ProviderRouter can select.
-- provider_code is the stable key rules reference. adapter_class documents which
-- SmsGatewayAdapter implementation delivers for that code (informational metadata for
-- operations/audit; the wiring itself lives in CommunicationSmsConfig). Seeded with the
-- agreed SMS provider set (Yo! SMS, Africa's Talking, Twilio) as disabled placeholders so
-- operators can see the intended catalog before B1B adapters exist; LEGACY_SETTINGS is the
-- enabled default that preserves the pre-router behavior.
--
-- communication_routing_rules: provider selection for a channel. merchant_id NULL is the
-- platform default; a merchant-specific row overrides it. Within the same scope, the row
-- with the lowest priority wins (lower = preferred), then lowest id. The seeded platform
-- default routes SMS to LEGACY_SETTINGS, so an unconfigured deployment keeps sending exactly
-- as before until an operator adds a rule pointing at a dedicated provider.

CREATE TABLE IF NOT EXISTS `communication_providers` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `provider_code` VARCHAR(50) NOT NULL,
  `provider_name` VARCHAR(100) NOT NULL,
  `channel` VARCHAR(20) NOT NULL DEFAULT 'SMS',
  `adapter_class` VARCHAR(255) NOT NULL,
  `base_url` VARCHAR(500) NULL,
  `credentials_ref` VARCHAR(255) NULL,
  `enabled_flag` VARCHAR(3) NOT NULL DEFAULT 'YES',
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_comm_provider` (`provider_code`, `channel`),
  KEY `idx_comm_provider_channel` (`channel`, `enabled_flag`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `communication_routing_rules` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `channel` VARCHAR(20) NOT NULL DEFAULT 'SMS',
  `merchant_id` BIGINT UNSIGNED NULL COMMENT 'NULL = platform default for the channel',
  `priority` INT NOT NULL DEFAULT 100 COMMENT 'lower wins; ties broken by lowest id',
  `provider_code` VARCHAR(50) NOT NULL,
  `enabled_flag` VARCHAR(3) NOT NULL DEFAULT 'YES',
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_comm_route_lookup` (`channel`, `merchant_id`, `priority`, `enabled_flag`),
  KEY `idx_comm_route_provider` (`provider_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Catalog: the legacy settings-driven adapter stays the default; the agreed B1B provider set
-- is registered (disabled) so the routing UI and operations docs can reference the codes before
-- the adapters land.
INSERT INTO `communication_providers`
  (`provider_code`, `provider_name`, `channel`, `adapter_class`, `credentials_ref`, `enabled_flag`)
VALUES
  ('LEGACY_SETTINGS', 'Legacy settings-driven HTTP gateway', 'SMS',
   'net.citotech.cito.communication.sms.LegacySettingsSmsGatewayAdapter', 'sms_api_url/sms_api_parameters/sms_api_http_method/sms_gateway_name', 'YES'),
  ('YO_SMS', 'Yo! SMS', 'SMS',
   'net.citotech.cito.communication.sms.YoSmsGatewayAdapter', 'yo_sms_* (B1B)', 'NO'),
  ('AFRICAS_TALKING', 'Africa''s Talking SMS', 'SMS',
   'net.citotech.cito.communication.sms.AfricasTalkingSmsGatewayAdapter', 'africastalking_* (B1B)', 'NO'),
  ('TWILIO_SMS', 'Twilio SMS', 'SMS',
   'net.citotech.cito.communication.sms.TwilioSmsGatewayAdapter', 'twilio_* (B1B)', 'NO')
  ON DUPLICATE KEY UPDATE `provider_name` = VALUES(`provider_name`);

-- Seeded platform default: an unconfigured deployment keeps the pre-router behavior exactly.
INSERT INTO `communication_routing_rules`
  (`channel`, `merchant_id`, `priority`, `provider_code`, `enabled_flag`)
VALUES
  ('SMS', NULL, 100, 'LEGACY_SETTINGS', 'YES')
  ON DUPLICATE KEY UPDATE `priority` = VALUES(`priority`);
