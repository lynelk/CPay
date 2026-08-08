-- Billing engine Phase 3, Slice 27 (see Docs/Adr/0004-billing-ledger-integration.md): traces a
-- ledger posting back to the billing artifact (charge, invoice, payment, cost, reversal) that
-- caused it, without touching ledger_transactions/ledger_entries themselves - the billing module
-- populates this table immediately after every DoubleEntryLedgerService.post()/reverse() call, via
-- the not-yet-built BillingLedgerAccountTemplateService (the only place in the billing module
-- allowed to call post()/reverse() directly).
--
-- No foreign keys, matching this schema's existing convention.

CREATE TABLE IF NOT EXISTS `billing_ledger_links` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `ledger_transaction_id` BIGINT UNSIGNED NOT NULL,
  `ledger_entry_id` BIGINT UNSIGNED NULL,
  `billing_tenant_id` BIGINT UNSIGNED NOT NULL,
  `link_type` VARCHAR(20) NOT NULL,
  `billing_reference` VARCHAR(191) NOT NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_billing_ledger_link_transaction` (`ledger_transaction_id`),
  KEY `idx_billing_ledger_link_tenant_ref` (`billing_tenant_id`, `link_type`, `billing_reference`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
