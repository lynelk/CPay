-- ChargeNow native provider profile: seed connector operations.
--
-- For every existing active connector, seed the full set of ChargeNow/Bajie OEM operations
-- with the production endpoint paths confirmed by the supplied API specification. Each merchant
-- row is seeded independently so connector operations remain tenant-scoped.

-- RELEASE_ASSET: POST /rent/order/create ?deviceId=&callbackURL=
INSERT INTO `vending_connector_operations`
  (`merchant_id`, `connector_code`, `command_type`, `http_method`, `command_path`,
   `request_template`, `response_success_field`, `response_success_value`,
   `response_reference_field`, `response_message_field`, `completion_mode`, `active_flag`)
SELECT c.`merchant_id`, c.`connector_code`, 'RELEASE_ASSET', 'POST',
       '/rent/order/create?deviceId={{externalDeviceId}}&callbackURL={{callbackURL}}',
       NULL, 'code', '0', 'data.tradeNo', 'msg', 'CALLBACK', 'YES'
FROM `vending_connector_configs` c
WHERE c.`connector_code` = 'CHARGENOW' AND c.`active_flag` = 'YES'
ON DUPLICATE KEY UPDATE
  `http_method`=VALUES(`http_method`),
  `command_path`=VALUES(`command_path`),
  `response_success_field`=VALUES(`response_success_field`),
  `response_success_value`=VALUES(`response_success_value`),
  `response_reference_field`=VALUES(`response_reference_field`),
  `response_message_field`=VALUES(`response_message_field`);

-- QUERY_RENTAL: POST /rent/order/query ?tradeNo=
INSERT INTO `vending_connector_operations`
  (`merchant_id`, `connector_code`, `command_type`, `http_method`, `command_path`,
   `request_template`, `response_success_field`, `response_success_value`,
   `response_reference_field`, `response_message_field`, `completion_mode`, `active_flag`)
SELECT c.`merchant_id`, c.`connector_code`, 'QUERY_RENTAL', 'POST',
       '/rent/order/query?tradeNo={{providerReference}}',
       NULL, 'code', '0', 'data.tradeNo', 'msg', 'CALLBACK', 'YES'
FROM `vending_connector_configs` c
WHERE c.`connector_code` = 'CHARGENOW' AND c.`active_flag` = 'YES'
ON DUPLICATE KEY UPDATE
  `http_method`=VALUES(`http_method`),
  `command_path`=VALUES(`command_path`),
  `response_success_field`=VALUES(`response_success_field`),
  `response_success_value`=VALUES(`response_success_value`),
  `response_reference_field`=VALUES(`response_reference_field`),
  `response_message_field`=VALUES(`response_message_field`);

-- GET_RENTAL_DETAIL: GET /rent/order/detail ?tradeNo=
INSERT INTO `vending_connector_operations`
  (`merchant_id`, `connector_code`, `command_type`, `http_method`, `command_path`,
   `request_template`, `response_success_field`, `response_success_value`,
   `response_reference_field`, `response_message_field`, `completion_mode`, `active_flag`)
SELECT c.`merchant_id`, c.`connector_code`, 'GET_RENTAL_DETAIL', 'GET',
       '/rent/order/detail?tradeNo={{providerReference}}',
       NULL, 'code', '0', 'data.tradeNo', 'msg', 'IMMEDIATE', 'YES'
FROM `vending_connector_configs` c
WHERE c.`connector_code` = 'CHARGENOW' AND c.`active_flag` = 'YES'
ON DUPLICATE KEY UPDATE
  `http_method`=VALUES(`http_method`),
  `command_path`=VALUES(`command_path`),
  `response_success_field`=VALUES(`response_success_field`),
  `response_success_value`=VALUES(`response_success_value`),
  `response_reference_field`=VALUES(`response_reference_field`),
  `response_message_field`=VALUES(`response_message_field`);

-- CLOSE_RENTAL: POST /rent/order/close ?tradeNo=
INSERT INTO `vending_connector_operations`
  (`merchant_id`, `connector_code`, `command_type`, `http_method`, `command_path`,
   `request_template`, `response_success_field`, `response_success_value`,
   `response_reference_field`, `response_message_field`, `completion_mode`, `active_flag`)
SELECT c.`merchant_id`, c.`connector_code`, 'CLOSE_RENTAL', 'POST',
       '/rent/order/close?tradeNo={{providerReference}}',
       NULL, 'code', '0', 'data.tradeNo', 'msg', 'IMMEDIATE', 'YES'
FROM `vending_connector_configs` c
WHERE c.`connector_code` = 'CHARGENOW' AND c.`active_flag` = 'YES'
ON DUPLICATE KEY UPDATE
  `http_method`=VALUES(`http_method`),
  `command_path`=VALUES(`command_path`),
  `response_success_field`=VALUES(`response_success_field`),
  `response_success_value`=VALUES(`response_success_value`),
  `response_reference_field`=VALUES(`response_reference_field`),
  `response_message_field`=VALUES(`response_message_field`);

-- QUERY_DEVICE: GET /rent/cabinet/query ?deviceId=
INSERT INTO `vending_connector_operations`
  (`merchant_id`, `connector_code`, `command_type`, `http_method`, `command_path`,
   `request_template`, `response_success_field`, `response_success_value`,
   `response_reference_field`, `response_message_field`, `completion_mode`, `active_flag`)
SELECT c.`merchant_id`, c.`connector_code`, 'QUERY_DEVICE', 'GET',
       '/rent/cabinet/query?deviceId={{externalDeviceId}}',
       NULL, 'code', '0', 'data.cabinet.id', 'msg', 'IMMEDIATE', 'YES'
FROM `vending_connector_configs` c
WHERE c.`connector_code` = 'CHARGENOW' AND c.`active_flag` = 'YES'
ON DUPLICATE KEY UPDATE
  `http_method`=VALUES(`http_method`),
  `command_path`=VALUES(`command_path`),
  `response_success_field`=VALUES(`response_success_field`),
  `response_success_value`=VALUES(`response_success_value`),
  `response_reference_field`=VALUES(`response_reference_field`),
  `response_message_field`=VALUES(`response_message_field`);
