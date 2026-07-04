package net.citotech.cito.reconciliation;

import java.math.BigDecimal;

public class StatementRow {
    public String providerCode;
    public String channelCode;
    public String providerReference;
    public String merchantReference;
    public BigDecimal amount;
    public String currency;
    public String transactionDate;
}
