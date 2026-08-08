package net.citotech.cito.billing.pricing;

import java.time.Instant;
import java.util.List;

public record ChargeShadowComparisonResult(
        Instant windowStart,
        Instant windowEnd,
        long comparedCount,
        long matchingCount,
        List<ChargeShadowDelta> diverging) {

    public boolean allMatch() {
        return diverging.isEmpty();
    }
}
