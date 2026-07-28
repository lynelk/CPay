package net.citotech.cito.ledger;

import java.math.BigDecimal;
import java.time.LocalDate;

public class TrialBalanceResult {
    private LocalDate runDate;
    private String currency;
    private BigDecimal totalDebits;
    private BigDecimal totalCredits;
    private boolean balanced;

    public TrialBalanceResult(LocalDate runDate, String currency, BigDecimal totalDebits, BigDecimal totalCredits) {
        this.runDate = runDate;
        this.currency = currency;
        this.totalDebits = totalDebits;
        this.totalCredits = totalCredits;
        this.balanced = totalDebits.compareTo(totalCredits) == 0;
    }

    public LocalDate getRunDate() {
        return runDate;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getTotalDebits() {
        return totalDebits;
    }

    public BigDecimal getTotalCredits() {
        return totalCredits;
    }

    public boolean isBalanced() {
        return balanced;
    }
}
