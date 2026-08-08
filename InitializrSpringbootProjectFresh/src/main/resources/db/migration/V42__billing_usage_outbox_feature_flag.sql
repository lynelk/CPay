-- Billing engine Phase 1, Slice 10: registers the "billing-usage-outbox" global feature flag
-- (FeatureRegistryService, V36) that gates PaymentOrchestrationService.collect()'s new outbox
-- write hook (see net.citotech.cito.billing.integration.cpay.PaymentUsageOutboxHook). Off by
-- default - this touches the live payment path, so it stays opt-in per merchant
-- (merchant_feature_flags override) until validated, matching the "identity-gnugrid" precedent in
-- V36 for a flag that turns on real new behaviour rather than just a UI surface.

INSERT IGNORE INTO `feature_flags` (`flag_key`, `enabled`, `description`) VALUES
  ('billing-usage-outbox', 0, 'Emit a billing_outbox entry for each submitted payment collection (Billing Phase 1)');
