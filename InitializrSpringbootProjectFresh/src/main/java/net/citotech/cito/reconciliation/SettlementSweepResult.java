package net.citotech.cito.reconciliation;

import java.math.BigDecimal;

public class SettlementSweepResult {
    private String runReference;
    private String status;
    private BigDecimal sweepAmount;
    private String message;

    public SettlementSweepResult(String runReference, String status, BigDecimal sweepAmount, String message) {
        this.runReference = runReference;
        this.status = status;
        this.sweepAmount = sweepAmount;
        this.message = message;
    }

    public String getRunReference() { return runReference; }
    public void setRunReference(String runReference) { this.runReference = runReference; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public BigDecimal getSweepAmount() { return sweepAmount; }
    public void setSweepAmount(BigDecimal sweepAmount) { this.sweepAmount = sweepAmount; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
