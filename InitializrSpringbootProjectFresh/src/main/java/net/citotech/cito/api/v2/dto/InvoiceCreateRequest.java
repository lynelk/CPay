package net.citotech.cito.api.v2.dto;

public class InvoiceCreateRequest {
    private String merchantNumber;
    private String amount;
    private String currency;
    private String description;
    private String reference;
    private String payerName;
    private String payerContact;
    private String dueDate;

    public String getMerchantNumber() { return merchantNumber; }
    public void setMerchantNumber(String merchantNumber) { this.merchantNumber = merchantNumber; }
    public String getAmount() { return amount; }
    public void setAmount(String amount) { this.amount = amount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }
    public String getPayerName() { return payerName; }
    public void setPayerName(String payerName) { this.payerName = payerName; }
    public String getPayerContact() { return payerContact; }
    public void setPayerContact(String payerContact) { this.payerContact = payerContact; }
    public String getDueDate() { return dueDate; }
    public void setDueDate(String dueDate) { this.dueDate = dueDate; }
}
