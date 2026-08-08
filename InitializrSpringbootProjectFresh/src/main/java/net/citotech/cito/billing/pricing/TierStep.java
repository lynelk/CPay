package net.citotech.cito.billing.pricing;

import java.math.BigDecimal;

/**
 * One band's contribution to a computed tier charge - the audit trail persisted as JSON in {@code
 * billing_rated_charges.tier_path}.
 */
public record TierStep(
        BigDecimal bandFrom,
        BigDecimal bandTo,
        BigDecimal rate,
        BigDecimal amountInBand,
        BigDecimal contribution) {}
