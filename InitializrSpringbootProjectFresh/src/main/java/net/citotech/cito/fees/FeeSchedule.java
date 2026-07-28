package net.citotech.cito.fees;

import java.math.BigDecimal;
import java.time.Instant;

/** One versioned, effective-dated fee schedule row (audit A5). */
public record FeeSchedule(
        long id,
        String gatewayId,
        Long merchantId,
        String service,
        String chargeType,
        String chargingMethod,
        BigDecimal amount,
        Instant effectiveFrom,
        Instant effectiveTo) {

    public BigDecimal apply(BigDecimal transactionAmount) {
        if ("FLAT_FEE".equals(chargingMethod)) {
            return amount;
        }
        if ("PERCENTAGE".equals(chargingMethod)) {
            return transactionAmount.multiply(amount).divide(BigDecimal.valueOf(100));
        }
        // TIER schedules are not yet computed automatically - callers should treat amount as a
        // starting-tier flat charge until per-tier bands are added.
        return amount;
    }
}
