CREATE TABLE IF NOT EXISTS cito_organizations (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  organization_reference VARCHAR(64) NOT NULL,
  merchant_id BIGINT NULL,
  organization_name VARCHAR(200) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_cito_org_reference (organization_reference),
  UNIQUE KEY uk_cito_org_merchant (merchant_id),
  KEY idx_cito_org_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS cito_service_catalog (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  service_code VARCHAR(64) NOT NULL,
  service_name VARCHAR(120) NOT NULL,
  description VARCHAR(500) NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  default_sandbox_access VARCHAR(3) NOT NULL DEFAULT 'NO',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_cito_service_code (service_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO cito_service_catalog(service_code, service_name, description, default_sandbox_access)
VALUES
  ('CPAY', 'CPay Payments', 'Collections, payouts, reconciliation and payment operations', 'YES'),
  ('IDENTITY_VALIDATION', 'Identity & Validation', 'KYC, KYB and configurable identity validation', 'YES'),
  ('COMMUNICATIONS', 'Communications', 'SMS and email routing, templates and delivery operations', 'YES'),
  ('VENDING', 'Vending', 'Vending locations, devices, pricing and rentals', 'YES'),
  ('BILLING', 'Billing', 'Usage metering, rating and invoicing', 'YES'),
  ('MARKETPLACE_PAYMENTS', 'Marketplace Payments', 'Subaccounts, splits and platform commissions', 'NO'),
  ('RECURRING_PAYMENTS', 'Recurring Payments', 'Plans, subscriptions, mandates and scheduled charges', 'NO'),
  ('MERCHANT_ANALYTICS', 'Merchant Intelligence', 'Payment and provider performance analytics', 'YES'),
  ('DEVELOPER_CONTROL_PLANE', 'Developer Control Plane', 'Projects, service accounts, API activity and test tools', 'YES'),
  ('VIRTUAL_ACCOUNTS', 'Virtual Accounts', 'Provider-backed account-to-account collection rails', 'YES'),
  ('EMBEDDED_CITO', 'Embedded Cito', 'Partner and white-label embedded Cito capabilities', 'NO'),
  ('INTEGRATIONS_MARKETPLACE', 'Integrations Marketplace', 'Installable accounting, ERP, POS and workflow connectors', 'YES')
ON DUPLICATE KEY UPDATE
  service_name=VALUES(service_name),
  description=VALUES(description),
  default_sandbox_access=VALUES(default_sandbox_access);

CREATE TABLE IF NOT EXISTS cito_service_entitlements (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  organization_id BIGINT NOT NULL,
  service_code VARCHAR(64) NOT NULL,
  environment VARCHAR(16) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'REQUESTED',
  plan_code VARCHAR(64) NULL,
  starts_at TIMESTAMP NULL,
  ends_at TIMESTAMP NULL,
  approved_by VARCHAR(160) NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_cito_entitlement (organization_id, service_code, environment),
  KEY idx_cito_entitlement_status (service_code, environment, status),
  CONSTRAINT fk_cito_entitlement_org FOREIGN KEY (organization_id) REFERENCES cito_organizations(id),
  CONSTRAINT fk_cito_entitlement_service FOREIGN KEY (service_code) REFERENCES cito_service_catalog(service_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS cito_role_templates (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  role_code VARCHAR(64) NOT NULL,
  role_name VARCHAR(120) NOT NULL,
  permissions_json JSON NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_cito_role_code (role_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO cito_role_templates(role_code, role_name, permissions_json)
VALUES
  ('OWNER', 'Organization Owner', JSON_ARRAY('ORG_ADMIN','SERVICE_ADMIN','FINANCE','DEVELOPER','VIEW')),
  ('FINANCE', 'Finance', JSON_ARRAY('FINANCE','VIEW')),
  ('DEVELOPER', 'Developer', JSON_ARRAY('DEVELOPER','VIEW')),
  ('OPERATIONS', 'Operations', JSON_ARRAY('OPERATIONS','VIEW')),
  ('COMPLIANCE', 'Compliance', JSON_ARRAY('COMPLIANCE','VIEW')),
  ('VIEWER', 'Viewer', JSON_ARRAY('VIEW'))
ON DUPLICATE KEY UPDATE role_name=VALUES(role_name), permissions_json=VALUES(permissions_json);

CREATE TABLE IF NOT EXISTS cito_access_grants (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  organization_id BIGINT NOT NULL,
  user_reference VARCHAR(190) NOT NULL,
  role_code VARCHAR(64) NOT NULL,
  service_code VARCHAR(64) NULL,
  environment VARCHAR(16) NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  granted_by VARCHAR(160) NULL,
  granted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  revoked_at TIMESTAMP NULL,
  UNIQUE KEY uk_cito_access_grant (organization_id, user_reference, role_code, service_code, environment),
  KEY idx_cito_access_user (user_reference, status),
  CONSTRAINT fk_cito_access_org FOREIGN KEY (organization_id) REFERENCES cito_organizations(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS cito_access_reviews (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  review_reference VARCHAR(64) NOT NULL,
  organization_id BIGINT NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'PENDING_REVIEW',
  requested_by VARCHAR(160) NULL,
  reviewer VARCHAR(160) NULL,
  due_at TIMESTAMP NULL,
  completed_at TIMESTAMP NULL,
  notes VARCHAR(1000) NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_cito_access_review_ref (review_reference),
  KEY idx_cito_access_review_org (organization_id, status),
  CONSTRAINT fk_cito_access_review_org FOREIGN KEY (organization_id) REFERENCES cito_organizations(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS cito_access_events (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  organization_id BIGINT NULL,
  event_type VARCHAR(80) NOT NULL,
  target_reference VARCHAR(190) NULL,
  actor_reference VARCHAR(190) NULL,
  detail_json JSON NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_cito_access_event_org (organization_id, created_at),
  KEY idx_cito_access_event_type (event_type, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;