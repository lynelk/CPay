-- Billing engine Phase 3, Slice 32 (see Docs/Adr/0004-billing-ledger-integration.md and the
-- billing plan's Phase 3 section): the new periodic billing-statement domain
-- (net.citotech.cito.billing.invoicing), distinct from the existing one-off request-to-pay
-- `invoices` table (V24__invoices.sql, checkout.InvoiceService) - a billing_invoices row rolls up
-- a billing tenant's staged billing_rated_charges (V44) for a period into one statement, not a
-- single ad-hoc payment request.
--
-- Lifecycle: DRAFT (lines can still be staged/removed) -> FINALIZED (immutable; ledger_transaction_id
-- populated once BillingLedgerAccountTemplateService posts the invoice total, per ADR 0004) -> VOID
-- (a finalized invoice is never edited or deleted - a billing_credit_notes row, V48, corrects it).
--
-- No foreign keys, matching this schema's existing convention (billing_tenants, billing_rated_charges).

CREATE TABLE IF NOT EXISTS `billing_invoices` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `billing_tenant_id` BIGINT UNSIGNED NOT NULL,
  `invoice_number` VARCHAR(60) NOT NULL,
  `currency` VARCHAR(10) NOT NULL,
  `period_start` DATE NOT NULL,
  `period_end` DATE NOT NULL,
  `status` VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
  `subtotal_amount` DECIMAL(19,4) NOT NULL DEFAULT 0,
  `tax_amount` DECIMAL(19,4) NOT NULL DEFAULT 0,
  `total_amount` DECIMAL(19,4) NOT NULL DEFAULT 0,
  `finalized_at` TIMESTAMP NULL,
  `finalized_by` VARCHAR(191) NULL,
  `ledger_transaction_id` BIGINT UNSIGNED NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_billing_invoice_number` (`invoice_number`),
  KEY `idx_billing_invoice_tenant_period` (`billing_tenant_id`, `period_start`, `period_end`),
  KEY `idx_billing_invoice_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- One line per staged billing_rated_charges row (or, later, a manual adjustment when
-- billing_rated_charge_id is NULL). The unique key on billing_rated_charge_id prevents the same
-- rated charge from ever being staged onto two lines (or the same line twice); MySQL treats
-- multiple NULLs in a unique index as distinct, so NULL (manual) lines are unaffected.
CREATE TABLE IF NOT EXISTS `billing_invoice_lines` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `billing_invoice_id` BIGINT UNSIGNED NOT NULL,
  `billing_rated_charge_id` BIGINT UNSIGNED NULL,
  `description` VARCHAR(255) NOT NULL,
  `amount` DECIMAL(19,4) NOT NULL,
  `currency` VARCHAR(10) NOT NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_billing_invoice_line_charge` (`billing_rated_charge_id`),
  KEY `idx_billing_invoice_line_invoice` (`billing_invoice_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
