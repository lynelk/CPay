package net.citotech.cito.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Pure-math coverage for the O3 float dashboard's burn rate / forecast, exercised with hand-built
 * sample balance-snapshot sequences - no DB involved.
 */
class FloatBurnRateCalculatorTest {

    private static BalanceSnapshotPoint point(String isoDate, long balance) {
        return new BalanceSnapshotPoint(LocalDate.parse(isoDate), BigDecimal.valueOf(balance));
    }

    @Test
    void steadyLinearDecreaseYieldsExpectedBurnRateAndDaysRemaining() {
        List<BalanceSnapshotPoint> snapshots = List.of(
                point("2026-07-01", 1000),
                point("2026-07-02", 900),
                point("2026-07-03", 800),
                point("2026-07-04", 700));

        BurnRateForecast forecast = FloatBurnRateCalculator.compute(snapshots, 7);

        assertThat(forecast.burnRatePerDay()).isEqualByComparingTo("100.0000");
        assertThat(forecast.currentBalance()).isEqualByComparingTo("700");
        assertThat(forecast.estimatedDaysRemaining()).isCloseTo(7.0, within(0.0001));
    }

    @Test
    void flatBalanceHasZeroBurnRateAndNoForecast() {
        List<BalanceSnapshotPoint> snapshots = List.of(
                point("2026-07-01", 500),
                point("2026-07-02", 500),
                point("2026-07-03", 500));

        BurnRateForecast forecast = FloatBurnRateCalculator.compute(snapshots, 7);

        assertThat(forecast.burnRatePerDay()).isEqualByComparingTo("0.0000");
        assertThat(forecast.currentBalance()).isEqualByComparingTo("500");
        assertThat(forecast.estimatedDaysRemaining()).isNull();
    }

    @Test
    void growingBalanceHasNegativeBurnRateAndNoForecast() {
        // A top-up mid-window makes the balance grow overall - not "burning", so no ETA-to-empty.
        List<BalanceSnapshotPoint> snapshots = List.of(
                point("2026-07-01", 500),
                point("2026-07-02", 600),
                point("2026-07-03", 700));

        BurnRateForecast forecast = FloatBurnRateCalculator.compute(snapshots, 7);

        assertThat(forecast.burnRatePerDay()).isLessThan(BigDecimal.ZERO);
        assertThat(forecast.currentBalance()).isEqualByComparingTo("700");
        assertThat(forecast.estimatedDaysRemaining()).isNull();
    }

    @Test
    void singleSnapshotReportsBalanceWithoutAForecast() {
        List<BalanceSnapshotPoint> snapshots = List.of(point("2026-07-01", 1234));

        BurnRateForecast forecast = FloatBurnRateCalculator.compute(snapshots, 7);

        assertThat(forecast.burnRatePerDay()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(forecast.currentBalance()).isEqualByComparingTo("1234");
        assertThat(forecast.estimatedDaysRemaining()).isNull();
    }

    @Test
    void emptySnapshotListReportsZeroBalanceAndNoForecast() {
        BurnRateForecast forecast = FloatBurnRateCalculator.compute(List.of(), 7);

        assertThat(forecast.burnRatePerDay()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(forecast.currentBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(forecast.estimatedDaysRemaining()).isNull();
    }

    @Test
    void windowSmallerThanHistoryOnlyConsidersTheTrailingDays() {
        // 10 days of history, decreasing by 10/day, but ask for just the trailing 3-day window
        // (2 gaps): balance goes from 970 (day8) to 950 (day10), so burn rate should be ~10/day
        // rather than reflecting the full 10-day decline.
        List<BalanceSnapshotPoint> snapshots = List.of(
                point("2026-07-01", 1000),
                point("2026-07-02", 990),
                point("2026-07-03", 980),
                point("2026-07-04", 970),
                point("2026-07-05", 960),
                point("2026-07-06", 950),
                point("2026-07-07", 940),
                point("2026-07-08", 930),
                point("2026-07-09", 920),
                point("2026-07-10", 910));

        BurnRateForecast forecast = FloatBurnRateCalculator.compute(snapshots, 3);

        assertThat(forecast.burnRatePerDay()).isEqualByComparingTo("10.0000");
        assertThat(forecast.currentBalance()).isEqualByComparingTo("910");
        assertThat(forecast.estimatedDaysRemaining()).isCloseTo(91.0, within(0.01));
    }
}
