CREATE TABLE IF NOT EXISTS payment_routing_policies (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  policy_code VARCHAR(64) NOT NULL,
  merchant_number VARCHAR(64) NULL,
  operation VARCHAR(16) NOT NULL,
  country_code VARCHAR(3) NULL,
  currency_code VARCHAR(3) NULL,
  strategy VARCHAR(32) NOT NULL DEFAULT 'BALANCED',
  fallback_allowed VARCHAR(3) NOT NULL DEFAULT 'YES',
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  created_by VARCHAR(160) NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_routing_policy_code (policy_code),
  KEY idx_routing_policy_scope (merchant_number, operation, country_code, currency_code, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS payment_routing_rules (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  policy_id BIGINT NOT NULL,
  channel_code VARCHAR(64) NOT NULL,
  priority_rank INT NOT NULL DEFAULT 100,
  weight DECIMAL(10,4) NOT NULL DEFAULT 1.0000,
  cost_score DECIMAL(10,4) NOT NULL DEFAULT 0.0000,
  min_success_rate DECIMAL(8,5) NULL,
  max_latency_ms BIGINT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_routing_rule_channel (policy_id, channel_code),
  CONSTRAINT fk_routing_rule_policy FOREIGN KEY (policy_id) REFERENCES payment_routing_policies(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS provider_health_metrics (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  channel_code VARCHAR(64) NOT NULL,
  country_code VARCHAR(3) NULL,
  currency_code VARCHAR(3) NULL,
  success_count BIGINT NOT NULL DEFAULT 0,
  failure_count BIGINT NOT NULL DEFAULT 0,
  average_latency_ms BIGINT NOT NULL DEFAULT 0,
  status VARCHAR(32) NOT NULL DEFAULT 'HEALTHY',
  last_success_at TIMESTAMP NULL,
  last_failure_at TIMESTAMP NULL,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_provider_health (channel_code, country_code, currency_code),
  KEY idx_provider_health_status (status, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS payment_route_decisions (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  decision_reference VARCHAR(64) NOT NULL,
  merchant_number VARCHAR(64) NOT NULL,
  transaction_reference VARCHAR(120) NOT NULL,
  operation VARCHAR(16) NOT NULL,
  country_code VARCHAR(3) NULL,
  currency_code VARCHAR(3) NULL,
  policy_id BIGINT NULL,
  selected_channel VARCHAR(64) NOT NULL,
  candidate_channels_json JSON NULL,
  explanation VARCHAR(1000) NULL,
  outcome VARCHAR(32) NULL,
  latency_ms BIGINT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  completed_at TIMESTAMP NULL,
  UNIQUE KEY uk_route_decision_reference (decision_reference),
  KEY idx_route_decision_tx (merchant_number, transaction_reference),
  KEY idx_route_decision_channel (selected_channel, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO payment_routing_policies
  (policy_code, merchant_number, operation, country_code, currency_code, strategy, fallback_allowed, status, created_by)
VALUES
  ('DEFAULT-COLLECT', NULL, 'COLLECT', NULL, NULL, 'BALANCED', 'YES', 'ACTIVE', 'SYSTEM'),
  ('DEFAULT-PAYOUT', NULL, 'PAYOUT', NULL, NULL, 'BALANCED', 'YES', 'ACTIVE', 'SYSTEM')
ON DUPLICATE KEY UPDATE updated_at=CURRENT_TIMESTAMP;