package net.citotech.cito.money;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MoneyAmountTest {
    @Test
    void parsesStringValue() {
        MoneyAmount amount = MoneyAmount.of("1000.555");
        assertEquals("1000.56", amount.toString());
    }
}
