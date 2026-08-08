package net.citotech.cito.billing.integration.cpay;

import java.math.BigDecimal;

/**
 * A legacy {@code fee_schedules} row projected into the {@code billing_price_components} (V43)
 * shape, or a flag that it cannot be projected yet. Read-only/shadow use only in this phase -
 * nothing writes a {@code billing_price_components} row from this projection.
 */
public record ProjectedPriceComponent(
        boolean computable,
        String componentType,
        BigDecimal flatAmount,
        BigDecimal percentageRate,
        String notComputableReason) {

    public static ProjectedPriceComponent flat(BigDecimal amount) {
        return new ProjectedPriceComponent(true, "FLAT", amount, null, null);
    }

    public static ProjectedPriceComponent percentage(BigDecimal rate) {
        return new ProjectedPriceComponent(true, "PERCENTAGE", null, rate, null);
    }

    public static ProjectedPriceComponent notComputable(String reason) {
        return new ProjectedPriceComponent(false, null, null, null, reason);
    }
}
