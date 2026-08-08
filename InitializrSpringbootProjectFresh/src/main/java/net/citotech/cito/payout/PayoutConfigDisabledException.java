package net.citotech.cito.payout;

import net.citotech.cito.gateway.PaymentGatewayException;

/**
 * Thrown when the {@code payout-controls-config} feature flag is off. Raised before any database
 * work so the admin surface fails closed - a SQL-free rollout gate that mirrors the other S5/S6
 * pilot surfaces.
 */
public class PayoutConfigDisabledException extends PaymentGatewayException {
    public PayoutConfigDisabledException(String message) {
        super(message);
    }
}
