CREATE TABLE IF NOT EXISTS merchant_channel_balances (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    merchant_id BIGINT NOT NULL,
    channel_code VARCHAR(80) NOT NULL,
    gateway_id VARCHAR(120) NULL,
    currency VARCHAR(12) NOT NULL,
    available_balance DECIMAL(18,2) NOT NULL DEFAULT 0.00,
    ledger_balance DECIMAL(18,2) NOT NULL DEFAULT 0.00,
    reserved_balance DECIMAL(18,2) NOT NULL DEFAULT 0.00,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uq_merchant_channel_balance (merchant_id, channel_code, currency),
    INDEX idx_merchant_channel_balance_merchant (merchant_id),
    INDEX idx_merchant_channel_balance_channel (channel_code)
);

CREATE TABLE IF NOT EXISTS webhook_deliveries (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    merchant_id BIGINT NOT NULL,
    transaction_id VARCHAR(120) NULL,
    merchant_reference VARCHAR(160) NULL,
    callback_url TEXT NOT NULL,
    payload MEDIUMTEXT NOT NULL,
    status VARCHAR(40) NOT NULL DEFAULT 'PENDING',
    attempts INT NOT NULL DEFAULT 0,
    max_attempts INT NOT NULL DEFAULT 5,
    next_retry_at TIMESTAMP NULL,
    last_attempt_at TIMESTAMP NULL,
    last_error TEXT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_webhook_deliveries_status_retry (status, next_retry_at),
    INDEX idx_webhook_deliveries_merchant (merchant_id),
    INDEX idx_webhook_deliveries_reference (merchant_reference)
);

CREATE TABLE IF NOT EXISTS reconciliation_imports (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    provider_code VARCHAR(80) NOT NULL,
    channel_code VARCHAR(80) NOT NULL,
    source_file_name VARCHAR(255) NULL,
    imported_by VARCHAR(120) NULL,
    status VARCHAR(40) NOT NULL DEFAULT 'IMPORTED',
    imported_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    total_records INT NOT NULL DEFAULT 0,
    matched_records INT NOT NULL DEFAULT 0,
    unmatched_records INT NOT NULL DEFAULT 0,
    notes TEXT NULL,
    INDEX idx_reconciliation_imports_channel (channel_code),
    INDEX idx_reconciliation_imports_status (status)
);

CREATE TABLE IF NOT EXISTS reconciliation_records (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    import_id BIGINT NOT NULL,
    provider_code VARCHAR(80) NOT NULL,
    channel_code VARCHAR(80) NOT NULL,
    provider_reference VARCHAR(160) NULL,
    merchant_reference VARCHAR(160) NULL,
    transaction_id VARCHAR(120) NULL,
    amount DECIMAL(18,2) NOT NULL DEFAULT 0.00,
    currency VARCHAR(12) NOT NULL,
    transaction_date TIMESTAMP NULL,
    match_status VARCHAR(40) NOT NULL DEFAULT 'UNMATCHED',
    match_reason VARCHAR(255) NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_reconciliation_records_import (import_id),
    INDEX idx_reconciliation_records_provider_ref (provider_reference),
    INDEX idx_reconciliation_records_merchant_ref (merchant_reference),
    INDEX idx_reconciliation_records_match_status (match_status)
);
