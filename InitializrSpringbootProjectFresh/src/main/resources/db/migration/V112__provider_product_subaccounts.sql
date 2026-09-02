-- Separate provider product wallets so collection inflows and disbursement prefunding are never
-- represented as one undifferentiated float balance. Existing V104 rows become zero-risk MASTER
-- control accounts; no balance is copied or invented by this migration.

ALTER TABLE provider_treasury_accounts
    ADD COLUMN account_role VARCHAR(24) NOT NULL DEFAULT 'MASTER' AFTER currency_code,
    ADD COLUMN display_name VARCHAR(255) NULL AFTER account_role,
    ADD COLUMN parent_account_id BIGINT NULL AFTER display_name,
    ADD COLUMN prefund_required VARCHAR(3) NOT NULL DEFAULT 'NO' AFTER parent_account_id,
    DROP INDEX uq_provider_treasury_scope,
    ADD UNIQUE KEY uq_provider_treasury_scope_role
        (channel_code, environment, country_code, currency_code, account_role),
    ADD KEY idx_provider_treasury_parent (parent_account_id),
    ADD CONSTRAINT fk_provider_treasury_parent FOREIGN KEY (parent_account_id)
        REFERENCES provider_treasury_accounts(id);

UPDATE provider_treasury_accounts
SET account_role = 'MASTER',
    display_name = CONCAT(channel_code, ' ', environment, ' master account'),
    prefund_required = 'NO'
WHERE parent_account_id IS NULL;

-- MTN's official sandbox uses EUR. This account remains at zero until an approved adjustment is
-- posted; sandbox credentials and production credentials remain in separate scopes.
INSERT INTO provider_treasury_accounts
    (channel_code, environment, country_code, currency_code, account_role, display_name,
     prefund_required, low_float_threshold)
VALUES
    ('mtn_momo', 'SANDBOX', 'UG', 'EUR', 'MASTER', 'MTN MoMo sandbox master account',
     'NO', 0.0000)
ON DUPLICATE KEY UPDATE display_name = VALUES(display_name);

-- Every configured provider scope gets explicit operation sub-accounts. Collection balances grow
-- only after confirmed provider collections. Disbursement balances must be prefunded and are the
-- only balances reserved/consumed by shared-provider payouts.
INSERT INTO provider_treasury_accounts
    (channel_code, environment, country_code, currency_code, account_role, display_name,
     parent_account_id, prefund_required, low_float_threshold)
SELECT channel_code, environment, country_code, currency_code, 'COLLECTION',
       CONCAT(channel_code, ' ', environment, ' collection account'), id, 'NO', 0.0000
FROM provider_treasury_accounts
WHERE account_role = 'MASTER'
ON DUPLICATE KEY UPDATE
    display_name = VALUES(display_name),
    parent_account_id = VALUES(parent_account_id),
    prefund_required = VALUES(prefund_required);

INSERT INTO provider_treasury_accounts
    (channel_code, environment, country_code, currency_code, account_role, display_name,
     parent_account_id, prefund_required, low_float_threshold)
SELECT channel_code, environment, country_code, currency_code, 'DISBURSEMENT',
       CONCAT(channel_code, ' ', environment, ' disbursement account'), id, 'YES', 0.0000
FROM provider_treasury_accounts
WHERE account_role = 'MASTER'
ON DUPLICATE KEY UPDATE
    display_name = VALUES(display_name),
    parent_account_id = VALUES(parent_account_id),
    prefund_required = VALUES(prefund_required);
