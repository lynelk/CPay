package net.citotech.cito.money;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Canonical monetary value used by Cito calculation paths.
 *
 * <p>All authoritative calculations use four decimal places and HALF_UP rounding. Presentation
 * layers may format to a currency-specific display scale, but must not reduce calculation precision
 * before fees, tax, FX, ledger posting, settlement or reconciliation are complete.
 */
public final class MoneyAmount {
    public static final int SCALE = 4;
    public static final int DISPLAY_SCALE = 2;
    public static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;

    private final BigDecimal value;

    private MoneyAmount(BigDecimal value) {
        this.value = normalize(value);
    }

    public static MoneyAmount of(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("amount is required");
        }
        BigDecimal parsed = new BigDecimal(value.trim().replace(",", ""));
        if (parsed.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("amount must be greater than zero");
        }
        return new MoneyAmount(parsed);
    }

    public static MoneyAmount of(BigDecimal value) {
        if (value == null) {
            throw new IllegalArgumentException("amount is required");
        }
        return new MoneyAmount(value);
    }

    public static MoneyAmount zero() {
        return new MoneyAmount(BigDecimal.ZERO);
    }

    public static BigDecimal normalize(BigDecimal value) {
        if (value == null) {
            throw new IllegalArgumentException("amount is required");
        }
        return value.setScale(SCALE, ROUNDING_MODE);
    }

    public MoneyAmount plus(MoneyAmount other) {
        requireOther(other);
        return new MoneyAmount(this.value.add(other.value));
    }

    public MoneyAmount minus(MoneyAmount other) {
        requireOther(other);
        return new MoneyAmount(this.value.subtract(other.value));
    }

    public boolean isLessThan(MoneyAmount other) {
        requireOther(other);
        return this.value.compareTo(other.value) < 0;
    }

    public BigDecimal asBigDecimal() {
        return value;
    }

    /**
     * Compatibility-only conversion for adapters that still expose Double signatures. Never use the
     * returned value for authoritative arithmetic.
     */
    @Deprecated(forRemoval = false)
    public Double asLegacyDouble() {
        return value.doubleValue();
    }

    public BigDecimal forDisplay() {
        return value.setScale(DISPLAY_SCALE, ROUNDING_MODE);
    }

    @Override
    public String toString() {
        return value.toPlainString();
    }

    private static void requireOther(MoneyAmount other) {
        if (other == null) {
            throw new IllegalArgumentException("amount is required");
        }
    }
}
