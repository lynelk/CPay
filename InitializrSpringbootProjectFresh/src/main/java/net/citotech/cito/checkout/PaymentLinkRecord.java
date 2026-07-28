package net.citotech.cito.checkout;

import java.math.BigDecimal;
import java.time.Instant;

public class PaymentLinkRecord {
    private long id;
    private long merchantId;
    private String merchantNumber;
    private String linkReference;
    private BigDecimal amount;
    private String currency;
    private String country;
    private String description;
    private String callbackUrl;
    private String status;
    private Instant expiresAt;

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public long getMerchantId() { return merchantId; }
    public void setMerchantId(long merchantId) { this.merchantId = merchantId; }
    public String getMerchantNumber() { return merchantNumber; }
    public void setMerchantNumber(String merchantNumber) { this.merchantNumber = merchantNumber; }
    public String getLinkReference() { return linkReference; }
    public void setLinkReference(String linkReference) { this.linkReference = linkReference; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getCallbackUrl() { return callbackUrl; }
    public void setCallbackUrl(String callbackUrl) { this.callbackUrl = callbackUrl; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
}
