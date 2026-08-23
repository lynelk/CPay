CREATE TABLE IF NOT EXISTS developer_projects (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  merchant_id BIGINT NOT NULL,
  project_reference VARCHAR(64) NOT NULL,
  project_name VARCHAR(160) NOT NULL,
  description VARCHAR(500) NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  created_by VARCHAR(160) NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_developer_project_reference (project_reference),
  KEY idx_developer_project_merchant (merchant_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS developer_project_environments (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  project_id BIGINT NOT NULL,
  environment VARCHAR(16) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  production_eligible VARCHAR(3) NOT NULL DEFAULT 'NO',
  activated_by VARCHAR(160) NULL,
  activated_at TIMESTAMP NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_developer_project_environment (project_id, environment),
  CONSTRAINT fk_developer_environment_project FOREIGN KEY (project_id) REFERENCES developer_projects(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS developer_service_accounts (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  project_id BIGINT NOT NULL,
  service_account_reference VARCHAR(64) NOT NULL,
  display_name VARCHAR(160) NOT NULL,
  scopes_json JSON NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  created_by VARCHAR(160) NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  revoked_at TIMESTAMP NULL,
  UNIQUE KEY uk_developer_service_account_reference (service_account_reference),
  KEY idx_developer_service_account_project (project_id, status),
  CONSTRAINT fk_developer_service_account_project FOREIGN KEY (project_id) REFERENCES developer_projects(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS developer_credentials (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  service_account_id BIGINT NOT NULL,
  credential_reference VARCHAR(64) NOT NULL,
  key_prefix VARCHAR(24) NOT NULL,
  secret_hash CHAR(64) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  expires_at TIMESTAMP NULL,
  last_used_at TIMESTAMP NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  revoked_at TIMESTAMP NULL,
  UNIQUE KEY uk_developer_credential_reference (credential_reference),
  KEY idx_developer_credential_account (service_account_id, status),
  CONSTRAINT fk_developer_credential_account FOREIGN KEY (service_account_id) REFERENCES developer_service_accounts(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS developer_api_request_log (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  merchant_id BIGINT NOT NULL,
  project_id BIGINT NULL,
  service_account_id BIGINT NULL,
  request_id VARCHAR(120) NOT NULL,
  http_method VARCHAR(12) NOT NULL,
  route_template VARCHAR(255) NOT NULL,
  environment VARCHAR(16) NULL,
  response_status INT NULL,
  latency_ms BIGINT NULL,
  error_code VARCHAR(120) NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_developer_api_log_merchant (merchant_id, created_at),
  KEY idx_developer_api_log_project (project_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS developer_test_events (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  merchant_id BIGINT NOT NULL,
  project_id BIGINT NOT NULL,
  event_reference VARCHAR(64) NOT NULL,
  event_type VARCHAR(120) NOT NULL,
  payload_json JSON NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'CREATED',
  created_by VARCHAR(160) NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  dispatched_at TIMESTAMP NULL,
  UNIQUE KEY uk_developer_test_event_reference (event_reference),
  KEY idx_developer_test_event_project (project_id, created_at),
  CONSTRAINT fk_developer_test_event_project FOREIGN KEY (project_id) REFERENCES developer_projects(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;