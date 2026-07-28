package net.citotech.cito.gateway;

/** Balance returned by a payment channel adapter. */
public class GatewayBalance {
    private final String channelCode;
    private final String currencyCode;
    private final Double availableBalance;
    private final String providerReference;

    public GatewayBalance(String channelCode, String currencyCode, Double availableBalance, String providerReference) {
        this.channelCode = channelCode;
        this.currencyCode = currencyCode;
        this.availableBalance = availableBalance;
        this.providerReference = providerReference;
    }

    public String getChannelCode() {
        return channelCode;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public Double getAvailableBalance() {
        return availableBalance;
    }

    public String getProviderReference() {
        return providerReference;
    }
}

