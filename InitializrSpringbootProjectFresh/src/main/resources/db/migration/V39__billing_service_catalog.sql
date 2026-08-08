-- Billing engine Phase 1, Slice 4 (see plan Phase 1 "billing foundation"): the service/meter
-- catalog that usage_events (V40) will reference by service_code/meter_code. Draft/publish
-- workflow states for meter versions are deliberately deferred to Phase 2
-- (V43__billing_meter_publish_workflow.sql) - this is the MVP shape: one active version per
-- meter, effective-dated, no approval step yet.
--
-- No foreign keys, matching this schema's existing convention.

CREATE TABLE IF NOT EXISTS `billing_service_catalog` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `service_code` VARCHAR(40) NOT NULL,
  `service_name` VARCHAR(120) NOT NULL,
  `service_status` VARCHAR(40) NOT NULL DEFAULT 'ACTIVE',
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_billing_service_code` (`service_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- A meter is one countable/summable quantity within a service (e.g. "payment events" vs
-- "payment volume" both live under service_code='PAYMENT'). aggregation_type is deliberately
-- restricted to what billing/metering/MeterAggregationService actually computes this phase
-- (COUNT/SUM only - see Slice 13); a wider set (MAX/DISTINCT/percentile) is a later addition.
CREATE TABLE IF NOT EXISTS `billing_meters` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `service_code` VARCHAR(40) NOT NULL,
  `meter_code` VARCHAR(60) NOT NULL,
  `meter_name` VARCHAR(120) NOT NULL,
  `aggregation_type` VARCHAR(20) NOT NULL DEFAULT 'COUNT',
  `meter_status` VARCHAR(40) NOT NULL DEFAULT 'ACTIVE',
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_billing_meter_code` (`meter_code`),
  KEY `idx_billing_meter_service` (`service_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- dimension_keys documents which usage_events.dimensions keys this meter version is allowed to
-- group/filter by (e.g. ["currency","provider"]); enforcement of that contract belongs to
-- application code (billing/metering), not the DB. effective_to is nullable/open-ended so a
-- meter can be superseded later without deleting history.
CREATE TABLE IF NOT EXISTS `billing_meter_versions` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `meter_id` BIGINT UNSIGNED NOT NULL,
  `version_no` INT NOT NULL,
  `dimension_keys` JSON NULL,
  `effective_from` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `effective_to` TIMESTAMP NULL DEFAULT NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_billing_meter_version` (`meter_id`, `version_no`),
  KEY `idx_billing_meter_version_meter` (`meter_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Seed the four services cut in together per the approved plan (decision #4: "all"): payments,
-- SMS, webhooks/API, invoicing. One MVP meter per service - additional meters (e.g. a separate
-- payment-volume SUM meter) are added by later slices/phases as real producers need them.
INSERT IGNORE INTO `billing_service_catalog` (`service_code`, `service_name`, `service_status`) VALUES
  ('PAYMENT', 'Payment collections and payouts', 'ACTIVE'),
  ('SMS', 'SMS notifications', 'ACTIVE'),
  ('WEBHOOK', 'Webhook/API delivery', 'ACTIVE'),
  ('INVOICE', 'Invoicing', 'ACTIVE');

INSERT IGNORE INTO `billing_meters` (`service_code`, `meter_code`, `meter_name`, `aggregation_type`, `meter_status`) VALUES
  ('PAYMENT', 'payment_event_count', 'Payment events', 'COUNT', 'ACTIVE'),
  ('SMS', 'sms_sent_count', 'SMS sent', 'COUNT', 'ACTIVE'),
  ('WEBHOOK', 'webhook_delivered_count', 'Webhooks delivered', 'COUNT', 'ACTIVE'),
  ('INVOICE', 'invoice_issued_count', 'Invoices issued', 'COUNT', 'ACTIVE');

INSERT INTO `billing_meter_versions` (`meter_id`, `version_no`, `dimension_keys`, `effective_from`)
SELECT `id`, 1, JSON_ARRAY('currency'), CURRENT_TIMESTAMP
FROM `billing_meters`
WHERE `id` NOT IN (SELECT `meter_id` FROM `billing_meter_versions`);
