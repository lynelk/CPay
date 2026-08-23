INSERT INTO cito_service_catalog(service_code, service_name, description, default_sandbox_access, status)
VALUES
  ('INTELLIGENT_ROUTING', 'Intelligent Payment Routing', 'Policy-driven provider routing, health scoring and safe preflight failover', 'YES', 'ACTIVE'),
  ('REFUND_OPERATIONS', 'Refunds, Reversals & Disputes', 'Governed post-payment refund, reversal and dispute operations', 'YES', 'ACTIVE')
ON DUPLICATE KEY UPDATE
  service_name=VALUES(service_name),
  description=VALUES(description),
  default_sandbox_access='YES',
  status='ACTIVE';

UPDATE cito_service_catalog
SET default_sandbox_access='YES'
WHERE service_code IN (
  'MARKETPLACE_PAYMENTS',
  'RECURRING_PAYMENTS',
  'MERCHANT_ANALYTICS',
  'DEVELOPER_CONTROL_PLANE',
  'VIRTUAL_ACCOUNTS',
  'EMBEDDED_CITO',
  'INTEGRATIONS_MARKETPLACE'
);

INSERT IGNORE INTO cito_service_entitlements
  (organization_id, service_code, environment, status, plan_code, approved_by)
SELECT o.id, s.service_code, 'SANDBOX', 'ACTIVE', 'SANDBOX', 'SYSTEM'
FROM cito_organizations o
JOIN cito_service_catalog s ON s.default_sandbox_access='YES' AND s.status='ACTIVE';

ALTER TABLE payment_routing_policies
  ADD COLUMN environment VARCHAR(16) NOT NULL DEFAULT 'SANDBOX' AFTER operation;

ALTER TABLE payment_route_decisions
  ADD COLUMN environment VARCHAR(16) NOT NULL DEFAULT 'SANDBOX' AFTER operation;

CREATE INDEX idx_routing_policy_environment
  ON payment_routing_policies(merchant_number, operation, environment, country_code, currency_code, status);

INSERT INTO payment_routing_policies
  (policy_code, merchant_number, operation, environment, country_code, currency_code, strategy, fallback_allowed, status, created_by)
VALUES
  ('DEFAULT-COLLECT-PRODUCTION', NULL, 'COLLECT', 'PRODUCTION', NULL, NULL, 'BALANCED', 'YES', 'ACTIVE', 'SYSTEM'),
  ('DEFAULT-PAYOUT-PRODUCTION', NULL, 'PAYOUT', 'PRODUCTION', NULL, NULL, 'BALANCED', 'YES', 'ACTIVE', 'SYSTEM')
ON DUPLICATE KEY UPDATE environment=VALUES(environment), status='ACTIVE', updated_at=CURRENT_TIMESTAMP;

CREATE TABLE IF NOT EXISTS marketplace_split_refund_allocations (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  refund_id BIGINT NOT NULL,
  split_execution_id BIGINT NOT NULL,
  subaccount_id BIGINT NOT NULL,
  original_allocation_amount DECIMAL(18,6) NOT NULL,
  refund_allocation_amount DECIMAL(18,6) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'ALLOCATED',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_split_refund_recipient (refund_id, subaccount_id),
  KEY idx_split_refund_execution (split_execution_id, refund_id),
  CONSTRAINT fk_split_refund_refund FOREIGN KEY (refund_id) REFERENCES refunds(id),
  CONSTRAINT fk_split_refund_execution FOREIGN KEY (split_execution_id) REFERENCES marketplace_split_executions(id),
  CONSTRAINT fk_split_refund_subaccount FOREIGN KEY (subaccount_id) REFERENCES marketplace_subaccounts(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS platform_feature_events (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  merchant_id BIGINT NOT NULL,
  event_reference VARCHAR(80) NOT NULL,
  event_type VARCHAR(120) NOT NULL,
  subject_reference VARCHAR(190) NULL,
  payload_json JSON NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  attempt_count INT NOT NULL DEFAULT 0,
  next_attempt_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  last_error VARCHAR(1000) NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  processed_at TIMESTAMP NULL,
  UNIQUE KEY uk_platform_feature_event_reference (event_reference),
  KEY idx_platform_feature_event_queue (status, next_attempt_at),
  KEY idx_platform_feature_event_merchant (merchant_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;