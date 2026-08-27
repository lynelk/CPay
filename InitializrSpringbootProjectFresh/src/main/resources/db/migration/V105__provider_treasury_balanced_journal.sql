-- Extend the V104 provider treasury journal into a balanced double-entry journal.
-- Provider-float legs reference a provider treasury account. Clearing and merchant-exposure
-- counter-legs intentionally have no provider account FK and are identified by ledger_account_code.
ALTER TABLE provider_treasury_journal
    MODIFY treasury_account_id BIGINT NULL,
    ADD COLUMN ledger_account_code VARCHAR(191) NOT NULL AFTER treasury_account_id,
    ADD KEY idx_provider_treasury_journal_ledger_account (ledger_account_code, created_at);
