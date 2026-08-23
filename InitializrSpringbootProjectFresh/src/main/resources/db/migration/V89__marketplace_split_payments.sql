CREATE TABLE IF NOT EXISTS marketplace_subaccounts (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  merchant_id BIGINT NOT NULL,
  subaccount_reference VARCHAR(64) NOT NULL,
  display_name VARCHAR(160) NOT NULL,
  currency_code VARCHAR(3) NOT NULL,
  destination_type VARCHAR(32) NOT NULL DEFAULT 'INTERNAL_LEDGER',
  destination_reference VARCHAR(190) NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  metadata_json JSON NULL,
  created_by VARCHAR(160) NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_marketplace_subaccount_reference (subaccount_reference),
  KEY idx_marketplace_subaccount_merchant (merchant_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS marketplace_split_rules (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  merchant_id BIGINT NOT NULL,
  split_rule_reference VARCHAR(64) NOT NULL,
  rule_name VARCHAR(160) NOT NULL,
  currency_code VARCHAR(3) NOT NULL,
  allocation_mode VARCHAR(16) NOT NULL,
  platform_fee_type VARCHAR(16) NOT NULL DEFAULT 'PERCENTAGE',
  platform_fee_value DECIMAL(18,6) NOT NULL DEFAULT 0,
  fee_bearer VARCHAR(32) NOT NULL DEFAULT 'PLATFORM',
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  created_by VARCHAR(160) NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_marketplace_split_rule_reference (split_rule_reference),
  KEY idx_marketplace_split_rule_merchant (merchant_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS marketplace_split_recipients (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  split_rule_id BIGINT NOT NULL,
  subaccount_id BIGINT NOT NULL,
  allocation_value DECIMAL(18,6) NOT NULL,
  priority_rank INT NOT NULL DEFAULT 100,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_split_rule_recipient (split_rule_id, subaccount_id),
  CONSTRAINT fk_split_recipient_rule FOREIGN KEY (split_rule_id) REFERENCES marketplace_split_rules(id),
  CONSTRAINT fk_split_recipient_subaccount FOREIGN KEY (subaccount_id) REFERENCES marketplace_subaccounts(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS marketplace_split_executions (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  merchant_id BIGINT NOT NULL,
  execution_reference VARCHAR(64) NOT NULL,
  transaction_reference VARCHAR(120) NOT NULL,
  split_rule_reference VARCHAR(64) NOT NULL,
  currency_code VARCHAR(3) NOT NULL,
  gross_amount DECIMAL(18,6) NOT NULL,
  platform_fee_amount DECIMAL(18,6) NOT NULL DEFAULT 0,
  distributable_amount DECIMAL(18,6) NOT NULL,
  allocated_amount DECIMAL(18,6) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'ALLOCATED',
  snapshot_json JSON NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_split_execution_reference (execution_reference),
  UNIQUE KEY uk_split_execution_transaction (merchant_id, transaction_reference, split_rule_reference),
  KEY idx_split_execution_merchant (merchant_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS marketplace_split_allocations (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  execution_id BIGINT NOT NULL,
  subaccount_id BIGINT NOT NULL,
  allocation_amount DECIMAL(18,6) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'PENDING_SETTLEMENT',
  settlement_reference VARCHAR(120) NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  settled_at TIMESTAMP NULL,
  UNIQUE KEY uk_split_execution_subaccount (execution_id, subaccount_id),
  KEY idx_split_allocation_status (status, created_at),
  CONSTRAINT fk_split_allocation_execution FOREIGN KEY (execution_id) REFERENCES marketplace_split_executions(id),
  CONSTRAINT fk_split_allocation_subaccount FOREIGN KEY (subaccount_id) REFERENCES marketplace_subaccounts(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;