CREATE TABLE IF NOT EXISTS callback_task_claims (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_id BIGINT NOT NULL,
    worker_name VARCHAR(120) NOT NULL,
    claim_status VARCHAR(40) NOT NULL DEFAULT 'ACTIVE',
    claimed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    released_at TIMESTAMP NULL,
    UNIQUE KEY uq_callback_task_active_claim (task_id, claim_status),
    INDEX idx_callback_task_claim_status (claim_status, claimed_at)
);

CREATE TABLE IF NOT EXISTS provider_endpoint_runs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    channel_code VARCHAR(80) NOT NULL,
    operation_name VARCHAR(40) NOT NULL,
    reference_value VARCHAR(180) NOT NULL,
    endpoint_url VARCHAR(500) NOT NULL,
    http_status INT,
    request_hash VARCHAR(128),
    response_summary TEXT,
    run_status VARCHAR(40) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_provider_endpoint_reference (reference_value),
    INDEX idx_provider_endpoint_channel_status (channel_code, run_status, created_at)
);

CREATE TABLE IF NOT EXISTS operating_control_events (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    event_type VARCHAR(80) NOT NULL,
    severity VARCHAR(20) NOT NULL DEFAULT 'LOW',
    event_status VARCHAR(40) NOT NULL DEFAULT 'OPEN',
    reference_value VARCHAR(180),
    message TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    reviewed_by VARCHAR(120),
    reviewed_at TIMESTAMP NULL,
    INDEX idx_operating_control_status (event_status, severity, created_at)
);
