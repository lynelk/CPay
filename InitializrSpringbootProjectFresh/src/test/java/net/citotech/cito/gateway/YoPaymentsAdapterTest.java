package net.citotech.cito.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import net.citotech.cito.Model.GateWayResponse;
import org.junit.jupiter.api.Test;

class YoPaymentsAdapterTest {

    @Test
    void advertisesChannelMetadata() {
        YoPaymentsAdapter adapter = new YoPaymentsAdapter(mock(ProviderEndpointExecutionService.class));

        assertThat(adapter.channelCode()).isEqualTo("yo_payments");
        assertThat(adapter.displayName()).isEqualTo("Yo! Payments");
        assertThat(adapter.countryCode()).isEqualTo("UG");
        assertThat(adapter.currencyCode()).isEqualTo("UGX");
        assertThat(adapter.legacyGatewayId()).isEqualTo(LegacyGatewayIds.YO_PAYMENTS);
        assertThat(adapter.capabilities().supportsCollections()).isTrue();
        assertThat(adapter.capabilities().supportsPayouts()).isTrue();
    }

    @Test
    void isOnlySelectedByExplicitChannelNotMsisdnPrefix() {
        YoPaymentsAdapter adapter = new YoPaymentsAdapter(mock(ProviderEndpointExecutionService.class));

        assertThat(adapter.supportsAccount("256770000000")).isFalse();
        assertThat(adapter.supportsAccount("256750000000")).isFalse();
    }

    @Test
    void collectDelegatesToProviderEndpointExecutionService() {
        ProviderEndpointExecutionService executionService = mock(ProviderEndpointExecutionService.class);
        GateWayResponse response = new GateWayResponse();
        response.setStatus("SUCCESS");
        when(executionService.execute(eq("yo_payments"), eq("Yo! Payments"), eq("COLLECT"), any(PaymentGatewayRequest.class)))
            .thenReturn(response);
        YoPaymentsAdapter adapter = new YoPaymentsAdapter(executionService);
        PaymentGatewayRequest request = new PaymentGatewayRequest("M1", "256770000000", 1000.0, "REF-1", "test", "cb", null);

        GateWayResponse result = adapter.collect(request);

        assertThat(result.getStatus()).isEqualTo("SUCCESS");
        verify(executionService).execute("yo_payments", "Yo! Payments", "COLLECT", request);
    }

    @Test
    void payoutDelegatesToProviderEndpointExecutionService() {
        ProviderEndpointExecutionService executionService = mock(ProviderEndpointExecutionService.class);
        GateWayResponse response = new GateWayResponse();
        response.setStatus("SUCCESS");
        when(executionService.execute(eq("yo_payments"), eq("Yo! Payments"), eq("PAYOUT"), any(PaymentGatewayRequest.class)))
            .thenReturn(response);
        YoPaymentsAdapter adapter = new YoPaymentsAdapter(executionService);
        PaymentGatewayRequest request = new PaymentGatewayRequest("M1", "256770000000", 1000.0, "REF-2", "test", "cb", null);

        GateWayResponse result = adapter.payout(request);

        assertThat(result.getStatus()).isEqualTo("SUCCESS");
        verify(executionService).execute("yo_payments", "Yo! Payments", "PAYOUT", request);
    }

    // Audit C9: PaymentChannelAdapter#verifyCallback defaults to a no-op for every other
    // adapter, but Yo! Payments has real signature material (its configured apiKey) available
    // to check a provider response against, so it overrides the default. These tests cover
    // that override end-to-end through the adapter, not just the underlying verifier helper.

    @Test
    void verifyCallbackAcceptsAResponseSignedWithTheConfiguredApiKey() {
        YoPaymentsAdapter adapter = new YoPaymentsAdapter(mock(ProviderEndpointExecutionService.class));
        String body = "{\"status\":\"ok\"}";
        Map<String, String> headers = new HashMap<>();
        headers.put("X-Yo-Signature", sign("yo-api-key", body));

        boolean verified = adapter.verifyCallback(headers, body, Map.of("apiKey", "yo-api-key"));

        assertThat(verified).isTrue();
    }

    @Test
    void verifyCallbackRejectsAResponseSignedWithTheWrongSecret() {
        YoPaymentsAdapter adapter = new YoPaymentsAdapter(mock(ProviderEndpointExecutionService.class));
        String body = "{\"status\":\"ok\"}";
        Map<String, String> headers = new HashMap<>();
        headers.put("X-Yo-Signature", sign("wrong-key", body));

        boolean verified = adapter.verifyCallback(headers, body, Map.of("apiKey", "yo-api-key"));

        assertThat(verified).isFalse();
    }

    @Test
    void verifyCallbackIsPermissiveWhenNoSignatureMaterialIsAvailable() {
        YoPaymentsAdapter adapter = new YoPaymentsAdapter(mock(ProviderEndpointExecutionService.class));

        boolean verified = adapter.verifyCallback(new HashMap<>(), "{\"status\":\"ok\"}", Map.of());

        assertThat(verified).isTrue();
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
