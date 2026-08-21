-- CPay Identification & Validation Module: security convergence of the V37 pilot.
-- Additive. Corrects verified-profile tenant uniqueness risk and adds keyed-HMAC lookup
-- support without destroying legacy data.

-- 1) Add keyed HMAC lookup digest. Legacy plain SHA-256 identity_number_hash stays for
--    back-compat reads; new writes populate both during migration.
ALTER TABLE verified_profiles
  ADD COLUMN identity_lookup_hmac VARCHAR(128) NULL AFTER identity_number_hash;

CREATE INDEX idx_verified_profiles_merchant_hmac
  ON verified_profiles (merchant_id, identity_lookup_hmac);

-- 2) Migrate the pilot's global identity-hash unique key toward tenant-scoped uniqueness.
--    The legacy global key is renamed so historical upsert behavior is not silently
--    changed; new application code writes the tenant-scoped index. A production backfill
--    job must review duplicates before the global key is dropped in a later release.
ALTER TABLE verified_profiles DROP INDEX uk_verified_profile_identity;

CREATE UNIQUE INDEX uk_verified_profile_merchant_identity
  ON verified_profiles (merchant_id, identity_number_hash);

-- 3) Address V37 provider_result_json as a potential sensitive-data sink for future writes.
--    The column is retained for pilot compatibility; the generalized validation_evidence
--    table (V79) holds sanitized normalized results and protected artifact references.
