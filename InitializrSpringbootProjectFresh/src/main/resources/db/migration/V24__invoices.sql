-- Audit N9: invoicing / request-to-pay objects.
--
-- Merchants had no way to create a "request for payment" object distinct from an ad-hoc
-- collection - an invoice with an amount, description, due date, and a payer reference that a
-- customer can look up and pay later. This is modeled as a sibling of `payment_links`
-- (V7__audit_roadmap_production_features.sql): same link/reference/token/expiry/status-lifecycle
-- pattern, but keyed by the merchant's own invoice reference (unique per merchant, not globally),
-- carrying invoice-shaped fields (payer name/contact, due date), and with an explicit DRAFT state
-- before an invoice becomes payable.
--
-- Like payment_links.paid_transaction_id, `created_transaction_id` stores the transaction id
-- string returned by PaymentOrchestrationService.collect(...) (the legacy tx_unique_id), not the
-- merchant_transactions_log auto-increment id - collect() never hands back that numeric key, so
-- storing the string it does return is the only value this column can actually be populated with.
CREATE TABLE IF NOT EXISTS `invoices` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `merchant_id` BIGINT UNSIGNED NOT NULL,
  `reference` VARCHAR(255) NOT NULL,
  `amount` DECIMAL(19,4) NOT NULL,
  `currency` VARCHAR(10) NOT NULL,
  `description` TEXT NULL,
  `payer_name` VARCHAR(255) NULL,
  `payer_contact` VARCHAR(255) NULL,
  `due_date` DATE NULL,
  `status` VARCHAR(40) NOT NULL DEFAULT 'DRAFT',
  `public_token_hash` VARCHAR(64) NULL,
  `created_transaction_id` VARCHAR(255) NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_invoice_merchant_reference` (`merchant_id`, `reference`),
  UNIQUE KEY `uk_invoice_public_token` (`public_token_hash`),
  KEY `idx_invoice_merchant_status` (`merchant_id`, `status`),
  KEY `idx_invoice_due_date` (`status`, `due_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
