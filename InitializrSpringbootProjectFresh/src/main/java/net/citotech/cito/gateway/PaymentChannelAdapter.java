package net.citotech.cito.gateway;

import java.util.Map;
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

    /**
     * Verifies that a provider response/callback is authentic before its result is trusted
     * (audit C9). Most adapters here are driven through synchronous request/response HTTP
     * calls (see {@code ProviderEndpointExecutionService}) and carry no verifiable signature
     * material from the provider, so the default implementation is a permissive no-op that
     * preserves existing behaviour for every adapter that does not override it.
     *
     * Adapters that do have signature material available - a shared secret already present
     * in their merchant channel credentials, and a signature header the provider actually
     * sends back - should override this and reject payloads that fail verification rather
     * than silently trusting an unauthenticated body. See {@code YoPaymentsAdapter} and
     * {@code YoPaymentsCallbackVerifier} for the first real implementation.
     *
     * @param responseHeaders headers returned alongside the provider response (never null, may be empty)
     * @param responseBody raw response body returned by the provider
     * @param channelConfig resolved channel configuration/credential fields for this call (never null, may be empty)
     * @return true when the response is verified authentic, or when this adapter has no
     *         verification material to check against; false only when verification was
     *         attempted and the signature did not match
     */
    default boolean verifyCallback(Map<String, String> responseHeaders, String responseBody, Map<String, String> channelConfig) {
        return true;
    }
}

