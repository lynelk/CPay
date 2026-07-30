package net.citotech.cito.reconciliation;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Audit O2: a narrow read projection of {@code merchant_transactions_log}, used only to populate
 * the manual-match workbench's candidate-transaction search panel. Deliberately smaller than the
 * full {@code net.citotech.cito.Model.Transaction} legacy model - the workbench only needs enough
 * to let an operator visually confirm a match, not the full transaction-log shape.
 */
public class CandidateTransaction {
    public long id;
    public String txUniqueId;
    public String txMerchantRef;
    public String txGatewayRef;
    public Long merchantId;
    public BigDecimal originalAmount;
    public String currency;
    public String status;
    public String txType;
    public LocalDateTime createdOn;
    public String payerNumber;
}
