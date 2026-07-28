package net.citotech.cito.reporting;

import java.math.BigDecimal;

/**
 * Result of {@link FloatBurnRateCalculator#compute}: the average daily float consumption over a
 * trailing window (audit O3), the balance the rate was measured against, and a simple linear
 * forecast of how many days remain until the account would run dry at that rate.
 *
 * <p>{@code estimatedDaysRemaining} is {@code null} whenever a forecast doesn't make sense: no
 * data, a single data point, or a balance that is flat/growing rather than being burned down.
 */
public record BurnRateForecast(
        BigDecimal burnRatePerDay,
        BigDecimal currentBalance,
        Double estimatedDaysRemaining) {
}
