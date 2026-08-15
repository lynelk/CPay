ALTER TABLE `ledger_accounts`
  ADD COLUMN `owner_scope_id` BIGINT UNSIGNED NOT NULL DEFAULT 0 AFTER `owner_id`;

UPDATE `ledger_accounts`
SET `owner_scope_id` = COALESCE(`owner_id`, 0)
WHERE `owner_scope_id` = 0;

ALTER TABLE `ledger_accounts`
  DROP KEY `uk_ledger_account_code`,
  ADD UNIQUE KEY `uk_ledger_account_scope` (`owner_type`, `owner_scope_id`, `currency`, `account_code`),
  ADD KEY `idx_ledger_account_type_scope` (`account_type`, `owner_type`, `owner_scope_id`, `currency`);
