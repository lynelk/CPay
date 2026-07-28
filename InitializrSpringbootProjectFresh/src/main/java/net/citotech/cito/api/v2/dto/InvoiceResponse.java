package net.citotech.cito.api.v2.dto;

public class InvoiceResponse {
    private String reference;
    private String status;
    private String amount;
    private String currency;
    private String description;
    private String payerName;
    private String payerContact;
    private String dueDate;
    private String payUrl;
    private String createdTransactionId;

    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getAmount() { return amount; }
    public void setAmount(String amount) { this.amount = amount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getPayerName() { return payerName; }
    public void setPayerName(String payerName) { this.payerName = payerName; }
    public String getPayerContact() { return payerContact; }
    public void setPayerContact(String payerContact) { this.payerContact = payerContact; }
    public String getDueDate() { return dueDate; }
    public void setDueDate(String dueDate) { this.dueDate = dueDate; }
    public String getPayUrl() { return payUrl; }
    public void setPayUrl(String payUrl) { this.payUrl = payUrl; }
    public String getCreatedTransactionId() { return createdTransactionId; }
    public void setCreatedTransactionId(String createdTransactionId) { this.createdTransactionId = createdTransactionId; }
}
