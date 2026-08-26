package net.citotech.cito.billing.tax;

import java.math.BigDecimal;

/** Immutable tax evidence retained for a periodic billing invoice. */
public record BillingTaxSnapshot(
        long taxRuleVersionId,
        String taxCode,
        BigDecimal taxableAmount,
        BigDecimal taxRate,
        BigDecimal taxAmount,
        String currency) {}
