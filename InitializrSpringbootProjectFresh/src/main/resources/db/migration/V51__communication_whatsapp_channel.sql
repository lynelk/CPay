-- First production-capable WhatsApp channel registration.
-- No platform-default route is inserted: operators must explicitly opt a merchant or platform
-- into WhatsApp after configuring Twilio credentials. This avoids accidental sends.
INSERT INTO `communication_providers`
  (`provider_code`, `provider_name`, `channel`, `adapter_class`, `credentials_ref`, `enabled_flag`)
VALUES
  ('TWILIO_WHATSAPP', 'Twilio WhatsApp', 'WHATSAPP',
   'net.citotech.cito.communication.whatsapp.TwilioWhatsAppGatewayAdapter',
   'twilio_account_sid/twilio_auth_token/twilio_whatsapp_from_number', 'YES')
ON DUPLICATE KEY UPDATE
  `provider_name` = VALUES(`provider_name`),
  `adapter_class` = VALUES(`adapter_class`),
  `credentials_ref` = VALUES(`credentials_ref`);
