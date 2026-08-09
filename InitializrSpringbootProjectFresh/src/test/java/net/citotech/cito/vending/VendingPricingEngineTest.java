package net.citotech.cito.vending;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class VendingPricingEngineTest {
    private final VendingPricingEngine engine = new VendingPricingEngine();

    @Test
    void suppliedPowerBankProfileRoundsUpAndChargesMinimumOneHour() {
        VendingPricingPolicy policy = powerBankPolicy();
        Instant start = Instant.parse("2026-08-09T10:00:00Z");

        var firstMinute = engine.rate(policy, start, start.plusSeconds(60), 0);
        assertEquals(1, firstMinute.billedBlocks());
        assertEquals(new BigDecimal("2000.0000"), firstMinute.usageAmount());

        var sixtyOneMinutes = engine.rate(policy, start, start.plusSeconds(61 * 60L), 0);
        assertEquals(2, sixtyOneMinutes.billedBlocks());
        assertEquals(new BigDecimal("4000.0000"), sixtyOneMinutes.usageAmount());
    }

    @Test
    void freeWindowIsFreeButOnceExceededBillingIncludesTheFreeWindow() {
        VendingPricingPolicy policy =
                new VendingPricingPolicy(
                        1,
                        7,
                        "FREE5",
                        "UGX",
                        new BigDecimal("20000"),
                        5,
                        new BigDecimal("1000"),
                        30,
                        1,
                        null,
                        null,
                        null,
                        "ORIGINAL_ROUTE");
        Instant start = Instant.parse("2026-08-09T10:00:00Z");
        assertEquals(
                BigDecimal.ZERO.setScale(4),
                engine.rate(policy, start, start.plusSeconds(5 * 60L), 0).usageAmount());
        assertEquals(
                new BigDecimal("1000.0000"),
                engine.rate(policy, start, start.plusSeconds(6 * 60L), 0).usageAmount());
    }

    @Test
    void unbilledSuspensionReducesChargeableDuration() {
        VendingPricingPolicy policy = powerBankPolicy();
        Instant start = Instant.parse("2026-08-09T10:00:00Z");
        var result = engine.rate(policy, start, start.plusSeconds(125 * 60L), 65 * 60L);
        assertEquals(1, result.billedBlocks());
        assertEquals(new BigDecimal("2000.0000"), result.usageAmount());
    }

    @Test
    void dailyAndOvertimeCapsAreEnforced() {
        VendingPricingPolicy policy =
                new VendingPricingPolicy(
                        1,
                        7,
                        "CAP",
                        "UGX",
                        new BigDecimal("20000"),
                        0,
                        new BigDecimal("5000"),
                        60,
                        1,
                        new BigDecimal("10000"),
                        new BigDecimal("18000"),
                        3,
                        "ORIGINAL_ROUTE");
        Instant start = Instant.parse("2026-08-01T00:00:00Z");
        var dayOne = engine.rate(policy, start, start.plusSeconds(10 * 60 * 60L), 0);
        assertEquals(new BigDecimal("10000.0000"), dayOne.usageAmount());

        var overtime = engine.rate(policy, start, start.plusSeconds(3 * 24 * 60 * 60L), 0);
        assertTrue(overtime.overtimeSettled());
        assertEquals(new BigDecimal("18000.0000"), overtime.usageAmount());
    }

    private VendingPricingPolicy powerBankPolicy() {
        return new VendingPricingPolicy(
                1,
                7,
                "POWERBANK_UG",
                "UGX",
                new BigDecimal("20000"),
                0,
                new BigDecimal("2000"),
                60,
                1,
                null,
                null,
                null,
                "ORIGINAL_ROUTE");
    }
}
