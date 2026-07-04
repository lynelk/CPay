CREATE TABLE IF NOT EXISTS callback_delivery_signatures (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    callback_task_id BIGINT NOT NULL,
    signature_algorithm VARCHAR(80) NOT NULL,
    signature_value TEXT NOT NULL,
    nonce VARCHAR(120) NOT NULL,
    signed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_callback_signature_nonce (nonce),
    INDEX idx_callback_signature_task (callback_task_id)
);

CREATE TABLE IF NOT EXISTS admin_permissions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    role_name VARCHAR(80) NOT NULL,
    permission_code VARCHAR(120) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_admin_permission (role_name, permission_code)
);

CREATE TABLE IF NOT EXISTS admin_audit_events (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    actor VARCHAR(120) NOT NULL,
    permission_code VARCHAR(120) NOT NULL,
    action_name VARCHAR(160) NOT NULL,
    resource_reference VARCHAR(180),
    request_summary TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_admin_audit_actor (actor),
    INDEX idx_admin_audit_action (action_name)
);
