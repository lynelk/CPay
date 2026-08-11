-- Communication core (track B3): templates, per-merchant channel preferences, consent log.
-- ISO domain mapping: communication/{template,preference,message}.

CREATE TABLE IF NOT EXISTS communication_message_templates (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  template_key VARCHAR(120) NOT NULL,
  channel VARCHAR(20) NOT NULL,
  subject_template VARCHAR(500) NULL,
  body_template TEXT NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_cmt_template_channel (template_key, channel)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS communication_message_preferences (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  merchant_id BIGINT NOT NULL,
  channel VARCHAR(20) NOT NULL,
  enabled_flag CHAR(1) NOT NULL DEFAULT 'Y',
  quiet_hours_start VARCHAR(5) NULL,
  quiet_hours_end VARCHAR(5) NULL,
  updated_by VARCHAR(120) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_cmp_merchant_channel (merchant_id, channel)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Consent audit trail (ISO/IEC 27001 A.7.1.2 signal capture).
CREATE TABLE IF NOT EXISTS communication_consent_log (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  merchant_id BIGINT NOT NULL,
  channel VARCHAR(20) NOT NULL,
  consent_type VARCHAR(40) NOT NULL,
  source VARCHAR(40) NOT NULL,
  changed_by VARCHAR(120) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_cconsent_merchant_channel (merchant_id, channel, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Seed a small set of canonical templates. Existing deployments that already
-- store equivalent bodies as settings are unaffected; templates are additive.
INSERT INTO communication_message_templates (template_key, channel, subject_template, body_template)
VALUES
  ('merchant_sms_payment_receipt', 'SMS', NULL, 'Payment of {amount} received. Ref {reference}.'),
  ('merchant_sms_payout_notice', 'SMS', NULL, 'Payout of {amount} to {recipient} completed. Ref {reference}.'),
  ('merchant_email_verification', 'EMAIL', 'Verify your CPay merchant email', 'Click {link} to verify your email.'),
  ('merchant_email_statement', 'EMAIL', 'CPay account statement {period}', 'Your statement for {period} is ready.'),
  ('merchant_whatsapp_receipt', 'WHATSAPP', NULL, 'Payment of {amount} received. Ref {reference}.')
ON DUPLICATE KEY UPDATE body_template = VALUES(body_template);
