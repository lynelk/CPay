package net.citotech.cito.checkout;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Row shape for the {@code invoices} table (audit N9). Sibling of {@link PaymentLinkRecord}:
 * same id/reference/amount/currency/status/token shape, plus invoice-specific fields
 * (payer name/contact, due date) and no country/callback-url columns of its own.
 */
public class InvoiceRecord {
    private long id;
    private long merchantId;
    private String reference;
    private BigDecimal amount;
    private String currency;
    private String description;
    private String payerName;
    private String payerContact;
    private LocalDate dueDate;
    private String status;
    private String publicTokenHash;
    private String createdTransactionId;

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public long getMerchantId() { return merchantId; }
    public void setMerchantId(long merchantId) { this.merchantId = merchantId; }
    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getPayerName() { return payerName; }
    public void setPayerName(String payerName) { this.payerName = payerName; }
    public String getPayerContact() { return payerContact; }
    public void setPayerContact(String payerContact) { this.payerContact = payerContact; }
    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getPublicTokenHash() { return publicTokenHash; }
    public void setPublicTokenHash(String publicTokenHash) { this.publicTokenHash = publicTokenHash; }
    public String getCreatedTransactionId() { return createdTransactionId; }
    public void setCreatedTransactionId(String createdTransactionId) { this.createdTransactionId = createdTransactionId; }
}
