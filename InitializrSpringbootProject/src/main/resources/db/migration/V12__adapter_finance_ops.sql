CREATE TABLE IF NOT EXISTS merchant_callback_secrets (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    merchant_id BIGINT NOT NULL,
    secret_alias VARCHAR(120) NOT NULL,
    secret_value TEXT NOT NULL,
    active_flag VARCHAR(10) NOT NULL DEFAULT 'YES',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    rotated_at TIMESTAMP NULL,
    UNIQUE KEY uq_merchant_callback_secret (merchant_id, secret_alias)
);

CREATE TABLE IF NOT EXISTS provider_sandbox_runs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    provider_code VARCHAR(80) NOT NULL,
    channel_code VARCHAR(80) NOT NULL,
    scenario_name VARCHAR(120) NOT NULL,
    run_status VARCHAR(40) NOT NULL DEFAULT 'PENDING',
    request_summary TEXT,
    response_summary TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_provider_sandbox_runs_provider (provider_code, run_status)
);

CREATE TABLE IF NOT EXISTS reconciliation_daily_closes (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    close_date DATE NOT NULL,
    currency VARCHAR(12) NOT NULL,
    close_status VARCHAR(40) NOT NULL DEFAULT 'OPEN',
    matched_count INT NOT NULL DEFAULT 0,
    unmatched_count INT NOT NULL DEFAULT 0,
    exception_count INT NOT NULL DEFAULT 0,
    variance_amount DECIMAL(18,2) NOT NULL DEFAULT 0.00,
    closed_by VARCHAR(120),
    closed_at TIMESTAMP NULL,
    UNIQUE KEY uq_recon_daily_close (close_date, currency)
);

CREATE TABLE IF NOT EXISTS operations_alerts (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    alert_type VARCHAR(80) NOT NULL,
    severity VARCHAR(20) NOT NULL DEFAULT 'INFO',
    alert_status VARCHAR(40) NOT NULL DEFAULT 'OPEN',
    message TEXT NOT NULL,
    resource_reference VARCHAR(180),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    resolved_at TIMESTAMP NULL,
    INDEX idx_ops_alerts_status (alert_status, severity)
);
