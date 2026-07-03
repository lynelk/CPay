package net.citotech.cito.gateway;

/**
 * Describes the operations supported by a payment channel.
 *
 * This small model is intentionally independent from the legacy settings table
 * so new channels can advertise their supported operations before the older
 * hardcoded routing paths are fully refactored.
 */
public class GatewayCapabilities {
    private final boolean collections;
    private final boolean payouts;
    private final boolean balanceCheck;
    private final boolean statusCheck;
    private final boolean refunds;
    private final boolean callbacks;

    public GatewayCapabilities(boolean collections,
                               boolean payouts,
                               boolean balanceCheck,
                               boolean statusCheck,
                               boolean refunds,
                               boolean callbacks) {
        this.collections = collections;
        this.payouts = payouts;
        this.balanceCheck = balanceCheck;
        this.statusCheck = statusCheck;
        this.refunds = refunds;
        this.callbacks = callbacks;
    }

    public static GatewayCapabilities mobileMoneyDefaults() {
        return new GatewayCapabilities(true, true, true, true, false, true);
    }

    public boolean supportsCollections() {
        return collections;
    }

    public boolean supportsPayouts() {
        return payouts;
    }

    public boolean supportsBalanceCheck() {
        return balanceCheck;
    }

    public boolean supportsStatusCheck() {
        return statusCheck;
    }

    public boolean supportsRefunds() {
        return refunds;
    }

    public boolean supportsCallbacks() {
        return callbacks;
    }
}
