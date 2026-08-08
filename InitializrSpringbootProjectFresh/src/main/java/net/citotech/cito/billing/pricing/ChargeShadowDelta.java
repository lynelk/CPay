package net.citotech.cito.billing.pricing;

import java.math.BigDecimal;

/**
 * One compared transaction: {@code RatingEngine}'s rated charge vs. the legacy {@code DoPayGateway}
 * charge already recorded on {@code merchant_transactions_log.charges}.
 */
public record ChargeShadowDelta(
        String sourceReference, BigDecimal legacyCharge, BigDecimal ratedCharge, BigDecimal delta) {

    public boolean matches() {
        return delta.compareTo(BigDecimal.ZERO) == 0;
    }
}
