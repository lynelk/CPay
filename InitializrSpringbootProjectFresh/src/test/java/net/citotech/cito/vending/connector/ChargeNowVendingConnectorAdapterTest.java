package net.citotech.cito.vending.connector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import net.citotech.cito.Common;
import net.citotech.cito.Model.HttpRequestResponse;
import net.citotech.cito.vending.connector.VendingConnectorConfigurationService.Contract;
import net.citotech.cito.vending.connector.VendingConnectorConfigurationService.Operation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * §54 mandatory ChargeNow adapter tests covering Basic auth, all five operations, bodyless
 * GET/POST, structural callbackURL encoding, data.tradeNo extraction, missing tradeNo, invalid
 * JSON, HTTP failure modes and provider timeout.
 */
class ChargeNowVendingConnectorAdapterTest {

    @AfterEach
    void resetHttpExecutor() {
        Common.setOutboundHttpExecutor(null);
    }

    @Test
    void basicAuthReleaseAssetUsesBodylessPostWithStructuralUrl() {
        var configurations = mock(VendingConnectorConfigurationService.class);
        Contract contract = basicAuthContract();
        Operation operation =
                new Operation(
                        "RELEASE_ASSET",
                        "POST",
                        "/rent/order/create",
                        null,
                        "",
                        "code",
                        "0",
                        "data.tradeNo",
                        "msg",
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
                    HttpRequestResponse resp = new HttpRequestResponse();
                    resp.setStatusCode(200);
                    resp.setResponse(
                            "{\"code\":0,\"data\":{\"tradeNo\":\"23022111552701091626\"}}");
                    return resp;
                });

        var adapter = new ChargeNowVendingConnectorAdapter(configurations, new ObjectMapper());
        var result =
                adapter.execute(
                        new VendingConnectorAdapter.VendingCommand(
                                7L,
                                55L,
                                "CAB-99",
                                "VEND-RELEASE-R1",
                                "RELEASE_ASSET",
                                Map.of(
                                        "rentalReference",
                                        "R1",
                                        "callbackUrl",
                                        "https://cpay.example/callback?merchant=7&event=release")));

        assertTrue(result.success());
        assertEquals("23022111552701091626", result.providerReference());
        assertEquals("POST", method.get());
        // bodyless: request_template is null → empty body
        assertEquals("", body.get());
        // Basic auth header
        assertTrue(headers.get().get("Authorization").startsWith("Basic "));
        // URL must contain deviceId and callbackURL as properly encoded query params
        assertTrue(url.get().contains("deviceId=CAB-99"));
        // callbackURL itself must be URL-encoded in the outer query string
        assertTrue(url.get().contains("callbackURL="));
        String decoded = URLDecoder.decode(url.get(), StandardCharsets.UTF_8);
        assertTrue(decoded.contains("merchant=7"));
        assertTrue(decoded.contains("event=release"));
    }

    @Test
    void bodylessQueryRentalExtractsTradeNoFromDataNode() {
        var configurations = mock(VendingConnectorConfigurationService.class);
        Contract contract = basicAuthContract();
        Operation operation =
                new Operation(
                        "QUERY_RENTAL",
                        "POST",
                        "/rent/order/query",
                        null,
                        "",
                        "code",
                        "0",
                        "data.tradeNo",
                        "msg",
                        "IMMEDIATE");
        when(configurations.require(7L, "CHARGENOW")).thenReturn(contract);
        when(configurations.requireOperation(7L, "CHARGENOW", "QUERY_RENTAL"))
                .thenReturn(operation);

        AtomicReference<String> url = new AtomicReference<>();
        Common.setOutboundHttpExecutor(
                (m, u, d, h) -> {
                    url.set(u);
                    HttpRequestResponse resp = new HttpRequestResponse();
                    resp.setStatusCode(200);
                    resp.setResponse("{\"code\":0,\"data\":{\"tradeNo\":\"TRD-456\"}}");
                    return resp;
                });

        var adapter = new ChargeNowVendingConnectorAdapter(configurations, new ObjectMapper());
        var result =
                adapter.execute(
                        new VendingConnectorAdapter.VendingCommand(
                                7L,
                                55L,
                                "CAB-99",
                                "VEND-QR-1",
                                "QUERY_RENTAL",
                                Map.of("providerReference", "TRD-456")));

        assertTrue(result.success());
        assertEquals("TRD-456", result.providerReference());
        assertEquals("COMPLETED", result.status());
        assertTrue(url.get().contains("tradeNo=TRD-456"));
    }

    @Test
    void bodylessGetRentalDetail() {
        var configurations = mock(VendingConnectorConfigurationService.class);
        Contract contract = basicAuthContract();
        Operation operation =
                new Operation(
                        "GET_RENTAL_DETAIL",
                        "GET",
                        "/rent/order/detail",
                        null,
                        "",
                        "code",
                        "0",
                        "data.tradeNo",
                        "msg",
                        "IMMEDIATE");
        when(configurations.require(7L, "CHARGENOW")).thenReturn(contract);
        when(configurations.requireOperation(7L, "CHARGENOW", "GET_RENTAL_DETAIL"))
                .thenReturn(operation);

        AtomicReference<String> method = new AtomicReference<>();
        Common.setOutboundHttpExecutor(
                (m, u, d, h) -> {
                    method.set(m);
                    HttpRequestResponse resp = new HttpRequestResponse();
                    resp.setStatusCode(200);
                    resp.setResponse("{\"code\":0,\"data\":{\"tradeNo\":\"TRD-789\"}}");
                    return resp;
                });

        var adapter = new ChargeNowVendingConnectorAdapter(configurations, new ObjectMapper());
        var result =
                adapter.execute(
                        new VendingConnectorAdapter.VendingCommand(
                                7L,
                                55L,
                                "CAB-99",
                                "VEND-GRD-1",
                                "GET_RENTAL_DETAIL",
                                Map.of("providerReference", "TRD-789")));

        assertTrue(result.success());
        assertEquals("GET", method.get());
        assertEquals("TRD-789", result.providerReference());
    }

    @Test
    void bodylessCloseRental() {
        var configurations = mock(VendingConnectorConfigurationService.class);
        Contract contract = basicAuthContract();
        Operation operation =
                new Operation(
                        "CLOSE_RENTAL",
                        "POST",
                        "/rent/order/close",
                        null,
                        "",
                        "code",
                        "0",
                        "data.tradeNo",
                        "msg",
                        "IMMEDIATE");
        when(configurations.require(7L, "CHARGENOW")).thenReturn(contract);
        when(configurations.requireOperation(7L, "CHARGENOW", "CLOSE_RENTAL"))
                .thenReturn(operation);

        Common.setOutboundHttpExecutor(
                (m, u, d, h) -> {
                    HttpRequestResponse resp = new HttpRequestResponse();
                    resp.setStatusCode(200);
                    resp.setResponse("{\"code\":0,\"data\":{\"tradeNo\":\"TRD-CLOSE\"}}");
                    return resp;
                });

        var adapter = new ChargeNowVendingConnectorAdapter(configurations, new ObjectMapper());
        var result =
                adapter.execute(
                        new VendingConnectorAdapter.VendingCommand(
                                7L,
                                55L,
                                "CAB-99",
                                "VEND-CR-1",
                                "CLOSE_RENTAL",
                                Map.of("providerReference", "TRD-CLOSE")));

        assertTrue(result.success());
        assertEquals("COMPLETED", result.status());
    }

    @Test
    void queryDeviceBodylessGet() {
        var configurations = mock(VendingConnectorConfigurationService.class);
        Contract contract = basicAuthContract();
        Operation operation =
                new Operation(
                        "QUERY_DEVICE",
                        "GET",
                        "/rent/cabinet/query",
                        null,
                        "",
                        "code",
                        "0",
                        "data.cabinetId",
                        "msg",
                        "IMMEDIATE");
        when(configurations.require(7L, "CHARGENOW")).thenReturn(contract);
        when(configurations.requireOperation(7L, "CHARGENOW", "QUERY_DEVICE"))
                .thenReturn(operation);

        AtomicReference<String> method = new AtomicReference<>();
        Common.setOutboundHttpExecutor(
                (m, u, d, h) -> {
                    method.set(m);
                    HttpRequestResponse resp = new HttpRequestResponse();
                    resp.setStatusCode(200);
                    resp.setResponse("{\"code\":0,\"data\":{\"cabinetId\":\"CAB-99\"}}");
                    return resp;
                });

        var adapter = new ChargeNowVendingConnectorAdapter(configurations, new ObjectMapper());
        var result =
                adapter.execute(
                        new VendingConnectorAdapter.VendingCommand(
                                7L, 55L, "CAB-99", "VEND-QD-1", "QUERY_DEVICE", Map.of()));

        assertTrue(result.success());
        assertEquals("GET", method.get());
        assertEquals("CAB-99", result.providerReference());
    }

    @Test
    void callbackUrlContainingQueryParametersIsProperlyEncoded() {
        var configurations = mock(VendingConnectorConfigurationService.class);
        Contract contract = basicAuthContract();
        Operation operation =
                new Operation(
                        "RELEASE_ASSET",
                        "POST",
                        "/rent/order/create",
                        null,
                        "",
                        "code",
                        "0",
                        "data.tradeNo",
                        "msg",
                        "CALLBACK");
        when(configurations.require(7L, "CHARGENOW")).thenReturn(contract);
        when(configurations.requireOperation(7L, "CHARGENOW", "RELEASE_ASSET"))
                .thenReturn(operation);

        AtomicReference<String> urlCaptured = new AtomicReference<>();
        Common.setOutboundHttpExecutor(
                (m, u, d, h) -> {
                    urlCaptured.set(u);
                    HttpRequestResponse resp = new HttpRequestResponse();
                    resp.setStatusCode(200);
                    resp.setResponse("{\"code\":0,\"data\":{\"tradeNo\":\"TN-1\"}}");
                    return resp;
                });

        var adapter = new ChargeNowVendingConnectorAdapter(configurations, new ObjectMapper());
        adapter.execute(
                new VendingConnectorAdapter.VendingCommand(
                        7L,
                        55L,
                        "D1",
                        "CMD-1",
                        "RELEASE_ASSET",
                        Map.of(
                                "callbackUrl",
                                "https://cpay.example/api/v2/vending/callbacks/chargenow/rentals?m=7&type=release")));

        // The callbackURL value must be percent-encoded inside the outer URL query string.
        // UriComponentsBuilder handles this: the inner '?' becomes %3F.
        String captured = urlCaptured.get();
        assertTrue(captured.contains("callbackURL="));
        // The decoded URL must contain the inner query string intact
        String decoded = URLDecoder.decode(captured, StandardCharsets.UTF_8);
        assertTrue(decoded.contains("type=release"));
    }

    @Test
    void missingTradeNoReturnsCommandReferenceAsFallback() {
        var configurations = mock(VendingConnectorConfigurationService.class);
        Contract contract = basicAuthContract();
        Operation operation =
                new Operation(
                        "QUERY_RENTAL",
                        "POST",
                        "/rent/order/query",
                        null,
                        "",
                        "code",
                        "0",
                        "data.tradeNo",
                        "msg",
                        "IMMEDIATE");
        when(configurations.require(7L, "CHARGENOW")).thenReturn(contract);
        when(configurations.requireOperation(7L, "CHARGENOW", "QUERY_RENTAL"))
                .thenReturn(operation);

        Common.setOutboundHttpExecutor(
                (m, u, d, h) -> {
                    HttpRequestResponse resp = new HttpRequestResponse();
                    resp.setStatusCode(200);
                    // no data.tradeNo
                    resp.setResponse("{\"code\":0,\"data\":{}}");
                    return resp;
                });

        var adapter = new ChargeNowVendingConnectorAdapter(configurations, new ObjectMapper());
        var result =
                adapter.execute(
                        new VendingConnectorAdapter.VendingCommand(
                                7L, 55L, "CAB-99", "VEND-FALLBACK-1", "QUERY_RENTAL", Map.of()));

        assertTrue(result.success());
        assertEquals("VEND-FALLBACK-1", result.providerReference());
    }

    @Test
    void providerCodeNonZeroIsFailure() {
        var configurations = mock(VendingConnectorConfigurationService.class);
        Contract contract = basicAuthContract();
        Operation operation =
                new Operation(
                        "QUERY_RENTAL",
                        "POST",
                        "/rent/order/query",
                        null,
                        "",
                        "code",
                        "0",
                        "data.tradeNo",
                        "msg",
                        "IMMEDIATE");
        when(configurations.require(7L, "CHARGENOW")).thenReturn(contract);
        when(configurations.requireOperation(7L, "CHARGENOW", "QUERY_RENTAL"))
                .thenReturn(operation);

        Common.setOutboundHttpExecutor(
                (m, u, d, h) -> {
                    HttpRequestResponse resp = new HttpRequestResponse();
                    resp.setStatusCode(200);
                    resp.setResponse("{\"code\":40001,\"msg\":\"invalid tradeNo\"}");
                    return resp;
                });

        var adapter = new ChargeNowVendingConnectorAdapter(configurations, new ObjectMapper());
        var result =
                adapter.execute(
                        new VendingConnectorAdapter.VendingCommand(
                                7L, 55L, "CAB-99", "VEND-FAIL-1", "QUERY_RENTAL", Map.of()));

        assertFalse(result.success());
        assertEquals("FAILED", result.status());
    }

    @Test
    void providerTimeoutProducesTransportError() {
        var configurations = mock(VendingConnectorConfigurationService.class);
        Contract contract = basicAuthContract();
        Operation operation =
                new Operation(
                        "QUERY_DEVICE",
                        "GET",
                        "/rent/cabinet/query",
                        null,
                        "",
                        "code",
                        "0",
                        "data.cabinetId",
                        "msg",
                        "IMMEDIATE");
        when(configurations.require(7L, "CHARGENOW")).thenReturn(contract);
        when(configurations.requireOperation(7L, "CHARGENOW", "QUERY_DEVICE"))
                .thenReturn(operation);

        Common.setOutboundHttpExecutor(
                (m, u, d, h) -> {
                    HttpRequestResponse resp = new HttpRequestResponse();
                    resp.setStatusCode(0);
                    resp.setErrorMessage("Connection timed out");
                    return resp;
                });

        var adapter = new ChargeNowVendingConnectorAdapter(configurations, new ObjectMapper());
        var result =
                adapter.execute(
                        new VendingConnectorAdapter.VendingCommand(
                                7L, 55L, "CAB-99", "VEND-TMO-1", "QUERY_DEVICE", Map.of()));

        assertFalse(result.success());
        assertEquals("FAILED", result.status());
        assertTrue(result.message().contains("Connection timed out"));
    }

    @Test
    void provider5xxProducesFailure() {
        var configurations = mock(VendingConnectorConfigurationService.class);
        Contract contract = basicAuthContract();
        Operation operation =
                new Operation(
                        "RELEASE_ASSET",
                        "POST",
                        "/rent/order/create",
                        null,
                        "",
                        "code",
                        "0",
                        "data.tradeNo",
                        "msg",
                        "CALLBACK");
        when(configurations.require(7L, "CHARGENOW")).thenReturn(contract);
        when(configurations.requireOperation(7L, "CHARGENOW", "RELEASE_ASSET"))
                .thenReturn(operation);

        Common.setOutboundHttpExecutor(
                (m, u, d, h) -> {
                    HttpRequestResponse resp = new HttpRequestResponse();
                    resp.setStatusCode(503);
                    resp.setResponse("Service Unavailable");
                    return resp;
                });

        var adapter = new ChargeNowVendingConnectorAdapter(configurations, new ObjectMapper());
        var result =
                adapter.execute(
                        new VendingConnectorAdapter.VendingCommand(
                                7L,
                                55L,
                                "CAB-99",
                                "VEND-5XX-1",
                                "RELEASE_ASSET",
                                Map.of("rentalReference", "R1")));

        assertFalse(result.success());
        assertTrue(result.message().contains("503"));
    }

    @Test
    void invalidProviderJsonReturnsEmptyNode() {
        var configurations = mock(VendingConnectorConfigurationService.class);
        Contract contract = basicAuthContract();
        Operation operation =
                new Operation(
                        "QUERY_RENTAL",
                        "POST",
                        "/rent/order/query",
                        null,
                        "",
                        "code",
                        "0",
                        "data.tradeNo",
                        "msg",
                        "IMMEDIATE");
        when(configurations.require(7L, "CHARGENOW")).thenReturn(contract);
        when(configurations.requireOperation(7L, "CHARGENOW", "QUERY_RENTAL"))
                .thenReturn(operation);

        Common.setOutboundHttpExecutor(
                (m, u, d, h) -> {
                    HttpRequestResponse resp = new HttpRequestResponse();
                    resp.setStatusCode(200);
                    resp.setResponse("NOT_JSON{{{");
                    return resp;
                });

        var adapter = new ChargeNowVendingConnectorAdapter(configurations, new ObjectMapper());
        var result =
                adapter.execute(
                        new VendingConnectorAdapter.VendingCommand(
                                7L, 55L, "CAB-99", "VEND-BAD-1", "QUERY_RENTAL", Map.of()));

        // HTTP was 200 but contract field missing → treated as failure
        assertFalse(result.success());
    }

    private Contract basicAuthContract() {
        return new Contract(
                7L,
                "CHARGENOW",
                "https://developer.chargenow.top/cdb-open-api/v1",
                "BASIC",
                "",
                "",
                "",
                "BASE64",
                "",
                "my-username",
                "my-password",
                "cb-secret",
                "STATIC_TOKEN_HEADER",
                "BASE64",
                "X-Callback-Token",
                "",
                "",
                "eventType",
                "eventId",
                "deviceId",
                "rentalReference",
                "assetCode",
                "availableCount");
    }
}
