-- Preserve exact revenue/tax allocation evidence for every posted billing credit note.
-- The allocation table also lets the final full-invoice credit absorb any prior 4dp rounding residual.
CREATE TABLE IF NOT EXISTS `billing_credit_note_allocations` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `billing_tenant_id` BIGINT UNSIGNED NOT NULL,
  `billing_invoice_id` BIGINT UNSIGNED NOT NULL,
  `credit_note_number` VARCHAR(160) NOT NULL,
  `gross_amount` DECIMAL(19,4) NOT NULL,
  `revenue_amount` DECIMAL(19,4) NOT NULL,
  `tax_amount` DECIMAL(19,4) NOT NULL,
  `currency` VARCHAR(10) NOT NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_billing_credit_note_allocation_number` (`billing_tenant_id`,`credit_note_number`),
  KEY `idx_billing_credit_note_allocation_invoice` (`billing_invoice_id`),
  CONSTRAINT `chk_billing_credit_note_allocation_gross` CHECK (`gross_amount` > 0),
  CONSTRAINT `chk_billing_credit_note_allocation_revenue` CHECK (`revenue_amount` >= 0),
  CONSTRAINT `chk_billing_credit_note_allocation_tax` CHECK (`tax_amount` >= 0),
  CONSTRAINT `chk_billing_credit_note_allocation_sum` CHECK (`gross_amount` = `revenue_amount` + `tax_amount`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
