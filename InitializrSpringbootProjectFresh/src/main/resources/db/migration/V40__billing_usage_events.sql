-- Billing engine Phase 1, Slice 5: the immutable usage-event stream. Every meterable thing that
-- happens in CPay (a payment, an SMS send, a webhook delivery, an invoice issuance) lands here
-- exactly once, keyed by idempotency_key so a retried producer call (e.g. a callback redelivery)
-- never double-counts. billing/usage/UsageGatewayService (Slice 6) is the only writer; rows are
-- never updated or deleted by application code (no updated_at column - append-only by
-- convention, matching ledger_entries).
--
-- No foreign keys, matching this schema's existing convention.

CREATE TABLE IF NOT EXISTS `billing_usage_events` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `billing_tenant_id` BIGINT UNSIGNED NOT NULL,
  `service_code` VARCHAR(40) NOT NULL,
  `meter_code` VARCHAR(60) NOT NULL,
  `event_time` TIMESTAMP NOT NULL,
  `quantity` DECIMAL(19,4) NOT NULL DEFAULT 1,
  `currency` VARCHAR(10) NULL,
  `dimensions` JSON NULL,
  `source_reference` VARCHAR(120) NULL,
  `idempotency_key` VARCHAR(160) NOT NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_billing_usage_event_idempotency` (`idempotency_key`),
  KEY `idx_billing_usage_event_tenant_service` (`billing_tenant_id`, `service_code`, `event_time`),
  KEY `idx_billing_usage_event_source` (`source_reference`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
