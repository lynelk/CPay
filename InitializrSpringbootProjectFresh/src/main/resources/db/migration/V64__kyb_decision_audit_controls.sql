CREATE TABLE IF NOT EXISTS kyb_review_decisions (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    subject_type VARCHAR(40) NOT NULL,
    subject_id BIGINT UNSIGNED NOT NULL,
    old_status VARCHAR(40) NULL,
    new_status VARCHAR(40) NOT NULL,
    decision VARCHAR(40) NOT NULL,
    reason TEXT NULL,
    reviewer_user_id VARCHAR(255) NOT NULL,
    reviewer_role VARCHAR(100) NULL,
    policy_version VARCHAR(80) NOT NULL DEFAULT 'kyb-review-v1',
    evidence_reference VARCHAR(255) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_kyb_review_subject (subject_type, subject_id, created_at),
    KEY idx_kyb_review_reviewer (reviewer_user_id, created_at),
    KEY idx_kyb_review_decision (decision, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
