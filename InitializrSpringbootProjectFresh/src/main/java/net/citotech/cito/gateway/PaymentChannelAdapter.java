package net.citotech.cito.gateway;

import net.citotech.cito.Model.GateWayResponse;

/**
 * Adapter contract for adding payment channels without expanding the legacy
 * DoPayGateway switch/if chain.
 *
 * Existing MTN, Airtel, Safaricom, and Yo! Payments integrations can be
 * wrapped behind this interface incrementally. New channels such as
 * Flutterwave, Pesapal, Stripe, bank transfers, QR, USSD, or WhatsApp
 * payment links should start here.
 */
public interface PaymentChannelAdapter {
    /** Stable machine code, for example mtn_momo, airtel_money, or safaricom_mpesa. */
    String channelCode();

    /** Human-readable display name for admin and merchant portals. */
    String displayName();

    /** Country code, for example UG or KE. */
    String countryCode();

    /** Settlement or transaction currency, for example UGX or KES. */
    String currencyCode();

    GatewayCapabilities capabilities();

    /** Return true when this adapter can route the supplied customer account. */
    boolean supportsAccount(String accountIdentifier);

    GateWayResponse collect(PaymentGatewayRequest request);

    GateWayResponse payout(PaymentGatewayRequest request);

    GateWayResponse checkStatus(PaymentStatusRequest request);

    GatewayBalance getBalance(GatewayBalanceRequest request);
}

