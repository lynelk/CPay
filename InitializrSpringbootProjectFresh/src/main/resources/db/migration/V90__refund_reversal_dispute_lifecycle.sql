ALTER TABLE refunds
  ADD COLUMN approval_required VARCHAR(3) NOT NULL DEFAULT 'NO',
  ADD COLUMN approval_status VARCHAR(32) NULL,
  ADD COLUMN requested_by VARCHAR(160) NULL,
  ADD COLUMN approved_by VARCHAR(160) NULL,
  ADD COLUMN approved_at TIMESTAMP NULL,
  ADD COLUMN split_execution_reference VARCHAR(64) NULL;

CREATE TABLE IF NOT EXISTS refund_attempts (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  refund_id BIGINT UNSIGNED NOT NULL,
  attempt_number INT NOT NULL,
  provider_channel VARCHAR(64) NULL,
  provider_reference VARCHAR(190) NULL,
  outcome VARCHAR(32) NOT NULL DEFAULT 'PROCESSING',
  failure_code VARCHAR(120) NULL,
  failure_message VARCHAR(1000) NULL,
  started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  completed_at TIMESTAMP NULL,
  UNIQUE KEY uk_refund_attempt_number (refund_id, attempt_number),
  KEY idx_refund_attempt_outcome (outcome, started_at),
  CONSTRAINT fk_refund_attempt_refund FOREIGN KEY (refund_id) REFERENCES refunds(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS payment_reversals (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  reversal_reference VARCHAR(64) NOT NULL,
  merchant_id BIGINT NOT NULL,
  original_transaction_id BIGINT NOT NULL,
  original_merchant_ref VARCHAR(120) NOT NULL,
  provider_channel VARCHAR(64) NULL,
  provider_reference VARCHAR(190) NULL,
  amount DECIMAL(18,6) NOT NULL,
  currency_code VARCHAR(3) NOT NULL,
  reversal_type VARCHAR(32) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'RECEIVED',
  reason_code VARCHAR(120) NULL,
  evidence_json JSON NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  completed_at TIMESTAMP NULL,
  UNIQUE KEY uk_payment_reversal_reference (reversal_reference),
  KEY idx_payment_reversal_tx (merchant_id, original_transaction_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS payment_disputes (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  dispute_reference VARCHAR(64) NOT NULL,
  merchant_id BIGINT NOT NULL,
  transaction_reference VARCHAR(120) NOT NULL,
  dispute_type VARCHAR(48) NOT NULL,
  amount DECIMAL(18,6) NULL,
  currency_code VARCHAR(3) NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'OPEN',
  reason_code VARCHAR(120) NULL,
  customer_reference VARCHAR(190) NULL,
  assigned_to VARCHAR(160) NULL,
  due_at TIMESTAMP NULL,
  opened_by VARCHAR(160) NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  closed_at TIMESTAMP NULL,
  UNIQUE KEY uk_payment_dispute_reference (dispute_reference),
  KEY idx_payment_dispute_merchant (merchant_id, status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS payment_dispute_events (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  dispute_id BIGINT NOT NULL,
  event_type VARCHAR(64) NOT NULL,
  actor_reference VARCHAR(160) NULL,
  notes VARCHAR(2000) NULL,
  evidence_reference VARCHAR(255) NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_dispute_event (dispute_id, created_at),
  CONSTRAINT fk_dispute_event_dispute FOREIGN KEY (dispute_id) REFERENCES payment_disputes(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS payment_financial_timeline (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  merchant_id BIGINT NOT NULL,
  transaction_reference VARCHAR(120) NOT NULL,
  event_reference VARCHAR(120) NULL,
  event_type VARCHAR(64) NOT NULL,
  event_status VARCHAR(32) NULL,
  amount DECIMAL(18,6) NULL,
  currency_code VARCHAR(3) NULL,
  detail_json JSON NULL,
  occurred_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_financial_timeline_tx (merchant_id, transaction_reference, occurred_at),
  KEY idx_financial_timeline_event (event_type, occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;