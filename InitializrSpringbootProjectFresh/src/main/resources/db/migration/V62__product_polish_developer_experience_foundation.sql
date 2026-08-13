-- P4 Product Polish and Developer Experience foundation
-- Adds merchant onboarding UX state, developer portal resources, payment links,
-- hosted checkout sessions, invoices, channel journeys, dashboards, sandbox docs,
-- and go-live guidance records.

CREATE TABLE IF NOT EXISTS merchant_onboarding_workflows (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workflow_reference VARCHAR(80) NOT NULL UNIQUE,
    merchant_id BIGINT NOT NULL,
    current_step VARCHAR(80) NOT NULL DEFAULT 'ACCOUNT_CREATED',
    status VARCHAR(40) NOT NULL DEFAULT 'IN_PROGRESS',
    completion_percentage DECIMAL(5, 2) NOT NULL DEFAULT 0,
    blocked_reason TEXT,
    assigned_owner VARCHAR(120),
    metadata JSON NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (merchant_id),
    CONSTRAINT merchant_onboarding_status_chk CHECK (status IN ('IN_PROGRESS', 'BLOCKED', 'READY_FOR_REVIEW', 'APPROVED', 'LIVE', 'SUSPENDED'))
);

CREATE INDEX idx_merchant_onboarding_workflows_status
    ON merchant_onboarding_workflows(status, current_step);

CREATE TABLE IF NOT EXISTS merchant_onboarding_steps (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workflow_id BIGINT NOT NULL,
    step_code VARCHAR(80) NOT NULL,
    step_name VARCHAR(160) NOT NULL,
    status VARCHAR(40) NOT NULL DEFAULT 'PENDING',
    required_for_go_live BOOLEAN NOT NULL DEFAULT TRUE,
    completed_by VARCHAR(120),
    completed_at TIMESTAMP,
    evidence_url TEXT,
    notes TEXT,
    sort_order INTEGER NOT NULL DEFAULT 0,
    metadata JSON NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (workflow_id, step_code),
    CONSTRAINT fk_merchant_onboarding_step_workflow FOREIGN KEY (workflow_id) REFERENCES merchant_onboarding_workflows(id) ON DELETE CASCADE,
    CONSTRAINT merchant_onboarding_step_status_chk CHECK (status IN ('PENDING', 'IN_PROGRESS', 'COMPLETED', 'FAILED', 'WAIVED', 'BLOCKED'))
);

CREATE INDEX idx_merchant_onboarding_steps_workflow
    ON merchant_onboarding_steps(workflow_id, status);

CREATE TABLE IF NOT EXISTS developer_portal_applications (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    application_reference VARCHAR(80) NOT NULL UNIQUE,
    merchant_id BIGINT NOT NULL,
    name VARCHAR(180) NOT NULL,
    environment VARCHAR(20) NOT NULL DEFAULT 'SANDBOX',
    status VARCHAR(40) NOT NULL DEFAULT 'ACTIVE',
    callback_url TEXT,
    allowed_origins TEXT,
    created_by VARCHAR(120),
    metadata JSON NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT developer_app_environment_chk CHECK (environment IN ('SANDBOX', 'PRODUCTION')),
    CONSTRAINT developer_app_status_chk CHECK (status IN ('ACTIVE', 'DISABLED', 'REVOKED', 'PENDING_PRODUCTION_APPROVAL'))
);

CREATE INDEX idx_developer_portal_applications_merchant
    ON developer_portal_applications(merchant_id, environment, status);

CREATE TABLE IF NOT EXISTS developer_portal_api_keys (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    application_id BIGINT NOT NULL,
    key_reference VARCHAR(80) NOT NULL UNIQUE,
    key_label VARCHAR(160),
    public_key_pem TEXT,
    status VARCHAR(40) NOT NULL DEFAULT 'ACTIVE',
    last_used_at TIMESTAMP,
    expires_at TIMESTAMP,
    rotated_from_key_reference VARCHAR(80),
    created_by VARCHAR(120),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revoked_at TIMESTAMP,
    revoked_by VARCHAR(120),
    CONSTRAINT fk_developer_api_key_application FOREIGN KEY (application_id) REFERENCES developer_portal_applications(id) ON DELETE CASCADE,
    CONSTRAINT developer_api_key_status_chk CHECK (status IN ('ACTIVE', 'ROTATING', 'REVOKED', 'EXPIRED'))
);

CREATE TABLE IF NOT EXISTS payment_links_v2 (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    payment_link_reference VARCHAR(80) NOT NULL UNIQUE,
    merchant_id BIGINT NOT NULL,
    token_hash VARCHAR(160) NOT NULL UNIQUE,
    title VARCHAR(240),
    description TEXT,
    amount DECIMAL(24, 6),
    currency_code VARCHAR(3) NOT NULL,
    country_code VARCHAR(3),
    status VARCHAR(40) NOT NULL DEFAULT 'ACTIVE',
    reusable BOOLEAN NOT NULL DEFAULT FALSE,
    partial_payment_allowed BOOLEAN NOT NULL DEFAULT FALSE,
    max_uses INTEGER,
    use_count INTEGER NOT NULL DEFAULT 0,
    expires_at TIMESTAMP,
    created_by VARCHAR(120),
    metadata JSON NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT payment_links_v2_status_chk CHECK (status IN ('CREATED', 'ACTIVE', 'PAID', 'PARTIALLY_PAID', 'EXPIRED', 'CANCELLED', 'REFUNDED'))
);

CREATE INDEX idx_payment_links_v2_merchant
    ON payment_links_v2(merchant_id, status);

CREATE TABLE IF NOT EXISTS hosted_checkout_sessions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    checkout_reference VARCHAR(80) NOT NULL UNIQUE,
    merchant_id BIGINT NOT NULL,
    payment_link_id BIGINT,
    invoice_id BIGINT,
    token_hash VARCHAR(160) NOT NULL UNIQUE,
    customer_msisdn VARCHAR(40),
    customer_email VARCHAR(180),
    amount DECIMAL(24, 6) NOT NULL,
    currency_code VARCHAR(3) NOT NULL,
    country_code VARCHAR(3),
    selected_channel VARCHAR(64),
    status VARCHAR(40) NOT NULL DEFAULT 'CREATED',
    expires_at TIMESTAMP NOT NULL,
    paid_transaction_reference VARCHAR(120),
    failed_attempt_count INTEGER NOT NULL DEFAULT 0,
    metadata JSON NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_hosted_checkout_payment_link FOREIGN KEY (payment_link_id) REFERENCES payment_links_v2(id),
    CONSTRAINT hosted_checkout_status_chk CHECK (status IN ('CREATED', 'PENDING_PAYMENT', 'PAID', 'FAILED', 'EXPIRED', 'CANCELLED'))
);

CREATE INDEX idx_hosted_checkout_sessions_merchant
    ON hosted_checkout_sessions(merchant_id, status);

CREATE TABLE IF NOT EXISTS merchant_invoices_v2 (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    invoice_reference VARCHAR(80) NOT NULL UNIQUE,
    merchant_id BIGINT NOT NULL,
    invoice_number VARCHAR(80) NOT NULL,
    customer_name VARCHAR(220),
    customer_email VARCHAR(180),
    customer_msisdn VARCHAR(40),
    currency_code VARCHAR(3) NOT NULL,
    subtotal_amount DECIMAL(24, 6) NOT NULL DEFAULT 0,
    tax_amount DECIMAL(24, 6) NOT NULL DEFAULT 0,
    total_amount DECIMAL(24, 6) NOT NULL DEFAULT 0,
    amount_paid DECIMAL(24, 6) NOT NULL DEFAULT 0,
    status VARCHAR(40) NOT NULL DEFAULT 'DRAFT',
    due_date DATE,
    partial_payment_allowed BOOLEAN NOT NULL DEFAULT FALSE,
    payment_token_hash VARCHAR(160) UNIQUE,
    created_by VARCHAR(120),
    metadata JSON NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (merchant_id, invoice_number),
    CONSTRAINT merchant_invoices_v2_status_chk CHECK (status IN ('DRAFT', 'ISSUED', 'PARTIALLY_PAID', 'PAID', 'OVERDUE', 'CANCELLED', 'REFUNDED'))
);

CREATE INDEX idx_merchant_invoices_v2_merchant
    ON merchant_invoices_v2(merchant_id, status);

CREATE TABLE IF NOT EXISTS merchant_invoice_line_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    invoice_id BIGINT NOT NULL,
    description TEXT NOT NULL,
    quantity DECIMAL(18, 4) NOT NULL DEFAULT 1,
    unit_amount DECIMAL(24, 6) NOT NULL DEFAULT 0,
    tax_amount DECIMAL(24, 6) NOT NULL DEFAULT 0,
    line_total DECIMAL(24, 6) NOT NULL DEFAULT 0,
    sort_order INTEGER NOT NULL DEFAULT 0,
    metadata JSON NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_merchant_invoice_line_item_invoice FOREIGN KEY (invoice_id) REFERENCES merchant_invoices_v2(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS channel_journey_guides (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    guide_reference VARCHAR(80) NOT NULL UNIQUE,
    channel_code VARCHAR(64) NOT NULL,
    country_code VARCHAR(3),
    environment VARCHAR(20) NOT NULL DEFAULT 'SANDBOX',
    title VARCHAR(220) NOT NULL,
    status VARCHAR(40) NOT NULL DEFAULT 'DRAFT',
    journey_json JSON NULL,
    published_by VARCHAR(120),
    published_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT channel_journey_guides_environment_chk CHECK (environment IN ('SANDBOX', 'PRODUCTION')),
    CONSTRAINT channel_journey_guides_status_chk CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED'))
);

CREATE INDEX idx_channel_journey_guides_channel
    ON channel_journey_guides(channel_code, country_code, environment, status);

CREATE TABLE IF NOT EXISTS dashboard_widgets (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    widget_reference VARCHAR(80) NOT NULL UNIQUE,
    audience VARCHAR(40) NOT NULL,
    widget_code VARCHAR(120) NOT NULL,
    title VARCHAR(220) NOT NULL,
    status VARCHAR(40) NOT NULL DEFAULT 'ACTIVE',
    config_json JSON NULL,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (audience, widget_code),
    CONSTRAINT dashboard_widgets_audience_chk CHECK (audience IN ('MERCHANT', 'ADMIN', 'FINANCE', 'OPERATIONS', 'COMPLIANCE', 'DEVELOPER')),
    CONSTRAINT dashboard_widgets_status_chk CHECK (status IN ('ACTIVE', 'DISABLED', 'ARCHIVED'))
);

CREATE TABLE IF NOT EXISTS sandbox_guides (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    guide_reference VARCHAR(80) NOT NULL UNIQUE,
    title VARCHAR(220) NOT NULL,
    audience VARCHAR(40) NOT NULL DEFAULT 'DEVELOPER',
    status VARCHAR(40) NOT NULL DEFAULT 'DRAFT',
    content_markdown TEXT NOT NULL,
    sample_payload_json JSON NULL,
    published_by VARCHAR(120),
    published_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT sandbox_guides_status_chk CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED'))
);

CREATE TABLE IF NOT EXISTS go_live_checklists (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    checklist_reference VARCHAR(80) NOT NULL UNIQUE,
    merchant_id BIGINT NOT NULL,
    status VARCHAR(40) NOT NULL DEFAULT 'OPEN',
    requested_by VARCHAR(120),
    reviewed_by VARCHAR(120),
    approved_by VARCHAR(120),
    blocked_reason TEXT,
    requested_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_at TIMESTAMP,
    approved_at TIMESTAMP,
    metadata JSON NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (merchant_id),
    CONSTRAINT go_live_checklists_status_chk CHECK (status IN ('OPEN', 'BLOCKED', 'READY_FOR_REVIEW', 'APPROVED', 'REJECTED', 'LIVE'))
);

CREATE TABLE IF NOT EXISTS go_live_checklist_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    checklist_id BIGINT NOT NULL,
    item_code VARCHAR(100) NOT NULL,
    item_name VARCHAR(220) NOT NULL,
    status VARCHAR(40) NOT NULL DEFAULT 'PENDING',
    required BOOLEAN NOT NULL DEFAULT TRUE,
    evidence_url TEXT,
    completed_by VARCHAR(120),
    completed_at TIMESTAMP,
    notes TEXT,
    sort_order INTEGER NOT NULL DEFAULT 0,
    metadata JSON NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (checklist_id, item_code),
    CONSTRAINT fk_go_live_checklist_item_checklist FOREIGN KEY (checklist_id) REFERENCES go_live_checklists(id) ON DELETE CASCADE,
    CONSTRAINT go_live_checklist_item_status_chk CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED', 'WAIVED', 'BLOCKED'))
);
