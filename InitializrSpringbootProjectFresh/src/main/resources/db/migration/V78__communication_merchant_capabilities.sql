-- CPay Communications Gateway: merchant capability activation + quotas.
-- Additive. Merchants activate CPay capabilities; provider routing stays internal.

CREATE TABLE IF NOT EXISTS communication_merchant_capabilities (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  merchant_id BIGINT NOT NULL,
  channel VARCHAR(20) NOT NULL,
  capability VARCHAR(50) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'REQUESTED',
  routing_mode VARCHAR(20) NOT NULL DEFAULT 'AUTOMATIC',
  preferred_provider_code VARCHAR(50) NULL,
  sender_identity VARCHAR(160) NULL,
  pricing_plan_code VARCHAR(80) NULL,
  daily_limit INT NULL,
  monthly_limit INT NULL,
  activated_at DATETIME NULL,
  suspended_at DATETIME NULL,
  created_by VARCHAR(120) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_comm_merchant_cap (merchant_id, channel, capability),
  KEY idx_comm_merchant_cap_status (merchant_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
