-- Billing engine Phase 3, Slice 34 (see Docs/Adr/0004-billing-ledger-integration.md and the
-- billing plan's Phase 3 section): the two objects that correct/settle a FINALIZED
-- billing_invoices row (V47) without ever mutating it - a finalized invoice is immutable, per its
-- own lifecycle comment.
--
-- No foreign keys, matching this schema's existing billing_* convention.

-- Corrects a finalized invoice by the given amount (a billing error, a dispute, a partial refund)
-- rather than editing the invoice's own lines/totals. Posted via
-- BillingLedgerAccountTemplateService.postCreditNote (link_type REVERSAL, billing_ledger_links).
CREATE TABLE IF NOT EXISTS `billing_credit_notes` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `billing_tenant_id` BIGINT UNSIGNED NOT NULL,
  `billing_invoice_id` BIGINT UNSIGNED NOT NULL,
  `credit_note_number` VARCHAR(60) NOT NULL,
  `currency` VARCHAR(10) NOT NULL,
  `amount` DECIMAL(19,4) NOT NULL,
  `reason` VARCHAR(255) NOT NULL,
  `ledger_transaction_id` BIGINT UNSIGNED NULL,
  `issued_by` VARCHAR(191) NOT NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_billing_credit_note_number` (`credit_note_number`),
  KEY `idx_billing_credit_note_invoice` (`billing_invoice_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Records how much of a payment/receipt (by its own payment_reference - e.g. the tx_unique_id a
-- PaymentOrchestrationService.collect() call returns) was applied against a given invoice's
-- outstanding receivable. The unique key on (billing_invoice_id, payment_reference) makes applying
-- the same payment to the same invoice twice a no-op at the DB level, the same idempotency shape as
-- billing_invoice_lines' unique key on billing_rated_charge_id (V47). Posted via
-- BillingLedgerAccountTemplateService.postInvoicePayment (link_type PAYMENT).
CREATE TABLE IF NOT EXISTS `billing_payment_allocations` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `billing_tenant_id` BIGINT UNSIGNED NOT NULL,
  `billing_invoice_id` BIGINT UNSIGNED NOT NULL,
  `payment_reference` VARCHAR(191) NOT NULL,
  `amount` DECIMAL(19,4) NOT NULL,
  `currency` VARCHAR(10) NOT NULL,
  `ledger_transaction_id` BIGINT UNSIGNED NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_billing_payment_allocation` (`billing_invoice_id`, `payment_reference`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
