package net.citotech.cito;

import java.math.BigDecimal;
import net.citotech.cito.money.MoneyAmount;

/**
 * @deprecated Use {@link MoneyAmount}. This compatibility wrapper remains only for older adapters
 *     and delegates all precision/rounding policy to the canonical money type.
 */
@Deprecated(forRemoval = false)
public final class Amount {
    private final MoneyAmount value;

    private Amount(MoneyAmount value) {
        this.value = value;
    }

    public static Amount parse(String raw) {
        return new Amount(MoneyAmount.of(raw));
    }

    public BigDecimal asBigDecimal() {
        return value.asBigDecimal();
    }

    /** Compatibility boundary only. Do not perform authoritative arithmetic on Double values. */
    @Deprecated(forRemoval = false)
    public Double asLegacyDouble() {
        return value.asBigDecimal().doubleValue();
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
