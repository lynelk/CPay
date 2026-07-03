CREATE TABLE IF NOT EXISTS merchant_channel_balances (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    merchant_id BIGINT NOT NULL,
    channel_code VARCHAR(80) NOT NULL,
    gateway_id VARCHAR(120) NULL,
    currency VARCHAR(12) NOT NULL,
    available_balance DECIMAL(18,2) NOT NULL DEFAULT 0.00,
    ledger_balance DECIMAL(18,2) NOT NULL DEFAULT 0.00,
    pending_balance DECIMAL(18,2) NOT NULL DEFAULT 0.00,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uq_merchant_channel_balance (merchant_id, channel_code, currency),
    INDEX idx_merchant_channel_balance_merchant (merchant_id),
    INDEX idx_merchant_channel_balance_channel (channel_code)
);
