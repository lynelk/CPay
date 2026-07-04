CREATE TABLE IF NOT EXISTS merchant_channel_credentials (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    merchant_id BIGINT NOT NULL,
    channel_code VARCHAR(80) NOT NULL,
    environment VARCHAR(20) NOT NULL DEFAULT 'SANDBOX',
    display_name VARCHAR(160),
    credential_payload TEXT NOT NULL,
    credential_mask TEXT NOT NULL,
    status VARCHAR(40) NOT NULL DEFAULT 'CONFIGURED',
    last_test_status VARCHAR(40),
    last_test_message TEXT,
    last_tested_at TIMESTAMP NULL,
    submitted_for_approval_at TIMESTAMP NULL,
    approved_by VARCHAR(120),
    approved_at TIMESTAMP NULL,
    created_by VARCHAR(180),
    updated_by VARCHAR(180),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uq_merchant_channel_env (merchant_id, channel_code, environment),
    INDEX idx_merchant_channel_status (merchant_id, status),
    INDEX idx_channel_env (channel_code, environment)
);

CREATE TABLE IF NOT EXISTS merchant_channel_audit_events (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    merchant_id BIGINT NOT NULL,
    channel_code VARCHAR(80) NOT NULL,
    environment VARCHAR(20) NOT NULL DEFAULT 'SANDBOX',
    action VARCHAR(80) NOT NULL,
    actor VARCHAR(180),
    message TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_merchant_channel_audit (merchant_id, channel_code, created_at)
);
