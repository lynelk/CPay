ALTER TABLE payment_route_decisions ADD COLUMN merchant_id BIGINT NULL AFTER merchant_number;
CREATE INDEX idx_route_decision_merchant_id ON payment_route_decisions(merchant_id, created_at);

CREATE TABLE IF NOT EXISTS merchant_analytics_daily (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  merchant_id BIGINT NOT NULL,
  metric_date DATE NOT NULL,
  transaction_count BIGINT NOT NULL DEFAULT 0,
  successful_count BIGINT NOT NULL DEFAULT 0,
  failed_count BIGINT NOT NULL DEFAULT 0,
  transaction_volume DECIMAL(20,6) NOT NULL DEFAULT 0,
  refund_count BIGINT NOT NULL DEFAULT 0,
  refund_amount DECIMAL(20,6) NOT NULL DEFAULT 0,
  split_execution_count BIGINT NOT NULL DEFAULT 0,
  split_volume DECIMAL(20,6) NOT NULL DEFAULT 0,
  recurring_charge_count BIGINT NOT NULL DEFAULT 0,
  recurring_success_count BIGINT NOT NULL DEFAULT 0,
  recurring_failed_count BIGINT NOT NULL DEFAULT 0,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_merchant_analytics_daily (merchant_id, metric_date),
  KEY idx_merchant_analytics_date (metric_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS merchant_provider_analytics (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  merchant_id BIGINT NOT NULL,
  metric_date DATE NOT NULL,
  channel_code VARCHAR(64) NOT NULL,
  operation VARCHAR(16) NOT NULL,
  routed_count BIGINT NOT NULL DEFAULT 0,
  successful_count BIGINT NOT NULL DEFAULT 0,
  failed_count BIGINT NOT NULL DEFAULT 0,
  average_latency_ms BIGINT NOT NULL DEFAULT 0,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_merchant_provider_analytics (merchant_id, metric_date, channel_code, operation),
  KEY idx_merchant_provider_date (merchant_id, metric_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS merchant_analytics_recommendations (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  merchant_id BIGINT NOT NULL,
  recommendation_code VARCHAR(64) NOT NULL,
  subject_reference VARCHAR(120) NOT NULL,
  severity VARCHAR(16) NOT NULL DEFAULT 'INFO',
  title VARCHAR(200) NOT NULL,
  detail VARCHAR(1200) NOT NULL,
  evidence_json JSON NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'OPEN',
  generated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  acknowledged_by VARCHAR(160) NULL,
  acknowledged_at TIMESTAMP NULL,
  UNIQUE KEY uk_merchant_recommendation_open (merchant_id, recommendation_code, subject_reference, status),
  KEY idx_merchant_recommendation_status (merchant_id, status, generated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;