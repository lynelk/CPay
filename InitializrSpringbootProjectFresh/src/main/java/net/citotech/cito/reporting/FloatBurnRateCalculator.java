package net.citotech.cito.reporting;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Pure math for the float dashboard (audit O3): burn rate is the average daily decrease in
 * balance across a trailing window of daily snapshots, and the forecast is a simple linear
 * projection of how many days remain until the balance would reach zero at that rate.
 *
 * <p>Deliberately has no database/service dependency so it can be unit tested against plain
 * sample sequences without a DB.
 */
public final class FloatBurnRateCalculator {

    private FloatBurnRateCalculator() {
    }

    /**
     * @param snapshotsAscendingByDate balance snapshots ordered oldest-first; only the last
     *                                 {@code windowDays} entries are considered
     * @param windowDays trailing window size in days (e.g. 7 or 30)
     */
    public static BurnRateForecast compute(List<BalanceSnapshotPoint> snapshotsAscendingByDate, int windowDays) {
        if (snapshotsAscendingByDate == null || snapshotsAscendingByDate.isEmpty()) {
            return new BurnRateForecast(BigDecimal.ZERO, BigDecimal.ZERO, null);
        }

        int size = snapshotsAscendingByDate.size();
        int effectiveWindow = Math.max(windowDays, 1);
        int windowStart = Math.max(0, size - effectiveWindow);
        List<BalanceSnapshotPoint> window = snapshotsAscendingByDate.subList(windowStart, size);

        BigDecimal currentBalance = window.get(window.size() - 1).balance();
        if (window.size() < 2) {
            // Not enough history to derive a rate - report the balance itself, no forecast.
            return new BurnRateForecast(BigDecimal.ZERO, currentBalance, null);
        }

        BalanceSnapshotPoint first = window.get(0);
        BalanceSnapshotPoint last = window.get(window.size() - 1);
        long days = ChronoUnit.DAYS.between(first.date(), last.date());
        if (days <= 0) {
            return new BurnRateForecast(BigDecimal.ZERO, currentBalance, null);
        }

        BigDecimal totalDecrease = first.balance().subtract(currentBalance);
        BigDecimal burnRatePerDay = totalDecrease.divide(BigDecimal.valueOf(days), 4, RoundingMode.HALF_UP);

        Double daysRemaining = null;
        if (burnRatePerDay.compareTo(BigDecimal.ZERO) > 0) {
            // Only a genuinely depleting balance (positive burn rate) yields a meaningful
            // "days until empty" forecast; a flat or growing balance has none.
            daysRemaining = currentBalance.divide(burnRatePerDay, MathContext.DECIMAL64).doubleValue();
        }
        return new BurnRateForecast(burnRatePerDay, currentBalance, daysRemaining);
    }
}
