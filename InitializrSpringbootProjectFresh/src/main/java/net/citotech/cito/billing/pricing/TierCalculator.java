package net.citotech.cito.billing.pricing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Graduated/marginal tier calculation - the real implementation {@code FeeSchedule.apply()} never
 * provided for its own {@code TIER} charging method (that method silently falls back to a flat
 * starting-tier charge, a known, documented gap). Each band's rate applies only to the slice of
 * {@code baseAmount} that falls within that band, like a tax bracket, not a cliff/all-or-nothing
 * model - a deliberate design choice for this phase, not the only valid interpretation of "tiered
 * pricing"; revisit if product/finance intends a different model.
 */
public final class TierCalculator {
    private static final int CONTRIBUTION_SCALE = 4;

    private TierCalculator() {}

    public static TierResult calculate(BigDecimal baseAmount, List<TierBand> bands) {
        if (bands == null || bands.isEmpty()) {
            throw new IllegalArgumentException("At least one tier band is required");
        }

        List<TierStep> steps = new ArrayList<>();
        BigDecimal remaining = baseAmount;
        BigDecimal bandFrom = BigDecimal.ZERO;
        BigDecimal total = BigDecimal.ZERO;

        for (TierBand band : bands) {
            if (remaining.signum() <= 0) {
                break;
            }
            BigDecimal bandWidth =
                    band.upToInclusive() == null
                            ? remaining
                            : band.upToInclusive().subtract(bandFrom);
            BigDecimal amountInBand = remaining.min(bandWidth.max(BigDecimal.ZERO));
            BigDecimal contribution =
                    amountInBand
                            .multiply(band.rate())
                            .setScale(CONTRIBUTION_SCALE, RoundingMode.HALF_UP);

            steps.add(
                    new TierStep(
                            bandFrom,
                            band.upToInclusive(),
                            band.rate(),
                            amountInBand,
                            contribution));
            total = total.add(contribution);
            remaining = remaining.subtract(amountInBand);
            bandFrom = band.upToInclusive() == null ? bandFrom : band.upToInclusive();
        }

        return new TierResult(total, steps);
    }
}
