-- P1 §1: Settlement lifecycle enforcement.
--
-- Confidence gap closed: finance_settlement_batches already carried lifecycle
-- columns (calculated_at/approved_at/...) but FinanceOperationsController accepted
-- arbitrary status transitions with no state machine, no maker/checker separation
-- and no close gates. This migration adds:
--   1. settlement_state_transitions - append-only audit trail for every applied
--      settlement state change (mirrors the payment_state_transitions pattern).
--   2. SETTLEMENT_MANAGE permission seeds so the maker-side lifecycle steps
--      (calculate, submit-review, mark-paid, reconcile, close) are permission
--      gated exactly like the existing SETTLEMENT_APPROVAL checker step.
--   3. The corresponding admin_access_matrix row so the catalog stays the source
--      of truth for which roles may drive the lifecycle.
--
-- Everything is additive / idempotent (CREATE TABLE IF NOT EXISTS, INSERT IGNORE)
-- so it is safe against any existing schema state.

CREATE TABLE IF NOT EXISTS settlement_state_transitions (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    settlement_batch_id BIGINT NOT NULL,
    settlement_reference VARCHAR(80) NOT NULL,
    previous_status VARCHAR(40) NULL,
    next_status VARCHAR(40) NOT NULL,
    actor VARCHAR(120) NULL,
    reason TEXT NULL,
    request_id VARCHAR(100) NULL,
    transition_result VARCHAR(20) NOT NULL DEFAULT 'APPLIED',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_settlement_state_transition_batch (settlement_batch_id, created_at),
    KEY idx_settlement_state_transition_reference (settlement_reference, created_at),
    KEY idx_settlement_state_transition_next (next_status, created_at),
    CONSTRAINT fk_settlement_state_transition_batch
        FOREIGN KEY (settlement_batch_id) REFERENCES finance_settlement_batches(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Maker-side lifecycle permission: calculate, submit review, mark paid, reconcile, close.
INSERT IGNORE INTO admin_permissions (role_name, permission_code) VALUES
  ('ADMIN', 'SETTLEMENT_MANAGE'),
  ('SUPER_ADMIN', 'SETTLEMENT_MANAGE'),
  ('FINANCE_MAKER', 'SETTLEMENT_MANAGE');

INSERT IGNORE INTO admin_access_matrix
  (action_code, action_name, allowed_roles, access_mode, maker_checker_flag, audit_level, environment_restriction)
VALUES
  ('SETTLEMENT_MANAGE', 'Settlement lifecycle management (maker steps)', 'SUPER_ADMIN,FINANCE_MAKER', 'WRITE', 'NONE', 'FULL', 'ALL');
