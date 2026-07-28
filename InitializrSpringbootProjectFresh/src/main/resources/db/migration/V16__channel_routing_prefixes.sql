-- Audit B4: prefix->channel routing moves from hardcoded String[] arrays in each gateway class
-- into a DB-backed table, seeded with the exact prefixes that were previously hardcoded so
-- routing behaviour is unchanged by default.
CREATE TABLE IF NOT EXISTS `channel_routing_prefixes` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `gateway_id` VARCHAR(100) NOT NULL,
  `msisdn_prefix` VARCHAR(20) NOT NULL,
  `active_flag` VARCHAR(3) NOT NULL DEFAULT 'YES',
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_channel_routing_prefix` (`gateway_id`, `msisdn_prefix`),
  KEY `idx_channel_routing_active` (`active_flag`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT IGNORE INTO `channel_routing_prefixes` (`gateway_id`, `msisdn_prefix`) VALUES
  ('MTNMoMoPaymentGateway', '25677'),
  ('MTNMoMoPaymentGateway', '25678'),
  ('MTNMoMoPaymentGateway', '25676'),
  ('AirtelMoneyPaymentGateway', '25675'),
  ('AirtelMoneyPaymentGateway', '25670'),
  ('AirtelMoneyPaymentGateway', '25676'),
  ('AirtelMoneyOpenApiPaymentGateway', '25675'),
  ('AirtelMoneyOpenApiPaymentGateway', '25670'),
  ('AirtelMoneyOpenApiPaymentGateway', '25676'),
  ('SafariComPaymentGateway', '25470'),
  ('SafariComPaymentGateway', '25471'),
  ('SafariComPaymentGateway', '25472'),
  ('SafariComPaymentGateway', '25474'),
  ('SafariComPaymentGateway', '25479'),
  ('SafariComPaymentGateway', '25411');
