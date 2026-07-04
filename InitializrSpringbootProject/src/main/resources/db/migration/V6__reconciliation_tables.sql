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
    notes TEXT NULL
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
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
