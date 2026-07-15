package net.citotech.cito.gateway;

/** Request object used by channel adapters for balance checks. */
public class GatewayBalanceRequest {
    private final String merchantNumber;
    private final String account;

    public GatewayBalanceRequest(String merchantNumber, String account) {
        this.merchantNumber = merchantNumber;
        this.account = account;
    }

    public String getMerchantNumber() {
        return merchantNumber;
    }

    public String getAccount() {
        return account;
    }
}

