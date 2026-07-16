package net.citotech.cito.api.v2.dto;

public class PaymentLinkCreateRequest {
    private String merchantNumber;
    private String amount;
    private String currency;
    private String country;
    private String description;
    private String reference;
    private String callbackUrl;
    private String expiresAt;

    public String getMerchantNumber() { return merchantNumber; }
    public void setMerchantNumber(String merchantNumber) { this.merchantNumber = merchantNumber; }
    public String getAmount() { return amount; }
    public void setAmount(String amount) { this.amount = amount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }
    public String getCallbackUrl() { return callbackUrl; }
    public void setCallbackUrl(String callbackUrl) { this.callbackUrl = callbackUrl; }
    public String getExpiresAt() { return expiresAt; }
    public void setExpiresAt(String expiresAt) { this.expiresAt = expiresAt; }
}
