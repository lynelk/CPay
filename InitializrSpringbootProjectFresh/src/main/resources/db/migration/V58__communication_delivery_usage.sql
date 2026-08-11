-- Per-channel delivery tracking + usage metering into billing (tracks B5a/B5b).
-- ISO domain mapping: communication/delivery; integration with billing usage events.

-- Per-message delivery ledger mirroring the merchant_sms status shape, but
-- channel-agnostic (SMS/EMAIL/WHATSAPP/USSD).
CREATE TABLE IF NOT EXISTS communication_message_deliveries (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  merchant_id BIGINT NOT NULL,
  channel VARCHAR(20) NOT NULL,
  provider_code VARCHAR(40) NULL,
  reference_type VARCHAR(30) NULL,
  reference_id BIGINT NULL,
  recipient VARCHAR(128) NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  trace VARCHAR(500) NULL,
  gw_response TEXT NULL,
  charged_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
  billed_flag CHAR(1) NOT NULL DEFAULT 'N',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_cmd_merchant_status (merchant_id, status, created_at),
  KEY idx_cmd_channel_status (channel, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Processing checkpoint for the per-service usage relay (B5b). Mirrors the
-- billing outbox idea: the delivery worker records the outcome here and a
-- ShedLock-guarded relay converts SENT rows into billing usage events
-- (service code per channel) idempotently.
CREATE TABLE IF NOT EXISTS communication_usage_watermark (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  channel VARCHAR(20) NOT NULL,
  last_delivery_id BIGINT NOT NULL DEFAULT 0,
  processed_flag CHAR(1) NOT NULL DEFAULT 'N',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_cuw_channel (channel)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO communication_usage_watermark (channel, last_delivery_id)
VALUES ('SMS', 0), ('EMAIL', 0), ('WHATSAPP', 0), ('USSD', 0);
