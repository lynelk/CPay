CREATE TABLE IF NOT EXISTS balance_ledger_events (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    merchant_id BIGINT NOT NULL,
    channel_code VARCHAR(80) NOT NULL,
    gateway_id VARCHAR(120),
    currency VARCHAR(12) NOT NULL,
    event_type VARCHAR(80) NOT NULL,
    source_type VARCHAR(80) NOT NULL,
    source_reference VARCHAR(160) NOT NULL,
    amount_delta DECIMAL(18,2) NOT NULL DEFAULT 0.00,
    pending_delta DECIMAL(18,2) NOT NULL DEFAULT 0.00,
    ledger_delta DECIMAL(18,2) NOT NULL DEFAULT 0.00,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_balance_event_source (source_type, source_reference, event_type),
    INDEX idx_balance_events_merchant_channel (merchant_id, channel_code, currency)
);

CREATE TABLE IF NOT EXISTS normalized_balance_backfill_runs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    run_status VARCHAR(40) NOT NULL DEFAULT 'STARTED',
    started_by VARCHAR(120) NOT NULL,
    started_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    finished_at TIMESTAMP NULL,
    merchants_processed INT NOT NULL DEFAULT 0,
    balances_written INT NOT NULL DEFAULT 0,
    message TEXT
);
