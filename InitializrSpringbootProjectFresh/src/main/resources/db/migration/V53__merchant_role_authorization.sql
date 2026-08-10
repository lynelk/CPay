-- MerchantRole is the canonical merchant-user authorization source.
-- Preserve valid explicit roles, but normalize missing/blank/unrecognized values to VIEWER.
-- Existing known OWNER rows remain OWNER. New account creators are assigned OWNER explicitly by
-- MerchantSelfServiceSignupService; ordinary inserts must not acquire owner authority by default.

UPDATE merchant_admins
SET role = 'VIEWER'
WHERE role IS NULL
   OR TRIM(role) = ''
   OR UPPER(TRIM(role)) NOT IN ('OWNER', 'FINANCE', 'DEVELOPER', 'VIEWER');

UPDATE merchant_admins
SET role = UPPER(TRIM(role))
WHERE role IS NOT NULL
  AND UPPER(TRIM(role)) IN ('OWNER', 'FINANCE', 'DEVELOPER', 'VIEWER');

ALTER TABLE merchant_admins
    MODIFY COLUMN role ENUM('OWNER','FINANCE','DEVELOPER','VIEWER') NOT NULL DEFAULT 'VIEWER';
