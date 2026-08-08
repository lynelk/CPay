-- Billing engine Phase 1, Slice 7 (see Docs/Adr/0005-billing-outbox-design.md): the transactional
-- outbox. OutboxWriter (Slice 8) inserts a row from inside the caller's existing @Transactional
-- method, so the row only survives if that transaction commits. OutboxRelay (Slice 9) is a
-- ShedLock-guarded @Scheduled poller - matching scheduler/LedgerOperationsScheduler's pattern, so
-- exactly one instance processes PENDING rows at a time cluster-wide and no per-row claim column
-- is needed here.
--
-- No foreign keys, matching this schema's existing convention.

CREATE TABLE IF NOT EXISTS `billing_outbox` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `aggregate_type` VARCHAR(60) NOT NULL,
  `aggregate_id` VARCHAR(120) NOT NULL,
  `event_type` VARCHAR(80) NOT NULL,
  `payload` JSON NOT NULL,
  `status` VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  `attempt_count` INT NOT NULL DEFAULT 0,
  `next_attempt_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `last_error` VARCHAR(500) NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_billing_outbox_status_next_attempt` (`status`, `next_attempt_at`),
  KEY `idx_billing_outbox_aggregate` (`aggregate_type`, `aggregate_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
