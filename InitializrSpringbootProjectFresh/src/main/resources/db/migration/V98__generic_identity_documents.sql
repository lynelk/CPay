-- Generalise the identity-verification persistence model beyond Uganda NIN while preserving
-- all existing rows as NIN/UG. Raw identity numbers remain outside persistence; only the existing
-- hash + mask are stored.

ALTER TABLE `verified_profiles`
  ADD COLUMN `identity_type` VARCHAR(40) NOT NULL DEFAULT 'NIN' AFTER `merchant_id`,
  ADD COLUMN `identity_country` VARCHAR(2) NOT NULL DEFAULT 'UG' AFTER `identity_type`;

ALTER TABLE `identity_verification_requests`
  ADD COLUMN `identity_type` VARCHAR(40) NOT NULL DEFAULT 'NIN' AFTER `merchant_id`,
  ADD COLUMN `identity_country` VARCHAR(2) NOT NULL DEFAULT 'UG' AFTER `identity_type`;

ALTER TABLE `verified_profiles`
  DROP INDEX `uk_verified_profile_identity`,
  ADD UNIQUE KEY `uk_verified_profile_identity`
    (`identity_type`, `identity_country`, `identity_number_hash`),
  ADD KEY `idx_verified_profile_document`
    (`identity_type`, `identity_country`, `verification_status`);

ALTER TABLE `identity_verification_requests`
  ADD KEY `idx_identity_request_document`
    (`identity_type`, `identity_country`, `request_status`),
  ADD KEY `idx_identity_request_document_lookup`
    (`identity_type`, `identity_country`, `identity_number_hash`, `request_status`);
