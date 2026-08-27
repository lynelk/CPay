-- CPay shared-provider execution and provider treasury control plane.
-- V104 closes two production control gaps:
--   1. explicitly approved merchants may fall back to CPay-owned provider credentials;
--   2. CPay-owned provider float is managed through reservations, maker-checker adjustments,
--      merchant exposure, immutable journal entries and reconciliation evidence.

CREATE TABLE IF NOT EXISTS shared_provider_entitlements (
    id BIGINT NOT NULL AUTO_INCREMENT,
    merchant_id BIGINT NOT NULL,
    channel_code VARCHAR(64) NOT NULL,
    environment VARCHAR(16) NOT NULL,
    country_code VARCHAR(8) NOT NULL,
    currency_code VARCHAR(8) NOT NULL,
    operation VARCHAR(16) NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    per_transaction_limit DECIMAL(19,4) NULL,
    daily_limit DECIMAL(19,4) NULL,
    requested_by VARCHAR(255) NOT NULL,
    requested_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    approved_by VARCHAR(255) NULL,
    approved_at DATETIME(6) NULL,
    rejected_by VARCHAR(255) NULL,
    rejected_at DATETIME(6) NULL,
    disabled_by VARCHAR(255) NULL,
    disabled_at DATETIME(6) NULL,
    notes VARCHAR(1000) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_shared_provider_entitlement_scope
        (merchant_id, channel_code, environment, country_code, currency_code, operation),
    KEY idx_shared_provider_entitlement_lookup
        (merchant_id, channel_code, environment, country_code, currency_code, operation, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS shared_provider_daily_usage (
    id BIGINT NOT NULL AUTO_INCREMENT,
    entitlement_id BIGINT NOT NULL,
    usage_date DATE NOT NULL,
    operation VARCHAR(16) NOT NULL,
    approved_amount DECIMAL(19,4) NOT NULL DEFAULT 0.0000,
    transaction_count BIGINT NOT NULL DEFAULT 0,
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_shared_provider_daily_usage (entitlement_id, usage_date, operation),
    CONSTRAINT fk_shared_provider_usage_entitlement FOREIGN KEY (entitlement_id)
        REFERENCES shared_provider_entitlements(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS platform_channel_credentials (
    id BIGINT NOT NULL AUTO_INCREMENT,
    channel_code VARCHAR(64) NOT NULL,
    environment VARCHAR(16) NOT NULL,
    country_code VARCHAR(8) NOT NULL,
    currency_code VARCHAR(8) NOT NULL,
    credential_payload LONGTEXT NOT NULL,
    credential_mask LONGTEXT NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'CONFIGURED',
    created_by VARCHAR(255) NOT NULL,
    updated_by VARCHAR(255) NOT NULL,
    approved_by VARCHAR(255) NULL,
    approved_at DATETIME(6) NULL,
    disabled_by VARCHAR(255) NULL,
    disabled_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_platform_channel_credential_scope
        (channel_code, environment, country_code, currency_code),
    KEY idx_platform_channel_credential_status
        (channel_code, environment, country_code, currency_code, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS provider_treasury_accounts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    channel_code VARCHAR(64) NOT NULL,
    environment VARCHAR(16) NOT NULL,
    country_code VARCHAR(8) NOT NULL,
    currency_code VARCHAR(8) NOT NULL,
    book_balance DECIMAL(19,4) NOT NULL DEFAULT 0.0000,
    reserved_balance DECIMAL(19,4) NOT NULL DEFAULT 0.0000,
    pending_outgoing_balance DECIMAL(19,4) NOT NULL DEFAULT 0.0000,
    pending_incoming_balance DECIMAL(19,4) NOT NULL DEFAULT 0.0000,
    provider_reported_balance DECIMAL(19,4) NULL,
    low_float_threshold DECIMAL(19,4) NOT NULL DEFAULT 0.0000,
    reconciliation_state VARCHAR(24) NOT NULL DEFAULT 'UNRECONCILED',
    lock_version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_provider_treasury_scope
        (channel_code, environment, country_code, currency_code),
    KEY idx_provider_treasury_reconciliation (reconciliation_state, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS provider_treasury_reservations (
    id BIGINT NOT NULL AUTO_INCREMENT,
    idempotency_key VARCHAR(191) NOT NULL,
    treasury_account_id BIGINT NOT NULL,
    entitlement_id BIGINT NULL,
    merchant_id BIGINT NOT NULL,
    merchant_number VARCHAR(128) NOT NULL,
    operation VARCHAR(16) NOT NULL,
    direction VARCHAR(16) NOT NULL,
    amount DECIMAL(19,4) NOT NULL,
    currency_code VARCHAR(8) NOT NULL,
    merchant_reference VARCHAR(191) NOT NULL,
    provider_reference VARCHAR(191) NULL,
    status VARCHAR(24) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    settled_at DATETIME(6) NULL,
    released_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_provider_treasury_reservation_idempotency (idempotency_key),
    KEY idx_provider_treasury_reservation_account_status (treasury_account_id, status),
    KEY idx_provider_treasury_reservation_merchant (merchant_id, created_at),
    CONSTRAINT fk_provider_treasury_reservation_account FOREIGN KEY (treasury_account_id)
        REFERENCES provider_treasury_accounts(id),
    CONSTRAINT fk_provider_treasury_reservation_entitlement FOREIGN KEY (entitlement_id)
        REFERENCES shared_provider_entitlements(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS merchant_provider_exposures (
    id BIGINT NOT NULL AUTO_INCREMENT,
    merchant_id BIGINT NOT NULL,
    channel_code VARCHAR(64) NOT NULL,
    environment VARCHAR(16) NOT NULL,
    country_code VARCHAR(8) NOT NULL,
    currency_code VARCHAR(8) NOT NULL,
    reserved_outgoing DECIMAL(19,4) NOT NULL DEFAULT 0.0000,
    pending_outgoing DECIMAL(19,4) NOT NULL DEFAULT 0.0000,
    pending_incoming DECIMAL(19,4) NOT NULL DEFAULT 0.0000,
    settled_net DECIMAL(19,4) NOT NULL DEFAULT 0.0000,
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_merchant_provider_exposure_scope
        (merchant_id, channel_code, environment, country_code, currency_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS provider_treasury_adjustments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    idempotency_key VARCHAR(191) NOT NULL,
    adjustment_type VARCHAR(24) NOT NULL,
    source_account_id BIGINT NULL,
    destination_account_id BIGINT NULL,
    amount DECIMAL(19,4) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    external_reference VARCHAR(191) NOT NULL,
    evidence_reference VARCHAR(500) NULL,
    value_date DATE NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    request_hash VARCHAR(64) NOT NULL,
    requested_by VARCHAR(255) NOT NULL,
    requested_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    approved_by VARCHAR(255) NULL,
    approved_at DATETIME(6) NULL,
    rejected_by VARCHAR(255) NULL,
    rejected_at DATETIME(6) NULL,
    posted_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_provider_treasury_adjustment_idempotency (idempotency_key),
    UNIQUE KEY uq_provider_treasury_adjustment_request_hash (request_hash),
    KEY idx_provider_treasury_adjustment_status (status, requested_at),
    CONSTRAINT fk_provider_treasury_adjustment_source FOREIGN KEY (source_account_id)
        REFERENCES provider_treasury_accounts(id),
    CONSTRAINT fk_provider_treasury_adjustment_destination FOREIGN KEY (destination_account_id)
        REFERENCES provider_treasury_accounts(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS provider_treasury_journal (
    id BIGINT NOT NULL AUTO_INCREMENT,
    entry_group VARCHAR(191) NOT NULL,
    sequence_no INT NOT NULL,
    treasury_account_id BIGINT NOT NULL,
    merchant_id BIGINT NULL,
    reservation_id BIGINT NULL,
    adjustment_id BIGINT NULL,
    transaction_reference VARCHAR(191) NULL,
    entry_type VARCHAR(32) NOT NULL,
    entry_side VARCHAR(8) NOT NULL,
    amount DECIMAL(19,4) NOT NULL,
    currency_code VARCHAR(8) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    external_reference VARCHAR(191) NULL,
    previous_hash VARCHAR(64) NULL,
    entry_hash VARCHAR(64) NOT NULL,
    actor VARCHAR(255) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_provider_treasury_journal_group_sequence (entry_group, sequence_no),
    UNIQUE KEY uq_provider_treasury_journal_hash (entry_hash),
    KEY idx_provider_treasury_journal_account_created (treasury_account_id, created_at),
    KEY idx_provider_treasury_journal_merchant_created (merchant_id, created_at),
    CONSTRAINT fk_provider_treasury_journal_account FOREIGN KEY (treasury_account_id)
        REFERENCES provider_treasury_accounts(id),
    CONSTRAINT fk_provider_treasury_journal_reservation FOREIGN KEY (reservation_id)
        REFERENCES provider_treasury_reservations(id),
    CONSTRAINT fk_provider_treasury_journal_adjustment FOREIGN KEY (adjustment_id)
        REFERENCES provider_treasury_adjustments(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS provider_treasury_reconciliations (
    id BIGINT NOT NULL AUTO_INCREMENT,
    treasury_account_id BIGINT NOT NULL,
    statement_reference VARCHAR(191) NOT NULL,
    evidence_reference VARCHAR(500) NULL,
    book_balance DECIMAL(19,4) NOT NULL,
    provider_reported_balance DECIMAL(19,4) NOT NULL,
    variance DECIMAL(19,4) NOT NULL,
    state VARCHAR(24) NOT NULL,
    notes VARCHAR(1000) NULL,
    reconciled_by VARCHAR(255) NOT NULL,
    reconciled_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_provider_treasury_reconciliation_statement
        (treasury_account_id, statement_reference),
    KEY idx_provider_treasury_reconciliation_state (state, reconciled_at),
    CONSTRAINT fk_provider_treasury_reconciliation_account FOREIGN KEY (treasury_account_id)
        REFERENCES provider_treasury_accounts(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Seed the platform-owned mobile-money treasury scopes at zero. Funds must be introduced only
-- through an approved CREDIT adjustment or a confirmed shared-provider collection.
INSERT INTO provider_treasury_accounts
    (channel_code, environment, country_code, currency_code, low_float_threshold)
VALUES
    ('airtel_money', 'PRODUCTION', 'UG', 'UGX', 0.0000),
    ('airtel_open_api', 'PRODUCTION', 'UG', 'UGX', 0.0000),
    ('mtn_momo', 'PRODUCTION', 'UG', 'UGX', 0.0000),
    ('safaricom_mpesa', 'PRODUCTION', 'KE', 'KES', 0.0000)
ON DUPLICATE KEY UPDATE channel_code = VALUES(channel_code);
