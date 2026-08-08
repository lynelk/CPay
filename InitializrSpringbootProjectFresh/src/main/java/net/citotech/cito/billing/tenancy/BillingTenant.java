package net.citotech.cito.billing.tenancy;

import java.time.Instant;

/**
 * A row from {@code billing_tenants} (see {@code Docs/Adr/0003-billing-tenant-model.md}). Every row
 * maps to exactly one {@code merchants.id} today - {@code merchantId} is unique - even though the
 * schema is shaped to allow a future multi-tenant Billing-as-a-Service model without a breaking
 * migration.
 */
public record BillingTenant(
        long id, long merchantId, String tenantType, String tenantStatus, Instant createdAt) {}
