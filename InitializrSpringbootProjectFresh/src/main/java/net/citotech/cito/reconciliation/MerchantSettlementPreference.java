package net.citotech.cito.reconciliation;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A merchant's self-service settlement scheduling preference (audit N4): how often their float
 * should settle (DAILY/WEEKLY), which day of the week for WEEKLY, and a minimum amount worth
 * settling. {@code id} is {@code 0} and the timestamps are {@code null} for the implied default
 * (DAILY, no day, zero minimum) returned to a merchant who has never saved one - see
 * {@link MerchantSettlementPreferenceService#getOrDefault(long)}.
 */
public record MerchantSettlementPreference(
        long id,
        long merchantId,
        String settlementFrequency,
        String settlementDayOfWeek,
        BigDecimal minimumSettlementAmount,
        String updatedBy,
        Instant createdAt,
        Instant updatedAt) {
}
