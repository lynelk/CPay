package net.citotech.cito.api.v2.dto;

public class PaymentReferenceRequest {
    private String merchantNumber;
    private String reference;

    public String getMerchantNumber() {
        return merchantNumber;
    }

    public void setMerchantNumber(String merchantNumber) {
        this.merchantNumber = merchantNumber;
    }

    public String getReference() {
        return reference;
    }

    public void setReference(String reference) {
        this.reference = reference;
    }
}
