package net.citotech.cito.gateway;

/** Request object used by channel adapters for status checks. */
public class PaymentStatusRequest {
    private final String merchantNumber;
    private final String reference;
    private final String providerReference;

    public PaymentStatusRequest(String merchantNumber, String reference, String providerReference) {
        this.merchantNumber = merchantNumber;
        this.reference = reference;
        this.providerReference = providerReference;
    }

    public String getMerchantNumber() {
        return merchantNumber;
    }

    public String getReference() {
        return reference;
    }

    public String getProviderReference() {
        return providerReference;
    }
}
