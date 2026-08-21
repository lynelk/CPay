-- CPay Identification & Validation Module: generalized validation core.
-- Additive. Keep V37 pilot tables readable; new model becomes the system of record.

CREATE TABLE IF NOT EXISTS validation_cases (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  case_reference VARCHAR(64) NOT NULL,
  merchant_id BIGINT NOT NULL,
  merchant_reference VARCHAR(120) NULL,
  subject_type VARCHAR(40) NOT NULL,
  subject_reference VARCHAR(120) NULL,
  use_case VARCHAR(80) NOT NULL,
  policy_id VARCHAR(100) NOT NULL,
  policy_version INT NOT NULL,
  status VARCHAR(40) NOT NULL DEFAULT 'CREATED',
  execution_backend VARCHAR(20) NOT NULL DEFAULT 'LOCAL_SPRING',
  version BIGINT NOT NULL DEFAULT 0,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  submitted_at TIMESTAMP NULL,
  completed_at TIMESTAMP NULL,
  expires_at TIMESTAMP NULL,
  UNIQUE KEY uk_validation_case_reference (case_reference),
  UNIQUE KEY uk_validation_case_merchant_reference (merchant_id, merchant_reference),
  KEY idx_validation_case_merchant_status (merchant_id, status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS validation_checks (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  check_reference VARCHAR(64) NOT NULL,
  case_id BIGINT NOT NULL,
  merchant_id BIGINT NOT NULL,
  capability VARCHAR(80) NOT NULL,
  status VARCHAR(40) NOT NULL DEFAULT 'PENDING',
  provider_code VARCHAR(80) NULL,
  external_reference VARCHAR(255) NULL,
  confidence DECIMAL(8,5) NULL,
  reason_code VARCHAR(120) NULL,
  started_at TIMESTAMP NULL,
  completed_at TIMESTAMP NULL,
  UNIQUE KEY uk_validation_check_reference (check_reference),
  KEY idx_validation_check_case (case_id),
  KEY idx_validation_check_merchant (merchant_id, status),
  CONSTRAINT fk_validation_check_case FOREIGN KEY (case_id) REFERENCES validation_cases(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS validation_provider_attempts (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  attempt_reference VARCHAR(64) NOT NULL,
  check_id BIGINT NOT NULL,
  merchant_id BIGINT NOT NULL,
  provider_code VARCHAR(80) NOT NULL,
  operation_code VARCHAR(80) NOT NULL,
  attempt_number INT NOT NULL,
  outcome VARCHAR(40) NULL,
  external_reference VARCHAR(255) NULL,
  provider_http_status INT NULL,
  provider_cost DECIMAL(18,6) NULL,
  provider_currency VARCHAR(3) NULL,
  technical_error_code VARCHAR(120) NULL,
  requested_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  responded_at TIMESTAMP NULL,
  UNIQUE KEY uk_validation_attempt_reference (attempt_reference),
  UNIQUE KEY uk_validation_attempt_sequence (check_id, provider_code, operation_code, attempt_number),
  KEY idx_validation_attempt_merchant (merchant_id, provider_code, requested_at),
  CONSTRAINT fk_validation_attempt_check FOREIGN KEY (check_id) REFERENCES validation_checks(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS validation_evidence (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  evidence_reference VARCHAR(64) NOT NULL,
  check_id BIGINT NOT NULL,
  merchant_id BIGINT NOT NULL,
  evidence_type VARCHAR(80) NOT NULL,
  normalized_result_json JSON NULL,
  protected_artifact_reference VARCHAR(255) NULL,
  retention_class VARCHAR(80) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  expires_at TIMESTAMP NULL,
  UNIQUE KEY uk_validation_evidence_reference (evidence_reference),
  KEY idx_validation_evidence_merchant (merchant_id, created_at),
  CONSTRAINT fk_validation_evidence_check FOREIGN KEY (check_id) REFERENCES validation_checks(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS validation_consents (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  consent_reference VARCHAR(64) NOT NULL,
  merchant_id BIGINT NOT NULL,
  case_id BIGINT NULL,
  subject_reference VARCHAR(120) NOT NULL,
  purpose VARCHAR(80) NOT NULL,
  capabilities VARCHAR(255) NOT NULL,
  consent_text_version VARCHAR(40) NOT NULL,
  captured_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  channel VARCHAR(40) NULL,
  captured_by VARCHAR(120) NULL,
  expires_at TIMESTAMP NULL,
  withdrawn_at TIMESTAMP NULL,
  evidence_reference VARCHAR(255) NULL,
  UNIQUE KEY uk_validation_consent_reference (consent_reference),
  KEY idx_validation_consent_merchant (merchant_id, subject_reference, purpose)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS validation_audit_events (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  event_reference VARCHAR(64) NOT NULL,
  merchant_id BIGINT NOT NULL,
  case_id BIGINT NULL,
  subject_reference VARCHAR(120) NULL,
  actor VARCHAR(120) NULL,
  action VARCHAR(80) NOT NULL,
  outcome VARCHAR(40) NULL,
  reason_code VARCHAR(120) NULL,
  correlation_id VARCHAR(64) NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_validation_audit_reference (event_reference),
  KEY idx_validation_audit_merchant (merchant_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS validation_usage (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  usage_reference VARCHAR(64) NOT NULL,
  merchant_id BIGINT NOT NULL,
  case_id BIGINT NULL,
  check_id BIGINT NULL,
  capability VARCHAR(80) NOT NULL,
  provider_code VARCHAR(80) NOT NULL,
  provider_operation VARCHAR(80) NULL,
  provider_reference VARCHAR(255) NULL,
  provider_cost DECIMAL(18,6) NULL,
  provider_currency VARCHAR(3) NULL,
  billable_attempt CHAR(1) NOT NULL DEFAULT 'Y',
  merchant_charge DECIMAL(18,6) NULL,
  merchant_currency VARCHAR(3) NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_validation_usage_reference (usage_reference),
  KEY idx_validation_usage_merchant (merchant_id, capability, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
