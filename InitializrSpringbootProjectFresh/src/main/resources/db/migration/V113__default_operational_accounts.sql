-- Create the non-interactive accounts required by legacy/core CPay posting paths. They start at
-- zero, have no users or API keys, and therefore cannot be used to sign in or call merchant APIs.
-- Provider wallet balances remain in provider_treasury_accounts; these merchant-shaped accounts
-- exist only because the established posting engine resolves the four account settings below to
-- Merchant records.

INSERT INTO merchants
    (name, status, account_number, created_by, account_type, allowed_apis, short_name)
VALUES
    ('Cito Float Stock (System)', 'ACTIVE', 'CITO-FLOAT-STOCK',
     'SYSTEM_MIGRATION_V113', 'business', '', 'Float Stock'),
    ('Cito Gateway Revenue (System)', 'ACTIVE', 'CITO-GATEWAY-REVENUE',
     'SYSTEM_MIGRATION_V113', 'business', '', 'Gateway Revenue'),
    ('Cito Gateway Suspense (System)', 'ACTIVE', 'CITO-GATEWAY-SUSPENSE',
     'SYSTEM_MIGRATION_V113', 'business', '', 'Gateway Suspense'),
    ('Cito SMS Revenue (System)', 'ACTIVE', 'CITO-SMS-REVENUE',
     'SYSTEM_MIGRATION_V113', 'business', '', 'SMS Revenue')
ON DUPLICATE KEY UPDATE account_number = VALUES(account_number);

-- The legacy balance reader derives every channel balance from the latest merchant_statement row.
-- Seed one zero row only when a system account has no history. No prefunding is implied.
INSERT INTO merchant_statement
    (merchant_id, gateway_id, description, amount, mtnmm_balance, tx_type, airtelmm_balance,
     narrative, recorded_by, sms_balance, safaricom_balance)
SELECT m.id, 'SYSTEM_BOOTSTRAP', 'Zero-balance operational account bootstrap', 0.0000, 0.0000,
       'CR', 0.0000, 'No funds introduced by migration', 'SYSTEM_MIGRATION_V113', 0.0000, 0.0000
FROM merchants m
WHERE m.account_number IN
      ('CITO-FLOAT-STOCK', 'CITO-GATEWAY-REVENUE', 'CITO-GATEWAY-SUSPENSE',
       'CITO-SMS-REVENUE')
  AND NOT EXISTS (
      SELECT 1 FROM merchant_statement ms WHERE ms.merchant_id = m.id
  );

-- Seed the normalized read model at zero as well. Airtel legacy and Open API intentionally have
-- separate normalized rows even though the compatibility statement keeps one Airtel column.
INSERT INTO merchant_channel_balances
    (merchant_id, channel_code, gateway_id, currency, available_balance, ledger_balance,
     pending_balance)
SELECT m.id, channels.channel_code, channels.gateway_id, channels.currency, 0.0000, 0.0000, 0.0000
FROM merchants m
CROSS JOIN (
    SELECT 'mtn_momo' AS channel_code, 'MTNMoMoPaymentGateway' AS gateway_id, 'UGX' AS currency
    UNION ALL SELECT 'airtel_money', 'AirtelMoneyPaymentGateway', 'UGX'
    UNION ALL SELECT 'airtel_open_api', 'AirtelMoneyOpenApiPaymentGateway', 'UGX'
    UNION ALL SELECT 'safaricom_mpesa', 'SafariComPaymentGateway', 'KES'
    UNION ALL SELECT 'sms', 'SmsGateway', 'UGX'
) channels
WHERE m.account_number IN
      ('CITO-FLOAT-STOCK', 'CITO-GATEWAY-REVENUE', 'CITO-GATEWAY-SUSPENSE',
       'CITO-SMS-REVENUE')
ON DUPLICATE KEY UPDATE gateway_id = VALUES(gateway_id);

-- V38's billing backfill ran before these internal merchants existed. Preserve the one-tenant,
-- one-customer, one-account invariant without making internal operational accounts billable.
INSERT INTO billing_tenants (merchant_id, tenant_type, tenant_status)
SELECT m.id, 'CPAY_INTERNAL', 'INTERNAL'
FROM merchants m
WHERE m.account_number IN
      ('CITO-FLOAT-STOCK', 'CITO-GATEWAY-REVENUE', 'CITO-GATEWAY-SUSPENSE',
       'CITO-SMS-REVENUE')
  AND NOT EXISTS (
      SELECT 1 FROM billing_tenants bt WHERE bt.merchant_id = m.id
  );

INSERT INTO billing_customers
    (billing_tenant_id, customer_type, display_name, customer_status)
SELECT bt.id, 'INTERNAL_ACCOUNT', m.name, 'INTERNAL'
FROM billing_tenants bt
JOIN merchants m ON m.id = bt.merchant_id
WHERE m.account_number IN
      ('CITO-FLOAT-STOCK', 'CITO-GATEWAY-REVENUE', 'CITO-GATEWAY-SUSPENSE',
       'CITO-SMS-REVENUE')
  AND NOT EXISTS (
      SELECT 1 FROM billing_customers bc WHERE bc.billing_tenant_id = bt.id
  );

INSERT INTO billing_accounts
    (billing_tenant_id, billing_customer_id, currency, account_status)
SELECT bc.billing_tenant_id, bc.id, 'UGX', 'INTERNAL'
FROM billing_customers bc
JOIN billing_tenants bt ON bt.id = bc.billing_tenant_id
JOIN merchants m ON m.id = bt.merchant_id
WHERE m.account_number IN
      ('CITO-FLOAT-STOCK', 'CITO-GATEWAY-REVENUE', 'CITO-GATEWAY-SUSPENSE',
       'CITO-SMS-REVENUE')
  AND NOT EXISTS (
      SELECT 1 FROM billing_accounts ba WHERE ba.billing_customer_id = bc.id
  );

-- Preserve any operator-supplied account choice. Only an absent or blank value receives the safe
-- default. Re-running settings synchronization therefore cannot redirect an established account.
INSERT INTO settings (label, name, setting_value, description, setting_group)
VALUES
    ('Float Stock Account', 'float_stock_account', 'CITO-FLOAT-STOCK',
     'Non-interactive system merchant used for shared gateway float postings.', 'Accounts'),
    ('Revenue Account', 'revenue_account', 'CITO-GATEWAY-REVENUE',
     'Non-interactive system merchant used for gateway fee revenue postings.', 'Accounts'),
    ('Suspense Account', 'suspense_account', 'CITO-GATEWAY-SUSPENSE',
     'Non-interactive system merchant used for pending and ambiguous gateway postings.', 'Accounts'),
    ('SMS Revenue Account', 'sms_revenue_account', 'CITO-SMS-REVENUE',
     'Non-interactive system merchant used for SMS revenue postings.', 'Accounts')
ON DUPLICATE KEY UPDATE
    setting_value = IF(setting_value IS NULL OR TRIM(setting_value) = '',
                       VALUES(setting_value), setting_value);
