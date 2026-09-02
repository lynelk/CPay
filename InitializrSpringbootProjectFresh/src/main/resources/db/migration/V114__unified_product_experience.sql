-- Unified product-experience foundation.
-- This migration adds workflow, support, notification, incident, analytics and sales records.
-- It does not move financial balances, alter transaction finality, or synthesize production data.

CREATE TABLE IF NOT EXISTS merchant_activation_lifecycles (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    lifecycle_reference VARCHAR(80) NOT NULL,
    merchant_id BIGINT UNSIGNED NOT NULL,
    status VARCHAR(40) NOT NULL DEFAULT 'ACCOUNT_CREATED',
    current_step_code VARCHAR(80) NOT NULL DEFAULT 'ACCOUNT_CREATED',
    next_action VARCHAR(500) NULL,
    blocked_reason VARCHAR(1000) NULL,
    assigned_owner VARCHAR(190) NULL,
    due_at TIMESTAMP NULL,
    activated_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_merchant_activation_lifecycle_reference (lifecycle_reference),
    UNIQUE KEY uk_merchant_activation_lifecycle_merchant (merchant_id),
    KEY idx_merchant_activation_lifecycle_status (status, updated_at),
    CONSTRAINT fk_merchant_activation_lifecycle_merchant FOREIGN KEY (merchant_id) REFERENCES merchants(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS merchant_activation_steps (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    lifecycle_id BIGINT UNSIGNED NOT NULL,
    step_code VARCHAR(80) NOT NULL,
    step_name VARCHAR(180) NOT NULL,
    status VARCHAR(40) NOT NULL DEFAULT 'NOT_STARTED',
    responsible_party VARCHAR(40) NOT NULL DEFAULT 'MERCHANT',
    required_for_activation BOOLEAN NOT NULL DEFAULT TRUE,
    guidance VARCHAR(1000) NULL,
    blocker VARCHAR(1000) NULL,
    completed_by VARCHAR(190) NULL,
    completed_at TIMESTAMP NULL,
    sort_order INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_merchant_activation_step (lifecycle_id, step_code),
    KEY idx_merchant_activation_step_status (lifecycle_id, status, sort_order),
    CONSTRAINT fk_merchant_activation_step_lifecycle FOREIGN KEY (lifecycle_id) REFERENCES merchant_activation_lifecycles(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT IGNORE INTO merchant_activation_lifecycles
    (lifecycle_reference, merchant_id, status, current_step_code, next_action, activated_at)
SELECT CONCAT('ACT-', LPAD(m.id, 12, '0')), m.id,
       CASE WHEN m.status = 'ACTIVE' THEN 'LIVE' WHEN m.status = 'SUSPENDED' THEN 'SUSPENDED' ELSE 'ACCOUNT_CREATED' END,
       CASE WHEN m.status = 'ACTIVE' THEN 'PRODUCTION_ACTIVATED' ELSE 'ACCOUNT_CREATED' END,
       CASE WHEN m.status = 'ACTIVE' THEN 'Monitor live processing and complete operational reviews.' ELSE 'Complete the business profile.' END,
       CASE WHEN m.status = 'ACTIVE' THEN m.updated_on ELSE NULL END
FROM merchants m;

INSERT IGNORE INTO merchant_activation_steps
    (lifecycle_id, step_code, step_name, status, responsible_party, required_for_activation, guidance, sort_order)
SELECT l.id, s.step_code, s.step_name,
       CASE WHEN l.status = 'LIVE' THEN CASE WHEN s.sort_order = 15 THEN 'COMPLETED' ELSE 'WAIVED_LEGACY' END ELSE CASE WHEN s.sort_order = 1 THEN 'IN_PROGRESS' ELSE 'NOT_STARTED' END END,
       s.responsible_party, s.required_for_activation, s.guidance, s.sort_order
FROM merchant_activation_lifecycles l
JOIN (
    SELECT 'ACCOUNT_CREATED' step_code, 'Account created' step_name, 'MERCHANT' responsible_party, TRUE required_for_activation, 'Confirm the account owner and primary contact.' guidance, 1 sort_order
    UNION ALL SELECT 'EMAIL_VERIFIED', 'Email verified', 'MERCHANT', TRUE, 'Verify the primary account email.', 2
    UNION ALL SELECT 'BUSINESS_PROFILE', 'Business profile completed', 'MERCHANT', TRUE, 'Provide the legal business profile and operating details.', 3
    UNION ALL SELECT 'OWNERSHIP', 'Ownership and directors recorded', 'MERCHANT', TRUE, 'Record directors and beneficial owners.', 4
    UNION ALL SELECT 'DOCUMENTS', 'Verification documents submitted', 'MERCHANT', TRUE, 'Upload current, legible verification evidence.', 5
    UNION ALL SELECT 'KYB_REVIEW', 'KYB review', 'COMPLIANCE', TRUE, 'Compliance reviews the submitted business evidence.', 6
    UNION ALL SELECT 'RISK_REVIEW', 'Risk review', 'RISK', TRUE, 'Risk confirms the account risk profile and controls.', 7
    UNION ALL SELECT 'COMMERCIAL_APPROVAL', 'Commercial approval', 'SALES', TRUE, 'Commercial terms and products are approved.', 8
    UNION ALL SELECT 'SERVICES_SELECTED', 'Services selected', 'MERCHANT', TRUE, 'Select only the Cito services the business needs.', 9
    UNION ALL SELECT 'SANDBOX_CONFIGURED', 'Sandbox configured', 'DEVELOPER', TRUE, 'Create a sandbox application and credentials.', 10
    UNION ALL SELECT 'INTEGRATION_TESTED', 'Integration tested', 'DEVELOPER', TRUE, 'Complete collection, payout and callback test journeys as applicable.', 11
    UNION ALL SELECT 'PROVIDER_CERTIFIED', 'Provider certification completed', 'OPERATIONS', TRUE, 'Attach provider and statement evidence for enabled channels.', 12
    UNION ALL SELECT 'SETTLEMENT_CONFIGURED', 'Settlement configured', 'FINANCE', TRUE, 'Confirm settlement accounts, schedule and currency.', 13
    UNION ALL SELECT 'GO_LIVE_APPROVED', 'Go-live approved', 'OPERATIONS', TRUE, 'Complete maker-checker readiness approval.', 14
    UNION ALL SELECT 'PRODUCTION_ACTIVATED', 'Production activated', 'OPERATIONS', TRUE, 'Activate production with monitored limits.', 15
) s ON 1 = 1;

CREATE TABLE IF NOT EXISTS support_cases (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    case_reference VARCHAR(80) NOT NULL,
    merchant_id BIGINT UNSIGNED NULL,
    subject VARCHAR(240) NOT NULL,
    category VARCHAR(80) NOT NULL,
    severity VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    status VARCHAR(40) NOT NULL DEFAULT 'OPEN',
    channel VARCHAR(30) NOT NULL DEFAULT 'PORTAL',
    transaction_reference VARCHAR(255) NULL,
    provider_reference VARCHAR(255) NULL,
    assigned_team VARCHAR(80) NULL,
    assigned_to VARCHAR(190) NULL,
    opened_by VARCHAR(190) NOT NULL,
    description TEXT NOT NULL,
    first_response_due_at TIMESTAMP NULL,
    resolution_due_at TIMESTAMP NULL,
    resolved_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_support_case_reference (case_reference),
    KEY idx_support_case_merchant (merchant_id, status, updated_at),
    KEY idx_support_case_queue (status, severity, updated_at),
    KEY idx_support_case_transaction (transaction_reference),
    CONSTRAINT fk_support_case_merchant FOREIGN KEY (merchant_id) REFERENCES merchants(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS support_case_events (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    support_case_id BIGINT UNSIGNED NOT NULL,
    event_type VARCHAR(40) NOT NULL,
    actor_type VARCHAR(30) NOT NULL,
    actor_reference VARCHAR(190) NOT NULL,
    visibility VARCHAR(20) NOT NULL DEFAULT 'CUSTOMER',
    message TEXT NULL,
    detail_json JSON NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_support_case_event (support_case_id, created_at),
    CONSTRAINT fk_support_case_event_case FOREIGN KEY (support_case_id) REFERENCES support_cases(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS user_notifications (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    notification_reference VARCHAR(80) NOT NULL,
    recipient_type VARCHAR(30) NOT NULL,
    recipient_reference VARCHAR(190) NOT NULL,
    merchant_id BIGINT UNSIGNED NULL,
    notification_type VARCHAR(80) NOT NULL,
    severity VARCHAR(20) NOT NULL DEFAULT 'INFO',
    title VARCHAR(240) NOT NULL,
    message VARCHAR(1200) NOT NULL,
    action_url VARCHAR(500) NULL,
    read_at TIMESTAMP NULL,
    expires_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_notification_reference (notification_reference),
    KEY idx_user_notification_recipient (recipient_type, recipient_reference, read_at, created_at),
    KEY idx_user_notification_merchant (merchant_id, created_at),
    CONSTRAINT fk_user_notification_merchant FOREIGN KEY (merchant_id) REFERENCES merchants(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS provider_incidents (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    incident_reference VARCHAR(80) NOT NULL,
    provider_code VARCHAR(80) NOT NULL,
    country_code VARCHAR(3) NULL,
    channel_code VARCHAR(80) NULL,
    environment VARCHAR(20) NOT NULL DEFAULT 'PRODUCTION',
    severity VARCHAR(20) NOT NULL DEFAULT 'MINOR',
    status VARCHAR(30) NOT NULL DEFAULT 'INVESTIGATING',
    public_title VARCHAR(240) NOT NULL,
    public_message VARCHAR(1000) NOT NULL,
    internal_notes TEXT NULL,
    started_at TIMESTAMP NOT NULL,
    resolved_at TIMESTAMP NULL,
    created_by VARCHAR(190) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_provider_incident_reference (incident_reference),
    KEY idx_provider_incident_status (environment, status, started_at),
    KEY idx_provider_incident_provider (provider_code, country_code, channel_code, started_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS product_analytics_events (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    event_reference VARCHAR(80) NOT NULL,
    event_name VARCHAR(100) NOT NULL,
    audience VARCHAR(30) NOT NULL,
    merchant_id BIGINT UNSIGNED NULL,
    actor_reference_hash VARCHAR(128) NULL,
    session_reference_hash VARCHAR(128) NULL,
    page_path VARCHAR(500) NULL,
    environment VARCHAR(20) NOT NULL DEFAULT 'SANDBOX',
    properties_json JSON NULL,
    occurred_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_product_analytics_event_reference (event_reference),
    KEY idx_product_analytics_funnel (event_name, occurred_at),
    KEY idx_product_analytics_merchant (merchant_id, occurred_at),
    CONSTRAINT fk_product_analytics_merchant FOREIGN KEY (merchant_id) REFERENCES merchants(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS sales_enquiries (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    enquiry_reference VARCHAR(80) NOT NULL,
    contact_name VARCHAR(190) NOT NULL,
    work_email VARCHAR(190) NOT NULL,
    company_name VARCHAR(240) NOT NULL,
    country_code VARCHAR(3) NULL,
    service_interest VARCHAR(120) NOT NULL,
    message VARCHAR(2000) NULL,
    consent_recorded BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(30) NOT NULL DEFAULT 'NEW',
    source_path VARCHAR(500) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sales_enquiry_reference (enquiry_reference),
    KEY idx_sales_enquiry_queue (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
