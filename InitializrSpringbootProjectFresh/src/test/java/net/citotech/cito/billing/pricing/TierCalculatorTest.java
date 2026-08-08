package net.citotech.cito.billing.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Hand-computed fixtures for {@link TierCalculator}'s graduated/marginal tier math - each band's
 * rate applies only to the slice of the base amount within that band, like a tax bracket.
 */
class TierCalculatorTest {

    @Test
    void aSingleOpenEndedBandChargesTheFlatRateAcrossTheWholeAmount() {
        TierResult result =
                TierCalculator.calculate(
                        new BigDecimal("1000"),
                        List.of(new TierBand(null, new BigDecimal("0.02"))));

        assertThat(result.totalCharge()).isEqualByComparingTo("20.0000");
        assertThat(result.steps()).hasSize(1);
    }

    @Test
    void twoBandsSplitTheChargeAcrossTheBoundary() {
        // 15000 total: first 10000 @ 2% = 200, remaining 5000 @ 1% = 50, total 250.
        List<TierBand> bands =
                List.of(
                        new TierBand(new BigDecimal("10000"), new BigDecimal("0.02")),
                        new TierBand(null, new BigDecimal("0.01")));

        TierResult result = TierCalculator.calculate(new BigDecimal("15000"), bands);

        assertThat(result.totalCharge()).isEqualByComparingTo("250.0000");
        assertThat(result.steps()).hasSize(2);
        assertThat(result.steps().get(0).amountInBand()).isEqualByComparingTo("10000");
        assertThat(result.steps().get(0).contribution()).isEqualByComparingTo("200.0000");
        assertThat(result.steps().get(1).amountInBand()).isEqualByComparingTo("5000");
        assertThat(result.steps().get(1).contribution()).isEqualByComparingTo("50.0000");
    }

    @Test
    void threeBandsOnlyRecordStepsForBandsActuallyReached() {
        // 15000 total: first 10000 @ 2% = 200, next 5000 (of the 10000-50000 band) @ 1.5% = 75.
        // The third band (50000+) is never reached - remaining hits zero after the second band.
        List<TierBand> bands =
                List.of(
                        new TierBand(new BigDecimal("10000"), new BigDecimal("0.02")),
                        new TierBand(new BigDecimal("50000"), new BigDecimal("0.015")),
                        new TierBand(null, new BigDecimal("0.01")));

        TierResult result = TierCalculator.calculate(new BigDecimal("15000"), bands);

        assertThat(result.totalCharge()).isEqualByComparingTo("275.0000");
        assertThat(result.steps()).hasSize(2);
    }

    @Test
    void anAmountExactlyAtABandBoundaryConsumesOnlyThatBand() {
        List<TierBand> bands =
                List.of(
                        new TierBand(new BigDecimal("10000"), new BigDecimal("0.02")),
                        new TierBand(null, new BigDecimal("0.01")));

        TierResult result = TierCalculator.calculate(new BigDecimal("10000"), bands);

        assertThat(result.totalCharge()).isEqualByComparingTo("200.0000");
        assertThat(result.steps()).hasSize(1);
    }

    @Test
    void calculateRejectsAnEmptyBandList() {
        assertThatThrownBy(() -> TierCalculator.calculate(BigDecimal.TEN, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
