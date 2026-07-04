package net.citotech.cito.reconciliation;

import java.math.BigDecimal;

public class ReconciliationReview {
    public long id;
    public long reconciliationRecordId;
    public String transactionId;
    public String reviewType;
    public BigDecimal amount;
    public String currency;
    public String reason;
    public String reviewStatus;
    public String requestedBy;
    public String reviewedBy;
    public String reviewNote;
}
