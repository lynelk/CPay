-- Communication ISO/security controls (track B6).
-- ISO domain mapping: communication/configuration; integration/credential.

-- Provider credentials moved out of the settings table into a dedicated,
-- encrypted-at-rest store (ISO/IEC 27001 A.8.24 — protection of secrets).
-- Values are stored as encrypted blobs; the application decrypts with the
-- same key material used for merchant channel credentials (CPAY_KEY_ENCRYPTION_KEY).
CREATE TABLE IF NOT EXISTS communication_provider_credentials (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  provider_code VARCHAR(40) NOT NULL,
  credential_key VARCHAR(80) NOT NULL,
  credential_value_encrypted TEXT NOT NULL,
  updated_by VARCHAR(120) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_cpc_cred (provider_code, credential_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Per-provider outbound rate limits and timeouts so a misconfigured campaign
-- or a provider outage cannot hammer the gateway or the provider (rate limiting
-- control, ISO/IEC 27001 A.8.6).
CREATE TABLE IF NOT EXISTS communication_provider_policies (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  provider_code VARCHAR(40) NOT NULL,
  max_per_minute INT NOT NULL DEFAULT 60,
  max_per_hour INT NOT NULL DEFAULT 1000,
  connect_timeout_ms INT NOT NULL DEFAULT 10000,
  read_timeout_ms INT NOT NULL DEFAULT 30000,
  rate_limit_flag CHAR(1) NOT NULL DEFAULT 'Y',
  enabled_flag CHAR(1) NOT NULL DEFAULT 'Y',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_cpp_provider (provider_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Webhook callback verification for delivery status callbacks from providers
-- (WhatsApp webhooks, Yo! SMS DLR, etc.) — nonce + signature envelope so a
-- spoofed callback cannot mutate delivery state (ISO/IEC 27001 A.8.26).
CREATE TABLE IF NOT EXISTS communication_callback_nonces (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  provider_code VARCHAR(40) NOT NULL,
  nonce VARCHAR(64) NOT NULL,
  signature VARCHAR(256) NULL,
  expires_at DATETIME NOT NULL,
  used_flag CHAR(1) NOT NULL DEFAULT 'N',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_ccn_nonce (nonce)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Seed default provider policies (SMS providers + WhatsApp). Additive-safe.
INSERT INTO communication_provider_policies (provider_code, max_per_minute, max_per_hour)
VALUES
  ('LEGACY_SETTINGS', 60, 1000),
  ('YO_SMS', 60, 1000),
  ('AFRICAS_TALKING', 120, 6000),
  ('TWILIO_SMS', 120, 6000),
  ('WABA_CLOUD_API', 80, 4000),
  ('TWILIO_WA', 120, 6000),
  ('360DIALOG', 80, 4000)
ON DUPLICATE KEY UPDATE max_per_minute = VALUES(max_per_minute);
