package net.citotech.cito.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

/**
 * Covers audit C9: Yo! Payments has no inbound webhook wired into CPay, so the only place
 * CPay ever sees provider-controlled data for this channel is the raw HTTP response that
 * {@link ProviderEndpointExecutionService} reads back from the configured collect/payout
 * endpoint. That response's headers used to be discarded entirely, so a tampered response
 * body would have been trusted outright as long as the HTTP status looked like success.
 *
 * {@link YoPaymentsCallbackVerifier} closes that gap using the apiKey already required for
 * this channel (see MerchantChannelCredentialService) as an HMAC-SHA256 secret, verified
 * against a signature header the provider may return. It is deliberately permissive when
 * there is no secret configured or no header was sent, so it never breaks an existing
 * integration that predates this header.
 */
class YoPaymentsCallbackVerifierTest {

    @Test
    void acceptsAResponseWhoseSignatureMatchesTheConfiguredApiKey() {
        Map<String, String> headers = new HashMap<>();
        headers.put(YoPaymentsCallbackVerifier.SIGNATURE_HEADER, sign("secret-key", "{\"status\":\"ok\"}"));
        Map<String, String> config = Map.of("apiKey", "secret-key");

        boolean verified = YoPaymentsCallbackVerifier.verify(headers, "{\"status\":\"ok\"}", config);

        assertThat(verified).isTrue();
    }

    @Test
    void rejectsAResponseWhoseSignatureDoesNotMatch() {
        Map<String, String> headers = new HashMap<>();
        headers.put(YoPaymentsCallbackVerifier.SIGNATURE_HEADER, sign("a-different-secret", "{\"status\":\"ok\"}"));
        Map<String, String> config = Map.of("apiKey", "secret-key");

        boolean verified = YoPaymentsCallbackVerifier.verify(headers, "{\"status\":\"ok\"}", config);

        assertThat(verified).isFalse();
    }

    @Test
    void rejectsAResponseWhoseBodyWasTamperedWithAfterSigning() {
        Map<String, String> headers = new HashMap<>();
        headers.put(YoPaymentsCallbackVerifier.SIGNATURE_HEADER, sign("secret-key", "{\"status\":\"ok\"}"));
        Map<String, String> config = Map.of("apiKey", "secret-key");

        boolean verified = YoPaymentsCallbackVerifier.verify(headers, "{\"status\":\"tampered\"}", config);

        assertThat(verified).isFalse();
    }

    @Test
    void isPermissiveWhenNoApiKeyIsConfiguredYet() {
        // A merchant channel that predates any signing secret must not suddenly start failing.
        Map<String, String> headers = new HashMap<>();
        headers.put(YoPaymentsCallbackVerifier.SIGNATURE_HEADER, "garbage-signature");
        Map<String, String> config = Map.of();

        boolean verified = YoPaymentsCallbackVerifier.verify(headers, "{\"status\":\"ok\"}", config);

        assertThat(verified).isTrue();
    }

    @Test
    void isPermissiveWhenTheProviderDidNotSendASignatureHeader() {
        // A provider endpoint that hasn't started sending this header yet must not break.
        Map<String, String> config = Map.of("apiKey", "secret-key");

        boolean verified = YoPaymentsCallbackVerifier.verify(new HashMap<>(), "{\"status\":\"ok\"}", config);

        assertThat(verified).isTrue();
    }

    @Test
    void looksUpTheSignatureHeaderCaseInsensitively() {
        Map<String, String> headers = new HashMap<>();
        headers.put("x-yo-signature", sign("secret-key", "{\"status\":\"ok\"}"));
        Map<String, String> config = Map.of("apiKey", "secret-key");

        boolean verified = YoPaymentsCallbackVerifier.verify(headers, "{\"status\":\"ok\"}", config);

        assertThat(verified).isTrue();
    }

    @Test
    void toleratesNullHeadersAndNullConfigWithoutThrowing() {
        assertThat(YoPaymentsCallbackVerifier.verify(null, "body", null)).isTrue();
    }

    private static String sign(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getEncoder().encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
