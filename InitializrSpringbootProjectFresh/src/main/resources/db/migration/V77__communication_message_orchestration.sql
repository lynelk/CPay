-- CPay Communications Gateway: logical communication + attempt linkage + durable outbox.
-- All additive; V50/V56-V60 remain untouched.

-- Parent logical communication: one merchant request = one row; delivery rows are attempts.
CREATE TABLE IF NOT EXISTS communication_messages (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  public_id VARCHAR(64) NOT NULL,
  merchant_id BIGINT NOT NULL,
  external_reference VARCHAR(128) NULL,
  idempotency_key VARCHAR(128) NULL,
  purpose VARCHAR(40) NOT NULL,
  recipient_type VARCHAR(20) NOT NULL DEFAULT 'PHONE',
  recipient VARCHAR(255) NOT NULL,
  requested_channels VARCHAR(255) NOT NULL,
  selected_channel VARCHAR(20) NULL,
  selected_provider_code VARCHAR(50) NULL,
  template_key VARCHAR(120) NULL,
  fallback_enabled CHAR(1) NOT NULL DEFAULT 'Y',
  status VARCHAR(30) NOT NULL DEFAULT 'RECEIVED',
  scheduled_at DATETIME NULL,
  expires_at DATETIME NULL,
  metadata_json JSON NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_comm_public_id (public_id),
  UNIQUE KEY uk_comm_merchant_idempotency (merchant_id, idempotency_key),
  KEY idx_comm_merchant_status (merchant_id, status, created_at),
  KEY idx_comm_external_reference (merchant_id, external_reference)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Extend the existing delivery log into per-attempt rows. Historical rows keep NULL
-- communication_id; a FK is deliberately deferred until all rows can be correlated.
ALTER TABLE communication_message_deliveries
  ADD COLUMN communication_id BIGINT NULL AFTER id,
  ADD COLUMN attempt_no INT NOT NULL DEFAULT 1 AFTER communication_id,
  ADD COLUMN provider_message_id VARCHAR(160) NULL AFTER provider_code,
  ADD COLUMN failure_code VARCHAR(80) NULL AFTER status,
  ADD COLUMN retryable_flag CHAR(1) NOT NULL DEFAULT 'N' AFTER failure_code,
  ADD COLUMN sent_at DATETIME NULL,
  ADD COLUMN delivered_at DATETIME NULL;

CREATE INDEX idx_cmd_communication
  ON communication_message_deliveries (communication_id, attempt_no);

CREATE INDEX idx_cmd_provider_message
  ON communication_message_deliveries (provider_code, provider_message_id);

-- Canonical provider capability catalog (V50/V57 overlap resolution).
CREATE TABLE IF NOT EXISTS communication_provider_capabilities (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  provider_code VARCHAR(50) NOT NULL,
  channel VARCHAR(20) NOT NULL,
  capability VARCHAR(50) NOT NULL,
  country_code VARCHAR(3) NULL,
  currency_code VARCHAR(3) NULL,
  supports_templates CHAR(1) NOT NULL DEFAULT 'N',
  supports_delivery_receipts CHAR(1) NOT NULL DEFAULT 'N',
  supports_inbound CHAR(1) NOT NULL DEFAULT 'N',
  supports_status_query CHAR(1) NOT NULL DEFAULT 'N',
  enabled_flag CHAR(1) NOT NULL DEFAULT 'N',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_comm_capability (provider_code, channel, capability, country_code),
  KEY idx_comm_capability_lookup (channel, country_code, enabled_flag)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Durable outbox: merchant API commits message + outbox row together, then a worker dispatches.
CREATE TABLE IF NOT EXISTS communication_outbox (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  communication_id BIGINT NOT NULL,
  event_type VARCHAR(40) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  priority VARCHAR(20) NOT NULL DEFAULT 'NORMAL',
  attempts INT NOT NULL DEFAULT 0,
  next_attempt_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  claimed_by VARCHAR(120) NULL,
  claimed_at DATETIME NULL,
  last_error_code VARCHAR(80) NULL,
  last_error_safe VARCHAR(500) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  completed_at DATETIME NULL,
  UNIQUE KEY uk_comm_outbox_message_event (communication_id, event_type),
  KEY idx_comm_outbox_due (status, priority, next_attempt_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
