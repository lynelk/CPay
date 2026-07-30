package net.citotech.cito.reconciliation;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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
    // Audit O2: surfaced for the manual-match workbench's unmatched-rows list, which needs a date
    // column; not read before this, though the underlying `created_at` column has always existed.
    public LocalDateTime createdAt;
}
