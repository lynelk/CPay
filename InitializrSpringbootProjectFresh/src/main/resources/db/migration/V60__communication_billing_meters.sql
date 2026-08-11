-- Communication usage metering into the billing engine (tracks B5a/B5b).
-- ISO domain mapping: communication/delivery + communication/usage -> billing/usage.
--
-- V39 seeded the service/meter catalog with PAYMENT/SMS/WEBHOOK/INVOICE. The communication
-- usage relay (communication/usage/CommunicationUsageRelay) converts SENT
-- communication_message_deliveries (V53) rows into billing_usage_events keyed by
-- `comm:<channel>:<delivery_id>`. This migration adds the EMAIL / WHATSAPP / USSD services and
-- their COUNT meters so those usage events resolve to a real, effective-dated meter the way the
-- V39 meters do. Additive only - existing SMS metering is untouched.

INSERT IGNORE INTO `billing_service_catalog` (`service_code`, `service_name`, `service_status`) VALUES
  ('EMAIL', 'Email delivery', 'ACTIVE'),
  ('WHATSAPP', 'WhatsApp delivery', 'ACTIVE'),
  ('USSD', 'USSD sessions', 'ACTIVE');

INSERT IGNORE INTO `billing_meters` (`service_code`, `meter_code`, `meter_name`, `aggregation_type`, `meter_status`) VALUES
  ('EMAIL', 'email_delivered_count', 'Emails delivered', 'COUNT', 'ACTIVE'),
  ('WHATSAPP', 'whatsapp_message_count', 'WhatsApp messages sent', 'COUNT', 'ACTIVE'),
  ('USSD', 'ussd_session_count', 'USSD sessions', 'COUNT', 'ACTIVE');

INSERT INTO `billing_meter_versions` (`meter_id`, `version_no`, `dimension_keys`, `effective_from`)
SELECT `id`, 1, JSON_ARRAY('channel', 'provider_code'), CURRENT_TIMESTAMP
FROM `billing_meters`
WHERE `id` NOT IN (SELECT `meter_id` FROM `billing_meter_versions`);
