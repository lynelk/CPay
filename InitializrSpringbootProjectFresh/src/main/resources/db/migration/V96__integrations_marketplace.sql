CREATE TABLE IF NOT EXISTS integration_connectors (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  connector_code VARCHAR(64) NOT NULL,
  connector_name VARCHAR(160) NOT NULL,
  connector_category VARCHAR(64) NOT NULL,
  description VARCHAR(800) NULL,
  publisher VARCHAR(160) NOT NULL DEFAULT 'Core-Synergies',
  auth_type VARCHAR(32) NOT NULL DEFAULT 'OAUTH2',
  required_service_code VARCHAR(64) NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_integration_connector_code (connector_code),
  KEY idx_integration_connector_category (connector_category, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS integration_connector_versions (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  connector_id BIGINT NOT NULL,
  version_number VARCHAR(32) NOT NULL,
  manifest_json JSON NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  released_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deprecated_at TIMESTAMP NULL,
  UNIQUE KEY uk_integration_connector_version (connector_id, version_number),
  CONSTRAINT fk_integration_version_connector FOREIGN KEY (connector_id) REFERENCES integration_connectors(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS integration_installations (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  merchant_id BIGINT NOT NULL,
  connector_id BIGINT NOT NULL,
  connector_version_id BIGINT NOT NULL,
  installation_reference VARCHAR(64) NOT NULL,
  environment VARCHAR(16) NOT NULL,
  display_name VARCHAR(160) NOT NULL,
  credential_reference VARCHAR(255) NULL,
  configuration_json JSON NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  installed_by VARCHAR(160) NULL,
  installed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  uninstalled_at TIMESTAMP NULL,
  UNIQUE KEY uk_integration_installation_reference (installation_reference),
  UNIQUE KEY uk_integration_installation_scope (merchant_id, connector_id, environment, display_name),
  KEY idx_integration_installation_merchant (merchant_id, status),
  CONSTRAINT fk_integration_installation_connector FOREIGN KEY (connector_id) REFERENCES integration_connectors(id),
  CONSTRAINT fk_integration_installation_version FOREIGN KEY (connector_version_id) REFERENCES integration_connector_versions(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS integration_field_mappings (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  installation_id BIGINT NOT NULL,
  mapping_reference VARCHAR(64) NOT NULL,
  object_type VARCHAR(80) NOT NULL,
  source_field VARCHAR(190) NOT NULL,
  target_field VARCHAR(190) NOT NULL,
  transformation VARCHAR(500) NULL,
  direction VARCHAR(16) NOT NULL DEFAULT 'BIDIRECTIONAL',
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_integration_mapping_reference (mapping_reference),
  KEY idx_integration_mapping_installation (installation_id, object_type, status),
  CONSTRAINT fk_integration_mapping_installation FOREIGN KEY (installation_id) REFERENCES integration_installations(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS integration_event_subscriptions (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  installation_id BIGINT NOT NULL,
  event_type VARCHAR(120) NOT NULL,
  direction VARCHAR(16) NOT NULL DEFAULT 'OUTBOUND',
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_integration_event_subscription (installation_id, event_type, direction),
  CONSTRAINT fk_integration_event_installation FOREIGN KEY (installation_id) REFERENCES integration_installations(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS integration_sync_jobs (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  installation_id BIGINT NOT NULL,
  job_reference VARCHAR(80) NOT NULL,
  idempotency_key VARCHAR(120) NOT NULL,
  job_type VARCHAR(80) NOT NULL,
  object_reference VARCHAR(190) NULL,
  payload_json JSON NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'QUEUED',
  attempt_count INT NOT NULL DEFAULT 0,
  max_attempts INT NOT NULL DEFAULT 5,
  next_attempt_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  last_error VARCHAR(1000) NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  completed_at TIMESTAMP NULL,
  UNIQUE KEY uk_integration_job_reference (job_reference),
  UNIQUE KEY uk_integration_job_idempotency (installation_id, idempotency_key),
  KEY idx_integration_job_queue (status, next_attempt_at),
  CONSTRAINT fk_integration_job_installation FOREIGN KEY (installation_id) REFERENCES integration_installations(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS integration_sync_attempts (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  job_id BIGINT NOT NULL,
  attempt_number INT NOT NULL,
  outcome VARCHAR(32) NOT NULL,
  response_code VARCHAR(120) NULL,
  response_summary VARCHAR(1000) NULL,
  attempted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_integration_sync_attempt (job_id, attempt_number),
  CONSTRAINT fk_integration_attempt_job FOREIGN KEY (job_id) REFERENCES integration_sync_jobs(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO integration_connectors
  (connector_code, connector_name, connector_category, description, publisher, auth_type, required_service_code, status)
VALUES
  ('GENERIC_WEBHOOK', 'Generic Webhook', 'WORKFLOW', 'Map Cito events into a generic webhook workflow.', 'Core-Synergies', 'SECRET_REFERENCE', 'INTEGRATIONS_MARKETPLACE', 'ACTIVE'),
  ('GENERIC_ACCOUNTING_EXPORT', 'Generic Accounting Export', 'ACCOUNTING', 'Export normalized payment, fee, refund and settlement records for accounting systems.', 'Core-Synergies', 'SECRET_REFERENCE', 'INTEGRATIONS_MARKETPLACE', 'ACTIVE')
ON DUPLICATE KEY UPDATE connector_name=VALUES(connector_name), description=VALUES(description), status='ACTIVE';

INSERT INTO integration_connector_versions(connector_id, version_number, manifest_json, status)
SELECT id, '1.0.0', JSON_OBJECT('supports', JSON_ARRAY('payments','refunds','settlements'), 'transport', 'configurable'), 'ACTIVE'
FROM integration_connectors c
WHERE c.connector_code IN ('GENERIC_WEBHOOK','GENERIC_ACCOUNTING_EXPORT')
AND NOT EXISTS (
  SELECT 1 FROM integration_connector_versions v WHERE v.connector_id=c.id AND v.version_number='1.0.0'
);