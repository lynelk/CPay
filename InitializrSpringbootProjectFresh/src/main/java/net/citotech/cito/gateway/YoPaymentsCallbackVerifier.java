package net.citotech.cito.gateway;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Audit C9: verifies the HMAC-SHA256 signature Yo! Payments (or a compatible provider
 * endpoint standing in for it) returns on its response, keyed by the merchant's configured
 * {@code apiKey} - the shared secret already stored in merchant channel credentials for the
 * {@code yo_payments} channel (see
 * {@code net.citotech.cito.merchant.MerchantChannelCredentialService#sandboxCredentials}
 * and {@code #validateRequiredCredentials}, which already require {@code apiKey} for this
 * channel today).
 *
 * There is no inbound webhook wired up for Yo! Payments anywhere in CPay - collect and
 * payout both resolve synchronously through {@link ProviderEndpointExecutionService}'s plain
 * HTTP call, and that call used to discard every response header outright. That meant a
 * tampered or forged response body would have been trusted as long as the HTTP status looked
 * like success. This closes that gap without inventing a new inbound endpoint or a new
 * credential field: it verifies the response we already received, using the secret we
 * already have.
 *
 * Verification is deliberately permissive when there is nothing to check against - no
 * {@code apiKey} configured for this merchant/environment, or the provider endpoint did not
 * send a signature header at all - so this never breaks an existing yo_payments integration
 * that predates this header. It only ever rejects a response when a signature WAS supplied
 * and did not match, i.e. it can only get stricter than "trust everything", never introduce a
 * new way for a legitimate call to fail.
 *
 * This is kept as a standalone stateless helper - rather than a method the execution service
 * reaches by looking up the {@code YoPaymentsAdapter} bean through
 * {@code PaymentChannelRegistry} - because {@link ProviderEndpointExecutionService} is the
 * only component that ever sees the provider's raw response headers/body, and injecting the
 * registry there would create a circular Spring dependency: {@code
 * ProviderEndpointExecutionService -> PaymentChannelRegistry -> List<PaymentChannelAdapter>
 * -> YoPaymentsAdapter -> ProviderEndpointExecutionService}. {@link YoPaymentsAdapter#verifyCallback}
 * delegates to this same helper so the check is also reachable through the adapter contract.
 */
final class YoPaymentsCallbackVerifier {
    static final String SIGNATURE_HEADER = "X-Yo-Signature";

    private YoPaymentsCallbackVerifier() {
    }

    static boolean verify(Map<String, String> responseHeaders, String responseBody, Map<String, String> channelConfig) {
        String secret = channelConfig == null ? null : channelConfig.get("apiKey");
        String provided = header(responseHeaders, SIGNATURE_HEADER);
        if (isBlank(secret) || isBlank(provided)) {
            // Nothing to verify against yet - fail open rather than break every existing
            // yo_payments call that was configured before this header existed.
            return true;
        }
        String expected = hmacSha256Base64(secret, responseBody == null ? "" : responseBody);
        return expected != null && constantTimeEquals(expected, provided.trim());
    }

    private static String header(Map<String, String> headers, String name) {
        if (headers == null) {
            return null;
        }
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(name)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static String hmacSha256Base64(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getEncoder().encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean constantTimeEquals(String left, String right) {
        return MessageDigest.isEqual(
            left.getBytes(StandardCharsets.UTF_8),
            right.getBytes(StandardCharsets.UTF_8));
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
