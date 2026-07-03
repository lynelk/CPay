package net.citotech.cito.money;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MoneyAmountTest {
    @Test
    void parsesStringToTwoDecimalPlaces() {
        MoneyAmount amount = MoneyAmount.of("1000.555");
        assertEquals(new BigDecimal("1000.56"), amount.asBigDecimal());
    }
}
