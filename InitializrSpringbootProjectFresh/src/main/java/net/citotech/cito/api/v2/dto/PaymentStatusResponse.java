package net.citotech.cito.api.v2.dto;

public class PaymentStatusResponse {
    private String reference;
    private String status;
    private String channel;
    private String providerReference;
    private String message;

    public PaymentStatusResponse() {
    }

    public PaymentStatusResponse(String reference, String status, String channel, String providerReference, String message) {
        this.reference = reference;
        this.status = status;
        this.channel = channel;
        this.providerReference = providerReference;
        this.message = message;
    }

    public String getReference() {
        return reference;
    }

    public void setReference(String reference) {
        this.reference = reference;
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

    public String getProviderReference() {
        return providerReference;
    }

    public void setProviderReference(String providerReference) {
        this.providerReference = providerReference;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}

