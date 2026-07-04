package net.citotech.cito.reconciliation;

import java.math.BigDecimal;

public class ReconciliationRecord {
    public long id;
    public String providerCode;
    public String channelCode;
    public String providerReference;
    public String merchantReference;
    public String transactionId;
    public BigDecimal amount;
    public String currency;
    public String matchStatus;
    public String matchReason;
}

