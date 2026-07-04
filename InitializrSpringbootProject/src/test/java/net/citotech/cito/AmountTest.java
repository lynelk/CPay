package net.citotech.cito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class AmountTest {
    @Test
    void parsesMoneyToTwoDecimals() {
        assertEquals("50000.00", Amount.parse("50000").toString());
        assertEquals("50000.13", Amount.parse("50000.125").toString());
    }

    @Test
    void rejectsZeroAmount() {
        assertThrows(IllegalArgumentException.class, () -> Amount.parse("0"));
    }
}
