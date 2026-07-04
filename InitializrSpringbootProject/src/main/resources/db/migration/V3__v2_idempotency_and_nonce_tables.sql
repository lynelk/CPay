CREATE TABLE IF NOT EXISTS cpay_request_nonces (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    merchant_number VARCHAR(80) NOT NULL,
    nonce_value VARCHAR(160) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_cpay_request_nonce (merchant_number, nonce_value),
    INDEX idx_cpay_request_nonce_expires_at (expires_at)
);

CREATE TABLE IF NOT EXISTS cpay_idempotency_keys (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    merchant_number VARCHAR(80) NOT NULL,
    idempotency_key VARCHAR(160) NOT NULL,
    request_hash VARCHAR(128) NOT NULL,
    response_body TEXT NOT NULL,
    status VARCHAR(60) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_cpay_idempotency_key (merchant_number, idempotency_key),
    INDEX idx_cpay_idempotency_created_at (created_at)
);
