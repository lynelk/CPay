-- Airtel Uganda OpenAPI v2 route configuration.
-- Secrets remain operator-managed; this migration only seeds non-secret route metadata.

INSERT INTO `settings` (`name`, `label`, `setting_value`, `setting_group`, `description`) VALUES
('gw_airtelmoney_use_open_api', 'Airtel Use Open API', 'yes', 'Airtel', 'Use yes to route Airtel through the Open API implementation.'),
('gw_airtelmoney_api_url', 'Airtel API URL', 'https://openapiuat.airtel.africa', 'Airtel', 'Airtel Money API base URL.'),
('gw_airtelmoney_token_url', 'Airtel Token URL', '/auth/oauth2/token', 'Airtel', 'OAuth token path or absolute URL for Airtel Open API.'),
('gw_airtelmoney_collections_url', 'Airtel Collections URL', '/merchant/v2/payments/', 'Airtel', 'Collection request path or absolute URL for Airtel Open API v2.'),
('gw_airtelmoney_disbursements_url', 'Airtel Payouts URL', '/standard/v2/disbursements/', 'Airtel', 'Payout request path or absolute URL for Airtel Open API v2.'),
('gw_airtelmoney_balance_url', 'Airtel Balance URL', '/standard/v2/users/balance', 'Airtel', 'Balance path or absolute URL for Airtel Open API.'),
('gw_airtelmoney_collections_status_url', 'Airtel Collections Status URL', '/standard/v2/payments/{reference}/', 'Airtel', 'Collection status path or absolute URL; use {reference} for the transaction ID.'),
('gw_airtelmoney_disbursements_status_url', 'Airtel Payouts Status URL', '/standard/v2/disbursements/{reference}/', 'Airtel', 'Payout status path or absolute URL; use {reference} for the transaction ID.')
ON DUPLICATE KEY UPDATE
  `label` = VALUES(`label`),
  `setting_group` = VALUES(`setting_group`),
  `description` = VALUES(`description`),
  `setting_value` = CASE
    WHEN VALUES(`name`) = 'gw_airtelmoney_use_open_api' THEN VALUES(`setting_value`)
    WHEN `settings`.`setting_value` IS NULL OR TRIM(`settings`.`setting_value`) = '' THEN VALUES(`setting_value`)
    ELSE `settings`.`setting_value`
  END;

INSERT INTO `merchant_settings` (`merchant_id`, `name`, `label`, `setting_value`, `setting_group`, `description`)
SELECT
  m.`id`,
  seed.`name`,
  seed.`label`,
  seed.`setting_value`,
  seed.`setting_group`,
  seed.`description`
FROM `merchants` m
CROSS JOIN (
  SELECT 'gw_airtelmoney_use_open_api' AS `name`, 'Airtel Use Open API' AS `label`, 'yes' AS `setting_value`, 'Airtel' AS `setting_group`, 'Use yes to route Airtel through the Open API implementation.' AS `description`
  UNION ALL SELECT 'gw_airtelmoney_api_url', 'Airtel API URL', 'https://openapiuat.airtel.africa', 'Airtel', 'Airtel Money API base URL for this merchant.'
  UNION ALL SELECT 'gw_airtelmoney_token_url', 'Airtel Token URL', '/auth/oauth2/token', 'Airtel', 'OAuth token path or absolute URL for Airtel Open API for this merchant.'
  UNION ALL SELECT 'gw_airtelmoney_collections_url', 'Airtel Collections URL', '/merchant/v2/payments/', 'Airtel', 'Collection request path or absolute URL for Airtel Open API v2 for this merchant.'
  UNION ALL SELECT 'gw_airtelmoney_disbursements_url', 'Airtel Payouts URL', '/standard/v2/disbursements/', 'Airtel', 'Payout request path or absolute URL for Airtel Open API v2 for this merchant.'
  UNION ALL SELECT 'gw_airtelmoney_balance_url', 'Airtel Balance URL', '/standard/v2/users/balance', 'Airtel', 'Balance path or absolute URL for Airtel Open API for this merchant.'
  UNION ALL SELECT 'gw_airtelmoney_collections_status_url', 'Airtel Collections Status URL', '/standard/v2/payments/{reference}/', 'Airtel', 'Collection status path or absolute URL; use {reference} for the transaction ID.'
  UNION ALL SELECT 'gw_airtelmoney_disbursements_status_url', 'Airtel Payouts Status URL', '/standard/v2/disbursements/{reference}/', 'Airtel', 'Payout status path or absolute URL; use {reference} for the transaction ID.'
) seed
WHERE TRUE
ON DUPLICATE KEY UPDATE
  `label` = VALUES(`label`),
  `setting_group` = VALUES(`setting_group`),
  `description` = VALUES(`description`),
  `setting_value` = CASE
    WHEN VALUES(`name`) = 'gw_airtelmoney_use_open_api' THEN VALUES(`setting_value`)
    WHEN `merchant_settings`.`setting_value` IS NULL OR TRIM(`merchant_settings`.`setting_value`) = '' THEN VALUES(`setting_value`)
    ELSE `merchant_settings`.`setting_value`
  END;
