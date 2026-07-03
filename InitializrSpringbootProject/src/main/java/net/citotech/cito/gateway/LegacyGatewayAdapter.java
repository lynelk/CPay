package net.citotech.cito.gateway;

import net.citotech.cito.Model.GateWayResponse;

/** Base class for wrappers around existing provider implementations. */
public abstract class LegacyGatewayAdapter implements PaymentChannelAdapter {
    private final String channelCode;
    private final String displayName;
    private final String countryCode;
    private final String currencyCode;
    private final String legacyGatewayId;
    private final String[] supportedPrefixes;

    protected LegacyGatewayAdapter(String channelCode,
                                   String displayName,
                                   String countryCode,
                                   String currencyCode,
                                   String legacyGatewayId,
                                   String... supportedPrefixes) {
        this.channelCode = channelCode;
        this.displayName = displayName;
        this.countryCode = countryCode;
        this.currencyCode = currencyCode;
        this.legacyGatewayId = legacyGatewayId;
        this.supportedPrefixes = supportedPrefixes == null ? new String[0] : supportedPrefixes;
    }

    @Override
    public String channelCode() {
        return channelCode;
    }

    @Override
    public String displayName() {
        return displayName;
    }

    @Override
    public String countryCode() {
        return countryCode;
    }

    @Override
    public String currencyCode() {
        return currencyCode;
    }

    public String legacyGatewayId() {
        return legacyGatewayId;
    }

    @Override
    public GatewayCapabilities capabilities() {
        return GatewayCapabilities.mobileMoneyDefaults();
    }

    @Override
    public boolean supportsAccount(String accountIdentifier) {
        if (accountIdentifier == null) {
            return false;
        }
        String trimmed = accountIdentifier.trim();
        for (String prefix : supportedPrefixes) {
            if (trimmed.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public GateWayResponse collect(PaymentGatewayRequest request) {
        throw new PaymentGatewayException("Legacy adapter collect is routed by PaymentOrchestrationService");
    }

    @Override
    public GateWayResponse payout(PaymentGatewayRequest request) {
        throw new PaymentGatewayException("Legacy adapter payout is routed by PaymentOrchestrationService");
    }

    @Override
    public GateWayResponse checkStatus(PaymentStatusRequest request) {
        throw new PaymentGatewayException("Legacy adapter status check is routed by PaymentOrchestrationService");
    }

    @Override
    public GatewayBalance getBalance(GatewayBalanceRequest request) {
        throw new PaymentGatewayException("Legacy adapter balance check is routed by PaymentOrchestrationService");
    }
}
