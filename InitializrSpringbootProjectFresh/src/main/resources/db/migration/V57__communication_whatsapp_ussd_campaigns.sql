-- WhatsApp + USSD + campaigns (track B4).
-- ISO domain mapping: communication/{whatsapp,ussd,campaign,message}.

-- Provider channel bindings for the routing UI. V50's communication_providers
-- is channel-free; these rows scope provider codes per channel for the B4
-- surface (WHATSAPP/USSD). Additive — SMS rows from V50 are untouched.
CREATE TABLE IF NOT EXISTS communication_provider_channels (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  provider_code VARCHAR(40) NOT NULL,
  channel VARCHAR(20) NOT NULL,
  display_name VARCHAR(120) NOT NULL,
  enabled_flag CHAR(1) NOT NULL DEFAULT 'N',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_cpc_provider_channel (provider_code, channel)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Seeded WhatsApp/USSD provider bindings (codes used by B4 adapters).
INSERT INTO communication_provider_channels (provider_code, channel, display_name)
VALUES
  ('WABA_CLOUD_API', 'WHATSAPP', 'Meta WABA Cloud API'),
  ('TWILIO_WA', 'WHATSAPP', 'Twilio WhatsApp'),
  ('360DIALOG', 'WHATSAPP', '360dialog'),
  ('USSD_AFRICASTALKING', 'USSD', 'Africa''s Talking USSD'),
  ('USSD_YO_SMS', 'USSD', 'Yo! SMS USSD')
ON DUPLICATE KEY UPDATE display_name = VALUES(display_name);

CREATE TABLE IF NOT EXISTS communication_whatsapp_messages (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  merchant_id BIGINT NOT NULL,
  provider_code VARCHAR(40) NOT NULL,
  wa_phone VARCHAR(32) NOT NULL,
  template_name VARCHAR(120) NULL,
  message_body TEXT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  provider_message_id VARCHAR(120) NULL,
  trace VARCHAR(500) NULL,
  gw_response TEXT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_wa_merchant_status (merchant_id, status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- USSD menu sessions (short-lived by design).
CREATE TABLE IF NOT EXISTS communication_ussd_sessions (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  phone_number VARCHAR(32) NOT NULL,
  session_id VARCHAR(120) NOT NULL,
  current_menu VARCHAR(80) NOT NULL DEFAULT 'MAIN',
  state_json TEXT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_ussd_session (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Campaign batches (one or many messages/recipients under a single intent).
CREATE TABLE IF NOT EXISTS communication_campaigns (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  merchant_id BIGINT NOT NULL,
  name VARCHAR(120) NOT NULL,
  channel VARCHAR(20) NOT NULL,
  template_key VARCHAR(120) NULL,
  audience_sql VARCHAR(500) NULL,
  total_recipients INT NOT NULL DEFAULT 0,
  processed_recipients INT NOT NULL DEFAULT 0,
  status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
  scheduled_at DATETIME NULL,
  started_at DATETIME NULL,
  completed_at DATETIME NULL,
  created_by VARCHAR(120) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_ccampaign_merchant (merchant_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS communication_campaign_items (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  campaign_id BIGINT NOT NULL,
  recipient VARCHAR(64) NOT NULL,
  message_body TEXT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  trace VARCHAR(500) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_citem_campaign_status (campaign_id, status),
  CONSTRAINT fk_citem_campaign FOREIGN KEY (campaign_id) REFERENCES communication_campaigns (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
