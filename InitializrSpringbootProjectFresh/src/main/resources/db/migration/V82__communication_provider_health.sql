-- CPay Communications Gateway: provider health + circuit state (Track A P6, guide Step 19).
-- Additive. Health is observed from real dispatch outcomes by CommunicationProviderHealthService;
-- the router hard-filters on it before any scoring, so a degraded provider is excluded outright.

CREATE TABLE IF NOT EXISTS communication_provider_health (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  provider_code VARCHAR(50) NOT NULL,
  channel VARCHAR(20) NOT NULL,
  state VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN',
  consecutive_failures INT NOT NULL DEFAULT 0,
  circuit_open_until DATETIME NULL,
  last_success_at DATETIME NULL,
  last_failure_at DATETIME NULL,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_comm_provider_health (provider_code, channel)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
