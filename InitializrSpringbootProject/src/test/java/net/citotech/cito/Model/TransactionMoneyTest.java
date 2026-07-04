package net.citotech.cito.Model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TransactionMoneyTest {
    @Test
    void storesMoneyWithTwoDecimalsInternally() {
        Transaction tx = new Transaction();
        tx.setOriginal_amount(1000.555);
        tx.setCharges(10.225);
        tx.setTx_cost(1.115);
        assertEquals("1000.56", tx.getOriginalAmountDecimal().toPlainString());
        assertEquals("10.23", tx.getChargesDecimal().toPlainString());
        assertEquals("1.12", tx.getTxCostDecimal().toPlainString());
    }
}
