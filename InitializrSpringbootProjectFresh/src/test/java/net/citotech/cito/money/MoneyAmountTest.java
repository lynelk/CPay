package net.citotech.cito.money;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MoneyAmountTest {

    @Test
    void canonicalCalculationPrecisionIsFourDecimals() {
        assertThat(MoneyAmount.of("12.34567").asBigDecimal()).isEqualByComparingTo("12.3457");
        assertThat(MoneyAmount.of(new BigDecimal("0.00005")).asBigDecimal())
                .isEqualByComparingTo("0.0001");
    }

    @Test
    void displayRoundingDoesNotChangeCalculationValue() {
        MoneyAmount value = MoneyAmount.of("12.3456");

        assertThat(value.forDisplay()).isEqualByComparingTo("12.35");
        assertThat(value.asBigDecimal()).isEqualByComparingTo("12.3456");
    }

    @Test
    void stringFactoryRejectsZeroAndNegativeCommercialAmounts() {
        assertThatThrownBy(() -> MoneyAmount.of("0"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("greater than zero");
        assertThatThrownBy(() -> MoneyAmount.of("-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("greater than zero");
    }

    @Test
    void arithmeticKeepsCanonicalPrecision() {
        MoneyAmount result = MoneyAmount.of("1.1111").plus(MoneyAmount.of("2.2222"));
        assertThat(result.asBigDecimal()).isEqualByComparingTo("3.3333");
    }
}
