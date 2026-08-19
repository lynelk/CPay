package net.citotech.cito.vending.connector;

import java.util.Locale;
import net.citotech.cito.gateway.PaymentGatewayException;

/**
 * How a vending connector verifies inbound device/rental callbacks before they may advance
 * consequential lifecycle steps.
 *
 * <p>The first four modes reuse the existing cryptographic callback security paths. {@link
 * #VERIFY_BY_PROVIDER_QUERY} covers vendors (ChargeNow included) whose public callback contract
 * does not provide a verifiable signature: the raw callback is persisted and deduplicated first,
 * then the provider is re-queried over its authenticated connector channel and the callback claim
 * is compared with authoritative provider state before any state change.
 */
public enum VendingCallbackVerificationMode {
    HMAC_SHA256_TS_NONCE_BODY,
    HMAC_SHA256_TS_BODY,
    HMAC_SHA256_BODY,
    STATIC_TOKEN_HEADER,
    VERIFY_BY_PROVIDER_QUERY;

    public static VendingCallbackVerificationMode require(String value) {
        if (value == null || value.isBlank()) {
            throw new PaymentGatewayException("Vending callback verification mode is required");
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new PaymentGatewayException(
                    "Unsupported vending callback verification mode: " + value);
        }
    }

    public String value() {
        return name();
    }
}
