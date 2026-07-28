package net.citotech.cito.api.v2.dto;

public class PaymentResult {
    private String reference;
    private String transactionId;
    private String status;
    private String channel;
    private String environment;
    private String currency;
    private String message;
    private Object providerResponse;

    public String getReference() {
        return reference;
    }

    public void setReference(String reference) {
        this.reference = reference;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public String getEnvironment() {
        return environment;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Object getProviderResponse() {
        return providerResponse;
    }

    public void setProviderResponse(Object providerResponse) {
        this.providerResponse = providerResponse;
    }
}

