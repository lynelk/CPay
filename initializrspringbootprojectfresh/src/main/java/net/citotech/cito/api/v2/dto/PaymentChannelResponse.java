package net.citotech.cito.api.v2.dto;

public class PaymentChannelResponse {
    private String channelCode;
    private String displayName;
    private String countryCode;
    private String currencyCode;
    private boolean collections;
    private boolean payouts;
    private boolean balanceCheck;
    private boolean statusCheck;
    private boolean refunds;
    private boolean callbacks;

    public String getChannelCode() {
        return channelCode;
    }

    public void setChannelCode(String channelCode) {
        this.channelCode = channelCode;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public void setCountryCode(String countryCode) {
        this.countryCode = countryCode;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public void setCurrencyCode(String currencyCode) {
        this.currencyCode = currencyCode;
    }

    public boolean isCollections() {
        return collections;
    }

    public void setCollections(boolean collections) {
        this.collections = collections;
    }

    public boolean isPayouts() {
        return payouts;
    }

    public void setPayouts(boolean payouts) {
        this.payouts = payouts;
    }

    public boolean isBalanceCheck() {
        return balanceCheck;
    }

    public void setBalanceCheck(boolean balanceCheck) {
        this.balanceCheck = balanceCheck;
    }

    public boolean isStatusCheck() {
        return statusCheck;
    }

    public void setStatusCheck(boolean statusCheck) {
        this.statusCheck = statusCheck;
    }

    public boolean isRefunds() {
        return refunds;
    }

    public void setRefunds(boolean refunds) {
        this.refunds = refunds;
    }

    public boolean isCallbacks() {
        return callbacks;
    }

    public void setCallbacks(boolean callbacks) {
        this.callbacks = callbacks;
    }
}

