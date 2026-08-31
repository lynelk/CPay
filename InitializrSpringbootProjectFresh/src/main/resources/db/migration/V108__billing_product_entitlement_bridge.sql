-- Make Cito's platform-level service entitlement model authoritative for Billing-as-a-Service
-- without breaking already-approved production BaaS tenants.

INSERT IGNORE INTO `cito_organizations`
  (`organization_reference`,`merchant_id`,`organization_name`,`status`)
SELECT CONCAT('MERCHANT-',bt.merchant_id), bt.merchant_id, CONCAT('Merchant ',bt.merchant_id), 'ACTIVE'
FROM `billing_tenants` bt
WHERE bt.merchant_id IS NOT NULL;

-- Existing approved production BaaS tenants are migrated into an explicit BILLING entitlement.
INSERT IGNORE INTO `cito_service_entitlements`
  (`organization_id`,`service_code`,`environment`,`status`,`starts_at`,`approved_by`)
SELECT co.id,'BILLING','PRODUCTION','ACTIVE',CURRENT_TIMESTAMP,
       COALESCE(bp.approved_by,'migration:V108')
FROM `billing_baas_tenant_profiles` bp
JOIN `billing_tenants` bt ON bt.id=bp.billing_tenant_id
JOIN `cito_organizations` co ON co.merchant_id=bt.merchant_id
WHERE bp.activation_status='ACTIVE'
  AND bp.legal_model_status='APPROVED'
  AND bp.commercial_model_status='APPROVED'
  AND bp.tax_model_status='APPROVED'
  AND bp.funds_flow_status='APPROVED';
