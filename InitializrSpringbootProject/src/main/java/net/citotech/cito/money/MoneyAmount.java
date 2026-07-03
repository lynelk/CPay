package net.citotech.cito.money;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class MoneyAmount {
    public static final int SCALE = 2;
    public static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;

    private final BigDecimal value;

    private MoneyAmount(BigDecimal value) {
        this.value = value.setScale(SCALE, ROUNDING_MODE);
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

    public MoneyAmount plus(MoneyAmount other) {
        return new MoneyAmount(this.value.add(other.value));
    }

    public MoneyAmount minus(MoneyAmount other) {
        return new MoneyAmount(this.value.subtract(other.value));
    }

    public boolean isLessThan(MoneyAmount other) {
        return this.value.compareTo(other.value) < 0;
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
