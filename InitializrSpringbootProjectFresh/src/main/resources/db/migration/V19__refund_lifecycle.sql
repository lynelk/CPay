-- Audit B6: refunds get their own lifecycle object supporting partial refunds (tracked
-- cumulatively against the original payin) instead of the legacy always-full-amount,
-- fire-and-forget payout reversal with no queryable state.
CREATE TABLE IF NOT EXISTS `refunds` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `refund_reference` VARCHAR(255) NOT NULL,
  `merchant_id` BIGINT UNSIGNED NOT NULL,
  `original_transaction_id` BIGINT UNSIGNED NOT NULL,
  `original_merchant_ref` VARCHAR(255) NOT NULL,
  `payout_transaction_id` BIGINT UNSIGNED NULL,
  `requested_amount` DECIMAL(18,4) NOT NULL,
  `refund_status` VARCHAR(30) NOT NULL DEFAULT 'REQUESTED',
  `reason` VARCHAR(500) NULL,
  `failure_message` TEXT NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_refund_reference` (`merchant_id`, `refund_reference`),
  KEY `idx_refund_original_tx` (`original_transaction_id`),
  KEY `idx_refund_status` (`refund_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
