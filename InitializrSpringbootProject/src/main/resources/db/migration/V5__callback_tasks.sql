CREATE TABLE IF NOT EXISTS callback_tasks (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    merchant_id BIGINT NOT NULL,
    transaction_id VARCHAR(120) NULL,
    reference_value VARCHAR(160) NULL,
    target_url VARCHAR(500) NOT NULL,
    request_body TEXT NOT NULL,
    task_status VARCHAR(40) NOT NULL DEFAULT 'PENDING',
    attempt_count INT NOT NULL DEFAULT 0,
    attempt_limit INT NOT NULL DEFAULT 5,
    next_run_at TIMESTAMP NULL,
    last_run_at TIMESTAMP NULL,
    message TEXT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
