-- Billing engine Phase 3, Slice 35 (see the billing plan's Phase 3 section): the maker-checker
-- gate a DRAFT billing_invoices row (V47) must pass through to become FINALIZED, mirroring
-- reconciliation_daily_closes' maker/checker shape (V33__maker_checker_finance_close.sql) - a
-- PENDING_APPROVAL row requires a different actor (checker) to approve before the invoice can be
-- finalized; a same-actor approval attempt is a no-op, exactly like
-- FinanceWorkflowService.approveDailyClose's `requested_by <> approved_by` guard.
--
-- One row per invoice (unique on billing_invoice_id) rather than per currency/day like
-- reconciliation_daily_closes, since completeness here is "does this invoice have every rated
-- charge for its own tenant/currency/period staged onto it" (see
-- BillingInvoiceRepository#findUnstagedCustomerCharges), not a platform-wide daily check.
--
-- completeness_result records the automated PASS/FAIL at submit time; a checker may still approve
-- a FAIL (recording why in waiver_reason) instead of forcing the maker to keep re-submitting -
-- this is the "checker-waiver" path named in the Phase 3 exit criterion. Rejection keeps the row
-- PENDING_APPROVAL (with rejection_reason set) so the maker can re-submit, the same non-terminal
-- rejection shape reconciliation_daily_closes uses.
--
-- No foreign keys, matching this schema's existing billing_* convention.

CREATE TABLE IF NOT EXISTS `billing_completeness_gates` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `billing_invoice_id` BIGINT UNSIGNED NOT NULL,
  `billing_tenant_id` BIGINT UNSIGNED NOT NULL,
  `gate_status` VARCHAR(20) NOT NULL DEFAULT 'PENDING_APPROVAL',
  `completeness_result` VARCHAR(10) NOT NULL,
  `unstaged_charge_count` INT NOT NULL DEFAULT 0,
  `requested_by` VARCHAR(255) NOT NULL,
  `requested_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `approved_by` VARCHAR(255) NULL,
  `approved_at` TIMESTAMP NULL,
  `rejection_reason` TEXT NULL,
  `waiver_reason` TEXT NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_billing_completeness_gate_invoice` (`billing_invoice_id`),
  KEY `idx_billing_completeness_gate_status` (`gate_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
