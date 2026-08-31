-- Production hotfix: V89 marketplace tables used MySQL's server-default utf8mb4 collation,
-- while V19 refunds explicitly use utf8mb4_unicode_ci. On MySQL 8+/9 this makes the
-- refund-to-split reconciliation join fail with error 1267 (illegal mix of collations).
--
-- Align only the shared transaction-reference column used in the cross-domain join. Preserve
-- its existing length, nullability and unique-index semantics while making the financial
-- reference boundary deterministic across MySQL server versions.
ALTER TABLE `marketplace_split_executions`
  MODIFY COLUMN `transaction_reference` VARCHAR(120)
    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL;
