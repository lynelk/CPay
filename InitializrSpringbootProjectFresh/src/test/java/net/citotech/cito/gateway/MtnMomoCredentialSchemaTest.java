package net.citotech.cito.gateway;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MtnMomoCredentialSchemaTest {

    @Test
    void acceptsOfficialSandboxScopeWithSeparateProductCredentials() {
        assertThatCode(
                        () ->
                                MtnMomoCredentialSchema.validate(
                                        credentials(), "SANDBOX", "UG", "EUR"))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsUgxForMtnSandbox() {
        Map<String, Object> credentials = credentials();
        credentials.put("baseCurrency", "UGX");

        assertThatThrownBy(
                        () ->
                                MtnMomoCredentialSchema.validate(
                                        credentials, "SANDBOX", "UG", "UGX"))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("must use EUR");
    }

    @Test
    void rejectsCallbackUrlOnAHostDifferentFromRegisteredApiUserHost() {
        Map<String, Object> credentials = credentials();
        credentials.put("callbackUrl", "https://wrong.example/callbacks/mtn");

        assertThatThrownBy(
                        () ->
                                MtnMomoCredentialSchema.validate(
                                        credentials, "SANDBOX", "UG", "EUR"))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("must match");
    }

    private Map<String, Object> credentials() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("baseUrl", "https://sandbox.momodeveloper.mtn.com");
        values.put("targetEnvironment", "sandbox");
        values.put("baseCurrency", "EUR");
        values.put("callbackHost", "pay.example.com");
        values.put("callbackUrl", "https://pay.example.com/api/v2/provider-callbacks/mtn");
        values.put("collectionApiUser", "collection-user");
        values.put("collectionApiKey", "collection-key");
        values.put("collectionSubscriptionKey", "collection-subscription");
        values.put("disbursementApiUser", "disbursement-user");
        values.put("disbursementApiKey", "disbursement-key");
        values.put("disbursementSubscriptionKey", "disbursement-subscription");
        return values;
    }
}
