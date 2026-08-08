package net.citotech.cito.billing.integration.cpay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import net.citotech.cito.fees.FeeSchedule;
import net.citotech.cito.fees.FeeScheduleService;
import org.junit.jupiter.api.Test;

/**
 * Covers the projection of a legacy {@code fee_schedules} row into the new {@code
 * billing_price_components} shape (Slice 12), most importantly that a {@code TIER} row is flagged
 * not-yet-computable rather than silently reusing {@code FeeSchedule.apply()}'s flat-fallback,
 * which is a known gap, not a correct tier calculation.
 */
class LegacyFeeSchedulePriceAdapterTest {

    @Test
    void projectReturnsAFlatComponentForAFlatFeeSchedule() {
        FeeScheduleService feeScheduleService = mock(FeeScheduleService.class);
        when(feeScheduleService.currentSchedule("mtn_momo", 42L, "PAYIN", "CUSTOMER_CHARGE"))
                .thenReturn(Optional.of(schedule("FLAT_FEE", new BigDecimal("500"))));

        Optional<ProjectedPriceComponent> result =
                new LegacyFeeSchedulePriceAdapter(feeScheduleService)
                        .project("mtn_momo", 42L, "PAYIN", "CUSTOMER_CHARGE");

        assertThat(result).isPresent();
        assertThat(result.get().computable()).isTrue();
        assertThat(result.get().componentType()).isEqualTo("FLAT");
        assertThat(result.get().flatAmount()).isEqualByComparingTo("500");
    }

    @Test
    void projectReturnsAPercentageComponentForAPercentageSchedule() {
        FeeScheduleService feeScheduleService = mock(FeeScheduleService.class);
        when(feeScheduleService.currentSchedule("airtel_money", null, "PAYOUT", "COST_OF_PAYMENT"))
                .thenReturn(Optional.of(schedule("PERCENTAGE", new BigDecimal("1.5"))));

        Optional<ProjectedPriceComponent> result =
                new LegacyFeeSchedulePriceAdapter(feeScheduleService)
                        .project("airtel_money", null, "PAYOUT", "COST_OF_PAYMENT");

        assertThat(result).isPresent();
        assertThat(result.get().computable()).isTrue();
        assertThat(result.get().componentType()).isEqualTo("PERCENTAGE");
        assertThat(result.get().percentageRate()).isEqualByComparingTo("1.5");
    }

    @Test
    void projectFlagsATierScheduleAsNotYetComputable() {
        FeeScheduleService feeScheduleService = mock(FeeScheduleService.class);
        when(feeScheduleService.currentSchedule("mtn_momo", 42L, "PAYIN", "CUSTOMER_CHARGE"))
                .thenReturn(Optional.of(schedule("TIER", new BigDecimal("500"))));

        Optional<ProjectedPriceComponent> result =
                new LegacyFeeSchedulePriceAdapter(feeScheduleService)
                        .project("mtn_momo", 42L, "PAYIN", "CUSTOMER_CHARGE");

        assertThat(result).isPresent();
        assertThat(result.get().computable()).isFalse();
        assertThat(result.get().componentType()).isNull();
        assertThat(result.get().notComputableReason())
                .contains("TIER")
                .contains("not yet computable");
    }

    @Test
    void projectReturnsEmptyWhenNoLegacyScheduleExists() {
        FeeScheduleService feeScheduleService = mock(FeeScheduleService.class);
        when(feeScheduleService.currentSchedule("safaricom", 1L, "PAYIN", "CUSTOMER_CHARGE"))
                .thenReturn(Optional.empty());

        Optional<ProjectedPriceComponent> result =
                new LegacyFeeSchedulePriceAdapter(feeScheduleService)
                        .project("safaricom", 1L, "PAYIN", "CUSTOMER_CHARGE");

        assertThat(result).isEmpty();
    }

    private FeeSchedule schedule(String chargingMethod, BigDecimal amount) {
        return new FeeSchedule(
                1L,
                "mtn_momo",
                42L,
                "PAYIN",
                "CUSTOMER_CHARGE",
                chargingMethod,
                amount,
                Instant.now(),
                null);
    }
}
