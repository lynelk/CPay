CREATE TABLE IF NOT EXISTS virtual_account_providers (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  provider_code VARCHAR(64) NOT NULL,
  provider_name VARCHAR(160) NOT NULL,
  country_code VARCHAR(3) NOT NULL,
  currency_code VARCHAR(3) NOT NULL,
  environment VARCHAR(16) NOT NULL,
  provider_type VARCHAR(32) NOT NULL DEFAULT 'BANK',
  connector_reference VARCHAR(190) NULL,
  certified VARCHAR(3) NOT NULL DEFAULT 'NO',
  status VARCHAR(32) NOT NULL DEFAULT 'INACTIVE',
  activated_by VARCHAR(160) NULL,
  activated_at TIMESTAMP NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_virtual_account_provider (provider_code, country_code, currency_code, environment)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO virtual_account_providers
  (provider_code, provider_name, country_code, currency_code, environment, provider_type, certified, status, activated_by, activated_at)
VALUES
  ('CITO_SANDBOX', 'Cito Virtual Account Sandbox', 'UG', 'UGX', 'SANDBOX', 'SIMULATOR', 'YES', 'ACTIVE', 'SYSTEM', CURRENT_TIMESTAMP)
ON DUPLICATE KEY UPDATE provider_name=VALUES(provider_name), status='ACTIVE', certified='YES';

CREATE TABLE IF NOT EXISTS virtual_accounts (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  merchant_id BIGINT NOT NULL,
  account_reference VARCHAR(64) NOT NULL,
  provider_id BIGINT NOT NULL,
  environment VARCHAR(16) NOT NULL,
  account_type VARCHAR(32) NOT NULL DEFAULT 'TEMPORARY',
  account_name VARCHAR(190) NOT NULL,
  account_number VARCHAR(64) NOT NULL,
  bank_code VARCHAR(64) NULL,
  bank_name VARCHAR(160) NULL,
  customer_reference VARCHAR(190) NULL,
  purpose_reference VARCHAR(190) NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  expires_at TIMESTAMP NULL,
  created_by VARCHAR(160) NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  closed_at TIMESTAMP NULL,
  UNIQUE KEY uk_virtual_account_reference (account_reference),
  UNIQUE KEY uk_virtual_account_number_env (provider_id, account_number, environment),
  KEY idx_virtual_account_merchant (merchant_id, environment, status),
  CONSTRAINT fk_virtual_account_provider FOREIGN KEY (provider_id) REFERENCES virtual_account_providers(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS virtual_account_incoming_transfers (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  transfer_reference VARCHAR(80) NOT NULL,
  provider_id BIGINT NOT NULL,
  virtual_account_id BIGINT NOT NULL,
  provider_transfer_reference VARCHAR(190) NULL,
  amount DECIMAL(18,6) NOT NULL,
  currency_code VARCHAR(3) NOT NULL,
  sender_name VARCHAR(190) NULL,
  sender_reference VARCHAR(190) NULL,
  narration VARCHAR(500) NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'RECEIVED',
  received_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  processed_at TIMESTAMP NULL,
  UNIQUE KEY uk_virtual_transfer_reference (transfer_reference),
  UNIQUE KEY uk_virtual_provider_transfer (provider_id, provider_transfer_reference),
  KEY idx_virtual_transfer_account (virtual_account_id, received_at),
  CONSTRAINT fk_virtual_transfer_provider FOREIGN KEY (provider_id) REFERENCES virtual_account_providers(id),
  CONSTRAINT fk_virtual_transfer_account FOREIGN KEY (virtual_account_id) REFERENCES virtual_accounts(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS virtual_account_matches (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  transfer_id BIGINT NOT NULL,
  merchant_id BIGINT NOT NULL,
  transaction_reference VARCHAR(120) NULL,
  reconciliation_reference VARCHAR(120) NULL,
  match_type VARCHAR(32) NOT NULL DEFAULT 'AUTO',
  status VARCHAR(32) NOT NULL DEFAULT 'MATCHED',
  matched_by VARCHAR(160) NULL,
  matched_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_virtual_account_match_transfer (transfer_id),
  KEY idx_virtual_account_match_merchant (merchant_id, matched_at),
  CONSTRAINT fk_virtual_match_transfer FOREIGN KEY (transfer_id) REFERENCES virtual_account_incoming_transfers(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;