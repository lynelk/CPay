package net.citotech.cito.api.v2.dto;

public class PaymentLinkResponse {
    private String linkReference;
    private String checkoutUrl;
    private String status;
    private String amount;
    private String currency;
    private String description;
    private String expiresAt;

    public String getLinkReference() { return linkReference; }
    public void setLinkReference(String linkReference) { this.linkReference = linkReference; }
    public String getCheckoutUrl() { return checkoutUrl; }
    public void setCheckoutUrl(String checkoutUrl) { this.checkoutUrl = checkoutUrl; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getAmount() { return amount; }
    public void setAmount(String amount) { this.amount = amount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getExpiresAt() { return expiresAt; }
    public void setExpiresAt(String expiresAt) { this.expiresAt = expiresAt; }
}
