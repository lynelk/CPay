package net.citotech.cito.billing.reconciliation;

import java.math.BigDecimal;

public record RevenueAssuranceSummary(
        long billingTenantId,
        long incompleteSourceWatermarks,
        long openMaterialExceptions,
        long unratedUsageEvents,
        long uninvoicedCustomerCharges,
        long negativeMarginCharges,
        BigDecimal negativeMarginExposure) {}
