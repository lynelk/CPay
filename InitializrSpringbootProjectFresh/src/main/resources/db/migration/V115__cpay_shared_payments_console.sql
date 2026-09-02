-- CPay Shared Payments is the safe default while a merchant has no approved provider-owned
-- credentials. Collection access is provisioned automatically for active merchants; payout
-- access stays maker-checker controlled and all balances start at zero.

INSERT INTO settings (label, name, setting_value, description, setting_group)
VALUES
    ('Shared payments enabled', 'shared_provider_default_enabled', 'true',
     'Automatically provision CPay Shared Payments entitlements for active merchants.',
     'Shared Payments'),
    ('Shared payments country', 'shared_provider_default_country', 'UG',
     'Default ISO country code for automatically provisioned shared-provider access.',
     'Shared Payments'),
    ('Shared payments currency', 'shared_provider_default_currency', 'UGX',
     'Default ISO currency for automatically provisioned shared-provider access.',
     'Shared Payments'),
    ('Collection transaction limit',
     'shared_provider_default_collection_per_transaction_limit', '500000.0000',
     'Maximum amount for one default CPay Shared Payments collection.', 'Shared Payments'),
    ('Collection daily limit', 'shared_provider_default_collection_daily_limit', '2000000.0000',
     'Maximum daily total for default CPay Shared Payments collections.', 'Shared Payments'),
    ('Payout transaction limit', 'shared_provider_default_payout_per_transaction_limit',
     '100000.0000', 'Maximum amount for one approved shared-provider payout.',
     'Shared Payments'),
    ('Payout daily limit', 'shared_provider_default_payout_daily_limit', '500000.0000',
     'Maximum daily total for approved shared-provider payouts.', 'Shared Payments'),
    ('Default payouts enabled', 'shared_provider_default_payout_enabled', 'false',
     'Whether automatically provisioned shared payouts become active without separate approval.',
     'Shared Payments'),
    ('Live payment test maximum', 'provider_live_test_max_amount', '10000.0000',
     'Maximum amount allowed for an admin-initiated live provider transaction test.',
     'Shared Payments')
ON DUPLICATE KEY UPDATE name = VALUES(name);

INSERT IGNORE INTO admin_permissions (role_name, permission_code)
VALUES
    ('ADMIN', 'PAYMENT_BALANCE_VIEW'),
    ('ADMIN', 'PAYMENT_BALANCE_REFRESH'),
    ('ADMIN', 'SHARED_PAYMENT_ENTITLEMENT_MANAGE'),
    ('ADMIN', 'SHARED_PAYMENT_LIMIT_APPROVE'),
    ('ADMIN', 'LIVE_COLLECTION_TEST'),
    ('ADMIN', 'LIVE_DISBURSEMENT_TEST'),
    ('ADMIN', 'LIVE_DISBURSEMENT_APPROVE'),
    ('ADMIN', 'PROVIDER_CREDENTIAL_MANAGE'),
    ('ADMIN', 'PROVIDER_CREDENTIAL_APPROVE'),
    ('ADMIN', 'RECONCILIATION_VIEW'),
    ('ADMIN', 'RECONCILIATION_MANAGE'),
    ('ADMIN', 'ADMIN_PERMISSION_MANAGE'),
    ('ADMIN', 'ADMIN_IMPERSONATE_MERCHANT'),
    ('ADMIN', 'BALANCE_BACKFILL'),
    ('ADMIN', 'CALLBACK_OPERATIONS'),
    ('ADMIN', 'RECONCILIATION_IMPORT'),
    ('ADMIN', 'RECONCILIATION_APPROVE'),
    ('ADMIN', 'PROVIDER_SANDBOX_VALIDATION');

ALTER TABLE provider_treasury_accounts
    ADD COLUMN provider_balance_status VARCHAR(32) NOT NULL DEFAULT 'NOT_SYNCHRONIZED'
        AFTER provider_reported_balance,
    ADD COLUMN provider_balance_updated_at DATETIME(6) NULL AFTER provider_balance_status,
    ADD COLUMN provider_balance_message VARCHAR(500) NULL AFTER provider_balance_updated_at;

CREATE TABLE IF NOT EXISTS provider_live_tests (
    id BIGINT NOT NULL AUTO_INCREMENT,
    test_reference VARCHAR(80) NOT NULL,
    idempotency_key VARCHAR(191) NOT NULL,
    merchant_id BIGINT UNSIGNED NOT NULL,
    channel_code VARCHAR(64) NOT NULL,
    credential_source VARCHAR(32) NOT NULL DEFAULT 'PLATFORM_SHARED',
    environment VARCHAR(16) NOT NULL,
    country_code VARCHAR(8) NOT NULL,
    currency_code VARCHAR(8) NOT NULL,
    operation VARCHAR(16) NOT NULL,
    amount DECIMAL(19,4) NOT NULL,
    party_payload LONGTEXT NOT NULL,
    party_mask VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    provider_reference VARCHAR(191) NULL,
    result_message VARCHAR(1000) NULL,
    requested_by VARCHAR(255) NOT NULL,
    requested_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    approved_by VARCHAR(255) NULL,
    approved_at DATETIME(6) NULL,
    executed_by VARCHAR(255) NULL,
    executed_at DATETIME(6) NULL,
    completed_at DATETIME(6) NULL,
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_provider_live_test_reference (test_reference),
    UNIQUE KEY uq_provider_live_test_idempotency (idempotency_key),
    KEY idx_provider_live_test_status (status, requested_at),
    KEY idx_provider_live_test_scope
        (channel_code, environment, country_code, currency_code, operation, requested_at),
    CONSTRAINT fk_provider_live_test_merchant FOREIGN KEY (merchant_id) REFERENCES merchants(id),
    CONSTRAINT chk_provider_live_test_operation CHECK (operation IN ('COLLECT','PAYOUT')),
    CONSTRAINT chk_provider_live_test_amount CHECK (amount > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS provider_live_test_events (
    id BIGINT NOT NULL AUTO_INCREMENT,
    live_test_id BIGINT NOT NULL,
    sequence_no INT NOT NULL,
    event_type VARCHAR(40) NOT NULL,
    status VARCHAR(32) NOT NULL,
    message VARCHAR(1000) NULL,
    actor VARCHAR(255) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_provider_live_test_event_sequence (live_test_id, sequence_no),
    CONSTRAINT fk_provider_live_test_event FOREIGN KEY (live_test_id)
        REFERENCES provider_live_tests(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Backfill existing active, interactive merchants. The platform-owned operational accounts are
-- deliberately excluded and no balance or credential value is created by this migration.
INSERT INTO shared_provider_entitlements
    (merchant_id, channel_code, environment, country_code, currency_code, operation, status,
     per_transaction_limit, daily_limit, requested_by, approved_by, approved_at, notes)
SELECT m.id, channels.channel_code, 'PRODUCTION', 'UG', 'UGX', operations.operation,
       CASE WHEN operations.operation = 'COLLECT' THEN 'ACTIVE' ELSE 'PENDING' END,
       CASE WHEN operations.operation = 'COLLECT' THEN 500000.0000 ELSE 100000.0000 END,
       CASE WHEN operations.operation = 'COLLECT' THEN 2000000.0000 ELSE 500000.0000 END,
       'SYSTEM_DEFAULT',
       CASE WHEN operations.operation = 'COLLECT' THEN 'SYSTEM_DEFAULT' ELSE NULL END,
       CASE WHEN operations.operation = 'COLLECT' THEN CURRENT_TIMESTAMP(6) ELSE NULL END,
       'Automatically provisioned CPay Shared Payments access'
FROM merchants m
CROSS JOIN (
    SELECT 'mtn_momo' AS channel_code
    UNION ALL SELECT 'airtel_open_api'
) channels
CROSS JOIN (
    SELECT 'COLLECT' AS operation
    UNION ALL SELECT 'PAYOUT'
) operations
WHERE m.status = 'ACTIVE'
  AND m.account_number NOT LIKE 'CITO-%'
ON DUPLICATE KEY UPDATE merchant_id = VALUES(merchant_id);

-- Collection access must pass both the shared entitlement and the established merchant API
-- authorization gate. Add only PAYIN; payout remains a separately approved privilege.
UPDATE merchants
SET allowed_apis = CASE
    WHEN allowed_apis IS NULL OR TRIM(allowed_apis) = '' THEN 'MOBILE_MONEY_PAYIN'
    ELSE CONCAT(TRIM(TRAILING ',' FROM allowed_apis), ',MOBILE_MONEY_PAYIN')
END
WHERE status = 'ACTIVE'
  AND account_number NOT LIKE 'CITO-%'
  AND FIND_IN_SET('MOBILE_MONEY_PAYIN', REPLACE(COALESCE(allowed_apis, ''), ' ', '')) = 0;
