CREATE TABLE IF NOT EXISTS reconciliation_reviews (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    reconciliation_record_id BIGINT NOT NULL,
    transaction_id VARCHAR(120) NULL,
    review_type VARCHAR(80) NOT NULL,
    amount DECIMAL(18,2) NOT NULL DEFAULT 0.00,
    currency VARCHAR(12) NOT NULL,
    reason TEXT NOT NULL,
    review_status VARCHAR(40) NOT NULL DEFAULT 'PENDING',
    requested_by VARCHAR(120) NOT NULL,
    requested_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    reviewed_by VARCHAR(120) NULL,
    reviewed_at TIMESTAMP NULL,
    review_note TEXT NULL,
    INDEX idx_recon_reviews_status (review_status),
    INDEX idx_recon_reviews_record (reconciliation_record_id)
);
