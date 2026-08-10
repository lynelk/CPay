-- MerchantRole is the canonical merchant-user authorization source.
-- Per the account-compatibility policy, missing/blank/unrecognized roles receive
-- maximum account authority (OWNER) rather than relying on legacy privilege rows.

UPDATE merchant_admins
SET role = 'OWNER'
WHERE role IS NULL
   OR TRIM(role) = ''
   OR UPPER(TRIM(role)) NOT IN ('OWNER', 'FINANCE', 'DEVELOPER', 'VIEWER');

UPDATE merchant_admins
SET role = UPPER(TRIM(role))
WHERE role IS NOT NULL
  AND UPPER(TRIM(role)) IN ('OWNER', 'FINANCE', 'DEVELOPER', 'VIEWER');
