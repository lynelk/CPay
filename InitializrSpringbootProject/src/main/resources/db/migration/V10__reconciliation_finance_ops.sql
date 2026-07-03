CREATE TABLE IF NOT EXISTS reconciliation_settlement_batches (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    batch_reference VARCHAR(120) NOT NULL UNIQUE,
    provider_code VARCHAR(80) NOT NULL,
    channel_code VARCHAR(80) NOT NULL,
    currency VARCHAR(12) NOT NULL,
    batch_status VARCHAR(40) NOT NULL DEFAULT 'OPEN',
    expected_amount DECIMAL(18,2) NOT NULL DEFAULT 0.00,
    matched_amount DECIMAL(18,2) NOT NULL DEFAULT 0.00,
    exception_amount DECIMAL(18,2) NOT NULL DEFAULT 0.00,
    opened_by VARCHAR(120) NOT NULL,
    opened_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    closed_by VARCHAR(120),
    closed_at TIMESTAMP NULL,
    INDEX idx_recon_batch_status (batch_status),
    INDEX idx_recon_batch_provider (provider_code, channel_code)
);

CREATE TABLE IF NOT EXISTS provider_statement_validation_runs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    provider_code VARCHAR(80) NOT NULL,
    channel_code VARCHAR(80) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    validation_status VARCHAR(40) NOT NULL DEFAULT 'PENDING',
    total_rows INT NOT NULL DEFAULT 0,
    valid_rows INT NOT NULL DEFAULT 0,
    invalid_rows INT NOT NULL DEFAULT 0,
    duplicate_rows INT NOT NULL DEFAULT 0,
    message TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_provider_statement_validation (provider_code, validation_status)
);
