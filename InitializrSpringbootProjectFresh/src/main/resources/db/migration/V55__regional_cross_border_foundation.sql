-- P3: Regional expansion and cross-border foundation
-- Adds corridor, beneficiary, FX, transfer lifecycle, corridor settlement and treasury exposure tables.

CREATE TABLE IF NOT EXISTS corridors (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    corridor_code VARCHAR(80) NOT NULL,
    source_country_code CHAR(2) NOT NULL,
    destination_country_code CHAR(2) NOT NULL,
    source_currency_code CHAR(3) NOT NULL,
    destination_currency_code CHAR(3) NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    status VARCHAR(40) NOT NULL DEFAULT 'DRAFT',
    risk_level VARCHAR(32) NOT NULL DEFAULT 'MEDIUM',
    settlement_model VARCHAR(80) NOT NULL DEFAULT 'PARTNER_LED',
    compliance_policy_code VARCHAR(80) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_corridors_code UNIQUE (corridor_code),
    INDEX idx_corridors_countries (source_country_code, destination_country_code),
    INDEX idx_corridors_status (status)
);

CREATE TABLE IF NOT EXISTS corridor_routes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    corridor_id BIGINT NOT NULL,
    route_code VARCHAR(80) NOT NULL,
    provider_code VARCHAR(80) NOT NULL,
    partner_code VARCHAR(80) NULL,
    delivery_method VARCHAR(80) NOT NULL,
    priority INT NOT NULL DEFAULT 100,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    supports_individuals BOOLEAN NOT NULL DEFAULT TRUE,
    supports_organisations BOOLEAN NOT NULL DEFAULT TRUE,
    min_amount DECIMAL(19,4) NULL,
    max_amount DECIMAL(19,4) NULL,
    expected_delivery_minutes INT NULL,
    configuration JSON NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_corridor_route_corridor FOREIGN KEY (corridor_id) REFERENCES corridors(id),
    CONSTRAINT uk_corridor_route_code UNIQUE (route_code),
    INDEX idx_corridor_routes_corridor (corridor_id),
    INDEX idx_corridor_routes_enabled (enabled),
    INDEX idx_corridor_routes_provider (provider_code)
);

CREATE TABLE IF NOT EXISTS corridor_limits (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    corridor_id BIGINT NOT NULL,
    limit_scope VARCHAR(80) NOT NULL DEFAULT 'MERCHANT',
    subject_reference VARCHAR(128) NULL,
    per_transaction_min DECIMAL(19,4) NULL,
    per_transaction_max DECIMAL(19,4) NULL,
    daily_amount_max DECIMAL(19,4) NULL,
    monthly_amount_max DECIMAL(19,4) NULL,
    daily_count_max INT NULL,
    monthly_count_max INT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_corridor_limit_corridor FOREIGN KEY (corridor_id) REFERENCES corridors(id),
    INDEX idx_corridor_limits_corridor (corridor_id),
    INDEX idx_corridor_limits_scope (limit_scope, subject_reference)
);

CREATE TABLE IF NOT EXISTS beneficiaries (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    beneficiary_reference VARCHAR(80) NOT NULL,
    merchant_id BIGINT NULL,
    beneficiary_type VARCHAR(40) NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    legal_name VARCHAR(255) NULL,
    country_code CHAR(2) NOT NULL,
    phone_hash VARCHAR(128) NULL,
    email_hash VARCHAR(128) NULL,
    address_text TEXT NULL,
    kyc_profile_id BIGINT NULL,
    risk_rating VARCHAR(32) NOT NULL DEFAULT 'UNRATED',
    status VARCHAR(40) NOT NULL DEFAULT 'DRAFT',
    created_by VARCHAR(128) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_beneficiary_reference UNIQUE (beneficiary_reference),
    CONSTRAINT fk_beneficiary_kyc_profile FOREIGN KEY (kyc_profile_id) REFERENCES kyc_profiles(id),
    INDEX idx_beneficiaries_merchant (merchant_id),
    INDEX idx_beneficiaries_country (country_code),
    INDEX idx_beneficiaries_status (status)
);

CREATE TABLE IF NOT EXISTS beneficiary_instruments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    beneficiary_id BIGINT NOT NULL,
    instrument_reference VARCHAR(80) NOT NULL,
    instrument_type VARCHAR(60) NOT NULL,
    provider_code VARCHAR(80) NULL,
    country_code CHAR(2) NOT NULL,
    currency_code CHAR(3) NOT NULL,
    account_identifier_hash VARCHAR(128) NOT NULL,
    account_identifier_mask VARCHAR(80) NOT NULL,
    account_name VARCHAR(255) NULL,
    validation_status VARCHAR(40) NOT NULL DEFAULT 'NOT_VALIDATED',
    status VARCHAR(40) NOT NULL DEFAULT 'ACTIVE',
    metadata JSON NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_beneficiary_instrument_beneficiary FOREIGN KEY (beneficiary_id) REFERENCES beneficiaries(id),
    CONSTRAINT uk_beneficiary_instrument_reference UNIQUE (instrument_reference),
    INDEX idx_beneficiary_instruments_beneficiary (beneficiary_id),
    INDEX idx_beneficiary_instruments_type (instrument_type),
    INDEX idx_beneficiary_instruments_validation (validation_status)
);

CREATE TABLE IF NOT EXISTS beneficiary_validation_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    instrument_id BIGINT NOT NULL,
    validation_reference VARCHAR(80) NOT NULL,
    provider_code VARCHAR(80) NULL,
    result_status VARCHAR(40) NOT NULL DEFAULT 'PENDING',
    result_message TEXT NULL,
    normalized_result JSON NULL,
    validated_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_beneficiary_validation_instrument FOREIGN KEY (instrument_id) REFERENCES beneficiary_instruments(id),
    CONSTRAINT uk_beneficiary_validation_reference UNIQUE (validation_reference),
    INDEX idx_beneficiary_validation_instrument (instrument_id),
    INDEX idx_beneficiary_validation_status (result_status)
);

CREATE TABLE IF NOT EXISTS fx_quotes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    quote_reference VARCHAR(80) NOT NULL,
    corridor_id BIGINT NOT NULL,
    merchant_id BIGINT NULL,
    source_currency_code CHAR(3) NOT NULL,
    destination_currency_code CHAR(3) NOT NULL,
    source_amount DECIMAL(19,4) NOT NULL,
    destination_amount DECIMAL(19,4) NOT NULL,
    rate DECIMAL(20,10) NOT NULL,
    spread_amount DECIMAL(19,4) NOT NULL DEFAULT 0.0000,
    fee_amount DECIMAL(19,4) NOT NULL DEFAULT 0.0000,
    rate_source VARCHAR(128) NULL,
    status VARCHAR(40) NOT NULL DEFAULT 'ACTIVE',
    expires_at TIMESTAMP NOT NULL,
    accepted_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_fx_quote_corridor FOREIGN KEY (corridor_id) REFERENCES corridors(id),
    CONSTRAINT uk_fx_quote_reference UNIQUE (quote_reference),
    INDEX idx_fx_quotes_corridor (corridor_id),
    INDEX idx_fx_quotes_merchant (merchant_id),
    INDEX idx_fx_quotes_status_expiry (status, expires_at)
);

CREATE TABLE IF NOT EXISTS cross_border_transfers (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    transfer_reference VARCHAR(80) NOT NULL,
    merchant_id BIGINT NULL,
    merchant_number VARCHAR(64) NULL,
    corridor_id BIGINT NOT NULL,
    route_id BIGINT NULL,
    beneficiary_id BIGINT NOT NULL,
    beneficiary_instrument_id BIGINT NOT NULL,
    fx_quote_id BIGINT NULL,
    source_amount DECIMAL(19,4) NOT NULL,
    source_currency_code CHAR(3) NOT NULL,
    destination_amount DECIMAL(19,4) NOT NULL,
    destination_currency_code CHAR(3) NOT NULL,
    purpose_code VARCHAR(80) NOT NULL,
    status VARCHAR(60) NOT NULL DEFAULT 'CREATED',
    compliance_case_id BIGINT NULL,
    compliance_hold_active BOOLEAN NOT NULL DEFAULT FALSE,
    treasury_reservation_reference VARCHAR(80) NULL,
    provider_reference VARCHAR(128) NULL,
    partner_reference VARCHAR(128) NULL,
    failure_code VARCHAR(80) NULL,
    failure_message TEXT NULL,
    metadata JSON NULL,
    created_by VARCHAR(128) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    submitted_at TIMESTAMP NULL,
    delivered_at TIMESTAMP NULL,
    settled_at TIMESTAMP NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_cross_border_transfer_ref UNIQUE (transfer_reference),
    CONSTRAINT fk_xborder_transfer_corridor FOREIGN KEY (corridor_id) REFERENCES corridors(id),
    CONSTRAINT fk_xborder_transfer_route FOREIGN KEY (route_id) REFERENCES corridor_routes(id),
    CONSTRAINT fk_xborder_transfer_beneficiary FOREIGN KEY (beneficiary_id) REFERENCES beneficiaries(id),
    CONSTRAINT fk_xborder_transfer_instrument FOREIGN KEY (beneficiary_instrument_id) REFERENCES beneficiary_instruments(id),
    CONSTRAINT fk_xborder_transfer_quote FOREIGN KEY (fx_quote_id) REFERENCES fx_quotes(id),
    CONSTRAINT fk_xborder_transfer_case FOREIGN KEY (compliance_case_id) REFERENCES compliance_cases(id),
    INDEX idx_cross_border_transfers_merchant (merchant_id),
    INDEX idx_cross_border_transfers_corridor (corridor_id),
    INDEX idx_cross_border_transfers_status (status),
    INDEX idx_cross_border_transfers_hold (compliance_hold_active)
);

CREATE TABLE IF NOT EXISTS cross_border_transfer_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    transfer_id BIGINT NOT NULL,
    event_type VARCHAR(80) NOT NULL,
    from_status VARCHAR(60) NULL,
    to_status VARCHAR(60) NULL,
    actor VARCHAR(128) NULL,
    notes TEXT NULL,
    event_payload JSON NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_xborder_event_transfer FOREIGN KEY (transfer_id) REFERENCES cross_border_transfers(id),
    INDEX idx_cross_border_events_transfer (transfer_id),
    INDEX idx_cross_border_events_type (event_type)
);

CREATE TABLE IF NOT EXISTS corridor_settlement_batches (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    settlement_reference VARCHAR(80) NOT NULL,
    corridor_id BIGINT NOT NULL,
    partner_code VARCHAR(80) NULL,
    settlement_currency_code CHAR(3) NOT NULL,
    business_date DATE NOT NULL,
    status VARCHAR(40) NOT NULL DEFAULT 'OPEN',
    gross_amount DECIMAL(19,4) NOT NULL DEFAULT 0.0000,
    fee_amount DECIMAL(19,4) NOT NULL DEFAULT 0.0000,
    net_amount DECIMAL(19,4) NOT NULL DEFAULT 0.0000,
    variance_amount DECIMAL(19,4) NOT NULL DEFAULT 0.0000,
    approved_by VARCHAR(128) NULL,
    approved_at TIMESTAMP NULL,
    paid_at TIMESTAMP NULL,
    closed_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_corridor_settlement_ref UNIQUE (settlement_reference),
    CONSTRAINT fk_corridor_settlement_corridor FOREIGN KEY (corridor_id) REFERENCES corridors(id),
    INDEX idx_corridor_settlement_corridor_date (corridor_id, business_date),
    INDEX idx_corridor_settlement_status (status)
);

CREATE TABLE IF NOT EXISTS corridor_settlement_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    settlement_batch_id BIGINT NOT NULL,
    transfer_id BIGINT NOT NULL,
    source_amount DECIMAL(19,4) NOT NULL,
    destination_amount DECIMAL(19,4) NOT NULL,
    settlement_amount DECIMAL(19,4) NOT NULL,
    fee_amount DECIMAL(19,4) NOT NULL DEFAULT 0.0000,
    status VARCHAR(40) NOT NULL DEFAULT 'OPEN',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_corridor_settlement_item_batch FOREIGN KEY (settlement_batch_id) REFERENCES corridor_settlement_batches(id),
    CONSTRAINT fk_corridor_settlement_item_transfer FOREIGN KEY (transfer_id) REFERENCES cross_border_transfers(id),
    CONSTRAINT uk_corridor_settlement_transfer UNIQUE (settlement_batch_id, transfer_id),
    INDEX idx_corridor_settlement_items_transfer (transfer_id)
);

CREATE TABLE IF NOT EXISTS treasury_exposure_snapshots (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    snapshot_reference VARCHAR(80) NOT NULL,
    corridor_id BIGINT NULL,
    partner_code VARCHAR(80) NULL,
    currency_code CHAR(3) NOT NULL,
    available_balance DECIMAL(19,4) NOT NULL DEFAULT 0.0000,
    reserved_balance DECIMAL(19,4) NOT NULL DEFAULT 0.0000,
    pending_delivery_exposure DECIMAL(19,4) NOT NULL DEFAULT 0.0000,
    unsettled_exposure DECIMAL(19,4) NOT NULL DEFAULT 0.0000,
    variance_exposure DECIMAL(19,4) NOT NULL DEFAULT 0.0000,
    captured_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    captured_by VARCHAR(128) NULL,
    CONSTRAINT uk_treasury_exposure_snapshot_ref UNIQUE (snapshot_reference),
    CONSTRAINT fk_treasury_exposure_corridor FOREIGN KEY (corridor_id) REFERENCES corridors(id),
    INDEX idx_treasury_exposure_corridor (corridor_id),
    INDEX idx_treasury_exposure_currency (currency_code),
    INDEX idx_treasury_exposure_captured (captured_at)
);

CREATE TABLE IF NOT EXISTS cross_border_report_runs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    report_reference VARCHAR(80) NOT NULL,
    report_type VARCHAR(80) NOT NULL,
    corridor_id BIGINT NULL,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    status VARCHAR(40) NOT NULL DEFAULT 'REQUESTED',
    requested_by VARCHAR(128) NULL,
    generated_at TIMESTAMP NULL,
    file_reference VARCHAR(255) NULL,
    record_count INT NOT NULL DEFAULT 0,
    totals JSON NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_cross_border_report_ref UNIQUE (report_reference),
    CONSTRAINT fk_cross_border_report_corridor FOREIGN KEY (corridor_id) REFERENCES corridors(id),
    INDEX idx_cross_border_reports_type (report_type),
    INDEX idx_cross_border_reports_period (period_start, period_end),
    INDEX idx_cross_border_reports_status (status)
);

INSERT INTO corridors
(corridor_code, source_country_code, destination_country_code, source_currency_code, destination_currency_code, display_name, status, risk_level, settlement_model, compliance_policy_code)
VALUES
('UG-KE-UGX-KES', 'UG', 'KE', 'UGX', 'KES', 'Uganda to Kenya', 'DRAFT', 'MEDIUM', 'PARTNER_LED', 'EAC_STANDARD'),
('UG-TZ-UGX-TZS', 'UG', 'TZ', 'UGX', 'TZS', 'Uganda to Tanzania', 'DRAFT', 'MEDIUM', 'PARTNER_LED', 'EAC_STANDARD')
ON DUPLICATE KEY UPDATE
    display_name = VALUES(display_name),
    risk_level = VALUES(risk_level),
    settlement_model = VALUES(settlement_model),
    compliance_policy_code = VALUES(compliance_policy_code);
