-- Audit B3: the payout failure-reversal sequence (DR suspense, CR customer, etc.) previously had
-- no persisted intermediate state - if a step failed partway through, the only record was a log
-- line. This table tracks saga progress so a stuck/partial compensation can be detected and
-- alerted on rather than silently leaving the ledger inconsistent.
CREATE TABLE IF NOT EXISTS `payout_compensation_sagas` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `transactions_log_id` BIGINT UNSIGNED NOT NULL,
  `tx_unique_id` VARCHAR(255) NOT NULL,
  `merchant_id` BIGINT UNSIGNED NOT NULL,
  `total_steps` INT NOT NULL,
  `completed_steps` INT NOT NULL DEFAULT 0,
  `last_step_name` VARCHAR(100) NULL,
  `saga_status` VARCHAR(30) NOT NULL DEFAULT 'STARTED',
  `last_error` TEXT NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_payout_saga_tx` (`transactions_log_id`),
  KEY `idx_payout_saga_status` (`saga_status`, `updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
