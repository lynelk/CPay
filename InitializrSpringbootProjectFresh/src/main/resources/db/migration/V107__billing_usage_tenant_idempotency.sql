-- Multi-tenant isolation: the same external idempotency key is valid in two different tenants.
-- V40 made the key globally unique, which could make a tenant retry resolve another tenant's row.
-- Preserve idempotency within each tenant while removing that cross-tenant coupling.
ALTER TABLE `billing_usage_events`
  DROP INDEX `uk_billing_usage_event_idempotency`,
  ADD UNIQUE KEY `uk_billing_usage_event_tenant_idempotency`
    (`billing_tenant_id`,`idempotency_key`);
