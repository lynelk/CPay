package net.citotech.cito.billing.pricing;

import java.math.BigDecimal;

/** The output of {@link RatingEngine#rate}, not yet persisted. */
public record RatedCharge(
        long priceBookVersionId,
        BigDecimal ratedAmount,
        String currency,
        String roundingPolicy,
        String tierPathJson,
        String formulaInputsJson) {}
