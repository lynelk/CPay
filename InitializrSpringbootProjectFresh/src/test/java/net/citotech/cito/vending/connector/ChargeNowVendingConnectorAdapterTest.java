package net.citotech.cito.vending.connector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import net.citotech.cito.Common;
import net.citotech.cito.Model.HttpRequestResponse;
import net.citotech.cito.vending.connector.VendingConnectorConfigurationService.Contract;
import net.citotech.cito.vending.connector.VendingConnectorConfigurationService.Operation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ChargeNowVendingConnectorAdapterTest {

    @AfterEach
    void resetHttpExecutor() {
        Common.setOutboundHttpExecutor(null);
    }

    @Test
    void releaseUsesConfiguredOemOperationAndWaitsForCallbackConfirmation() {
        var configurations = mock(VendingConnectorConfigurationService.class);
        Contract contract = bearerContract();
        Operation operation =
                new Operation(
                        "RELEASE_ASSET",
                        "POST",
                        "/stations/release",
                        "{\"stationId\":\"{{externalDeviceId}}\",\"requestId\":\"{{commandReference}}\",\"rental\":\"{{rentalReference}}\"}",
                        "Idempotency-Key",
                        "ok",
                        "true",
                        "requestId",
                        "message",
                        "CALLBACK");
        when(configurations.require(7L, "CHARGENOW")).thenReturn(contract);
        when(configurations.requireOperation(7L, "CHARGENOW", "RELEASE_ASSET"))
                .thenReturn(operation);

        AtomicReference<String> method = new AtomicReference<>();
        AtomicReference<String> url = new AtomicReference<>();
        AtomicReference<String> body = new AtomicReference<>();
        AtomicReference<Map<String, String>> headers = new AtomicReference<>();
        Common.setOutboundHttpExecutor(
                (m, u, d, h) -> {
                    method.set(m);
                    url.set(u);
                    body.set(d);
                    headers.set(h);
                    HttpRequestResponse response = new HttpRequestResponse();
                    response.setStatusCode(202);
                    response.setResponse(
                            "{\"ok\":true,\"requestId\":\"oem-123\",\"message\":\"queued\"}");
                    return response;
                });

        var adapter =
                new ChargeNowVendingConnectorAdapter(configurations, new ObjectMapper());
        var result =
                adapter.execute(
                        new VendingConnectorAdapter.VendingCommand(
                                7L,
                                55L,
                                "CAB-99",
                                "VEND-RELEASE-R1",
                                "RELEASE_ASSET",
                                Map.of("rentalReference", "R1")));

        assertTrue(result.success());
        assertEquals("ACCEPTED", result.status());
        assertEquals("oem-123", result.providerReference());
        assertEquals("POST", method.get());
        assertEquals("https://oem.example/api/stations/release", url.get());
        assertTrue(body.get().contains("\"stationId\":\"CAB-99\""));
        assertTrue(body.get().contains("\"rental\":\"R1\""));
        assertEquals("Bearer oem-token", headers.get().get("Authorization"));
        assertEquals("VEND-RELEASE-R1", headers.get().get("Idempotency-Key"));
    }

    @Test
    void hmacHexAuthenticationAndTemplatedImmediateProbeAreSupported() {
        var configurations = mock(VendingConnectorConfigurationService.class);
        Contract contract =
                new Contract(
                        7L,
                        "CHARGENOW",
                        "https://oem.example/api",
                        "HMAC_SHA256_TS_BODY",
                        "X-OEM-Signature",
                        "X-OEM-Timestamp",
                        "X-OEM-Key",
                        "HEX",
                        "{{timestamp}}|{{method}}|{{path}}|{{body}}",
                        "public-key",
                        "secret-key",
                        "callback-secret",
                        "STATIC_TOKEN_HEADER",
                        "BASE64",
                        "X-OEM-Callback-Token",
                        "",
                        "",
                        "eventType",
                        "eventId",
                        "deviceId",
                        "rentalReference",
                        "assetCode",
                        "availableCount");
        Operation operation =
                new Operation(
                        "QUERY_STATUS",
                        "GET",
                        "/stations/{{externalDeviceId}}/status",
                        "",
                        "",
                        "",
                        "",
                        "",
                        "",
                        "IMMEDIATE");
        when(configurations.require(7L, "CHARGENOW")).thenReturn(contract);
        when(configurations.requireOperation(7L, "CHARGENOW", "QUERY_STATUS"))
                .thenReturn(operation);

        AtomicReference<String> url = new AtomicReference<>();
        AtomicReference<Map<String, String>> headers = new AtomicReference<>();
        Common.setOutboundHttpExecutor(
                (m, u, d, h) -> {
                    url.set(u);
                    headers.set(h);
                    HttpRequestResponse response = new HttpRequestResponse();
                    response.setStatusCode(200);
                    response.setResponse("{\"online\":true}");
                    return response;
                });

        var adapter =
                new ChargeNowVendingConnectorAdapter(configurations, new ObjectMapper());
        var result =
                adapter.execute(
                        new VendingConnectorAdapter.VendingCommand(
                                7L,
                                55L,
                                "CAB-99",
                                "VEND-PROBE-1",
                                "QUERY_STATUS",
                                Map.of()));

        assertTrue(result.success());
        assertEquals("COMPLETED", result.status());
        assertEquals("https://oem.example/api/stations/CAB-99/status", url.get());
        assertEquals("public-key", headers.get().get("X-OEM-Key"));
        assertTrue(headers.get().get("X-OEM-Signature").matches("[0-9a-f]{64}"));
        assertTrue(headers.get().containsKey("X-OEM-Timestamp"));
    }

    private Contract bearerContract() {
        return new Contract(
                7L,
                "CHARGENOW",
                "https://oem.example/api",
                "BEARER",
                "",
                "",
                "",
                "BASE64",
                "",
                "oem-token",
                "",
                "callback-secret",
                "HMAC_SHA256_TS_NONCE_BODY",
                "BASE64",
                "X-CPay-Vending-Signature",
                "X-CPay-Vending-Timestamp",
                "X-CPay-Vending-Nonce",
                "eventType",
                "eventId",
                "deviceId",
                "rentalReference",
                "assetCode",
                "availableCount");
    }
}
