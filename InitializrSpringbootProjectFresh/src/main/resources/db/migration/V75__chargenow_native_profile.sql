-- Phase 3: Native ChargeNow/Bajie provider profile.
--
-- Adds the structural columns required for bodyless operations and native
-- provider profiles, then seeds all five ChargeNow operations with their
-- correct endpoint mappings per the published spec (§9):
--
--   RELEASE_ASSET       POST  /rent/order/create
--   QUERY_RENTAL        POST  /rent/order/query
--   GET_RENTAL_DETAIL   GET   /rent/order/detail
--   CLOSE_RENTAL        POST  /rent/order/close
--   QUERY_DEVICE        GET   /rent/cabinet/query
--
-- Everything is additive and idempotent so it is safe against any existing
-- connector state. Existing sandbox connector configs continue to work as-is.

-- ---------------------------------------------------------------------------
-- 1. Structural columns for bodyless operations and native profiles.
-- ---------------------------------------------------------------------------

ALTER TABLE `vending_connector_operations`
  ADD COLUMN `request_body_required` TINYINT(1) NOT NULL DEFAULT 1 AFTER `request_template`;

ALTER TABLE `vending_connector_configs`
  ADD COLUMN `callback_verification_mode` VARCHAR(40) NOT NULL DEFAULT 'HMAC_SHA256_TS_NONCE_BODY' AFTER `callback_command_reference_field`,
  ADD COLUMN `vendor_username` VARCHAR(200) NULL AFTER `callback_verification_mode`,
  ADD COLUMN `vendor_password` TEXT NULL AFTER `vendor_username`;

-- ---------------------------------------------------------------------------
-- 2. Seed the native ChargeNow operations (merchant_id=0 is a sentinel that
--    gets replaced by real merchant configs via VendingConnectorConfigurationService).
--    We seed a template row for connector_code='CHARGENOW' so the native profile
--    is documented in the schema and application-level provisioning picks it up.
-- ---------------------------------------------------------------------------

-- ChargeNow ops use Basic auth (username:password in Base64) over HTTPS. All
-- five operations are bodyless per the published Bajie contract (query params
-- only). We insert with command_path as a template that the adapter renders
-- via UriComponentsBuilder with {{externalDeviceId}} and {{providerReference}}
-- placeholders.

INSERT IGNORE INTO `vending_connector_operations`
  (`merchant_id`, `connector_code`, `command_type`, `http_method`,
   `command_path`, `request_template`, `request_body_required`,
   `response_success_field`, `response_success_value`,
   `response_reference_field`, `response_message_field`,
   `completion_mode`, `active_flag`)
VALUES
  -- RELEASE_ASSET: POST /rent/order/create?deviceId={}&callbackURL={}
  (0, 'CHARGENOW', 'RELEASE_ASSET', 'POST',
   '/rent/order/create', NULL, 0,
   'code', '0',
   'data.tradeNo', 'msg',
   'CALLBACK', 'YES'),

  -- QUERY_RENTAL: POST /rent/order/query?tradeNo={}
  (0, 'CHARGENOW', 'QUERY_RENTAL', 'POST',
   '/rent/order/query', NULL, 0,
   'code', '0',
   'data.tradeNo', 'msg',
   'IMMEDIATE', 'YES'),

  -- GET_RENTAL_DETAIL: GET /rent/order/detail?tradeNo={}
  (0, 'CHARGENOW', 'GET_RENTAL_DETAIL', 'GET',
   '/rent/order/detail', NULL, 0,
   'code', '0',
   'data.tradeNo', 'msg',
   'IMMEDIATE', 'YES'),

  -- CLOSE_RENTAL: POST /rent/order/close?tradeNo={}
  (0, 'CHARGENOW', 'CLOSE_RENTAL', 'POST',
   '/rent/order/close', NULL, 0,
   'code', '0',
   'data.tradeNo', 'msg',
   'IMMEDIATE', 'YES'),

  -- QUERY_DEVICE: GET /rent/cabinet/query?deviceId={}
  (0, 'CHARGENOW', 'QUERY_DEVICE', 'GET',
   '/rent/cabinet/query', NULL, 0,
   'code', '0',
   'data.cabinetId', 'msg',
   'IMMEDIATE', 'YES');
