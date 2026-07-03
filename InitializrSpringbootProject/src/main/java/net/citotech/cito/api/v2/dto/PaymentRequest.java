package net.citotech.cito.api.v2.dto;

import java.util.HashMap;
import java.util.Map;

public class PaymentRequest {
    private String merchantNumber;
    private String amount;
    private String currency;
    private String country;
    private String channel;
    private PaymentPartyRequest payer;
    private PaymentPartyRequest payee;
    private String reference;
    private String description;
    private String callbackUrl;
    private Map<String, String> metadata = new HashMap<>();

    public String getMerchantNumber() {
        return merchantNumber;
    }

    public void setMerchantNumber(String merchantNumber) {
        this.merchantNumber = merchantNumber;
    }

    public String getAmount() {
        return amount;
    }

    public void setAmount(String amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public PaymentPartyRequest getPayer() {
        return payer;
    }

    public void setPayer(PaymentPartyRequest payer) {
        this.payer = payer;
    }

    public PaymentPartyRequest getPayee() {
        return payee;
    }

    public void setPayee(PaymentPartyRequest payee) {
        this.payee = payee;
    }

    public String getReference() {
        return reference;
    }

    public void setReference(String reference) {
        this.reference = reference;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCallbackUrl() {
        return callbackUrl;
    }

    public void setCallbackUrl(String callbackUrl) {
        this.callbackUrl = callbackUrl;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata == null ? new HashMap<>() : metadata;
    }
}
