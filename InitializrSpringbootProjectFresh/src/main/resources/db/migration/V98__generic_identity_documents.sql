-- Generalize the V37 identity-verification tables beyond Uganda NIN while preserving all existing
-- records. Existing rows are backfilled through NOT NULL defaults as NIN/UG.

ALTER TABLE `verified_profiles`
  ADD COLUMN `identity_type` VARCHAR(40) NOT NULL DEFAULT 'NIN' AFTER `merchant_id`,
  ADD COLUMN `country_code` CHAR(2) NOT NULL DEFAULT 'UG' AFTER `identity_type`;

-- V80 already replaced the original global identity index with the merchant-scoped key below.
-- Preserve tenant ownership while extending uniqueness with document type and country.
ALTER TABLE `verified_profiles`
  DROP INDEX `uk_verified_profile_merchant_identity`,
  ADD UNIQUE KEY `uk_verified_profile_merchant_identity_document`
    (`merchant_id`, `identity_type`, `country_code`, `identity_number_hash`),
  ADD KEY `idx_verified_profile_document`
    (`identity_type`, `country_code`, `verification_status`);

ALTER TABLE `identity_verification_requests`
  ADD COLUMN `identity_type` VARCHAR(40) NOT NULL DEFAULT 'NIN' AFTER `merchant_id`,
  ADD COLUMN `country_code` CHAR(2) NOT NULL DEFAULT 'UG' AFTER `identity_type`,
  ADD KEY `idx_identity_request_document`
    (`identity_type`, `country_code`, `request_status`);
