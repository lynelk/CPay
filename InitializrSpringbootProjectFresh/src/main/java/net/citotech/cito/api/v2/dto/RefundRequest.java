package net.citotech.cito.api.v2.dto;

public class RefundRequest {
    private String merchantNumber;
    private String originalReference;
    private String reference;
    private String amount;
    private String reason;

    public String getMerchantNumber() {
        return merchantNumber;
    }

    public void setMerchantNumber(String merchantNumber) {
        this.merchantNumber = merchantNumber;
    }

    public String getOriginalReference() {
        return originalReference;
    }

    public void setOriginalReference(String originalReference) {
        this.originalReference = originalReference;
    }

    public String getReference() {
        return reference;
    }

    public void setReference(String reference) {
        this.reference = reference;
    }

    /** Optional - omit for a full refund of whatever remains unrefunded. */
    public String getAmount() {
        return amount;
    }

    public void setAmount(String amount) {
        this.amount = amount;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
