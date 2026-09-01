package net.citotech.cito.fees;

import java.math.BigDecimal;
import java.time.Instant;
import net.citotech.cito.gateway.PaymentGatewayException;
import net.citotech.cito.money.MoneyAmount;

/** One versioned, effective-dated fee schedule row. */
public record FeeSchedule(
        long id,
        String gatewayId,
        Long merchantId,
        String service,
        String chargeType,
        String chargingMethod,
        BigDecimal amount,
        Instant effectiveFrom,
        Instant effectiveTo) {

    public BigDecimal apply(BigDecimal transactionAmount) {
        if (transactionAmount == null || transactionAmount.signum() < 0) {
            throw new PaymentGatewayException("transactionAmount must be non-negative");
        }
        if ("FLAT_FEE".equals(chargingMethod)) {
            return MoneyAmount.normalize(amount);
        }
        if ("PERCENTAGE".equals(chargingMethod)) {
            return MoneyAmount.normalize(
                    transactionAmount.multiply(amount).divide(BigDecimal.valueOf(100)));
        }
        throw new PaymentGatewayException(
                "Unsupported charging method " + chargingMethod + "; tier pricing is not enabled");
    }
}
