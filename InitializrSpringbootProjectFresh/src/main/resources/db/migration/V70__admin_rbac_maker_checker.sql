-- P0 §2: Admin RBAC and maker-checker controls.
--
-- Confidence gap closed: previously /api/v2/admin/** required only a single
-- ROLE_ADMIN basic-auth credential, AdminPermissionService.require(...) recorded
-- an audit row but never verified the permission was actually granted, and there was
-- no approval-request lifecycle at all (no table, no maker/checker separation for
-- privileged actions outside the two V33 finance flows).
--
-- This migration adds:
--   1. admin_roles - the P0 role catalog (9 roles) plus the legacy ADMIN role.
--   2. admin_role_assignments - which admin id holds which roles.
--   3. admin_access_matrix - the authorization matrix mapping privileged actions to
--      allowed roles, read/write mode, maker-checker requirement, audit level and
--      environment restriction (queryable, and enforced by AdminPermissionService +
--      AdminApprovalService).
--   4. Broader admin_permissions seeds so the permission table matches the catalog.
--   5. approval_requests - the maker-checker lifecycle table (PENDING_APPROVAL ->
--      APPROVED / REJECTED -> CANCELLED, with requester/checker separation,
--      previous/new state hashes and the action payload).
--   6. Enriched admin_audit_events columns (actor_role, resource_type, resource_id,
--      previous_state_hash, new_state_hash, reason_text, request_id) so every
--      privileged action satisfies the P0 audit field list.
--
-- Everything is additive / idempotent (CREATE TABLE IF NOT EXISTS, INSERT IGNORE,
-- add_column_if_missing) so it is safe against any existing schema state.

DROP PROCEDURE IF EXISTS add_column_if_missing_v70;
DELIMITER //
CREATE PROCEDURE add_column_if_missing_v70(
    IN table_name_value VARCHAR(128),
    IN column_name_value VARCHAR(128),
    IN column_definition_value TEXT
)
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE table_schema = DATABASE()
          AND table_name = table_name_value
          AND column_name = column_name_value
    ) THEN
        SET @ddl = CONCAT('ALTER TABLE `', table_name_value, '` ADD COLUMN `', column_name_value, '` ', column_definition_value);
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END//
DELIMITER ;

-- 1. Role catalog.
CREATE TABLE IF NOT EXISTS `admin_roles` (
  `role_name` VARCHAR(100) NOT NULL,
  `description` VARCHAR(255) NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`role_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT IGNORE INTO `admin_roles` (`role_name`, `description`) VALUES
  ('ADMIN', 'Legacy single administrator role (broad grants)'),
  ('SUPER_ADMIN', 'Full platform administration and escalation'),
  ('OPERATIONS_ADMIN', 'Operational administration: callbacks, reconciliation import, sandbox runs'),
  ('FINANCE_MAKER', 'Finance maker: submits settlements, daily closes, adjustments for approval'),
  ('FINANCE_CHECKER', 'Finance checker: approves or rejects submitted finance actions'),
  ('COMPLIANCE_OFFICER', 'Compliance case review and closure'),
  ('PROVIDER_MANAGER', 'Provider sandbox validation and certification run management'),
  ('SUPPORT_AGENT', 'Support: read-only admin views and audited merchant impersonation'),
  ('READ_ONLY_AUDITOR', 'Audit and access read-only'),
  ('SECURITY_ADMIN', 'Security administration: secrets, role assignments, access control');

-- 2. Admin role assignments.
CREATE TABLE IF NOT EXISTS `admin_role_assignments` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `admin_id` BIGINT UNSIGNED NOT NULL,
  `role_name` VARCHAR(100) NOT NULL,
  `assigned_by` VARCHAR(255) NULL,
  `active_flag` VARCHAR(3) NOT NULL DEFAULT 'YES',
  `assigned_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_admin_role_assignment` (`admin_id`, `role_name`),
  CONSTRAINT `fk_admin_role_assignment_role` FOREIGN KEY (`role_name`) REFERENCES `admin_roles` (`role_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. Authorization matrix (action -> allowed roles, mode, maker-checker, audit, env).
CREATE TABLE IF NOT EXISTS `admin_access_matrix` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `action_code` VARCHAR(150) NOT NULL,
  `action_name` VARCHAR(255) NOT NULL,
  `allowed_roles` VARCHAR(255) NOT NULL,
  `access_mode` VARCHAR(20) NOT NULL DEFAULT 'WRITE',
  `maker_checker_flag` VARCHAR(20) NOT NULL DEFAULT 'NONE',
  `audit_level` VARCHAR(20) NOT NULL DEFAULT 'FULL',
  `environment_restriction` VARCHAR(30) NOT NULL DEFAULT 'ALL',
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_admin_access_matrix_action` (`action_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT IGNORE INTO `admin_access_matrix`
  (`action_code`, `action_name`, `allowed_roles`, `access_mode`, `maker_checker_flag`, `audit_level`, `environment_restriction`)
VALUES
  ('MERCHANT_PRODUCTION_ACTIVATION', 'Merchant production activation', 'SUPER_ADMIN,OPERATIONS_ADMIN', 'WRITE', 'MANDATORY', 'FULL', 'PRODUCTION_ONLY'),
  ('PROVIDER_PRODUCTION_ENABLEMENT', 'Provider production enablement', 'SUPER_ADMIN,PROVIDER_MANAGER', 'WRITE', 'MANDATORY', 'FULL', 'PRODUCTION_ONLY'),
  ('SETTLEMENT_APPROVAL', 'Settlement batch approval', 'SUPER_ADMIN,FINANCE_CHECKER', 'WRITE', 'MANDATORY', 'FULL', 'ALL'),
  ('DAILY_CLOSE', 'Reconciliation daily close', 'SUPER_ADMIN,FINANCE_CHECKER', 'WRITE', 'MANDATORY', 'FULL', 'ALL'),
  ('MANUAL_FINANCE_ADJUSTMENT', 'Manual finance adjustment', 'SUPER_ADMIN,FINANCE_CHECKER', 'WRITE', 'MANDATORY', 'FULL', 'ALL'),
  ('CALLBACK_SECRET_ROTATION', 'Callback secret rotation', 'SUPER_ADMIN,SECURITY_ADMIN', 'WRITE', 'MANDATORY', 'FULL', 'ALL'),
  ('HIGH_VALUE_PAYOUT_RELEASE', 'High-value payout release', 'SUPER_ADMIN,FINANCE_CHECKER', 'WRITE', 'MANDATORY', 'FULL', 'ALL'),
  ('COMPLIANCE_CASE_CLOSURE', 'Compliance case closure', 'SUPER_ADMIN,COMPLIANCE_OFFICER', 'WRITE', 'MANDATORY', 'FULL', 'ALL'),
  ('ADMIN_ROLE_CHANGE', 'Admin role assignment change', 'SUPER_ADMIN,SECURITY_ADMIN', 'WRITE', 'MANDATORY', 'FULL', 'ALL'),
  ('PRODUCTION_CAP_REMOVAL', 'Production transaction cap removal', 'SUPER_ADMIN', 'WRITE', 'MANDATORY', 'FULL', 'PRODUCTION_ONLY'),
  ('APPROVAL_REQUEST_CREATE', 'Submit an approval request (maker)', 'SUPER_ADMIN,OPERATIONS_ADMIN,FINANCE_MAKER,PROVIDER_MANAGER', 'WRITE', 'NONE', 'FULL', 'ALL'),
  ('APPROVAL_REQUEST_APPROVE', 'Approve an approval request (checker)', 'SUPER_ADMIN,FINANCE_CHECKER,COMPLIANCE_OFFICER,SECURITY_ADMIN', 'WRITE', 'NONE', 'FULL', 'ALL'),
  ('APPROVAL_REQUEST_REJECT', 'Reject an approval request (checker)', 'SUPER_ADMIN,FINANCE_CHECKER,COMPLIANCE_OFFICER,SECURITY_ADMIN', 'WRITE', 'NONE', 'FULL', 'ALL'),
  ('APPROVAL_REQUEST_READ', 'Read approval requests and their status', 'SUPER_ADMIN,OPERATIONS_ADMIN,FINANCE_MAKER,FINANCE_CHECKER,COMPLIANCE_OFFICER,PROVIDER_MANAGER,SUPPORT_AGENT,READ_ONLY_AUDITOR,SECURITY_ADMIN', 'READ', 'NONE', 'BASIC', 'ALL'),
  ('ACCESS_CONTROL_READ', 'Read roles, permissions and the access matrix', 'SUPER_ADMIN,OPERATIONS_ADMIN,SUPPORT_AGENT,READ_ONLY_AUDITOR,SECURITY_ADMIN', 'READ', 'NONE', 'BASIC', 'ALL'),
  ('ACCESS_CONTROL_MANAGE', 'Manage access-control entries', 'SUPER_ADMIN,SECURITY_ADMIN', 'WRITE', 'NONE', 'FULL', 'ALL'),
  ('ADMIN_IMPERSONATE_MERCHANT', 'Start an audited merchant impersonation session', 'SUPER_ADMIN,SUPPORT_AGENT', 'WRITE', 'NONE', 'FULL', 'ALL'),
  ('CERTIFICATION_RUN_MANAGE', 'Create and drive provider certification runs', 'SUPER_ADMIN,PROVIDER_MANAGER', 'WRITE', 'NONE', 'FULL', 'ALL'),
  ('PAYOUT_APPROVE', 'Approve a queued payout', 'SUPER_ADMIN,FINANCE_CHECKER', 'WRITE', 'MANDATORY', 'FULL', 'ALL'),
  ('PAYOUT_CREATE', 'Create a payout', 'SUPER_ADMIN,FINANCE_MAKER', 'WRITE', 'NONE', 'FULL', 'ALL'),
  ('BALANCE_BACKFILL', 'Run normalized balance backfill', 'SUPER_ADMIN,OPERATIONS_ADMIN', 'WRITE', 'NONE', 'FULL', 'ALL'),
  ('CALLBACK_OPERATIONS', 'Manage callback retry and replay operations', 'SUPER_ADMIN,OPERATIONS_ADMIN', 'WRITE', 'NONE', 'FULL', 'ALL'),
  ('RECONCILIATION_IMPORT', 'Import and validate provider statements', 'SUPER_ADMIN,OPERATIONS_ADMIN,FINANCE_MAKER', 'WRITE', 'NONE', 'FULL', 'ALL'),
  ('RECONCILIATION_APPROVE', 'Approve reconciliation reviews', 'SUPER_ADMIN,FINANCE_CHECKER', 'WRITE', 'MANDATORY', 'FULL', 'ALL'),
  ('PROVIDER_SANDBOX_VALIDATION', 'Record and run provider sandbox scenarios', 'SUPER_ADMIN,OPERATIONS_ADMIN,PROVIDER_MANAGER', 'WRITE', 'NONE', 'FULL', 'SANDBOX_ONLY'),
  ('WEBHOOK_SECRET_ROTATE', 'Rotate merchant webhook signing secrets', 'SUPER_ADMIN,SECURITY_ADMIN', 'WRITE', 'MANDATORY', 'FULL', 'ALL'),
  ('ADMIN_PERMISSION_MANAGE', 'Manage admin permission seeds', 'SUPER_ADMIN,SECURITY_ADMIN', 'WRITE', 'NONE', 'FULL', 'ALL');

-- 4. Broaden admin_permissions to match the catalog; the legacy ADMIN role keeps
--    every capability it had plus the new control codes so existing single-admin
--    deployments continue to work while fine-grained roles are being adopted.
INSERT IGNORE INTO `admin_permissions` (`role_name`, `permission_code`) VALUES
  ('ADMIN', 'BALANCE_BACKFILL'),
  ('ADMIN', 'CALLBACK_OPERATIONS'),
  ('ADMIN', 'RECONCILIATION_IMPORT'),
  ('ADMIN', 'RECONCILIATION_APPROVE'),
  ('ADMIN', 'PROVIDER_SANDBOX_VALIDATION'),
  ('ADMIN', 'ADMIN_IMPERSONATE_MERCHANT'),
  ('ADMIN', 'ADMIN_PERMISSION_MANAGE'),
  ('ADMIN', 'MERCHANT_PRODUCTION_ACTIVATION'),
  ('ADMIN', 'PROVIDER_PRODUCTION_ENABLEMENT'),
  ('ADMIN', 'SETTLEMENT_APPROVAL'),
  ('ADMIN', 'DAILY_CLOSE'),
  ('ADMIN', 'MANUAL_FINANCE_ADJUSTMENT'),
  ('ADMIN', 'CALLBACK_SECRET_ROTATION'),
  ('ADMIN', 'HIGH_VALUE_PAYOUT_RELEASE'),
  ('ADMIN', 'COMPLIANCE_CASE_CLOSURE'),
  ('ADMIN', 'ADMIN_ROLE_CHANGE'),
  ('ADMIN', 'PRODUCTION_CAP_REMOVAL'),
  ('ADMIN', 'APPROVAL_REQUEST_CREATE'),
  ('ADMIN', 'APPROVAL_REQUEST_APPROVE'),
  ('ADMIN', 'APPROVAL_REQUEST_REJECT'),
  ('ADMIN', 'APPROVAL_REQUEST_READ'),
  ('ADMIN', 'ACCESS_CONTROL_READ'),
  ('ADMIN', 'ACCESS_CONTROL_MANAGE'),
  ('ADMIN', 'CERTIFICATION_RUN_MANAGE'),
  ('ADMIN', 'PAYOUT_APPROVE'),
  ('ADMIN', 'PAYOUT_CREATE'),
  ('ADMIN', 'WEBHOOK_SECRET_ROTATE'),
  ('SUPER_ADMIN', 'BALANCE_BACKFILL'),
  ('SUPER_ADMIN', 'CALLBACK_OPERATIONS'),
  ('SUPER_ADMIN', 'RECONCILIATION_IMPORT'),
  ('SUPER_ADMIN', 'RECONCILIATION_APPROVE'),
  ('SUPER_ADMIN', 'PROVIDER_SANDBOX_VALIDATION'),
  ('SUPER_ADMIN', 'ADMIN_IMPERSONATE_MERCHANT'),
  ('SUPER_ADMIN', 'ADMIN_PERMISSION_MANAGE'),
  ('SUPER_ADMIN', 'MERCHANT_PRODUCTION_ACTIVATION'),
  ('SUPER_ADMIN', 'PROVIDER_PRODUCTION_ENABLEMENT'),
  ('SUPER_ADMIN', 'SETTLEMENT_APPROVAL'),
  ('SUPER_ADMIN', 'DAILY_CLOSE'),
  ('SUPER_ADMIN', 'MANUAL_FINANCE_ADJUSTMENT'),
  ('SUPER_ADMIN', 'CALLBACK_SECRET_ROTATION'),
  ('SUPER_ADMIN', 'HIGH_VALUE_PAYOUT_RELEASE'),
  ('SUPER_ADMIN', 'COMPLIANCE_CASE_CLOSURE'),
  ('SUPER_ADMIN', 'ADMIN_ROLE_CHANGE'),
  ('SUPER_ADMIN', 'PRODUCTION_CAP_REMOVAL'),
  ('SUPER_ADMIN', 'APPROVAL_REQUEST_CREATE'),
  ('SUPER_ADMIN', 'APPROVAL_REQUEST_APPROVE'),
  ('SUPER_ADMIN', 'APPROVAL_REQUEST_REJECT'),
  ('SUPER_ADMIN', 'APPROVAL_REQUEST_READ'),
  ('SUPER_ADMIN', 'ACCESS_CONTROL_READ'),
  ('SUPER_ADMIN', 'ACCESS_CONTROL_MANAGE'),
  ('SUPER_ADMIN', 'CERTIFICATION_RUN_MANAGE'),
  ('SUPER_ADMIN', 'PAYOUT_APPROVE'),
  ('SUPER_ADMIN', 'PAYOUT_CREATE'),
  ('SUPER_ADMIN', 'WEBHOOK_SECRET_ROTATE'),
  ('OPERATIONS_ADMIN', 'BALANCE_BACKFILL'),
  ('OPERATIONS_ADMIN', 'CALLBACK_OPERATIONS'),
  ('OPERATIONS_ADMIN', 'RECONCILIATION_IMPORT'),
  ('OPERATIONS_ADMIN', 'MERCHANT_PRODUCTION_ACTIVATION'),
  ('OPERATIONS_ADMIN', 'PROVIDER_SANDBOX_VALIDATION'),
  ('OPERATIONS_ADMIN', 'APPROVAL_REQUEST_CREATE'),
  ('OPERATIONS_ADMIN', 'APPROVAL_REQUEST_READ'),
  ('OPERATIONS_ADMIN', 'ACCESS_CONTROL_READ'),
  ('FINANCE_MAKER', 'RECONCILIATION_IMPORT'),
  ('FINANCE_MAKER', 'PAYOUT_CREATE'),
  ('FINANCE_MAKER', 'APPROVAL_REQUEST_CREATE'),
  ('FINANCE_MAKER', 'APPROVAL_REQUEST_READ'),
  ('FINANCE_CHECKER', 'RECONCILIATION_APPROVE'),
  ('FINANCE_CHECKER', 'SETTLEMENT_APPROVAL'),
  ('FINANCE_CHECKER', 'DAILY_CLOSE'),
  ('FINANCE_CHECKER', 'MANUAL_FINANCE_ADJUSTMENT'),
  ('FINANCE_CHECKER', 'HIGH_VALUE_PAYOUT_RELEASE'),
  ('FINANCE_CHECKER', 'PAYOUT_APPROVE'),
  ('FINANCE_CHECKER', 'APPROVAL_REQUEST_APPROVE'),
  ('FINANCE_CHECKER', 'APPROVAL_REQUEST_REJECT'),
  ('FINANCE_CHECKER', 'APPROVAL_REQUEST_READ'),
  ('COMPLIANCE_OFFICER', 'COMPLIANCE_CASE_CLOSURE'),
  ('COMPLIANCE_OFFICER', 'APPROVAL_REQUEST_APPROVE'),
  ('COMPLIANCE_OFFICER', 'APPROVAL_REQUEST_REJECT'),
  ('COMPLIANCE_OFFICER', 'APPROVAL_REQUEST_READ'),
  ('PROVIDER_MANAGER', 'PROVIDER_PRODUCTION_ENABLEMENT'),
  ('PROVIDER_MANAGER', 'PROVIDER_SANDBOX_VALIDATION'),
  ('PROVIDER_MANAGER', 'CERTIFICATION_RUN_MANAGE'),
  ('PROVIDER_MANAGER', 'APPROVAL_REQUEST_CREATE'),
  ('PROVIDER_MANAGER', 'APPROVAL_REQUEST_READ'),
  ('SUPPORT_AGENT', 'ADMIN_IMPERSONATE_MERCHANT'),
  ('SUPPORT_AGENT', 'APPROVAL_REQUEST_READ'),
  ('SUPPORT_AGENT', 'ACCESS_CONTROL_READ'),
  ('READ_ONLY_AUDITOR', 'APPROVAL_REQUEST_READ'),
  ('READ_ONLY_AUDITOR', 'ACCESS_CONTROL_READ'),
  ('SECURITY_ADMIN', 'CALLBACK_SECRET_ROTATION'),
  ('SECURITY_ADMIN', 'WEBHOOK_SECRET_ROTATE'),
  ('SECURITY_ADMIN', 'ADMIN_ROLE_CHANGE'),
  ('SECURITY_ADMIN', 'ACCESS_CONTROL_READ'),
  ('SECURITY_ADMIN', 'ACCESS_CONTROL_MANAGE'),
  ('SECURITY_ADMIN', 'ADMIN_PERMISSION_MANAGE'),
  ('SECURITY_ADMIN', 'APPROVAL_REQUEST_APPROVE'),
  ('SECURITY_ADMIN', 'APPROVAL_REQUEST_REJECT'),
  ('SECURITY_ADMIN', 'APPROVAL_REQUEST_READ');

-- 5. Maker-checker approval request lifecycle table.
CREATE TABLE IF NOT EXISTS `approval_requests` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `request_reference` VARCHAR(100) NOT NULL,
  `approval_type` VARCHAR(100) NOT NULL,
  `request_status` VARCHAR(40) NOT NULL DEFAULT 'PENDING_APPROVAL',
  `resource_type` VARCHAR(100) NOT NULL,
  `resource_id` VARCHAR(150) NOT NULL,
  `request_payload` JSON NULL,
  `requested_by` VARCHAR(255) NOT NULL,
  `requested_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `approved_by` VARCHAR(255) NULL,
  `approved_at` TIMESTAMP NULL,
  `rejected_by` VARCHAR(255) NULL,
  `rejected_at` TIMESTAMP NULL,
  `rejection_reason` TEXT NULL,
  `review_note` TEXT NULL,
  `previous_state_hash` VARCHAR(64) NULL,
  `new_state_hash` VARCHAR(64) NULL,
  `expires_at` TIMESTAMP NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_approval_request_reference` (`request_reference`),
  KEY `idx_approval_request_status` (`request_status`, `approval_type`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 6. Enrich admin_audit_events with the P0 audit field list.
CALL add_column_if_missing_v70('admin_audit_events', 'actor_role', 'VARCHAR(255) NULL');
CALL add_column_if_missing_v70('admin_audit_events', 'resource_type', 'VARCHAR(100) NULL');
CALL add_column_if_missing_v70('admin_audit_events', 'resource_id', 'VARCHAR(150) NULL');
CALL add_column_if_missing_v70('admin_audit_events', 'previous_state_hash', 'VARCHAR(64) NULL');
CALL add_column_if_missing_v70('admin_audit_events', 'new_state_hash', 'VARCHAR(64) NULL');
CALL add_column_if_missing_v70('admin_audit_events', 'reason_text', 'TEXT NULL');
CALL add_column_if_missing_v70('admin_audit_events', 'request_id', 'VARCHAR(100) NULL');

DROP PROCEDURE IF EXISTS add_column_if_missing_v70;
