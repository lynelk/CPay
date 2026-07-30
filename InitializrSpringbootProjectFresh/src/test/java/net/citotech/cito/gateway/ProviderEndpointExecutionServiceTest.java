package net.citotech.cito.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import net.citotech.cito.Model.GateWayResponse;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * Covers the generic executor that the Yo! Payments adapter (and other execution-service-backed
 * channels) delegates every collect/payout call to. This is one of the newest money-movement paths
 * and previously had no test coverage (see audit K6): the sandbox-scenario fallback used when no
 * live endpoint is configured, and the production guard that refuses to silently fall back to
 * sandbox behaviour when a real endpoint is required.
 */
class ProviderEndpointExecutionServiceTest {

    @Test
    void sandboxCollectionDefaultsToSuccessfulWhenNoEndpointConfigured() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        ProviderTokenStoreService tokenStoreService = mock(ProviderTokenStoreService.class);
        ProviderEndpointExecutionService service =
                new ProviderEndpointExecutionService(
                        jdbcTemplate, tokenStoreService, new ChannelCircuitBreaker());
        PaymentGatewayRequest request =
                new PaymentGatewayRequest(
                        "M1", "256770000001", 1000.0, "REF-1", "test", "cb", Map.of());

        GateWayResponse response =
                service.execute("yo_payments", "Yo! Payments", "COLLECT", request);

        assertThat(response.getStatus()).isEqualTo("SUCCESS");
        assertThat(response.getTransactionStatus()).isEqualTo("SUCCESSFUL");
        verify(jdbcTemplate)
                .update(contains("provider_endpoint_runs"), any(MapSqlParameterSource.class));
    }

    @Test
    void sandboxCollectionFailsForMagicDeclinedAccountSuffix() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        ProviderTokenStoreService tokenStoreService = mock(ProviderTokenStoreService.class);
        ProviderEndpointExecutionService service =
                new ProviderEndpointExecutionService(
                        jdbcTemplate, tokenStoreService, new ChannelCircuitBreaker());
        PaymentGatewayRequest request =
                new PaymentGatewayRequest(
                        "M1", "256770000002", 1000.0, "REF-2", "test", "cb", Map.of());

        GateWayResponse response =
                service.execute("yo_payments", "Yo! Payments", "COLLECT", request);

        assertThat(response.getTransactionStatus()).isEqualTo("FAILED");
    }

    @Test
    void sandboxPayoutFailsForMagicDeclinedAccountSuffix() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        ProviderTokenStoreService tokenStoreService = mock(ProviderTokenStoreService.class);
        ProviderEndpointExecutionService service =
                new ProviderEndpointExecutionService(
                        jdbcTemplate, tokenStoreService, new ChannelCircuitBreaker());
        PaymentGatewayRequest request =
                new PaymentGatewayRequest(
                        "M1", "256770000002", 500.0, "REF-3", "test", "cb", Map.of());

        GateWayResponse response =
                service.execute("yo_payments", "Yo! Payments", "PAYOUT", request);

        assertThat(response.getTransactionStatus()).isEqualTo("FAILED");
    }

    @Test
    void refusesToFallBackToSandboxWhenProductionEndpointIsMissing() {
        // A misconfigured production channel must fail loudly rather than silently resolve
        // real payments through the canned sandbox scenarios.
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        ProviderTokenStoreService tokenStoreService = mock(ProviderTokenStoreService.class);
        ProviderEndpointExecutionService service =
                new ProviderEndpointExecutionService(
                        jdbcTemplate, tokenStoreService, new ChannelCircuitBreaker());
        PaymentGatewayRequest request =
                new PaymentGatewayRequest(
                        "M1",
                        "256770000001",
                        1000.0,
                        "REF-4",
                        "test",
                        "cb",
                        Map.of("gatewayState", "PRODUCTION"));

        assertThatThrownBy(() -> service.execute("yo_payments", "Yo! Payments", "COLLECT", request))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("Provider endpoint URL is required in production mode");
    }

    // Audit C9: Yo! Payments has no inbound webhook wired into CPay - the only place it ever
    // receives provider-controlled data is this synchronous HTTP response, and every response
    // header used to be discarded here entirely. These cover the wiring that now verifies the
    // response signature (when the provider sends one and a secret is configured) before a 2xx
    // response is trusted as a real success.

    @Test
    void acceptsALiveYoPaymentsResponseWhenItsSignatureMatchesTheConfiguredApiKey()
            throws Exception {
        String responseBody = "{\"status\":\"ACCEPTED\"}";
        HttpServer server = startServer(responseBody, sign("yo-api-key", responseBody));
        try {
            NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
            ProviderTokenStoreService tokenStoreService = mock(ProviderTokenStoreService.class);
            ProviderEndpointExecutionService service =
                    new ProviderEndpointExecutionService(
                            jdbcTemplate, tokenStoreService, new ChannelCircuitBreaker());
            Map<String, String> metadata = new HashMap<>();
            metadata.put(
                    "collectUrl", "http://localhost:" + server.getAddress().getPort() + "/collect");
            metadata.put("apiKey", "yo-api-key");
            PaymentGatewayRequest request =
                    new PaymentGatewayRequest(
                            "M1", "256770000000", 1000.0, "REF-5", "test", "cb", metadata);

            GateWayResponse response =
                    service.execute("yo_payments", "Yo! Payments", "COLLECT", request);

            assertThat(response.getStatus()).isEqualTo("SUCCESS");
            assertThat(response.getTransactionStatus()).isEqualTo("SUBMITTED");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void rejectsALiveYoPaymentsResponseWhoseSignatureDoesNotMatch() throws Exception {
        String responseBody = "{\"status\":\"ACCEPTED\"}";
        HttpServer server = startServer(responseBody, sign("a-different-secret", responseBody));
        try {
            NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
            ProviderTokenStoreService tokenStoreService = mock(ProviderTokenStoreService.class);
            ProviderEndpointExecutionService service =
                    new ProviderEndpointExecutionService(
                            jdbcTemplate, tokenStoreService, new ChannelCircuitBreaker());
            Map<String, String> metadata = new HashMap<>();
            metadata.put(
                    "collectUrl", "http://localhost:" + server.getAddress().getPort() + "/collect");
            metadata.put("apiKey", "yo-api-key");
            PaymentGatewayRequest request =
                    new PaymentGatewayRequest(
                            "M1", "256770000000", 1000.0, "REF-6", "test", "cb", metadata);

            GateWayResponse response =
                    service.execute("yo_payments", "Yo! Payments", "COLLECT", request);

            assertThat(response.getStatus()).isEqualTo("FAILED");
            assertThat(response.getTransactionStatus()).isEqualTo("FAILED");
            // Audit C6/J7: the merchant-facing message must be the dedicated "response not
            // verified as authentic" translation
            // (ProviderErrorTranslator.SIGNATURE_VERIFICATION_FAILED),
            // not the generic HTTP-status-based provider-declined message and never the raw
            // signature-failure diagnostic string, which stays internal (provider_endpoint_runs).
            assertThat(response.getMessage())
                    .contains(
                            ProviderErrorTranslator.SIGNATURE_VERIFICATION_FAILED.merchantMessage())
                    .doesNotContain("signature verification failed");
            verify(jdbcTemplate)
                    .update(contains("provider_endpoint_runs"), any(MapSqlParameterSource.class));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void doesNotBreakExistingYoPaymentsIntegrationsThatHaveNoApiKeyConfiguredYet()
            throws Exception {
        // A channel configured before this signature check existed has no apiKey saved as a
        // signing secret yet - a garbage/absent signature header must not suddenly fail calls
        // that would have succeeded before this change.
        String responseBody = "{\"status\":\"ACCEPTED\"}";
        HttpServer server = startServer(responseBody, "garbage-signature-from-somewhere");
        try {
            NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
            ProviderTokenStoreService tokenStoreService = mock(ProviderTokenStoreService.class);
            ProviderEndpointExecutionService service =
                    new ProviderEndpointExecutionService(
                            jdbcTemplate, tokenStoreService, new ChannelCircuitBreaker());
            Map<String, String> metadata = new HashMap<>();
            metadata.put(
                    "collectUrl", "http://localhost:" + server.getAddress().getPort() + "/collect");
            PaymentGatewayRequest request =
                    new PaymentGatewayRequest(
                            "M1", "256770000000", 1000.0, "REF-7", "test", "cb", metadata);

            GateWayResponse response =
                    service.execute("yo_payments", "Yo! Payments", "COLLECT", request);

            assertThat(response.getStatus()).isEqualTo("SUCCESS");
            assertThat(response.getTransactionStatus()).isEqualTo("SUBMITTED");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void doesNotApplySignatureVerificationToOtherChannels() throws Exception {
        // Scoped narrowly to yo_payments: another execution-service-backed channel with an
        // apiKey configured and a mismatched header must be unaffected by this check.
        String responseBody = "{\"status\":\"ACCEPTED\"}";
        HttpServer server = startServer(responseBody, "garbage-signature-from-somewhere");
        try {
            NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
            ProviderTokenStoreService tokenStoreService = mock(ProviderTokenStoreService.class);
            ProviderEndpointExecutionService service =
                    new ProviderEndpointExecutionService(
                            jdbcTemplate, tokenStoreService, new ChannelCircuitBreaker());
            Map<String, String> metadata = new HashMap<>();
            metadata.put(
                    "collectUrl", "http://localhost:" + server.getAddress().getPort() + "/collect");
            metadata.put("apiKey", "some-other-channel-key");
            PaymentGatewayRequest request =
                    new PaymentGatewayRequest(
                            "M1", "256770000000", 1000.0, "REF-8", "test", "cb", metadata);

            GateWayResponse response =
                    service.execute("some_other_channel", "Some Other Channel", "COLLECT", request);

            assertThat(response.getStatus()).isEqualTo("SUCCESS");
            assertThat(response.getTransactionStatus()).isEqualTo("SUBMITTED");
        } finally {
            server.stop(0);
        }
    }

    private HttpServer startServer(String responseBody, String signatureHeaderValue)
            throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext(
                "/collect",
                exchange -> {
                    byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders()
                            .set(YoPaymentsCallbackVerifier.SIGNATURE_HEADER, signatureHeaderValue);
                    exchange.sendResponseHeaders(200, bytes.length);
                    exchange.getResponseBody().write(bytes);
                    exchange.close();
                });
        server.start();
        return server;
    }

    private static String sign(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getEncoder()
                    .encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
