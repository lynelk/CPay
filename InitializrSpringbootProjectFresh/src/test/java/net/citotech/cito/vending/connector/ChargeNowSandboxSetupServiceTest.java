package net.citotech.cito.vending.connector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import net.citotech.cito.gateway.PaymentGatewayException;
import org.junit.jupiter.api.Test;

class ChargeNowSandboxSetupServiceTest {

    @Test
    void appliesCompletePartnerBundleAndReturnsRedactedManifest() {
        var configurations = mock(VendingConnectorConfigurationService.class);
        var correlations = mock(VendingCallbackCorrelationService.class);
        var service = new ChargeNowSandboxSetupService(configurations, correlations);

        when(configurations.readiness(7L, "CHARGENOW"))
                .thenReturn(Map.of("status", "READY_FOR_OEM_SANDBOX", "readyForSandbox", true));
        when(configurations.view(7L, "CHARGENOW"))
                .thenReturn(Map.of("connector_code", "CHARGENOW", "auth_value_configured", "YES"));
        when(configurations.operations(7L, "CHARGENOW"))
                .thenReturn(List.of(Map.of("command_type", "RELEASE_ASSET")));
        when(correlations.mapping(7L, "CHARGENOW"))
                .thenReturn(new VendingCallbackCorrelationService.Mapping("requestId", "providerId"));

        Map<String, Object> release =
                Map.of(
                        "httpMethod",
                        "POST",
                        "commandPath",
                        "/stations/release",
                        "requestTemplate",
                        "{\"stationId\":\"{{externalDeviceId}}\"}",
                        "completionMode",
                        "CALLBACK");
        Map<String, Object> query =
                Map.of(
                        "httpMethod",
                        "GET",
                        "commandPath",
                        "/stations/{{externalDeviceId}}/status",
                        "completionMode",
                        "IMMEDIATE");
        Map<String, Object> connector =
                Map.of(
                        "commandBaseUrl",
                        "https://sandbox.oem.example/api",
                        "authMode",
                        "BEARER",
                        "authValue",
                        "sandbox-token",
                        "callbackSecret",
                        "callback-secret",
                        "callbackSignatureMode",
                        "HMAC_SHA256_TS_NONCE_BODY",
                        "callbackEventTypeField",
                        "eventType",
                        "callbackEventIdField",
                        "eventId",
                        "callbackDeviceField",
                        "deviceId");
        Map<String, Object> body =
                Map.of(
                        "connector",
                        connector,
                        "operations",
                        Map.of("RELEASE_ASSET", release, "QUERY_STATUS", query),
                        "callbackCorrelation",
                        Map.of("callbackCommandReferenceField", "requestId"));

        Map<String, Object> result = service.apply(7L, body);

        verify(configurations)
                .save(
                        eq(7L),
                        eq("CHARGENOW"),
                        argThat(
                                saved ->
                                        "/stations/release".equals(saved.get("releasePath"))
                                                && "CALLBACK"
                                                        .equals(saved.get("releaseCompletionMode"))));
        verify(configurations).saveOperation(7L, "CHARGENOW", "RELEASE_ASSET", release);
        verify(configurations).saveOperation(7L, "CHARGENOW", "QUERY_STATUS", query);
        verify(correlations).save(7L, "CHARGENOW", "requestId", "");
        assertEquals("CHARGENOW", result.get("connectorCode"));
        assertEquals(
                "/api/v2/vending/device-callbacks/CHARGENOW/7", result.get("callbackPath"));
    }

    @Test
    void rejectsBundleWithoutAnyCallbackRentalCorrelation() {
        var configurations = mock(VendingConnectorConfigurationService.class);
        var correlations = mock(VendingCallbackCorrelationService.class);
        var service = new ChargeNowSandboxSetupService(configurations, correlations);

        Map<String, Object> body =
                Map.of(
                        "connector",
                        Map.of(
                                "commandBaseUrl",
                                "https://sandbox.oem.example/api",
                                "callbackSecret",
                                "secret"),
                        "operations",
                        Map.of(
                                "RELEASE_ASSET",
                                Map.of(
                                        "commandPath",
                                        "/release",
                                        "requestTemplate",
                                        "{\"stationId\":\"{{externalDeviceId}}\"}")));

        PaymentGatewayException error =
                assertThrows(PaymentGatewayException.class, () -> service.apply(7L, body));
        assertEquals(
                "ChargeNow callback setup must map a rental, command, or provider reference field",
                error.getMessage());
    }
}
