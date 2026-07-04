package net.citotech.cito;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Utility for parsing payment amounts before passing legacy Double-based code. */
public class Amount {
    private final BigDecimal value;

    private Amount(BigDecimal value) {
        this.value = value;
    }

    public static Amount parse(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            throw new IllegalArgumentException("Amount is required");
        }
        BigDecimal parsed = new BigDecimal(raw.trim()).setScale(2, RoundingMode.HALF_UP);
        if (parsed.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }
        return new Amount(parsed);
    }

    public BigDecimal asBigDecimal() {
        return value;
    }

    public Double asLegacyDouble() {
        return value.doubleValue();
    }

    @Override
    public String toString() {
        return value.toPlainString();
    }
}

