package net.citotech.cito.vending;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Service;

/**
 * Rates a vending/rental session without provider or device assumptions.
 *
 * <p>The model covers the ChargeNow-style advanced pricing controls (deposit, free duration,
 * billing blocks, daily cap, overtime amount/days) while also reproducing the supplied power-bank
 * prototype with a 60-minute block and a minimum of one block.
 */
@Service
public class VendingPricingEngine {
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);

    public Rating rate(
            VendingPricingPolicy policy,
            Instant startedAt,
            Instant endedAt,
            long excludedSuspendedSeconds) {
        if (policy == null || startedAt == null || endedAt == null || endedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException(
                    "A valid pricing policy and rental time range are required");
        }
        if (policy.billingBlockMinutes() <= 0 || policy.minimumBillingBlocks() < 0) {
            throw new IllegalArgumentException("Billing block configuration is invalid");
        }

        long elapsedSeconds = Math.max(0, Duration.between(startedAt, endedAt).getSeconds());
        long billableSeconds = Math.max(0, elapsedSeconds - Math.max(0, excludedSuspendedSeconds));
        long billableMinutes = (billableSeconds + 59) / 60;

        if (policy.overtimeDays() != null
                && policy.overtimeDays() > 0
                && billableMinutes >= (long) policy.overtimeDays() * 24 * 60
                && positive(policy.overtimeAmount())) {
            return new Rating(billableMinutes, 0, money(policy.overtimeAmount()), true);
        }

        if (billableMinutes <= policy.freeMinutes()) {
            return new Rating(billableMinutes, 0, ZERO, false);
        }

        long blocks =
                (billableMinutes + policy.billingBlockMinutes() - 1L)
                        / policy.billingBlockMinutes();
        blocks = Math.max(blocks, policy.minimumBillingBlocks());
        BigDecimal amount = money(policy.unitPrice().multiply(BigDecimal.valueOf(blocks)));

        if (positive(policy.dailyCapAmount())) {
            long chargeDays = Math.max(1, (billableMinutes + 1439) / 1440);
            BigDecimal cap =
                    money(policy.dailyCapAmount().multiply(BigDecimal.valueOf(chargeDays)));
            amount = amount.min(cap);
        }
        if (positive(policy.overtimeAmount())) {
            amount = amount.min(money(policy.overtimeAmount()));
        }
        return new Rating(billableMinutes, blocks, money(amount), false);
    }

    private boolean positive(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0;
    }

    private BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(4, RoundingMode.HALF_UP);
    }

    public record Rating(
            long billableMinutes,
            long billedBlocks,
            BigDecimal usageAmount,
            boolean overtimeSettled) {}
}
