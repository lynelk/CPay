-- P1 Finance and Operations foundation
-- Adds settlement lifecycle, treasury positions, reconciliation exceptions,
-- daily close enforcement records, report/export requests, and incident management.

CREATE TABLE IF NOT EXISTS finance_settlement_batches (
    id BIGSERIAL PRIMARY KEY,
    settlement_reference VARCHAR(80) NOT NULL UNIQUE,
    merchant_id BIGINT,
    provider_code VARCHAR(64),
    channel_code VARCHAR(64),
    country_code VARCHAR(3),
    currency_code VARCHAR(3) NOT NULL,
    business_date DATE NOT NULL,
    settlement_cycle VARCHAR(40) NOT NULL DEFAULT 'DAILY',
    status VARCHAR(40) NOT NULL DEFAULT 'OPEN',
    gross_amount NUMERIC(24, 6) NOT NULL DEFAULT 0,
    fee_amount NUMERIC(24, 6) NOT NULL DEFAULT 0,
    tax_amount NUMERIC(24, 6) NOT NULL DEFAULT 0,
    adjustment_amount NUMERIC(24, 6) NOT NULL DEFAULT 0,
    net_amount NUMERIC(24, 6) NOT NULL DEFAULT 0,
    variance_amount NUMERIC(24, 6) NOT NULL DEFAULT 0,
    item_count INTEGER NOT NULL DEFAULT 0,
    calculated_at TIMESTAMP,
    review_requested_at TIMESTAMP,
    approved_at TIMESTAMP,
    paid_at TIMESTAMP,
    reconciled_at TIMESTAMP,
    closed_at TIMESTAMP,
    created_by VARCHAR(120),
    approved_by VARCHAR(120),
    finance_owner VARCHAR(120),
    notes TEXT,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT finance_settlement_batches_status_chk CHECK (status IN (
        'OPEN', 'CALCULATED', 'REVIEW_PENDING', 'APPROVED', 'PAID', 'RECONCILED', 'EXCEPTION', 'CLOSED'
    ))
);

CREATE INDEX IF NOT EXISTS idx_finance_settlement_batches_status
    ON finance_settlement_batches(status);
CREATE INDEX IF NOT EXISTS idx_finance_settlement_batches_business_date
    ON finance_settlement_batches(business_date);
CREATE INDEX IF NOT EXISTS idx_finance_settlement_batches_merchant
    ON finance_settlement_batches(merchant_id);
CREATE INDEX IF NOT EXISTS idx_finance_settlement_batches_provider
    ON finance_settlement_batches(provider_code, channel_code, country_code, currency_code);

CREATE TABLE IF NOT EXISTS finance_settlement_items (
    id BIGSERIAL PRIMARY KEY,
    settlement_batch_id BIGINT NOT NULL REFERENCES finance_settlement_batches(id) ON DELETE CASCADE,
    transaction_reference VARCHAR(120) NOT NULL,
    provider_reference VARCHAR(120),
    merchant_reference VARCHAR(120),
    transaction_type VARCHAR(40) NOT NULL,
    status VARCHAR(40) NOT NULL DEFAULT 'INCLUDED',
    amount NUMERIC(24, 6) NOT NULL DEFAULT 0,
    fee_amount NUMERIC(24, 6) NOT NULL DEFAULT 0,
    tax_amount NUMERIC(24, 6) NOT NULL DEFAULT 0,
    net_amount NUMERIC(24, 6) NOT NULL DEFAULT 0,
    variance_amount NUMERIC(24, 6) NOT NULL DEFAULT 0,
    transaction_created_at TIMESTAMP,
    provider_completed_at TIMESTAMP,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (settlement_batch_id, transaction_reference)
);

CREATE INDEX IF NOT EXISTS idx_finance_settlement_items_batch
    ON finance_settlement_items(settlement_batch_id);
CREATE INDEX IF NOT EXISTS idx_finance_settlement_items_reference
    ON finance_settlement_items(transaction_reference);

CREATE TABLE IF NOT EXISTS treasury_positions (
    id BIGSERIAL PRIMARY KEY,
    position_reference VARCHAR(80) NOT NULL UNIQUE,
    merchant_id BIGINT,
    provider_code VARCHAR(64),
    channel_code VARCHAR(64),
    country_code VARCHAR(3),
    currency_code VARCHAR(3) NOT NULL,
    position_date DATE NOT NULL,
    available_balance NUMERIC(24, 6) NOT NULL DEFAULT 0,
    reserved_balance NUMERIC(24, 6) NOT NULL DEFAULT 0,
    pending_payout_exposure NUMERIC(24, 6) NOT NULL DEFAULT 0,
    unsettled_receivable NUMERIC(24, 6) NOT NULL DEFAULT 0,
    unsettled_payable NUMERIC(24, 6) NOT NULL DEFAULT 0,
    unreconciled_exposure NUMERIC(24, 6) NOT NULL DEFAULT 0,
    source VARCHAR(80) NOT NULL DEFAULT 'INTERNAL',
    captured_by VARCHAR(120),
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (merchant_id, provider_code, channel_code, country_code, currency_code, position_date, source)
);

CREATE INDEX IF NOT EXISTS idx_treasury_positions_date
    ON treasury_positions(position_date);
CREATE INDEX IF NOT EXISTS idx_treasury_positions_provider
    ON treasury_positions(provider_code, channel_code, country_code, currency_code);

CREATE TABLE IF NOT EXISTS reconciliation_exceptions (
    id BIGSERIAL PRIMARY KEY,
    exception_reference VARCHAR(80) NOT NULL UNIQUE,
    settlement_batch_id BIGINT REFERENCES finance_settlement_batches(id),
    transaction_reference VARCHAR(120),
    provider_reference VARCHAR(120),
    merchant_id BIGINT,
    provider_code VARCHAR(64),
    channel_code VARCHAR(64),
    currency_code VARCHAR(3),
    exception_type VARCHAR(60) NOT NULL,
    severity VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    status VARCHAR(40) NOT NULL DEFAULT 'OPEN',
    internal_amount NUMERIC(24, 6),
    provider_amount NUMERIC(24, 6),
    variance_amount NUMERIC(24, 6),
    assigned_to VARCHAR(120),
    resolution_reason TEXT,
    resolved_by VARCHAR(120),
    resolved_at TIMESTAMP,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT reconciliation_exception_severity_chk CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    CONSTRAINT reconciliation_exception_status_chk CHECK (status IN ('OPEN', 'ASSIGNED', 'UNDER_REVIEW', 'RESOLVED', 'APPROVED', 'REJECTED', 'CLOSED'))
);

CREATE INDEX IF NOT EXISTS idx_reconciliation_exceptions_status
    ON reconciliation_exceptions(status, severity);
CREATE INDEX IF NOT EXISTS idx_reconciliation_exceptions_batch
    ON reconciliation_exceptions(settlement_batch_id);

CREATE TABLE IF NOT EXISTS finance_daily_close_records (
    id BIGSERIAL PRIMARY KEY,
    close_reference VARCHAR(80) NOT NULL UNIQUE,
    business_date DATE NOT NULL UNIQUE,
    status VARCHAR(40) NOT NULL DEFAULT 'OPEN',
    provider_statements_received BOOLEAN NOT NULL DEFAULT FALSE,
    reconciliation_import_completed BOOLEAN NOT NULL DEFAULT FALSE,
    unmatched_items_reviewed BOOLEAN NOT NULL DEFAULT FALSE,
    high_severity_controls_resolved BOOLEAN NOT NULL DEFAULT FALSE,
    maker_checker_approvals_complete BOOLEAN NOT NULL DEFAULT FALSE,
    finance_owner_signed_off BOOLEAN NOT NULL DEFAULT FALSE,
    blocked_reason TEXT,
    opened_by VARCHAR(120),
    submitted_by VARCHAR(120),
    approved_by VARCHAR(120),
    closed_by VARCHAR(120),
    submitted_at TIMESTAMP,
    approved_at TIMESTAMP,
    closed_at TIMESTAMP,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT finance_daily_close_status_chk CHECK (status IN ('OPEN', 'BLOCKED', 'READY_FOR_REVIEW', 'APPROVED', 'CLOSED', 'REOPENED'))
);

CREATE TABLE IF NOT EXISTS finance_report_exports (
    id BIGSERIAL PRIMARY KEY,
    export_reference VARCHAR(80) NOT NULL UNIQUE,
    report_type VARCHAR(80) NOT NULL,
    requested_by VARCHAR(120),
    status VARCHAR(40) NOT NULL DEFAULT 'REQUESTED',
    format VARCHAR(20) NOT NULL DEFAULT 'CSV',
    date_from DATE,
    date_to DATE,
    merchant_id BIGINT,
    provider_code VARCHAR(64),
    channel_code VARCHAR(64),
    country_code VARCHAR(3),
    currency_code VARCHAR(3),
    filter_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    output_location TEXT,
    failure_reason TEXT,
    requested_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    CONSTRAINT finance_report_exports_status_chk CHECK (status IN ('REQUESTED', 'RUNNING', 'COMPLETED', 'FAILED', 'EXPIRED')),
    CONSTRAINT finance_report_exports_format_chk CHECK (format IN ('CSV', 'XLSX', 'PDF', 'JSON'))
);

CREATE INDEX IF NOT EXISTS idx_finance_report_exports_type
    ON finance_report_exports(report_type, status);

CREATE TABLE IF NOT EXISTS operations_incidents (
    id BIGSERIAL PRIMARY KEY,
    incident_reference VARCHAR(80) NOT NULL UNIQUE,
    title VARCHAR(240) NOT NULL,
    severity VARCHAR(20) NOT NULL DEFAULT 'SEV3',
    status VARCHAR(40) NOT NULL DEFAULT 'OPEN',
    incident_type VARCHAR(80) NOT NULL,
    provider_code VARCHAR(64),
    channel_code VARCHAR(64),
    merchant_id BIGINT,
    business_impact TEXT,
    owner VARCHAR(120),
    detected_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    acknowledged_at TIMESTAMP,
    resolved_at TIMESTAMP,
    closed_at TIMESTAMP,
    root_cause TEXT,
    corrective_action TEXT,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT operations_incidents_severity_chk CHECK (severity IN ('SEV1', 'SEV2', 'SEV3', 'SEV4')),
    CONSTRAINT operations_incidents_status_chk CHECK (status IN ('OPEN', 'ACKNOWLEDGED', 'MITIGATING', 'RESOLVED', 'CLOSED', 'POSTMORTEM_REQUIRED'))
);

CREATE INDEX IF NOT EXISTS idx_operations_incidents_status
    ON operations_incidents(status, severity);

CREATE TABLE IF NOT EXISTS operations_incident_events (
    id BIGSERIAL PRIMARY KEY,
    incident_id BIGINT NOT NULL REFERENCES operations_incidents(id) ON DELETE CASCADE,
    event_type VARCHAR(80) NOT NULL,
    actor VARCHAR(120),
    message TEXT,
    evidence_url TEXT,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_operations_incident_events_incident
    ON operations_incident_events(incident_id, created_at);
