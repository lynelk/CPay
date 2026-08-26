-- Close BaaS P0 maker-checker evidence and one-time protected-action gaps.
ALTER TABLE `billing_baas_tenant_profiles`
  ADD COLUMN `activated_by` VARCHAR(191) NULL AFTER `approved_at`,
  ADD COLUMN `activated_at` TIMESTAMP NULL AFTER `activated_by`;

ALTER TABLE `billing_protected_action_requests`
  ADD COLUMN `consumed_by` VARCHAR(191) NULL AFTER `decision_reason`,
  ADD COLUMN `consumed_at` TIMESTAMP NULL AFTER `consumed_by`;

ALTER TABLE `billing_protected_action_requests`
  DROP CHECK `chk_billing_protected_action_status`,
  ADD CONSTRAINT `chk_billing_protected_action_status`
    CHECK (`status` IN ('PENDING','APPROVED','REJECTED','EXPIRED','CONSUMED'));
