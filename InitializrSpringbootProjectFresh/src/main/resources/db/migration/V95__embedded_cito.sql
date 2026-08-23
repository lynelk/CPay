CREATE TABLE IF NOT EXISTS embedded_partners (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  merchant_id BIGINT NOT NULL,
  partner_reference VARCHAR(64) NOT NULL,
  partner_name VARCHAR(180) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  created_by VARCHAR(160) NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_embedded_partner_reference (partner_reference),
  UNIQUE KEY uk_embedded_partner_merchant (merchant_id),
  KEY idx_embedded_partner_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS embedded_brand_profiles (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  partner_id BIGINT NOT NULL,
  brand_name VARCHAR(160) NOT NULL,
  logo_url VARCHAR(500) NULL,
  primary_color VARCHAR(32) NULL,
  support_email VARCHAR(190) NULL,
  custom_domain VARCHAR(255) NULL,
  terms_url VARCHAR(500) NULL,
  privacy_url VARCHAR(500) NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  updated_by VARCHAR(160) NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_embedded_brand_partner (partner_id),
  CONSTRAINT fk_embedded_brand_partner FOREIGN KEY (partner_id) REFERENCES embedded_partners(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS embedded_onboarding_sessions (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  partner_id BIGINT NOT NULL,
  session_reference VARCHAR(64) NOT NULL,
  token_hash CHAR(64) NOT NULL,
  token_prefix VARCHAR(24) NOT NULL,
  intended_email VARCHAR(190) NULL,
  intended_service_codes_json JSON NULL,
  return_url VARCHAR(500) NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  expires_at TIMESTAMP NOT NULL,
  consumed_at TIMESTAMP NULL,
  created_by VARCHAR(160) NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_embedded_session_reference (session_reference),
  UNIQUE KEY uk_embedded_session_hash (token_hash),
  KEY idx_embedded_session_status (partner_id, status, expires_at),
  CONSTRAINT fk_embedded_session_partner FOREIGN KEY (partner_id) REFERENCES embedded_partners(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS embedded_partner_merchants (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  partner_id BIGINT NOT NULL,
  downstream_merchant_id BIGINT NOT NULL,
  relationship_reference VARCHAR(64) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  created_by VARCHAR(160) NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  ended_at TIMESTAMP NULL,
  UNIQUE KEY uk_embedded_partner_merchant_relation (partner_id, downstream_merchant_id),
  UNIQUE KEY uk_embedded_relationship_reference (relationship_reference),
  CONSTRAINT fk_embedded_relation_partner FOREIGN KEY (partner_id) REFERENCES embedded_partners(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS embedded_service_delegations (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  partner_id BIGINT NOT NULL,
  downstream_merchant_id BIGINT NOT NULL,
  service_code VARCHAR(64) NOT NULL,
  environment VARCHAR(16) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  delegated_by VARCHAR(160) NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  revoked_at TIMESTAMP NULL,
  UNIQUE KEY uk_embedded_service_delegation (partner_id, downstream_merchant_id, service_code, environment),
  KEY idx_embedded_service_downstream (downstream_merchant_id, status),
  CONSTRAINT fk_embedded_delegation_partner FOREIGN KEY (partner_id) REFERENCES embedded_partners(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS embedded_commission_rules (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  partner_id BIGINT NOT NULL,
  service_code VARCHAR(64) NOT NULL,
  commission_type VARCHAR(16) NOT NULL,
  commission_value DECIMAL(18,6) NOT NULL,
  currency_code VARCHAR(3) NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  created_by VARCHAR(160) NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_embedded_commission_rule (partner_id, service_code),
  CONSTRAINT fk_embedded_commission_partner FOREIGN KEY (partner_id) REFERENCES embedded_partners(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;