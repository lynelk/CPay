package net.citotech.cito.billing.baas;

public record BillingBaasContext(
        long billingTenantId,
        long merchantId,
        long developerProjectId,
        long serviceAccountId,
        String environment) {}
